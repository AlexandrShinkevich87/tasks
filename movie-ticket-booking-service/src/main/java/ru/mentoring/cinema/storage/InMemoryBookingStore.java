package ru.mentoring.cinema.storage;

import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static ru.mentoring.cinema.model.Code.*;
import static ru.mentoring.cinema.model.NotificationState.PENDING;

/**
 * Согласованно хранит покупки, занятые места и задания уведомлений в памяти одного процесса.
 * Блокировка защищает потоки, но не обеспечивает сохранность после аварии процесса.
 */
public final class InMemoryBookingStore {
    private final ReentrantLock transaction = new ReentrantLock();
    private final Map<UUID, Order> ordersById = new LinkedHashMap<>();
    private final Map<CustomerPurchaseRequestKey, Confirmed> confirmedPurchasesByCustomerPurchaseRequestKey = new HashMap<>();
    private final Map<Long, Set<Seat>> soldSeatsByScreeningId = new HashMap<>();
    private final InMemoryOutbox outbox = new InMemoryOutbox(transaction);

    /**
     * Создает пустое хранилище; число ожидающих уведомлений ограничено доступной памятью.
     */
    public InMemoryBookingStore() {
    }

    /**
     * Оформляет все выбранные места и регистрирует PENDING-уведомление под одной блокировкой.
     * Ожидание или ошибка отправщика не препятствуют новым покупкам.
     * Фабрика выполняется под общей блокировкой: только короткое создание заказа, без I/O,
     * ожиданий и повторных вызовов операций хранилища. Проверка начала сеанса остается внутри нее.
     *
     * @param request запрос покупки
     * @param factory создание заказа после проверки повтора и занятости мест
     * @return новый заказ или результат точного повтора
     * @throws BookingException если запрос изменен или хотя бы одно место занято
     */
    public Order confirmPurchase(BookingRequest request, Supplier<Order> factory) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(factory, "factory");
        transaction.lock();
        try {
            var purchaseRequestKey = new CustomerPurchaseRequestKey(request.customer().id(), request.requestId());
            Confirmed previous = confirmedPurchasesByCustomerPurchaseRequestKey.get(purchaseRequestKey);
            if (previous != null) {
                if (!previous.request().equals(request))
                    throw new BookingException(REQUEST_CONFLICT, "Request ID was used with different data");
                return previous.order();
            }

            Set<Seat> occupied = soldSeatsByScreeningId.getOrDefault(request.screeningId(), Set.of());
            for (Seat seat : request.seats()) {
                if (occupied.contains(seat))
                    throw new BookingException(SEAT_TAKEN, "Seat already sold: " + seat);
            }

            // Все бизнес-проверки и подготовка заказа и события — до изменения общего состояния.
            Order order = Objects.requireNonNull(factory.get(), "Order factory returned null");
            if (ordersById.containsKey(order.id()))
                throw new IllegalStateException("Order ID already exists");
            var event = new OrderCreated(UUID.randomUUID(), order.id(), order.createdAt());
            var job = new NotificationJob(event, PENDING, null, null);
            var confirmed = new Confirmed(request, order);
            Set<Seat> updatedSeats = soldSeatsByScreeningId.get(request.screeningId());
            if (updatedSeats == null) updatedSeats = new HashSet<>();

            // Каждый читатель и worker видит весь результат после освобождения той же блокировки.
            // Сначала регистрация outbox: ее проверки не должны оставить частичную покупку.
            outbox.register(job);
            ordersById.put(order.id(), order);
            updatedSeats.addAll(request.seats());
            soldSeatsByScreeningId.put(request.screeningId(), updatedSeats);
            confirmedPurchasesByCustomerPurchaseRequestKey.put(purchaseRequestKey, confirmed);
            return order;
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Ищет оформленную покупку по номеру заказа.
     *
     * @param id номер заказа
     * @return заказ или пустой результат
     */
    public Optional<Order> findOrderById(UUID id) {
        transaction.lock();
        try {
            return Optional.ofNullable(ordersById.get(id));
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает оформленные заказы в порядке покупки.
     *
     * @return независимый неизменяемый список заказов
     */
    public List<Order> findAllOrders() {
        transaction.lock();
        try {
            return List.copyOf(ordersById.values());
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает проданные места указанного сеанса.
     *
     * @param screeningId идентификатор сеанса
     * @return снимок проданных мест
     */
    public Set<Seat> findOccupiedSeatsByScreeningId(long screeningId) {
        transaction.lock();
        try {
            return Set.copyOf(soldSeatsByScreeningId.getOrDefault(screeningId, Set.of()));
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает снимки заданий в порядке регистрации.
     *
     * @return неизменяемые снимки всех состояний outbox
     */
    public List<NotificationJob> findAllNotificationJobs() {
        return outbox.findAllNotificationJobs();
    }

    /**
     * Проверяет наличие ожидающей работы без построения полного отчета.
     *
     * @return true, если есть PENDING
     */
    public boolean hasPendingNotifications() {
        return outbox.hasPendingNotifications();
    }

    /**
     * Закрепляет ожидающее задание за одним worker, сохраняя его в outbox.
     *
     * @param timeout общее ожидание блокировки и задания, ноль означает проверку без ожидания
     * @return захват задания или пустой результат
     * @throws InterruptedException если ожидание прервано
     */
    public Optional<NotificationClaim> claimNextNotification(Duration timeout) throws InterruptedException {
        return outbox.claimNextNotification(timeout);
    }

    /**
     * Принимает результат только текущего владельца, игнорируя устаревшие подтверждения.
     *
     * @param claim право обработки одного задания
     * @param delivery итог текущего цикла отправки
     * @return true, если результат принят
     */
    public boolean completeNotification(NotificationClaim claim, Delivery delivery) {
        return outbox.completeNotification(claim, delivery);
    }

    /**
     * Возвращает прерванное задание в ожидание; вызывать должен завершающийся владелец.
     * Число терминальных состояний не меняется, поэтому их наблюдатели не пробуждаются.
     *
     * @param claim текущий захват завершающегося worker
     * @return true, если задание возвращено
     */
    public boolean releaseNotification(NotificationClaim claim) {
        return outbox.releaseNotification(claim);
    }

    /**
     * Возвращает только FAILED в ожидание с прежним eventId и без старого результата.
     *
     * @param eventId идентификатор события
     * @return true, если повтор разрешен
     */
    public boolean retryFailedNotification(UUID eventId) {
        return outbox.retryFailedNotification(eventId);
    }

    /**
     * Строит снимок ожидающих, выполняемых и текущих терминальных результатов.
     *
     * @return отчет с результатами SENT + FAILED
     */
    public Report getNotificationReport() {
        return outbox.getNotificationReport();
    }

    /**
     * Ожидает заданное число текущих терминальных состояний SENT + FAILED.
     * После ручного повтора FAILED их число временно уменьшается.
     *
     * @param count требуемое число терминальных состояний
     * @param timeout общее время ожидания блокировки и результатов
     * @return true, если число достигнуто вовремя
     * @throws InterruptedException если ожидание прервано
     */
    public boolean awaitProcessedNotifications(int count, Duration timeout) throws InterruptedException {
        return outbox.awaitProcessedNotifications(count, timeout);
    }
}
