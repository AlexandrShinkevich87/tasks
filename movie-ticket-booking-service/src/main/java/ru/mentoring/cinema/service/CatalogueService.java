package ru.mentoring.cinema.service;

import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.*;
import ru.mentoring.cinema.storage.InMemoryBookingStore;
import ru.mentoring.cinema.storage.InMemoryCatalogue;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Формирует афишу, расписание и список доступных кресел.
 */
public final class CatalogueService {
    private final InMemoryCatalogue catalogue;
    private final InMemoryBookingStore store;
    private final Clock clock;

    /**
     * Создает сервис афиши и расписания.
     *
     * @param catalogue фильмы, кинотеатры, залы и сеансы
     * @param store     хранилище покупок для учета занятости и популярности
     * @param clock     часы в часовом поясе афиши
     */
    public CatalogueService(InMemoryCatalogue catalogue, InMemoryBookingStore store, Clock clock) {
        this.catalogue = catalogue;
        this.store = store;
        this.clock = clock;
    }

    /**
     * Фильтрует афишу и применяет несколько критериев сортировки.
     * Какие фильмы показать в афише и в каком порядке?
     *
     * @param status режим отбора по периоду проката
     * @param genre  жанр без учета регистра или null
     * @param sort   критерии сортировки по приоритету
     * @return отсортированная афиша
     */
    public List<MovieListing> findMoviesForPoster(Status status, String genre, List<Sort> sort) {
        LocalDate today = LocalDate.now(clock);
        // Популярность — число купленных билетов с момента запуска приложения.
        Map<Long, Long> soldTicketCountByMovieId = store.findAllOrders().stream()
                .collect(Collectors.groupingBy(Order::movieId,
                        Collectors.summingLong(order -> order.seats().size()))
                );
        Comparator<MovieListing> comparator = buildMoviePosterSortOrder(sort);
        return catalogue.findAllMovies().stream()
                .filter(movie -> switch (status) {
                    case CURRENT -> movie.isShowingOn(today);
                    case UPCOMING ->
                            movie.releaseFrom().isAfter(today) && !movie.releaseFrom().isAfter(today.plusDays(30));
                    case ALL -> true;
                })
                .filter(movie -> genre == null || movie.genres().stream().anyMatch(g -> g.equalsIgnoreCase(genre)))
                .map(movie -> new MovieListing(movie, soldTicketCountByMovieId.getOrDefault(movie.id(), 0L)))
                .sorted(comparator).toList();
    }

    /**
     * Формирует порядок показа фильмов в афише.
     *
     * @param sort критерии сортировки в порядке приоритета
     * @return порядок фильмов; при равенстве критериев — по идентификатору
     */
    private static Comparator<MovieListing> buildMoviePosterSortOrder(List<Sort> sort) {
        Comparator<MovieListing> comparator = (a, b) -> 0;
        for (Sort item : List.copyOf(sort)) {
            Comparator<MovieListing> next = switch (item.field()) {
                case TITLE -> Comparator.comparing(value -> value.movie().title());
                case GENRE ->
                        Comparator.comparing(value -> value.movie().genres().stream().sorted().findFirst().orElse(""));
                case POPULARITY -> Comparator.comparingLong(MovieListing::popularity);
            };
            comparator = comparator.thenComparing(item.descending() ? next.reversed() : next);
        }
        comparator = comparator.thenComparingLong(value -> value.movie().id());
        return comparator;
    }

    /**
     * Возвращает данные фильма по идентификатору.
     *
     * @param id идентификатор фильма
     * @return найденный фильм
     * @throws BookingException если фильм не найден
     */
    public Movie getMovieById(long id) {
        return catalogue.findMovieById(id).orElseThrow(() ->
                new BookingException(Code.NOT_FOUND, "Movie not found"));
    }

    /**
     * Выбирает сеансы фильма в городе на локальную дату кинотеатра.
     * Где посмотреть этот фильм в выбранном городе и в выбранный день?
     *
     * @param movieId идентификатор фильма
     * @param city    город без учета регистра
     * @param date    дата показа
     * @return сеансы по времени начала
     */
    public List<SessionView> findScreeningsByMovieCityAndDate(long movieId, String city, LocalDate date) {
        getMovieById(movieId);
        return catalogue.findAllScreeningsOrderedByStartTime().stream().filter(s -> s.movieId() == movieId).map(this::buildSessionView)
                .filter(view -> view.cinema().city().equalsIgnoreCase(city))
                .filter(view -> view.screening().startsAt().atZone(view.cinema().zone()).toLocalDate().equals(date))
                .toList();
    }

    /**
     * Возвращает сведения о сеансе, кинотеатре и зале со свободными местами.
     * Какие места этого сеанса ещё доступны?
     *
     * @param screening сеанс из каталога
     * @return сведения о сеансе и доступные для покупки места
     */
    public SessionView buildSessionView(Screening screening) {
        Hall hall = catalogue.findHallById(screening.hallId()).orElseThrow();
        Cinema cinema = catalogue.findCinemaById(hall.cinemaId()).orElseThrow();
        Set<Seat> occupied = store.findOccupiedSeatsByScreeningId(screening.id());
        List<Seat> available = hall.seats().stream().filter(seat -> !occupied.contains(seat)).sorted().toList();
        return new SessionView(screening, getMovieById(screening.movieId()), cinema, hall, available, screening.startsAt().atZone(cinema.zone()).toLocalDate());
    }
}
