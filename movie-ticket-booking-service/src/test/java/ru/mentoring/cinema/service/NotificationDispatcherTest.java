package ru.mentoring.cinema.service;

import org.junit.jupiter.api.Test;
import ru.mentoring.cinema.model.Order;
import ru.mentoring.cinema.model.OrderCreated;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.notification.NotificationDispatcher;
import ru.mentoring.cinema.notification.RecordingNotificationSender;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет повторные попытки, ошибки и независимость отправки от покупки.
 */
class NotificationDispatcherTest {
    final BookingFlowTest fixture = new BookingFlowTest();

    /**
     * Проверяет успешный повтор отправки после временного сбоя.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void retriesAFailedAttemptWithoutCreatingANewOrderOrEvent() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var sender = new RecordingNotificationSender() {
            /**
             * Имитирует временный сбой только при первой отправке.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException если отправка прервана
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Temporary outage");
                super.sendOrderConfirmation(event, order);
            }
        };
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            assertEquals(2, attempts.get());
            assertEquals(1, sender.getSentNotifications().size());
            assertEquals(2, fixture.store.getNotificationReport().completed().getFirst().attempts());
            assertEquals(1, fixture.store.findAllOrders().size());
        }
    }

    /**
     * Проверяет учет ошибки доставки без отмены проданного билета.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void exhaustedDeliveryRemainsVisibleAndDoesNotUndoThePurchase() throws Exception {
        var sender = new RecordingNotificationSender() {
            /**
             * Имитирует недоступного отправщика при каждой попытке.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) {
                throw new IllegalStateException("Provider unavailable");
            }
        };
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            var report = fixture.store.getNotificationReport();
            assertEquals(1, report.countFailedNotifications());
            assertEquals(3, report.completed().getFirst().attempts());
            assertEquals(1, fixture.store.findAllOrders().size());
            assertEquals(1, fixture.store.findOccupiedSeatsByScreeningId(101).size());
        }
    }

    /**
     * Проверяет, что задержка подтверждения одному клиенту не мешает покупке другого клиента.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void notificationRunsAfterCommitAndDoesNotBlockTheBuyingThread() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        var sender = new RecordingNotificationSender() {
            /**
             * Удерживает отправку, пока другой клиент оформляет покупку.
             *
             * @param event событие покупки
             * @param order сохраненный заказ
             * @throws InterruptedException если ожидание разрешения прервано
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                assertTrue(fixture.store.findOrderById(order.id()).isPresent());
                enteredProvider.countDown();
                if (!releaseProvider.await(3, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test provider timed out");
            }
        };
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1)) {
            dispatcher.startNotificationWorkers();
            try {
                var first = fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
                assertNotNull(first);
                assertTrue(enteredProvider.await(2, TimeUnit.SECONDS));
                // Отправщик еще ждет: новая покупка должна завершиться без ожидания письма.
                assertNotNull(fixture.service.purchaseTickets(fixture.createBookingRequest("bob", "r", 101, new Seat(1, 2))));
                assertEquals(2, fixture.store.findAllOrders().size());
            } finally {
                releaseProvider.countDown();
            }
            assertTrue(fixture.store.awaitProcessedNotifications(2, Duration.ofSeconds(3)));
        }
    }

    /**
     * Проверяет завершение очереди и безопасное повторное закрытие.
     */
    @Test
    public void closeDrainsPendingNotificationsAndIsIdempotent() {
        var sender = new RecordingNotificationSender();
        var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1);
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        dispatcher.startNotificationWorkers();
        dispatcher.close();
        dispatcher.close();
        assertEquals(1, sender.getSentNotifications().size());
        assertEquals(0, fixture.store.getNotificationReport().queued());
        assertThrows(IllegalStateException.class, dispatcher::startNotificationWorkers);
    }
}
