package ru.mentoring.cinema.notification;

import ru.mentoring.cinema.model.Delivery;
import ru.mentoring.cinema.model.OrderCreated;
import ru.mentoring.cinema.storage.InMemoryBookingStore;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Организует отправку подтверждений покупателям, не задерживая новые покупки.
 */
public final class NotificationDispatcher implements AutoCloseable {
    private final InMemoryBookingStore store;
    private final RecordingNotificationSender sender;
    private final int workers;
    private final int maxAttempts;
    private final ExecutorService notificationWorkers;
    private final AtomicBoolean running = new AtomicBoolean();
    private boolean started;
    private boolean closed;

    /**
     * Настраивает отправку подтверждений и допустимое число попыток доставки.
     *
     * @param store       хранилище заказов, очереди и результатов доставки
     * @param sender      учебный отправщик уведомлений
     * @param workers     максимальное число одновременных отправок
     * @param maxAttempts максимум попыток для одного события
     */
    public NotificationDispatcher(InMemoryBookingStore store, RecordingNotificationSender sender,
                                  int workers, int maxAttempts) {
        if (workers <= 0 || maxAttempts <= 0)
            throw new IllegalArgumentException("Positive worker/attempt counts required");
        this.store = store;
        this.sender = sender;
        this.workers = workers;
        this.maxAttempts = maxAttempts;
        this.notificationWorkers = Executors.newFixedThreadPool(workers,
                Thread.ofPlatform().name("notification-", 1).factory());
    }

    /**
     * Начинает отправку подтверждений оформленных покупок.
     */
    public synchronized void startNotificationWorkers() {
        if (closed) throw new IllegalStateException("Dispatcher is closed");
        if (started) return;
        started = true;
        running.set(true);
        for (int i = 0; i < workers; i++) notificationWorkers.submit(this::processQueuedNotifications);
    }

    /**
     * Обрабатывает ожидающие подтверждения оформленных заказов.
     */
    private void processQueuedNotifications() {
        try {
            while (running.get() || store.getNotificationReport().queued() > 0) {
                var next = store.pollNextNotification(Duration.ofMillis(100));
                next.ifPresent(this::deliverNotificationWithRetries);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Отправляет уведомление с повторными попытками и сохраняет итог.
     *
     * @param event событие подтвержденной покупки
     */
    private void deliverNotificationWithRetries(OrderCreated event) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var order = store.findOrderById(event.orderId()).orElseThrow(() -> new IllegalStateException("Order missing"));
                sender.sendOrderConfirmation(event, order);
                store.recordNotificationDelivery(new Delivery(event, attempt, true, ""));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                store.recordNotificationDelivery(new Delivery(event, attempt, false, "Interrupted"));
                return;
            } catch (Exception failure) {
                if (attempt == maxAttempts) {
                    // Ошибка доставки остается в отчете и не отменяет покупку.
                    store.recordNotificationDelivery(new Delivery(event, attempt, false,
                            failure.getClass().getSimpleName()));
                    return;
                }
                try {
                    Thread.sleep(Duration.ofMillis(10L * attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    store.recordNotificationDelivery(new Delivery(event, attempt, false, "Interrupted"));
                    return;
                }
            }
        }
    }

    /**
     * Завершает работу службы уведомлений, предоставляя отправкам время на завершение.
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        running.set(false);
        // Покупки уже остановлены: завершаем накопленные уведомления и закрываем потоки.
        notificationWorkers.shutdown();
        try {
            if (!notificationWorkers.awaitTermination(5, TimeUnit.SECONDS)) {
                notificationWorkers.shutdownNow();
                if (!notificationWorkers.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IllegalStateException("Notification workers did not stop");
            }
        } catch (InterruptedException interrupted) {
            notificationWorkers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
