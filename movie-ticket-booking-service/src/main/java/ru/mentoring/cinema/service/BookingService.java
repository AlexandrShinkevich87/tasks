package ru.mentoring.cinema.service;

import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.*;
import ru.mentoring.cinema.storage.InMemoryBookingStore;
import ru.mentoring.cinema.storage.InMemoryCatalogue;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static ru.mentoring.cinema.model.Code.NOT_FOUND;
import static ru.mentoring.cinema.model.Code.SCREENING_STARTED;

/**
 * Оформляет покупку билетов и предоставляет сведения о заказах.
 */
public final class BookingService {
    private final InMemoryCatalogue catalogue;
    private final InMemoryBookingStore store;
    private final Clock clock;

    /**
     * Подготавливает оформление покупок с проверкой времени начала сеанса.
     *
     * @param catalogue каталог фильмов, залов и сеансов
     * @param store     хранилище покупок и событий
     * @param clock     источник времени для проверки начала сеанса
     */
    public BookingService(InMemoryCatalogue catalogue, InMemoryBookingStore store, Clock clock) {
        this.catalogue = catalogue;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Оформляет покупку, проверяя фильм, дату и принадлежность выбранных мест залу.
     * Можно ли оформить эту покупку с указанными фильмом, датой и местами?
     *
     * @param request данные клиента, сеанса и выбранных мест
     * @return созданный заказ или результат точного повтора
     * @throws BookingException         если сеанс не найден, начался либо покупка конфликтует
     * @throws IllegalArgumentException если фильм, дата или кресла не соответствуют сеансу
     */
    public Order purchaseTickets(BookingRequest request) {
        Screening screening = catalogue.findScreeningById(request.screeningId())
                .orElseThrow(() -> new BookingException(NOT_FOUND, "Screening not found"));
        Hall hall = catalogue.findHallById(screening.hallId()).orElseThrow();
        Cinema cinema = catalogue.findCinemaById(hall.cinemaId()).orElseThrow();
        Movie movie = catalogue.findMovieById(screening.movieId()).orElseThrow();
        var screeningDateInCinemaTimeZone = screening.startsAt().atZone(cinema.zone()).toLocalDate();
        if (request.movieId() != movie.id() || !request.date().equals(screeningDateInCinemaTimeZone))
            throw new IllegalArgumentException("Movie/date do not match the screening");
        if (!hall.seats().containsAll(request.seats()))
            throw new IllegalArgumentException("A seat does not belong to the screening hall");

        return store.confirmPurchase(request, () -> {
            // Создаем заказ под блокировкой; повтор запроса возвращает прежний результат.
            var now = clock.instant();
            if (!screening.startsAt().isAfter(now))
                throw new BookingException(SCREENING_STARTED, "Screening has already started");
            return new Order(UUID.randomUUID(), request.requestId(), request.customer(), movie.id(),
                    movie.title(), screening.id(), screeningDateInCinemaTimeZone, screening.startsAt(), cinema.name(), hall.name(),
                    request.seats(), screening.ticketPrice().multiply(BigDecimal.valueOf(request.seats().size())), now);
        });
    }

    /**
     * Находит оформленную покупку по номеру заказа.
     * Что было куплено по этому номеру заказа?
     *
     * @param id номер заказа
     * @return данные заказа на момент покупки
     * @throws BookingException если заказ не найден
     */
    public Order getOrderById(UUID id) {
        return store.findOrderById(id).orElseThrow(() -> new BookingException(NOT_FOUND, "Order not found"));
    }

    /**
     * Фильтрует заказы и сортирует их по времени оформления и номеру.
     * Какие покупки подходят под период, фильм и список клиентов?
     *
     * @param filter условия поиска
     * @return подходящие заказы
     */
    public List<Order> findOrdersByFilter(OrderFilter filter) {
        return store.findAllOrders().stream().filter(filter::matchesOrder)
                .sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::id)).toList();
    }
}
