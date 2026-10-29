package ru.mentoring.cinema.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mentoring.cinema.CinemaRuntime;
import ru.mentoring.cinema.model.*;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Показывает афишу, конкурентную покупку, поиск заказов и уведомления.
 */
public final class CinemaDemo {
    private static final Logger log = LoggerFactory.getLogger(CinemaDemo.class);

    private CinemaDemo() {
    }

    /**
     * Показывает путь клиента от выбора фильма до получения подтверждения покупки.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить вовремя
     */
    public static void runCinemaDemo() throws Exception {
        try (CinemaRuntime runtime = new CinemaRuntime(Clock.systemUTC())) {
            log.info("1. Poster: List + Comparator; catalogue lookup: LinkedHashMap.");
            runtime.movies.findMoviesForPoster(Status.CURRENT, null, List.of(new Sort(SortField.TITLE, false)))
                    .forEach(movie -> log.info("   {}", movie.movie().title()));
            var view = runtime.movies.buildSessionView(runtime.catalogue.findScreeningById(101).orElseThrow());
            log.info("2. Schedule: TreeMap; screening 101 date = {}",
                    view.screening().startsAt().atZone(view.cinema().zone()).toLocalDate());
            log.info("   Available seats (sorted): {}", view.availableSeats());

            var request = runtime.createBookingRequest("alice", "alice-first-order", 101,
                    List.of(new Seat(1, 1), new Seat(1, 2)));
            Order order = runtime.bookings.purchaseTickets(request);
            log.info("3. Two-seat purchase: {}, total = {}", order.id(), order.totalPrice());
            Order repeatedOrder = runtime.bookings.purchaseTickets(request);
            log.info("   Idempotent repeat returns the same order: {}", repeatedOrder.id().equals(order.id()));

            log.info("4. 100 customers compete for ONE seat, with a shared start gate:");
            var oneSeatRace = ConcurrentCustomers.simulateConcurrentPurchases(runtime, 100, List.of(new Seat(1, 3)));
            log.info("   {}", oneSeatRace);

            List<Seat> remaining = runtime.movies.buildSessionView(view.screening()).availableSeats();
            log.info("5. 100 customers compete for the remaining seats:");
            var remainingSeatsRace = ConcurrentCustomers.simulateConcurrentPurchases(runtime, 100, remaining);
            log.info("   {}", remainingSeatsRace);

            log.info("6. Order lookup: {}", runtime.bookings.getOrderById(order.id()).movieTitle());
            log.info("   Orders of alice: {}",
                    runtime.bookings.findOrdersByFilter(new OrderFilter(null, null, 1L, Set.of("alice"))).size());
            log.info("   Sold seats: {} / 15", runtime.store.findOccupiedSeatsByScreeningId(101).size());

            int count = runtime.store.findAllOrders().size();
            if (!runtime.store.awaitProcessedNotifications(count, Duration.ofSeconds(5)))
                throw new IllegalStateException("Notifications did not settle");
            var report = runtime.store.getNotificationReport();
            log.info("7. ArrayBlockingQueue -> independent notification workers.");
            log.info("   Orders = {}, delivered = {}, failed = {}", count, report.countDeliveredNotifications(), report.countFailedNotifications());
            runtime.sender.getSentNotifications().forEach(sent -> log.info(
                    "   order={}, worker={}", sent.orderId(), sent.workerThread()));
        }
    }
}
