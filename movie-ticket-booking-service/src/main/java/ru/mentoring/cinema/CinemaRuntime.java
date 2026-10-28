package ru.mentoring.cinema;

import ru.mentoring.cinema.model.BookingRequest;
import ru.mentoring.cinema.model.Customer;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.notification.NotificationDispatcher;
import ru.mentoring.cinema.notification.RecordingNotificationSender;
import ru.mentoring.cinema.service.BookingService;
import ru.mentoring.cinema.service.CatalogueService;
import ru.mentoring.cinema.storage.InMemoryBookingStore;
import ru.mentoring.cinema.storage.InMemoryCatalogue;
import ru.mentoring.cinema.storage.SampleCatalogue;

import java.time.Clock;
import java.util.List;

/**
 * Подготавливает учебный кинотеатр к покупке билетов и отправке подтверждений.
 */
public final class CinemaRuntime implements AutoCloseable {
    public final InMemoryCatalogue catalogue;
    public final InMemoryBookingStore store;
    public final RecordingNotificationSender sender;
    public final BookingService bookings;
    public final CatalogueService movies;
    private final NotificationDispatcher dispatcher;

    /**
     * Подготавливает кинотеатр к приему покупок и отправке подтверждений.
     *
     * @param clock источник времени для каталога и покупок
     */
    public CinemaRuntime(Clock clock) {
        catalogue = SampleCatalogue.createSampleCatalogue(clock);
        store = new InMemoryBookingStore();
        sender = new RecordingNotificationSender();
        bookings = new BookingService(catalogue, store, clock);
        movies = new CatalogueService(catalogue, store, clock.withZone(java.time.ZoneId.of("Asia/Almaty")));
        dispatcher = new NotificationDispatcher(store, sender, 2, 3);
        dispatcher.startNotificationWorkers();
    }

    /**
     * Готовит учебный запрос покупки с демонстрационными контактами клиента.
     *
     * @param customerId  идентификатор клиента
     * @param requestId   ключ запроса в пределах клиента
     * @param screeningId идентификатор сеанса
     * @param seats       выбранные кресла
     * @return готовый запрос с фильмом и датой из каталога
     */
    public BookingRequest createBookingRequest(String customerId, String requestId, long screeningId, List<Seat> seats) {
        var screening = catalogue.findScreeningById(screeningId).orElseThrow();
        var hall = catalogue.findHallById(screening.hallId()).orElseThrow();
        var cinema = catalogue.findCinemaById(hall.cinemaId()).orElseThrow();
        return new BookingRequest(requestId,
                new Customer(customerId, "+77000000000", customerId + "@example.test"),
                screening.movieId(), screening.startsAt().atZone(cinema.zone()).toLocalDate(), screeningId, seats);
    }

    /**
     * Завершает работу службы подтверждения покупок.
     */
    @Override
    public void close() {
        dispatcher.close();
    }
}
