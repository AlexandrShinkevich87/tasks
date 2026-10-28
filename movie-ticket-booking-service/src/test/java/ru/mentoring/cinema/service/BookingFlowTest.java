package ru.mentoring.cinema.service;

import org.junit.jupiter.api.Test;
import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.BookingRequest;
import ru.mentoring.cinema.model.Customer;
import ru.mentoring.cinema.model.Order;
import ru.mentoring.cinema.model.NotificationState;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.notification.NotificationDispatcher;
import ru.mentoring.cinema.notification.RecordingNotificationSender;
import ru.mentoring.cinema.storage.InMemoryBookingStore;
import ru.mentoring.cinema.storage.InMemoryCatalogue;
import ru.mentoring.cinema.storage.SampleCatalogue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mentoring.cinema.model.Code.*;

/**
 * Проверяет покупку, повторные запросы и конкуренцию за кресла.
 */
class BookingFlowTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC);
    final InMemoryCatalogue catalogue = SampleCatalogue.createSampleCatalogue(CLOCK);
    final InMemoryBookingStore store = new InMemoryBookingStore();
    final BookingService service = new BookingService(catalogue, store, CLOCK);

    /**
     * Создает тестовый запрос с данными выбранного сеанса.
     *
     * @param customer    идентификатор клиента
     * @param key         ключ повторного запроса
     * @param screeningId идентификатор сеанса
     * @param seats       покупаемые кресла
     * @return валидный запрос покупки
     */
    public BookingRequest createBookingRequest(String customer, String key, long screeningId, Seat... seats) {
        var screening = catalogue.findScreeningById(screeningId).orElseThrow();
        var cinema = catalogue.findCinemaById(catalogue.findHallById(screening.hallId()).orElseThrow().cinemaId()).orElseThrow();
        return new BookingRequest(key, new Customer(customer, "123456", customer + "@example.test"),
                screening.movieId(), screening.startsAt().atZone(cinema.zone()).toLocalDate(),
                screeningId, List.of(seats));
    }

    /**
     * Проверяет покупку всех выбранных мест и сохранение сведений об оформленном заказе.
     */
    @Test
    public void purchasesAllSeatsAndKeepsAnOrderSnapshot() {
        Order order = service.purchaseTickets(createBookingRequest("alice", "1", 101, new Seat(1, 1), new Seat(1, 2)));
        assertEquals(new java.math.BigDecimal("3999.80"), order.totalPrice());
        assertEquals(order, service.getOrderById(order.id()));
        assertEquals("Interstellar", order.movieTitle());
        assertEquals(2, store.findOccupiedSeatsByScreeningId(101).size());
        assertEquals(1, store.getNotificationReport().queued());
        assertThrows(UnsupportedOperationException.class, () -> order.seats().clear());
        assertThrows(UnsupportedOperationException.class, () -> store.findOccupiedSeatsByScreeningId(101).clear());
    }

    /**
     * Проверяет полный отказ заказа при конфликте хотя бы одного кресла.
     */
    @Test
    public void partialConflictDoesNotSellAnyOfTheOtherSeats() {
        service.purchaseTickets(
                createBookingRequest("alice", "1", 101, new Seat(1, 2))
        );
        var failure = assertThrows(BookingException.class, () ->
                service.purchaseTickets(
                        createBookingRequest("bob", "1", 101,
                                new Seat(1, 1),
                                new Seat(1, 2)
                        )
                )
        );
        assertEquals(SEAT_TAKEN, failure.getErrorCode());
        assertFalse(store.findOccupiedSeatsByScreeningId(101).contains(new Seat(1, 1)));
        assertEquals(1, store.findAllOrders().size());
        assertEquals(1, store.getNotificationReport().queued());
    }

    /**
     * Проверяет независимую занятость одного кресла на разных сеансах.
     */
    @Test
    public void aSeatCanBeSoldForAnotherScreening() {
        service.purchaseTickets(createBookingRequest("alice", "1", 101, new Seat(1, 1)));
        service.purchaseTickets(createBookingRequest("bob", "1", 104, new Seat(1, 1)));
        assertEquals(2, store.findAllOrders().size());
    }

    /**
     * Проверяет отклонение неверного сеанса и кресел без изменения хранилища.
     */
    @Test
    public void rejectsWrongMovieDateHallAndPastScreeningWithoutSideEffects() {
        var good = createBookingRequest("alice", "1", 101, new Seat(1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseTickets(new BookingRequest("2", good.customer(),
                2, good.date(), 101, good.seats())));
        assertThrows(IllegalArgumentException.class, () -> service.purchaseTickets(new BookingRequest("3", good.customer(),
                1, good.date().plusDays(1), 101, good.seats())));
        assertThrows(IllegalArgumentException.class, () ->
                service.purchaseTickets(createBookingRequest("alice", "4", 101, new Seat(99, 1))));
        var startedService = new BookingService(catalogue, store,
                Clock.fixed(catalogue.findScreeningById(101).orElseThrow().startsAt(), ZoneOffset.UTC));
        assertEquals(SCREENING_STARTED, assertThrows(BookingException.class,
                () -> startedService.purchaseTickets(good)).getErrorCode());
        assertTrue(store.findAllOrders().isEmpty());
        assertEquals(0, store.getNotificationReport().queued());
    }

    /**
     * Проверяет повтор запроса и область действия ключа клиента.
     */
    @Test
    public void repeatsReturnOriginalOrderAndDoNotPublishAnotherNotification() {
        var request = createBookingRequest("alice", "stable-key", 101, new Seat(1, 1));
        Order original = service.purchaseTickets(request);
        var originalJobs = store.findAllNotificationJobs();
        assertEquals(original, service.purchaseTickets(request));
        assertEquals(1, store.findAllOrders().size());
        assertEquals(1, store.getNotificationReport().queued());
        assertEquals(REQUEST_CONFLICT, assertThrows(BookingException.class, () ->
                service.purchaseTickets(createBookingRequest("alice", "stable-key", 101, new Seat(1, 2)))).getErrorCode());
        assertEquals(originalJobs, store.findAllNotificationJobs());
        assertEquals(1, store.findAllOrders().size());
        assertFalse(store.findOccupiedSeatsByScreeningId(101).contains(new Seat(1, 2)));
        // Ключ привязан к клиенту: другой клиент может использовать такой же requestId.
        service.purchaseTickets(createBookingRequest("bob", "stable-key", 101, new Seat(1, 2)));
        assertEquals(2, store.findAllOrders().size());
    }

    /**
     * Проверяет возврат прежнего заказа после начала сеанса.
     */
    @Test
    public void replayAfterScreeningStartStillReturnsTheOriginalOrder() {
        var request = createBookingRequest("alice", "stable-key", 101, new Seat(1, 1));
        var order = service.purchaseTickets(request);
        var later = new BookingService(catalogue, store,
                Clock.fixed(order.startsAt().plusSeconds(1), ZoneOffset.UTC));
        assertEquals(order, later.purchaseTickets(request));
    }

    /**
     * Проверяет продолжение продаж без отправщика и сохранение уведомления каждого заказа.
     *
     * @throws Exception если конкурентные покупки не завершились вовремя
     */
    @Test
    public void salesContinueWithoutNotificationWorkersAndKeepEveryEvent() throws Exception {
        var seats = catalogue.findHallById(1).orElseThrow().seats().stream().sorted().toList();
        var orderIds = runConcurrently(seats.size(), i -> () ->
                service.purchaseTickets(createBookingRequest("c" + i, "r", 101, seats.get(i))).id());

        assertEquals(seats.size(), new HashSet<>(orderIds).size());
        assertEquals(seats.size(), store.findAllOrders().size());
        assertEquals(new HashSet<>(seats), store.findOccupiedSeatsByScreeningId(101));
        var jobs = store.findAllNotificationJobs();
        assertEquals(seats.size(), jobs.size());
        assertEquals(new HashSet<>(orderIds), jobs.stream()
                .map(job -> job.event().orderId()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(jobs.size(), jobs.stream().map(job -> job.event().eventId()).distinct().count());
        assertTrue(jobs.stream().allMatch(job -> job.state() == NotificationState.PENDING));
        assertTrue(jobs.stream().allMatch(job -> job.claimToken() == null && job.delivery() == null));
        assertEquals(seats.size(), store.getNotificationReport().queued());
        assertEquals(0, store.getNotificationReport().inFlight());
        assertTrue(store.getNotificationReport().completed().isEmpty());
    }
    /**
     * Проверяет, что из ста покупателей одного кресла выигрывает один.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void oneHundredCustomersCannotBuyTheSameSeatTwice() throws Exception {
        var outcomes = runConcurrently(100, i -> () -> {
            try {
                service.purchaseTickets(createBookingRequest("c" + i, "r", 101, new Seat(1, 1)));
                return "SOLD";
            } catch (BookingException failure) {
                return failure.getErrorCode().name();
            }
        });
        assertEquals(1, Collections.frequency(outcomes, "SOLD"));
        assertEquals(99, Collections.frequency(outcomes, "SEAT_TAKEN"));
        assertEquals(1, store.findAllOrders().size());
        assertEquals(1, store.findOccupiedSeatsByScreeningId(101).size());
        assertEquals(1, store.getNotificationReport().queued());
    }

    /**
     * Проверяет целостность покупки нескольких кресел при конкуренции.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void competingMultiSeatOrdersDoNotPartiallySellTheLosersSeats() throws Exception {
        var outcomes = runConcurrently(2, i -> () -> {
            try {
                service.purchaseTickets(createBookingRequest("c" + i, "r", 101,
                        new Seat(1, 2), new Seat(1, i == 0 ? 1 : 3)));
                return "SOLD";
            } catch (BookingException failure) {
                return failure.getErrorCode().name();
            }
        });
        assertEquals(1, Collections.frequency(outcomes, "SOLD"));
        assertEquals(1, store.findAllOrders().size());
        assertEquals(2, store.findOccupiedSeatsByScreeningId(101).size());
    }

    /**
     * Проверяет, что пятьдесят повторов дают один заказ и одно событие.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void simultaneousIdempotentRequestsReturnOneOrderAndOneEvent() throws Exception {
        var request = createBookingRequest("alice", "same", 101, new Seat(1, 1));
        var ids = runConcurrently(50, i -> () -> service.purchaseTickets(request).id());
        assertEquals(1, new HashSet<>(ids).size());
        assertEquals(1, store.findAllOrders().size());
        assertEquals(1, store.getNotificationReport().queued());
    }

    /**
     * Проверяет параллельную продажу разных кресел и доставку всех уведомлений.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void differentSeatsCanAllBePurchasedAndNotified() throws Exception {
        var sender = new RecordingNotificationSender();
        try (var dispatcher = new NotificationDispatcher(store, sender, 3, 3)) {
            dispatcher.startNotificationWorkers();
            var seats = catalogue.findHallById(1).orElseThrow().seats().stream().sorted().toList();
            var ids = runConcurrently(seats.size(), i -> () ->
                    service.purchaseTickets(createBookingRequest("c" + i, "r", 101, seats.get(i))).id());
            assertEquals(15, new HashSet<>(ids).size());
            assertTrue(store.awaitProcessedNotifications(15, Duration.ofSeconds(5)));
            assertEquals(15, store.getNotificationReport().countDeliveredNotifications());
            assertEquals(0, store.getNotificationReport().countFailedNotifications());
            assertEquals(15, sender.getSentNotifications().size());
            assertTrue(sender.getSentNotifications().stream().allMatch(n -> n.workerThread().startsWith("notification-")));
        }
    }

    /**
     * Дожидается готовности всех участников, открывает общий старт и собирает результаты.
     *
     * @param <T>   тип результата действия
     * @param count количество участников
     * @param task  действие для участника с указанным номером
     * @return результаты в порядке номеров участников
     * @throws Exception если действие не завершилось или истекло время ожидания
     */
    static <T> List<T> runConcurrently(int count, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < count; i++) {
                    var action = task.apply(i);
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return action.call();
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Участники не подготовились к общему старту");
                start.countDown();
                List<T> results = new ArrayList<>();
                for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
                return results;
            } finally {
                start.countDown();
                for (var future : futures) {
                    if (!future.isDone()) future.cancel(true);
                }
                executor.shutdownNow();
            }
        }
    }
}
