package ru.mentoring.cinema.storage;

import ru.mentoring.cinema.model.Delivery;
import ru.mentoring.cinema.model.NotificationClaim;
import ru.mentoring.cinema.model.NotificationJob;
import ru.mentoring.cinema.model.Report;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static ru.mentoring.cinema.model.NotificationState.*;

/**
 * Внутренний outbox хранилища покупок. Использует ровно тот же lock, что заказ и места.
 * Не является самостоятельным репозиторием: регистрация разрешена только внутри операции покупки.
 * jobs хранит состояния, pendingIds — обновляемый под тем же lock индекс ожидающих заданий.
 */
public final class InMemoryOutbox {
    private final ReentrantLock transaction;
    private final Condition notificationAvailable;
    private final Condition notificationCompleted;
    private final Map<UUID, NotificationJob> jobs = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> pendingIds = new LinkedHashSet<>();
    private int inFlight;

    /**
     * Привязывает все операции outbox к блокировке владельца.
     *
     * @param transaction общая блокировка заказа, мест и outbox
     */
    public InMemoryOutbox(ReentrantLock transaction) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        notificationAvailable = transaction.newCondition();
        notificationCompleted = transaction.newCondition();
    }

    /**
     * Регистрирует подготовленное задание до остальных записей покупки под уже захваченным lock.
     * Worker сможет увидеть его только после освобождения общей блокировки.
     *
     * @param job новое задание PENDING без владельца и результата
     */
    public void register(NotificationJob job) {
        if (!transaction.isHeldByCurrentThread())
            throw new IllegalMonitorStateException("Registration requires the purchase lock");
        Objects.requireNonNull(job, "job");
        if (job.state() != PENDING || jobs.containsKey(job.event().eventId()))
            throw new IllegalArgumentException("Expected a new PENDING job");
        jobs.put(job.event().eventId(), job);
        pendingIds.add(job.event().eventId());
        // Сигнал — только подсказка: условие проверяется в цикле под тем же lock.
        // Сохраняем signalAll: все ожидающие перепроверят наличие работы.
        notificationAvailable.signalAll();
    }

    /**
     * Возвращает неизменяемый снимок всех состояний в порядке регистрации.
     *
     * @return снимки заданий
     */
    public List<NotificationJob> findAllNotificationJobs() {
        transaction.lock();
        try {
            return List.copyOf(jobs.values());
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Проверяет наличие PENDING за O(1), не формируя полный отчет.
     *
     * @return true, если есть задания для захвата
     */
    public boolean hasPendingNotifications() {
        transaction.lock();
        try {
            return !pendingIds.isEmpty();
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Захватывает первое ожидающее задание за O(1), не обходя историю SENT/FAILED.
     * Таймаут включает получение общего lock. Ноль не допускает ожидания.
     *
     * @param timeout общий бюджет ожидания блокировки и задания
     * @return захват или пустой результат
     * @throws InterruptedException если ожидание прервано
     */
    public Optional<NotificationClaim> claimNextNotification(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("Negative timeout");
        long budget = timeout.toNanos();
        long startedAt = System.nanoTime();
        if (!transaction.tryLock(budget, TimeUnit.NANOSECONDS)) return Optional.empty();
        try {
            while (pendingIds.isEmpty()) {
                long remaining = budget - (System.nanoTime() - startedAt);
                if (remaining <= 0) return Optional.empty();
                notificationAvailable.awaitNanos(remaining);
            }
            UUID eventId = pendingIds.getFirst();
            NotificationJob job = jobs.get(eventId);
            var claim = new NotificationClaim(job.event(), UUID.randomUUID());
            var claimedJob = new NotificationJob(job.event(), IN_FLIGHT, claim.token(), null);
            jobs.put(eventId, claimedJob);
            pendingIds.remove(eventId);
            inFlight++;
            return Optional.of(claim);
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Принимает итог только от текущего владельца, затем будит наблюдателей терминальных состояний.
     *
     * @param claim    право текущей попытки
     * @param delivery результат текущего цикла отправки
     * @return true, если результат принят
     */
    public boolean completeNotification(NotificationClaim claim, Delivery delivery) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(delivery, "delivery");
        if (!claim.event().equals(delivery.event()) || delivery.attempts() <= 0 || delivery.error() == null)
            throw new IllegalArgumentException("Invalid delivery result");
        transaction.lock();
        try {
            NotificationJob job = jobs.get(claim.event().eventId());
            if (!isCurrentClaim(job, claim)) return false;
            jobs.put(job.event().eventId(), new NotificationJob(job.event(),
                    delivery.delivered() ? SENT : FAILED, null, delivery));
            inFlight--;
            notificationCompleted.signalAll();
            return true;
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает прерванное задание в конец ожидающих только по запросу его владельца.
     * Не сигналит notificationCompleted: число SENT + FAILED не изменилось.
     *
     * @param claim захват завершающегося worker
     * @return true, если задание возвращено
     */
    public boolean releaseNotification(NotificationClaim claim) {
        Objects.requireNonNull(claim, "claim");
        transaction.lock();
        try {
            NotificationJob job = jobs.get(claim.event().eventId());
            if (!isCurrentClaim(job, claim)) return false;
            jobs.put(job.event().eventId(), new NotificationJob(job.event(), PENDING, null, null));
            inFlight--;
            pendingIds.add(job.event().eventId());
            notificationAvailable.signalAll();
            return true;
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает FAILED в конец ожидающих с прежним eventId и без прежнего результата.
     *
     * @param eventId идентификатор события
     * @return true только при переходе FAILED в PENDING
     */
    public boolean retryFailedNotification(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        transaction.lock();
        try {
            NotificationJob job = jobs.get(eventId);
            if (job == null || job.state() != FAILED) return false;
            jobs.put(eventId, new NotificationJob(job.event(), PENDING, null, null));
            pendingIds.add(eventId);
            notificationAvailable.signalAll();
            return true;
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Проверяет совпадение события и token выполняемого задания.
     *
     * @param job   текущее задание или null
     * @param claim проверяемый захват
     * @return true только для текущего владельца
     */
    private boolean isCurrentClaim(NotificationJob job, NotificationClaim claim) {
        return job != null && job.state() == IN_FLIGHT
                && job.event().equals(claim.event()) && job.claimToken().equals(claim.token());
    }

    /**
     * Создает полный отчет по явному запросу; workers не используют его для выбора работы.
     *
     * @return ожидающие, выполняемые и текущие терминальные результаты SENT/FAILED
     */
    public Report getNotificationReport() {
        transaction.lock();
        try {
            var completed = jobs.values().stream().map(NotificationJob::delivery).filter(Objects::nonNull).toList();
            return new Report(pendingIds.size(), inFlight, completed);
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Ждет число текущих терминальных состояний SENT + FAILED с проверкой счетчика за O(1).
     * После ручного повтора FAILED терминальных состояний становится меньше.
     *
     * @param count   требуемое число терминальных состояний
     * @param timeout общий бюджет ожидания блокировки и результатов
     * @return true, если число достигнуто вовремя
     * @throws InterruptedException если ожидание прервано
     */
    public boolean awaitProcessedNotifications(int count, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (count < 0 || timeout.isNegative()) throw new IllegalArgumentException("Invalid wait");
        long budget = timeout.toNanos();
        if (count == 0) return true;
        long startedAt = System.nanoTime();
        if (!transaction.tryLock(budget, TimeUnit.NANOSECONDS)) return false;
        try {
            while (jobs.size() - pendingIds.size() - inFlight < count) {
                long remaining = budget - (System.nanoTime() - startedAt);
                if (remaining <= 0) return false;
                notificationCompleted.awaitNanos(remaining);
            }
            return true;
        } finally {
            transaction.unlock();
        }
    }
}
