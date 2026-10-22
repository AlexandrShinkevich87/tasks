package ru.mentoring.cinema.storage;

import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static ru.mentoring.cinema.model.Code.*;

/**
 * Ведет учет покупок, занятых мест и подтверждений для клиентов.
 */
public final class InMemoryBookingStore {
    private final ReentrantLock transaction = new ReentrantLock();
    private final Condition notificationCompleted = transaction.newCondition();
    private final Map<UUID, Order> ordersById = new LinkedHashMap<>();
    private final Map<CustomerPurchaseRequestKey, Confirmed> confirmedPurchasesByCustomerPurchaseRequestKey = new HashMap<>();
    private final Map<Long, Set<Seat>> soldSeatsByScreeningId = new HashMap<>();
    private final ArrayBlockingQueue<OrderCreated> outgoing;
    private final Map<UUID, Delivery> deliveryResultsByEventId = new LinkedHashMap<>();

    Object lock = new Object();
    /**
     * Задает допустимое число подтверждений, ожидающих отправки.
     *
     * @param queueCapacity максимальное число ожидающих уведомлений
     */
    public InMemoryBookingStore(int queueCapacity) {
        outgoing = new ArrayBlockingQueue<>(queueCapacity);
    }

    /**
     * Оформляет все выбранные места и назначает уведомление; при отказе не продает ни одного места.
     * Можно ли продать все выбранные места и зарегистрировать заказ?
     *
     * @param request запрос покупки
     * @param factory создание заказа по данным покупки
     * @return новый заказ или результат точного повтора
     * @throws BookingException если запрос изменен, место занято или новые уведомления временно не принимаются
     */
    public Order confirmPurchase(BookingRequest request, Supplier<Order> factory) {
        transaction.lock();

        try {
            CustomerPurchaseRequestKey purchaseRequestKey = new CustomerPurchaseRequestKey(request.customer().id(), request.requestId());
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

            Order order = factory.get(); // До завершения проверок состояние не меняется.
            OrderCreated event = new OrderCreated(UUID.randomUUID(), order.id(), order.createdAt());
            // Не ждем освобождения очереди, удерживая блокировку покупки.
            // При заполненной очереди заказ не сохраняется.
            if (!outgoing.offer(event))
                throw new BookingException(QUEUE_FULL, "Notification queue is full; retry later");

            // Отправщик уже может взять событие, но прочитает заказ после освобождения блокировки.
            ordersById.put(order.id(), order);
            soldSeatsByScreeningId.computeIfAbsent(request.screeningId(), ignored -> new HashSet<>()).addAll(request.seats());
            confirmedPurchasesByCustomerPurchaseRequestKey.put(purchaseRequestKey, new Confirmed(request, order));
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
     * @return список оформленных заказов
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
     * @return проданные места сеанса
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
     * Выбирает следующее подтверждение для отправки, ожидая не дольше заданного времени.
     * Какое подтверждение нужно отправить следующим?
     *
     * @param timeout предельное время ожидания
     * @return подтверждение покупки или пустой результат, если время ожидания истекло
     * @throws InterruptedException если ожидание подтверждения для отправки прервано
     */
    public Optional<OrderCreated> pollNextNotification(Duration timeout) throws InterruptedException {
        return Optional.ofNullable(outgoing.poll(timeout.toNanos(), TimeUnit.NANOSECONDS));
    }

    /**
     * Учитывает результат доставки подтверждения; повторный результат одного события игнорирует.
     * Чем закончилась отправка подтверждения?
     *
     * @param delivery итог попыток доставки
     */
    public void recordNotificationDelivery(Delivery delivery) {
        transaction.lock();
        try {
            deliveryResultsByEventId.putIfAbsent(delivery.event().eventId(), delivery);
            notificationCompleted.signalAll();
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Возвращает число ожидающих подтверждений и результаты завершенных отправок.
     * Сколько подтверждений ожидают отправки и сколько завершились успешно или с ошибкой?
     *
     * @return текущее состояние уведомлений
     */
    public Report getNotificationReport() {
        transaction.lock();
        try {
            return new Report(outgoing.size(), List.copyOf(deliveryResultsByEventId.values()));
        } finally {
            transaction.unlock();
        }
    }

    /**
     * Ожидает обработку заданного числа уведомлений до истечения времени ожидания.
     *
     * @param count   необходимое число обработанных уведомлений
     * @param timeout предельное время ожидания
     * @return true, если нужное число уведомлений обработано вовремя
     * @throws InterruptedException если ожидание результатов отправки прервано
     */
    public boolean awaitProcessedNotifications(int count, Duration timeout) throws InterruptedException {
        if (count < 0 || timeout.isNegative()) throw new IllegalArgumentException("Invalid wait");
        long remaining = timeout.toNanos();
        transaction.lockInterruptibly();
        try {
            while (deliveryResultsByEventId.size() < count) {
                if (remaining <= 0) return false;
                remaining = notificationCompleted.awaitNanos(remaining);
            }
            return true;
        } finally {
            transaction.unlock();
        }
    }
}
