package ru.mentoring.cinema.service;

import org.junit.jupiter.api.Test;
import ru.mentoring.cinema.model.Delivery;
import ru.mentoring.cinema.model.NotificationClaim;
import ru.mentoring.cinema.model.OrderCreated;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.storage.InMemoryBookingStore;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mentoring.cinema.model.NotificationState.*;

/**
 * Проверяет регистрацию уведомлений вместе с покупкой и переходы владения заданием outbox.
 */
class OutboxStoreTest {
    private final BookingFlowTest fixture = new BookingFlowTest();
    private final InMemoryBookingStore store = fixture.store;

    /**
     * Проверяет, что ошибка фабрики не оставляет заказ, проданные места или уведомление.
     */
    @Test
    void factoryFailureAndNullResultDoNotCommitAnyPurchaseState() {
        var request = fixture.createBookingRequest("alice", "stable", 101, new Seat(1, 1));
        var failure = new IllegalStateException("Order creation failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> store.confirmPurchase(request, () -> { throw failure; })));
        assertEmptyPurchaseState();

        assertThrows(NullPointerException.class, () -> store.confirmPurchase(request, () -> null));
        assertEmptyPurchaseState();

        var order = fixture.service.purchaseTickets(request);
        assertEquals(order, fixture.service.purchaseTickets(request));
        assertEquals(1, store.findAllOrders().size());
        assertEquals(1, store.findOccupiedSeatsByScreeningId(101).size());
        assertEquals(1, store.findAllNotificationJobs().size());
        assertEquals(order.id(), store.findAllNotificationJobs().getFirst().event().orderId());
    }

    /**
     * Проверяет отказ повторному номеру заказа без изменения мест, ключа запроса и outbox.
     */
    @Test
    void duplicateOrderIdDoesNotCommitAnotherPurchase() {
        var originalRequest = fixture.createBookingRequest("alice", "first", 101, new Seat(1, 1));
        var originalOrder = fixture.service.purchaseTickets(originalRequest);
        var originalOrders = store.findAllOrders();
        var originalJobs = store.findAllNotificationJobs();
        var originalSeats = store.findOccupiedSeatsByScreeningId(101);
        var secondRequest = fixture.createBookingRequest("bob", "second", 101, new Seat(1, 2));

        assertThrows(IllegalStateException.class, () -> store.confirmPurchase(secondRequest, () -> originalOrder));
        assertEquals(originalOrders, store.findAllOrders());
        assertEquals(originalJobs, store.findAllNotificationJobs());
        assertEquals(originalSeats, store.findOccupiedSeatsByScreeningId(101));

        var secondOrder = fixture.service.purchaseTickets(secondRequest);
        assertNotEquals(originalOrder.id(), secondOrder.id());
        assertEquals(2, store.findAllOrders().size());
        assertEquals(2, store.findAllNotificationJobs().size());
        assertTrue(store.findOccupiedSeatsByScreeningId(101).contains(new Seat(1, 2)));
    }

    /**
     * Проверяет единственного владельца уведомления при одновременном захвате многими отправщиками.
     *
     * @throws Exception если конкурентные захваты не завершились вовремя
     */
    @Test
    void concurrentWorkersClaimOnePendingJobOnlyOnce() throws Exception {
        purchaseOneSeat();
        var results = BookingFlowTest.runConcurrently(32,
                ignored -> () -> store.claimNextNotification(Duration.ZERO));
        var claims = results.stream().flatMap(Optional::stream).toList();

        assertEquals(1, claims.size());
        var claim = claims.getFirst();
        var job = store.findAllNotificationJobs().getFirst();
        assertEquals(IN_FLIGHT, job.state());
        assertEquals(claim.event(), job.event());
        assertEquals(claim.token(), job.claimToken());
        assertNull(job.delivery());
        assertEquals(0, store.getNotificationReport().queued());
        assertEquals(1, store.getNotificationReport().inFlight());
        assertTrue(store.getNotificationReport().completed().isEmpty());
        assertTrue(store.claimNextNotification(Duration.ZERO).isEmpty());
    }

    /**
     * Проверяет, что старый владелец не завершает и не освобождает повторно захваченное задание.
     *
     * @throws InterruptedException если захват уведомления прерван
     */
    @Test
    void staleClaimCannotChangeReclaimedJobAndTerminalCompletionIsIgnored() throws InterruptedException {
        purchaseOneSeat();
        var first = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertTrue(store.releaseNotification(first));
        assertFalse(store.releaseNotification(first));
        var pending = store.findAllNotificationJobs().getFirst();
        assertEquals(PENDING, pending.state());
        assertNull(pending.claimToken());
        assertNull(pending.delivery());

        var current = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertEquals(first.event(), current.event());
        assertNotEquals(first.token(), current.token());
        var claimedSnapshot = store.findAllNotificationJobs();
        assertFalse(store.completeNotification(first, new Delivery(first.event(), 1, true, "")));
        assertFalse(store.releaseNotification(first));
        assertEquals(claimedSnapshot, store.findAllNotificationJobs());

        var success = new Delivery(current.event(), 1, true, "");
        assertTrue(store.completeNotification(current, success));
        var terminalSnapshot = store.findAllNotificationJobs();
        assertEquals(SENT, terminalSnapshot.getFirst().state());
        assertNull(terminalSnapshot.getFirst().claimToken());
        assertEquals(success, terminalSnapshot.getFirst().delivery());
        assertFalse(store.completeNotification(current, success));
        assertFalse(store.completeNotification(current, new Delivery(current.event(), 2, false, "late failure")));
        assertFalse(store.releaseNotification(current));
        assertFalse(store.retryFailedNotification(current.event().eventId()));
        assertEquals(terminalSnapshot, store.findAllNotificationJobs());
        assertEquals(1, store.getNotificationReport().completed().size());
        assertEquals(1, store.getNotificationReport().countDeliveredNotifications());
    }

    /**
     * Проверяет ручной повтор ошибки с прежним событием и замену текущего результата доставки.
     *
     * @throws InterruptedException если захват или ожидание результата прерваны
     */
    @Test
    void retryFailedNotificationPreservesEventAndReplacesFailureWithSuccess() throws InterruptedException {
        purchaseOneSeat();
        var event = store.findAllNotificationJobs().getFirst().event();
        assertFalse(store.retryFailedNotification(event.eventId()));
        assertFalse(store.retryFailedNotification(UUID.randomUUID()));
        var first = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertFalse(store.retryFailedNotification(event.eventId()));
        var failure = new Delivery(event, 3, false, "Sender unavailable");
        assertTrue(store.completeNotification(first, failure));
        var failedSnapshot = store.findAllNotificationJobs();
        assertEquals(FAILED, failedSnapshot.getFirst().state());
        assertNull(failedSnapshot.getFirst().claimToken());
        assertEquals(failure, failedSnapshot.getFirst().delivery());
        assertEquals(1, store.getNotificationReport().countFailedNotifications());
        assertTrue(store.awaitProcessedNotifications(1, Duration.ZERO));

        assertTrue(store.retryFailedNotification(event.eventId()));
        assertFalse(store.retryFailedNotification(event.eventId()));
        assertEquals(1, store.getNotificationReport().queued());
        assertEquals(0, store.getNotificationReport().inFlight());
        assertTrue(store.getNotificationReport().completed().isEmpty());
        assertFalse(store.awaitProcessedNotifications(1, Duration.ZERO));
        var pending = store.findAllNotificationJobs().getFirst();
        assertEquals(event, pending.event());
        assertEquals(PENDING, pending.state());
        assertNull(pending.claimToken());
        assertNull(pending.delivery());
        assertEquals(FAILED, failedSnapshot.getFirst().state());

        var retry = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertNotEquals(first.token(), retry.token());
        assertEquals(event, retry.event());
        assertFalse(store.completeNotification(first, failure));
        assertTrue(store.completeNotification(retry, new Delivery(event, 1, true, "")));
        assertEquals(1, store.getNotificationReport().countDeliveredNotifications());
        assertEquals(0, store.getNotificationReport().countFailedNotifications());
        assertEquals(1, store.getNotificationReport().completed().size());
        assertEquals(1, store.findAllNotificationJobs().size());
        assertEquals(1, store.findAllOrders().size());
    }

    /**
     * Проверяет отклонение чужого события и неверного числа попыток без потери владельца.
     *
     * @throws InterruptedException если захват уведомления прерван
     */
    @Test
    void invalidCompletionDoesNotChangeClaimOrReport() throws InterruptedException {
        purchaseOneSeat();
        var claim = store.claimNextNotification(Duration.ZERO).orElseThrow();
        var event = claim.event();
        var snapshot = store.findAllNotificationJobs();
        var unrelatedEvent = new OrderCreated(UUID.randomUUID(), event.orderId(), event.occurredAt());
        var changedEvent = new OrderCreated(event.eventId(), UUID.randomUUID(), event.occurredAt());

        assertThrows(IllegalArgumentException.class,
                () -> store.completeNotification(claim, new Delivery(unrelatedEvent, 1, true, "")));
        assertThrows(IllegalArgumentException.class,
                () -> store.completeNotification(claim, new Delivery(changedEvent, 1, true, "")));
        assertThrows(IllegalArgumentException.class,
                () -> store.completeNotification(claim, new Delivery(event, 0, true, "")));
        assertThrows(IllegalArgumentException.class,
                () -> store.completeNotification(claim, new Delivery(event, -1, false, "invalid")));
        var forgedClaim = new NotificationClaim(changedEvent, claim.token());
        assertFalse(store.completeNotification(forgedClaim, new Delivery(changedEvent, 1, true, "")));
        assertFalse(store.releaseNotification(forgedClaim));
        assertEquals(snapshot, store.findAllNotificationJobs());
        assertEquals(1, store.getNotificationReport().inFlight());
        assertTrue(store.getNotificationReport().completed().isEmpty());
        assertTrue(store.completeNotification(claim, new Delivery(event, 1, true, "")));
    }

    /**
     * Проверяет неблокирующий нулевой таймаут и отклонение отрицательных параметров ожидания.
     *
     * @throws InterruptedException если операция ожидания прервана
     */
    @Test
    void zeroTimeoutDoesNotWaitAndNegativeWaitArgumentsAreRejected() throws InterruptedException {
        assertTrue(store.claimNextNotification(Duration.ZERO).isEmpty());
        assertFalse(store.awaitProcessedNotifications(1, Duration.ZERO));
        assertTrue(store.awaitProcessedNotifications(0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> store.awaitProcessedNotifications(-1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> store.awaitProcessedNotifications(0, Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> store.claimNextNotification(Duration.ofNanos(-1)));

        purchaseOneSeat();
        assertThrows(IllegalArgumentException.class,
                () -> store.claimNextNotification(Duration.ofNanos(-1)));
        assertEquals(PENDING, store.findAllNotificationJobs().getFirst().state());
        assertTrue(store.claimNextNotification(Duration.ZERO).isPresent());
        assertFalse(store.awaitProcessedNotifications(1, Duration.ZERO));
    }

    /**
     * Проверяет, что таймаут включает ожидание занятой покупкой блокировки хранилища.
     *
     * @throws Exception если тестовые потоки не завершились в ограниченное время
     */
    @Test
    void notificationTimeoutsIncludeWaitingForPurchaseLock() throws Exception {
        var factoryEntered = new CountDownLatch(1);
        var releaseFactory = new CountDownLatch(1);
        var request = fixture.createBookingRequest("alice", "blocked", 101, new Seat(1, 1));
        var expectedFailure = new IllegalStateException("Test factory deliberately aborted");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var purchase = executor.submit(() -> store.confirmPurchase(request, () -> {
                factoryEntered.countDown();
                try {
                    releaseFactory.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test factory interrupted", interrupted);
                }
                throw expectedFailure;
            }));
            Future<?> checks = null;
            try {
                assertTrue(factoryEntered.await(5, TimeUnit.SECONDS), "Фабрика не заняла блокировку");
                checks = executor.submit(() -> {
                    assertTrue(store.claimNextNotification(Duration.ZERO).isEmpty());
                    assertTrue(store.claimNextNotification(Duration.ofMillis(100)).isEmpty());
                    assertFalse(store.awaitProcessedNotifications(1, Duration.ZERO));
                    assertFalse(store.awaitProcessedNotifications(1, Duration.ofMillis(100)));
                    assertTrue(store.awaitProcessedNotifications(0, Duration.ZERO));
                    assertTrue(store.awaitProcessedNotifications(0, Duration.ofSeconds(30)));
                    return null;
                });
                // Фабрика остается под lock до окончания всех проверок таймаута.
                checks.get(2, TimeUnit.SECONDS);
                releaseFactory.countDown();
                var failure = assertThrows(ExecutionException.class,
                        () -> purchase.get(2, TimeUnit.SECONDS));
                assertSame(expectedFailure, failure.getCause());
                assertEmptyPurchaseState();
            } finally {
                releaseFactory.countDown();
                if (checks != null) checks.cancel(true);
                purchase.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    /**
     * Проверяет прерывание реально ожидающих потоков без создания или потери заданий.
     */
    @Test
    void notificationAndCompletionWaitsCanBeInterrupted() {
        var claimFailure = assertThrows(ExecutionException.class, () -> withWaitingThread(
                () -> store.claimNextNotification(Duration.ofSeconds(30)), Thread::interrupt));
        assertInstanceOf(InterruptedException.class, claimFailure.getCause());
        var completionFailure = assertThrows(ExecutionException.class, () -> withWaitingThread(
                () -> store.awaitProcessedNotifications(1, Duration.ofSeconds(30)), Thread::interrupt));
        assertInstanceOf(InterruptedException.class, completionFailure.getCause());
        assertEmptyPurchaseState();
    }

    /**
     * Проверяет пробуждение отправщика после покупки и наблюдателя после завершения доставки.
     *
     * @throws Exception если ожидающие потоки не получили результат вовремя
     */
    @Test
    void purchaseAndCompletionWakeTheirWaitingThreads() throws Exception {
        var claim = withWaitingThread(
                () -> store.claimNextNotification(Duration.ofSeconds(30)), ignored -> purchaseOneSeat()).orElseThrow();
        assertEquals(IN_FLIGHT, store.findAllNotificationJobs().getFirst().state());
        boolean completed = withWaitingThread(
                () -> store.awaitProcessedNotifications(1, Duration.ofSeconds(30)), ignored ->
                        assertTrue(store.completeNotification(claim, new Delivery(claim.event(), 1, true, ""))));
        assertTrue(completed);
    }

    /**
     * Проверяет неизменяемость снимков заданий и отчета при последующих переходах состояния.
     *
     * @throws InterruptedException если захват уведомления прерван
     */
    @Test
    void jobAndReportSnapshotsAreImmutableAndDoNotTrackLaterChanges() throws InterruptedException {
        purchaseOneSeat();
        var pendingJobs = store.findAllNotificationJobs();
        var pendingReport = store.getNotificationReport();
        assertThrows(UnsupportedOperationException.class, pendingJobs::clear);
        var claim = store.claimNextNotification(Duration.ZERO).orElseThrow();
        var inFlightJobs = store.findAllNotificationJobs();
        assertTrue(store.completeNotification(claim, new Delivery(claim.event(), 1, true, "")));
        var completedReport = store.getNotificationReport();
        assertThrows(UnsupportedOperationException.class, completedReport.completed()::clear);

        assertEquals(PENDING, pendingJobs.getFirst().state());
        assertNull(pendingJobs.getFirst().claimToken());
        assertEquals(IN_FLIGHT, inFlightJobs.getFirst().state());
        assertNull(inFlightJobs.getFirst().delivery());
        assertEquals(1, pendingReport.queued());
        assertEquals(0, pendingReport.inFlight());
        assertTrue(pendingReport.completed().isEmpty());
        assertEquals(SENT, store.findAllNotificationJobs().getFirst().state());
        assertEquals(0, completedReport.queued());
        assertEquals(0, completedReport.inFlight());
        assertEquals(1, completedReport.completed().size());
    }

    /**
     * Проверяет, что worker видит заказ, места и результат идемпотентного запроса вместе с событием.
     *
     * @throws Exception если покупка или наблюдатель не завершились вовремя
     */
    @Test
    void notificationBecomesVisibleOnlyTogetherWithTheWholePurchase() throws Exception {
        var request = fixture.createBookingRequest("alice", "atomic", 101, new Seat(1, 1), new Seat(1, 2));
        var prepared = new BookingFlowTest().service.purchaseTickets(request);
        var factoryEntered = new CountDownLatch(1);
        var releaseFactory = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var purchase = executor.submit(() -> store.confirmPurchase(request, () -> {
                factoryEntered.countDown();
                try {
                    releaseFactory.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test factory interrupted", interrupted);
                }
                return prepared;
            }));
            try {
                assertTrue(factoryEntered.await(2, TimeUnit.SECONDS));
                var claim = withWaitingThread(() -> {
                    var acquired = store.claimNextNotification(Duration.ofSeconds(5)).orElseThrow();
                    assertEquals(prepared.id(), acquired.event().orderId());
                    assertEquals(prepared, store.findOrderById(acquired.event().orderId()).orElseThrow());
                    assertEquals(java.util.Set.copyOf(request.seats()),
                            store.findOccupiedSeatsByScreeningId(request.screeningId()));
                    assertEquals(prepared, store.confirmPurchase(request, () -> {
                        throw new AssertionError("Exact retry must not create another order");
                    }));
                    assertEquals(1, store.findAllNotificationJobs().size());
                    return acquired;
                }, ignored -> releaseFactory.countDown());
                assertEquals(prepared, purchase.get(2, TimeUnit.SECONDS));
                assertTrue(store.completeNotification(claim, new Delivery(claim.event(), 1, true, "")));
                assertOutboxState(0, 0, 1, 0);
            } finally {
                releaseFactory.countDown();
                purchase.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    /**
     * Проверяет смешанные состояния и порядок повторов: возвращенное задание не обгоняет уже ожидающие.
     *
     * @throws InterruptedException если захват или ожидание результата прерваны
     */
    @Test
    void pendingIndexAndTerminalCountsStayConsistentAcrossReleaseRetryAndCompletion() throws InterruptedException {
        for (int seat = 1; seat <= 3; seat++) {
            fixture.service.purchaseTickets(fixture.createBookingRequest("customer-" + seat, "r",
                    101, new Seat(1, seat)));
        }
        assertOutboxState(3, 0, 0, 0);
        var first = store.claimNextNotification(Duration.ZERO).orElseThrow();
        var second = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertTrue(store.completeNotification(first, new Delivery(first.event(), 3, false, "Unavailable")));
        assertOutboxState(1, 1, 0, 1);

        assertTrue(store.releaseNotification(second));
        assertFalse(store.releaseNotification(second));
        assertOutboxState(2, 0, 0, 1);
        var third = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertNotEquals(first.event(), third.event());
        assertNotEquals(second.event(), third.event());
        assertTrue(store.retryFailedNotification(first.event().eventId()));
        assertFalse(store.retryFailedNotification(first.event().eventId()));
        assertOutboxState(2, 1, 0, 0);

        var secondRetry = store.claimNextNotification(Duration.ZERO).orElseThrow();
        var firstRetry = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertEquals(second.event(), secondRetry.event());
        assertEquals(first.event(), firstRetry.event());
        assertOutboxState(0, 3, 0, 0);
        assertTrue(store.completeNotification(third, new Delivery(third.event(), 1, true, "")));
        assertTrue(store.completeNotification(secondRetry, new Delivery(secondRetry.event(), 1, true, "")));
        assertFalse(store.completeNotification(first, new Delivery(first.event(), 1, true, "")));
        assertFalse(store.releaseNotification(second));
        assertTrue(store.completeNotification(firstRetry, new Delivery(firstRetry.event(), 1, true, "")));
        assertOutboxState(0, 0, 3, 0);
        assertTrue(store.claimNextNotification(Duration.ZERO).isEmpty());

        fixture.service.purchaseTickets(fixture.createBookingRequest("last-customer", "r", 101, new Seat(1, 4)));
        assertOutboxState(1, 0, 3, 0);
        var last = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertTrue(store.completeNotification(last, new Delivery(last.event(), 1, true, "")));
        assertOutboxState(0, 0, 4, 0);
    }

    /**
     * Проверяет, что одновременные ручные повторы одной ошибки создают только одну доступную попытку.
     *
     * @throws Exception если конкурентные повторы не завершились вовремя
     */
    @Test
    void concurrentManualRetriesMakeOnlyOneAttemptAvailable() throws Exception {
        purchaseOneSeat();
        var original = store.claimNextNotification(Duration.ZERO).orElseThrow();
        assertTrue(store.completeNotification(original, new Delivery(original.event(), 1, false, "Unavailable")));
        var retries = BookingFlowTest.runConcurrently(16,
                ignored -> () -> store.retryFailedNotification(original.event().eventId()));
        assertEquals(1, retries.stream().filter(Boolean::booleanValue).count());
        assertOutboxState(1, 0, 0, 0);

        var claims = BookingFlowTest.runConcurrently(16,
                ignored -> () -> store.claimNextNotification(Duration.ZERO));
        var acquired = claims.stream().flatMap(Optional::stream).toList();
        assertEquals(1, acquired.size());
        var current = acquired.getFirst();
        assertEquals(original.event(), current.event());
        assertNotEquals(original.token(), current.token());
        assertTrue(store.completeNotification(current, new Delivery(current.event(), 1, true, "")));
        assertOutboxState(0, 0, 1, 0);
    }

    /**
     * Сравнивает внешний отчет и ожидания с фактическими состояниями заданий.
     *
     * @param pending ожидающие задания
     * @param inFlight выполняемые задания
     * @param sent отправленные задания
     * @param failed задания с исчерпанными попытками
     * @throws InterruptedException если проверка ожидания прервана
     */
    private void assertOutboxState(int pending, int inFlight, int sent, int failed) throws InterruptedException {
        var jobs = store.findAllNotificationJobs();
        var report = store.getNotificationReport();
        assertEquals(pending + inFlight + sent + failed, jobs.size());
        assertEquals(pending, jobs.stream().filter(job -> job.state() == PENDING).count());
        assertEquals(inFlight, jobs.stream().filter(job -> job.state() == IN_FLIGHT).count());
        assertEquals(sent, jobs.stream().filter(job -> job.state() == SENT).count());
        assertEquals(failed, jobs.stream().filter(job -> job.state() == FAILED).count());
        assertEquals(pending, report.queued());
        assertEquals(inFlight, report.inFlight());
        assertEquals(sent, report.countDeliveredNotifications());
        assertEquals(failed, report.countFailedNotifications());
        assertEquals(pending > 0, store.hasPendingNotifications());
        assertTrue(store.awaitProcessedNotifications(sent + failed, Duration.ZERO));
        assertFalse(store.awaitProcessedNotifications(sent + failed + 1, Duration.ZERO));
    }

    /**
     * Оформляет одну покупку для проверки жизненного цикла ее уведомления.
     */
    private void purchaseOneSeat() {
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "one", 101, new Seat(1, 1)));
    }

    /**
     * Проверяет отсутствие частичного состояния покупки во всех связанных хранилищах.
     */
    private void assertEmptyPurchaseState() {
        assertTrue(store.findAllOrders().isEmpty());
        assertTrue(store.findOccupiedSeatsByScreeningId(101).isEmpty());
        assertTrue(store.findAllNotificationJobs().isEmpty());
        assertEquals(0, store.getNotificationReport().queued());
        assertEquals(0, store.getNotificationReport().inFlight());
        assertTrue(store.getNotificationReport().completed().isEmpty());
    }

    /**
     * Выполняет действие только после перехода тестового потока в ожидание и всегда завершает его.
     *
     * @param <T> тип результата ожидающей операции
     * @param operation ожидающая операция
     * @param action действие, пробуждающее либо прерывающее поток
     * @return результат ожидающей операции
     * @throws Exception если операция завершилась ошибкой или ожидание превысило предел
     */
    private static <T> T withWaitingThread(Callable<T> operation, Consumer<Thread> action) throws Exception {
        var task = new FutureTask<>(operation);
        var waiter = Thread.ofPlatform().daemon(true).name("outbox-store-test-waiter").start(task);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (waiter.isAlive() && waiter.getState() != Thread.State.WAITING
                    && waiter.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue(waiter.getState() == Thread.State.WAITING || waiter.getState() == Thread.State.TIMED_WAITING,
                    "Тестовый поток не перешел в ожидание");
            action.accept(waiter);
            return task.get(5, TimeUnit.SECONDS);
        } finally {
            waiter.interrupt();
            task.cancel(true);
            waiter.join(5_000);
            assertFalse(waiter.isAlive(), "Тестовый поток не завершился после прерывания");
        }
    }
}
