package ru.mentoring.cinema.notification;

import ru.mentoring.cinema.model.Delivery;
import ru.mentoring.cinema.model.NotificationClaim;
import ru.mentoring.cinema.storage.InMemoryBookingStore;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Обрабатывает outbox вне блокировки покупки; прерванные задания возвращает в ожидание.
 */
public final class NotificationDispatcher implements AutoCloseable {
    private final InMemoryBookingStore store;
    private final RecordingNotificationSender sender;
    private final int workers;
    private final int maxAttempts;
    private final long shutdownTimeoutNanos;
    private final ExecutorService notificationWorkers;
    private final AtomicBoolean running = new AtomicBoolean();
    private boolean started;
    private boolean closed;

    /**
     * Настраивает отправку с пятью секундами на штатное завершение.
     *
     * @param store хранилище заказов и outbox
     * @param sender учебный отправщик
     * @param workers число одновременно работающих отправщиков
     * @param maxAttempts число попыток в одном цикле обработки задания
     */
    public NotificationDispatcher(InMemoryBookingStore store, RecordingNotificationSender sender,
                                  int workers, int maxAttempts) {
        this(store, sender, workers, maxAttempts, Duration.ofSeconds(5));
    }

    /**
     * Настраивает отправку и ограниченное ожидание при остановке.
     *
     * @param store хранилище заказов и outbox
     * @param sender учебный отправщик
     * @param workers число одновременно работающих отправщиков
     * @param maxAttempts число попыток в одном цикле обработки задания
     * @param shutdownTimeout время на завершение накопленной работы; ноль сразу разрешает прерывание
     */
    public NotificationDispatcher(InMemoryBookingStore store, RecordingNotificationSender sender,
                                  int workers, int maxAttempts, Duration shutdownTimeout) {
        if (workers <= 0 || maxAttempts <= 0)
            throw new IllegalArgumentException("Positive worker/attempt counts required");
        this.store = Objects.requireNonNull(store, "store");
        this.sender = Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isNegative()) throw new IllegalArgumentException("Negative shutdown timeout");
        this.shutdownTimeoutNanos = shutdownTimeout.toNanos();
        this.workers = workers;
        this.maxAttempts = maxAttempts;
        this.notificationWorkers = Executors.newFixedThreadPool(workers,
                Thread.ofPlatform().name("notification-", 1).factory());
    }

    /**
     * Запускает обработку ожидающих уведомлений; повторный запуск работающей службы ничего не меняет.
     */
    public synchronized void startNotificationWorkers() {
        if (closed) throw new IllegalStateException("Dispatcher is closed");
        if (started) return;
        started = true;
        running.set(true);
        for (int i = 0; i < workers; i++) notificationWorkers.submit(this::processPendingNotifications);
    }

    /**
     * Забирает задания до остановки, после нее завершает оставшиеся PENDING.
     * Терминальные задания не удерживают цикл завершения.
     */
    private void processPendingNotifications() {
        try {
            while (!Thread.currentThread().isInterrupted()
                    && (running.get() || store.hasPendingNotifications())) {
                var next = store.claimNextNotification(Duration.ofMillis(100));
                if (next.isPresent()) deliverNotificationWithRetries(next.orElseThrow());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Выполняет ограниченные повторы с тем же eventId; завершает или освобождает свой захват.
     *
     * @param claim право обработки одного задания
     * @throws InterruptedException если отправка или пауза между попытками прервана
     */
    private void deliverNotificationWithRetries(NotificationClaim claim) throws InterruptedException {
        boolean completed = false;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Worker interrupted");
                try {
                    var order = store.findOrderById(claim.event().orderId())
                            .orElseThrow(() -> new IllegalStateException("Order missing"));
                    sender.sendOrderConfirmation(claim.event(), order);
                    completed = store.completeNotification(claim, new Delivery(claim.event(), attempt, true, ""));
                    return;
                } catch (InterruptedException interrupted) {
                    throw interrupted;
                } catch (Exception failure) {
                    // Отправщик может восстановить interrupt flag и завернуть прерывание в другое исключение.
                    if (Thread.currentThread().isInterrupted()) {
                        var interrupted = new InterruptedException("Sender interrupted");
                        interrupted.initCause(failure);
                        throw interrupted;
                    }
                    if (attempt == maxAttempts) {
                        completed = store.completeNotification(claim, new Delivery(claim.event(), attempt, false,
                                failure.getClass().getSimpleName()));
                        return;
                    }
                }
                // Пауза и внешняя отправка никогда не удерживают блокировку покупки.
                Thread.sleep(Duration.ofMillis(10L * attempt));
            }
        } finally {
            // Даже прерывание или непредвиденный Error не удаляет захваченное задание.
            if (!completed) store.releaseNotification(claim);
        }
    }

    /**
     * После остановки покупателей завершает задания либо прерывает workers.
     * После фактического выхода прерванного worker его задание снова PENDING.
     * Не отбирает задания у отправщика, игнорирующего прерывание.
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        running.set(false);
        notificationWorkers.shutdown();
        boolean callerInterrupted = false;
        try {
            if (!notificationWorkers.awaitTermination(shutdownTimeoutNanos, TimeUnit.NANOSECONDS)) {
                notificationWorkers.shutdownNow();
                if (!notificationWorkers.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IllegalStateException("Notification workers did not stop; their claims remain owned");
            }
        } catch (InterruptedException interrupted) {
            callerInterrupted = true;
            notificationWorkers.shutdownNow();
            try {
                if (!notificationWorkers.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IllegalStateException("Notification workers did not stop; their claims remain owned");
            } catch (InterruptedException interruptedAgain) {
                throw new IllegalStateException("Interrupted while waiting for notification workers", interruptedAgain);
            }
        } finally {
            if (callerInterrupted) Thread.currentThread().interrupt();
        }
    }
}
