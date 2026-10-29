package ru.mentoring.cinema.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.mentoring.cinema.model.Order;
import ru.mentoring.cinema.model.OrderCreated;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.notification.NotificationDispatcher;
import ru.mentoring.cinema.notification.RecordingNotificationSender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mentoring.cinema.model.NotificationState.*;

/**
 * Проверяет отправку outbox, повторы, остановку workers и независимость покупки от уведомления.
 */
@Timeout(15)
class NotificationDispatcherTest {
    final BookingFlowTest fixture = new BookingFlowTest();

    /**
     * Проверяет повтор того же события после временного сбоя без нового заказа или задания.
     *
     * @throws Exception если уведомление не обработано вовремя
     */
    @Test
    public void retriesAFailedAttemptWithoutCreatingANewOrderOrEvent() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<UUID> attemptedEventIds = new ArrayList<>();
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
                attemptedEventIds.add(event.eventId());
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Temporary outage");
                super.sendOrderConfirmation(event, order);
            }
        };
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
            UUID eventId = fixture.store.findAllNotificationJobs().getFirst().event().eventId();
            dispatcher.startNotificationWorkers();

            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            assertEquals(2, attempts.get());
            assertEquals(List.of(eventId, eventId), attemptedEventIds);
            assertEquals(1, sender.getSentNotifications().size());
            assertEquals(2, fixture.store.getNotificationReport().completed().getFirst().attempts());
            assertEquals(1, fixture.store.findAllOrders().size());
            assertEquals(1, fixture.store.findAllNotificationJobs().size());
            assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
        }
    }

    /**
     * Проверяет сохранение окончательной ошибки без отмены заказа и без бесконечного завершения.
     *
     * @throws Exception если уведомление не обработано вовремя
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
        var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3);
        try {
            fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
            dispatcher.startNotificationWorkers();

            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            var report = fixture.store.getNotificationReport();
            var job = fixture.store.findAllNotificationJobs().getFirst();
            assertEquals(1, report.countFailedNotifications());
            assertEquals(3, report.completed().getFirst().attempts());
            assertEquals(FAILED, job.state());
            assertEquals("IllegalStateException", job.delivery().error());
            assertEquals(0, report.queued());
            assertEquals(0, report.inFlight());
            assertEquals(1, fixture.store.findAllOrders().size());
            assertEquals(1, fixture.store.findOccupiedSeatsByScreeningId(101).size());

            // FAILED остается в реестре, но не удерживает цикл завершения workers.
            assertTimeout(Duration.ofSeconds(2), dispatcher::close);
        } finally {
            dispatcher.close();
        }
    }

    /**
     * Проверяет покупку другого места, пока первый отправщик явно удерживается на барьере.
     *
     * @throws Exception если покупку или отправку не удалось завершить
     */
    @Test
    public void notificationRunsAfterCommitAndDoesNotBlockTheBuyingThread() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        var sender = new RecordingNotificationSender() {
            /**
             * Проверяет наличие заказа и удерживает отправку до разрешения теста.
             *
             * @param event событие покупки
             * @param order сохраненный заказ
             * @throws InterruptedException если тест останавливает отправку
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                assertTrue(fixture.store.findOrderById(order.id()).isPresent());
                enteredProvider.countDown();
                releaseProvider.await();
                super.sendOrderConfirmation(event, order);
            }
        };
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1);
             var customers = Executors.newVirtualThreadPerTaskExecutor()) {
            dispatcher.startNotificationWorkers();
            try {
                var first = customers.submit(() -> fixture.service.purchaseTickets(
                        fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1))));
                assertNotNull(first.get(2, TimeUnit.SECONDS));
                assertTrue(enteredProvider.await(2, TimeUnit.SECONDS));
                assertEquals(1, fixture.store.getNotificationReport().inFlight());

                var second = customers.submit(() -> fixture.service.purchaseTickets(
                        fixture.createBookingRequest("bob", "r", 101, new Seat(1, 2))));
                assertNotNull(second.get(2, TimeUnit.SECONDS));
                assertEquals(2, fixture.store.findAllOrders().size());
                assertEquals(1, releaseProvider.getCount());
                assertTrue(sender.getSentNotifications().isEmpty());
            } finally {
                releaseProvider.countDown();
            }
            assertTrue(fixture.store.awaitProcessedNotifications(2, Duration.ofSeconds(3)));
            assertEquals(2, fixture.store.getNotificationReport().countDeliveredNotifications());
            assertEquals(2, sender.getSentNotifications().size());
        }
    }

    /**
     * Проверяет накопление покупок без отправщика и последующую обработку всех заданий.
     *
     * @throws Exception если накопленные уведомления не обработаны вовремя
     */
    @Test
    public void purchasesContinueBeforeWorkersStartAndEveryNotificationIsLaterDelivered() throws Exception {
        var seats = fixture.catalogue.findHallById(1).orElseThrow().seats().stream().sorted().toList();
        for (int i = 0; i < seats.size(); i++) {
            fixture.service.purchaseTickets(fixture.createBookingRequest("customer-" + i, "r", 101, seats.get(i)));
        }

        assertEquals(seats.size(), fixture.store.findAllOrders().size());
        assertEquals(seats.size(), fixture.store.getNotificationReport().queued());
        assertEquals(0, fixture.store.getNotificationReport().inFlight());
        assertTrue(fixture.store.findAllNotificationJobs().stream().allMatch(job -> job.state() == PENDING));

        var sender = new RecordingNotificationSender();
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 2, 3)) {
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(seats.size(), Duration.ofSeconds(3)));
            assertEquals(seats.size(), sender.getSentNotifications().size());
            assertEquals(seats.size(), fixture.store.getNotificationReport().countDeliveredNotifications());
            assertEquals(0, fixture.store.getNotificationReport().queued());
            assertEquals(0, fixture.store.getNotificationReport().inFlight());
        }
    }

    /**
     * Проверяет ручной повтор FAILED с прежним событием и замену старой ошибки успешным итогом.
     *
     * @throws Exception если отправку не удалось завершить
     */
    @Test
    public void manualRetryUsesTheSameEventAndReplacesFailedResultWithSuccess() throws Exception {
        AtomicBoolean unavailable = new AtomicBoolean(true);
        var sender = new RecordingNotificationSender() {
            /**
             * Позволяет тесту восстановить отправщика перед ручным повтором.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException если отправка прервана
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                if (unavailable.get()) throw new IllegalStateException("Provider unavailable");
                super.sendOrderConfirmation(event, order);
            }
        };
        Order order = fixture.service.purchaseTickets(
                fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        OrderCreated event = fixture.store.findAllNotificationJobs().getFirst().event();
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1)) {
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            assertEquals(1, fixture.store.getNotificationReport().countFailedNotifications());
        }

        unavailable.set(false);
        assertTrue(fixture.store.retryFailedNotification(event.eventId()));
        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(event, fixture.store.findAllNotificationJobs().getFirst().event());
        assertTrue(fixture.store.getNotificationReport().completed().isEmpty());
        assertFalse(fixture.store.awaitProcessedNotifications(1, Duration.ZERO));

        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1)) {
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
        }

        var report = fixture.store.getNotificationReport();
        assertEquals(1, report.countDeliveredNotifications());
        assertEquals(0, report.countFailedNotifications());
        assertEquals(1, report.completed().size());
        assertEquals(event, report.completed().getFirst().event());
        assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(List.of(order), fixture.store.findAllOrders());
        assertEquals(1, sender.getSentNotifications().size());
        assertFalse(fixture.store.retryFailedNotification(event.eventId()));
    }

    /**
     * Проверяет идемпотентность отправщика при потере подтверждения уже выполненной отправки.
     *
     * @throws Exception если повторная попытка не завершена вовремя
     */
    @Test
    public void lostAcknowledgementRetriesTheSameEventWithoutSendingAnotherNotification() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var sender = new RecordingNotificationSender() {
            /**
             * Сначала отправляет подтверждение, затем имитирует потерю ответа на первую попытку.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException если отправка прервана
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                super.sendOrderConfirmation(event, order);
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Acknowledgement lost");
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        UUID eventId = fixture.store.findAllNotificationJobs().getFirst().event().eventId();
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            dispatcher.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
        }

        assertEquals(2, attempts.get());
        assertEquals(1, sender.getSentNotifications().size());
        assertEquals(eventId, sender.getSentNotifications().getFirst().eventId());
        assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(1, fixture.store.findAllOrders().size());
    }

    /**
     * Проверяет возврат прерванной отправки в PENDING и доставку новым dispatcher с тем же eventId.
     *
     * @throws Exception если worker не начал отправку или не завершился вовремя
     */
    @Test
    public void forcedShutdownReturnsInterruptedDeliveryToPendingForAnotherDispatcher() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch interruptedProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        var blockedSender = new RecordingNotificationSender() {
            /**
             * Удерживает отправку до принудительного завершения worker.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException когда dispatcher прерывает worker
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                enteredProvider.countDown();
                try {
                    releaseProvider.await();
                } catch (InterruptedException interrupted) {
                    interruptedProvider.countDown();
                    throw interrupted;
                }
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        OrderCreated event = fixture.store.findAllNotificationJobs().getFirst().event();
        var dispatcher = new NotificationDispatcher(fixture.store, blockedSender, 1, 3, Duration.ZERO);
        try {
            dispatcher.startNotificationWorkers();
            assertTrue(enteredProvider.await(2, TimeUnit.SECONDS));
            assertEquals(IN_FLIGHT, fixture.store.findAllNotificationJobs().getFirst().state());
            dispatcher.close();
            assertTrue(interruptedProvider.await(2, TimeUnit.SECONDS));
        } finally {
            releaseProvider.countDown();
            dispatcher.close();
        }

        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(event, fixture.store.findAllNotificationJobs().getFirst().event());
        assertEquals(1, fixture.store.getNotificationReport().queued());
        assertEquals(0, fixture.store.getNotificationReport().inFlight());
        assertTrue(fixture.store.getNotificationReport().completed().isEmpty());

        var recoveredSender = new RecordingNotificationSender();
        try (var recovered = new NotificationDispatcher(fixture.store, recoveredSender, 1, 1)) {
            recovered.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
        }
        assertEquals(event.eventId(), recoveredSender.getSentNotifications().getFirst().eventId());
        assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
    }

    /**
     * Проверяет возврат задания после прерывания ожидания между попытками, без записи FAILED.
     *
     * @throws Exception если worker не дошел до первой попытки
     */
    @Test
    public void interruptionBeforeBackoffReturnsTheJobToPendingWithoutAnotherAttempt() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var sender = new RecordingNotificationSender() {
            /**
             * Прерывает свой поток перед backoff; sleep должен сразу сообщить о прерывании.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) {
                attempts.incrementAndGet();
                Thread.currentThread().interrupt();
                attempted.countDown();
                throw new IllegalStateException("Temporary outage before interruption");
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        OrderCreated event = fixture.store.findAllNotificationJobs().getFirst().event();
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            dispatcher.startNotificationWorkers();
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
        }

        assertEquals(1, attempts.get());
        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(event, fixture.store.findAllNotificationJobs().getFirst().event());
        assertTrue(fixture.store.getNotificationReport().completed().isEmpty());
        assertEquals(0, fixture.store.getNotificationReport().inFlight());
    }

    /**
     * Проверяет завершение ожидающих заданий и безопасное повторное закрытие dispatcher.
     */
    @Test
    public void closeDrainsPendingNotificationsAndIsIdempotent() {
        var sender = new RecordingNotificationSender();
        var dispatcher = new NotificationDispatcher(fixture.store, sender, 2, 1);
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        fixture.service.purchaseTickets(fixture.createBookingRequest("bob", "r", 101, new Seat(1, 2)));
        try {
            dispatcher.startNotificationWorkers();
            dispatcher.startNotificationWorkers();
            dispatcher.close();
            dispatcher.close();
            assertEquals(2, sender.getSentNotifications().size());
            assertEquals(0, fixture.store.getNotificationReport().queued());
            assertEquals(0, fixture.store.getNotificationReport().inFlight());
            assertEquals(2, fixture.store.getNotificationReport().countDeliveredNotifications());
            assertThrows(IllegalStateException.class, dispatcher::startNotificationWorkers);
        } finally {
            dispatcher.close();
        }
    }

    /**
     * Проверяет, что закрытие до запуска оставляет задания для другого dispatcher.
     *
     * @throws Exception если новый dispatcher не обработал оставшееся задание
     */
    @Test
    public void closeBeforeStartPreservesPendingJobsForAnotherDispatcher() throws Exception {
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        var sender = new RecordingNotificationSender();
        var neverStarted = new NotificationDispatcher(fixture.store, sender, 1, 1);
        neverStarted.close();
        neverStarted.close();

        assertThrows(IllegalStateException.class, neverStarted::startNotificationWorkers);
        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(1, fixture.store.getNotificationReport().queued());
        assertTrue(sender.getSentNotifications().isEmpty());

        try (var replacement = new NotificationDispatcher(fixture.store, sender, 1, 1)) {
            replacement.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
        }
        assertEquals(1, sender.getSentNotifications().size());
    }

    /**
     * Проверяет сохранение задания, когда отправка состоялась, но ожидание подтверждения прервано.
     *
     * @throws Exception если повторная обработка не завершилась вовремя
     */
    @Test
    public void interruptionAfterSendingPreservesTheEventAndDoesNotDuplicateDelivery() throws Exception {
        CountDownLatch firstSendCompleted = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var sender = new RecordingNotificationSender() {
            /**
             * Имитирует прерывание после реальной отправки только в первой попытке.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException при потере подтверждения первой отправки
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                super.sendOrderConfirmation(event, order);
                if (attempts.incrementAndGet() == 1) {
                    firstSendCompleted.countDown();
                    throw new InterruptedException("Interrupted while waiting for acknowledgement");
                }
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        OrderCreated event = fixture.store.findAllNotificationJobs().getFirst().event();
        try (var first = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            first.startNotificationWorkers();
            assertTrue(firstSendCompleted.await(2, TimeUnit.SECONDS));
        }
        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertTrue(fixture.store.getNotificationReport().completed().isEmpty());
        assertEquals(1, sender.getSentNotifications().size());

        try (var second = new NotificationDispatcher(fixture.store, sender, 1, 3)) {
            second.startNotificationWorkers();
            assertTrue(fixture.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
        }

        assertEquals(2, attempts.get());
        assertEquals(1, sender.getSentNotifications().size());
        assertEquals(event.eventId(), sender.getSentNotifications().getFirst().eventId());
        assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
    }

    /**
     * Проверяет приоритет прерывания над FAILED даже в последней разрешенной попытке.
     *
     * @throws Exception если worker не дошел до отправки
     */
    @Test
    public void interruptionOnLastAttemptReturnsTheJobToPendingInsteadOfFailed() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var sender = new RecordingNotificationSender() {
            /**
             * Имитирует адаптер, который сохранил interrupt flag и завернул ошибку в RuntimeException.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) {
                attempts.incrementAndGet();
                Thread.currentThread().interrupt();
                attempted.countDown();
                throw new IllegalStateException("Interrupted provider call");
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        OrderCreated event = fixture.store.findAllNotificationJobs().getFirst().event();
        try (var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1)) {
            dispatcher.startNotificationWorkers();
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
        }

        assertEquals(1, attempts.get());
        assertEquals(PENDING, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(event, fixture.store.findAllNotificationJobs().getFirst().event());
        assertEquals(1, fixture.store.getNotificationReport().queued());
        assertEquals(0, fixture.store.getNotificationReport().inFlight());
        assertTrue(fixture.store.getNotificationReport().completed().isEmpty());
        assertEquals(1, fixture.store.findAllOrders().size());
    }

    /**
     * Проверяет, что остановка не отбирает задание у отправщика, игнорирующего прерывание.
     *
     * @throws Exception если worker не начал или не завершил работу после разрешения
     */
    @Test
    public void failedShutdownKeepsClaimOwnedUntilAnUninterruptibleSenderActuallyReturns() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch interruptedProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> worker = new java.util.concurrent.atomic.AtomicReference<>();
        var sender = new RecordingNotificationSender() {
            /**
             * Намеренно игнорирует прерывание, завершая отправку только по команде теста.
             *
             * @param event событие покупки
             * @param order оформленный заказ
             * @throws InterruptedException если базовый отправщик прерван
             */
            @Override
            public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
                worker.set(Thread.currentThread());
                enteredProvider.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        releaseProvider.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                        interruptedProvider.countDown();
                    }
                }
                super.sendOrderConfirmation(event, order);
            }
        };
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "r", 101, new Seat(1, 1)));
        var dispatcher = new NotificationDispatcher(fixture.store, sender, 1, 1, Duration.ZERO);
        try {
            dispatcher.startNotificationWorkers();
            assertTrue(enteredProvider.await(2, TimeUnit.SECONDS));
            UUID claimToken = fixture.store.findAllNotificationJobs().getFirst().claimToken();

            assertThrows(IllegalStateException.class, dispatcher::close);
            assertEquals(0, interruptedProvider.getCount());
            var job = fixture.store.findAllNotificationJobs().getFirst();
            assertEquals(IN_FLIGHT, job.state());
            assertEquals(claimToken, job.claimToken());
            assertEquals(1, fixture.store.getNotificationReport().inFlight());
            assertEquals(0, fixture.store.getNotificationReport().queued());
            assertTrue(fixture.store.claimNextNotification(Duration.ZERO).isEmpty());
            assertTrue(sender.getSentNotifications().isEmpty());
        } finally {
            releaseProvider.countDown();
            dispatcher.close();
            Thread sendingThread = worker.get();
            if (sendingThread != null) {
                sendingThread.join(Duration.ofSeconds(3));
                assertFalse(sendingThread.isAlive(), "Released sender must terminate");
            }
        }

        assertEquals(SENT, fixture.store.findAllNotificationJobs().getFirst().state());
        assertEquals(1, fixture.store.getNotificationReport().countDeliveredNotifications());
        assertEquals(0, fixture.store.getNotificationReport().inFlight());
        assertEquals(1, sender.getSentNotifications().size());
    }
}
