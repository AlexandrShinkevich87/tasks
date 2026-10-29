package ru.mentoring.cinema.storage;

import ru.mentoring.cinema.model.Cinema;
import ru.mentoring.cinema.model.Hall;
import ru.mentoring.cinema.model.Movie;
import ru.mentoring.cinema.model.Screening;

import java.time.Instant;
import java.util.*;
import java.util.function.ToLongFunction;

/**
 * Каталог фильмов, кинотеатров, залов и сеансов.
 */
public final class InMemoryCatalogue {
    private final Map<Long, Movie> moviesById;
    private final Map<Long, Cinema> cinemasById;
    private final Map<Long, Hall> hallsById;
    private final Map<Long, Screening> screeningsById;
    private final NavigableMap<Instant, List<Screening>> screeningsByStartTime = new TreeMap<>();

    /**
     * Принимает каталог и проверяет соответствие сеансов фильмам и залам, а залов — кинотеатрам.
     *
     * @param movies     фильмы каталога
     * @param cinemas    кинотеатры
     * @param halls      залы с креслами
     * @param screenings сеансы
     */
    public InMemoryCatalogue(List<Movie> movies, List<Cinema> cinemas,
                             List<Hall> halls, List<Screening> screenings) {
        this.moviesById = indexItemsById(movies, Movie::id);
        this.cinemasById = indexItemsById(cinemas, Cinema::id);
        this.hallsById = indexItemsById(halls, Hall::id);
        this.screeningsById = indexItemsById(screenings, Screening::id);
        for (Hall hall : halls) {
            if (!this.cinemasById.containsKey(hall.cinemaId()))
                throw new IllegalArgumentException("Unknown cinema for hall " + hall.id());
        }
        for (Screening screening : screenings) {
            if (!this.moviesById.containsKey(screening.movieId()) || !this.hallsById.containsKey(screening.hallId()))
                throw new IllegalArgumentException("Unknown movie/hall for screening " + screening.id());
            screeningsByStartTime
                    .computeIfAbsent(
                            screening.startsAt(),
                            ignored -> new ArrayList<>()
                    )
                    .add(screening);
        }
        screeningsByStartTime.replaceAll(
                (time, items) -> items.stream()
                        .sorted(Comparator.comparingLong(Screening::id))
                        .toList()
        );
    }

    /**
     * Подготавливает записи каталога для поиска по уникальным номерам.
     *
     * @param <T>   тип записи каталога
     * @param items исходные записи
     * @param id    определение номера записи каталога
     * @return записи каталога по их номерам
     * @throws IllegalArgumentException если несколько записей имеют одинаковый номер
     */
    private static <T> Map<Long, T> indexItemsById(List<T> items, ToLongFunction<T> id) {
        Map<Long, T> itemsById = new LinkedHashMap<>();
        for (T item : List.copyOf(items)) {
            if (itemsById.putIfAbsent(id.applyAsLong(item), item) != null)
                throw new IllegalArgumentException("Duplicate catalogue ID");
        }
        return Collections.unmodifiableMap(itemsById);
    }

    /**
     * Возвращает фильмы в порядке добавления в каталог.
     *
     * @return все фильмы каталога
     */
    public List<Movie> findAllMovies() {
        return List.copyOf(moviesById.values());
    }

    /**
     * Ищет фильм по идентификатору.
     *
     * @param id идентификатор фильма
     * @return фильм или пустой результат
     */
    public Optional<Movie> findMovieById(long id) {
        return Optional.ofNullable(moviesById.get(id));
    }

    /**
     * Ищет кинотеатр по идентификатору.
     *
     * @param id идентификатор кинотеатра
     * @return кинотеатр или пустой результат
     */
    public Optional<Cinema> findCinemaById(long id) {
        return Optional.ofNullable(cinemasById.get(id));
    }

    /**
     * Ищет зал по идентификатору.
     *
     * @param id идентификатор зала
     * @return зал или пустой результат
     */
    public Optional<Hall> findHallById(long id) {
        return Optional.ofNullable(hallsById.get(id));
    }

    /**
     * Ищет сеанс по идентификатору.
     * Какой именно показ выбрал клиент?
     *
     * @param id идентификатор сеанса
     * @return сеанс или пустой результат
     */
    public Optional<Screening> findScreeningById(long id) {
        return Optional.ofNullable(screeningsById.get(id));
    }

    /**
     * Возвращает сеансы в порядке начала показа.
     *
     * @return сеансы по времени начала, при равенстве — по ID
     */
    public List<Screening> findAllScreeningsOrderedByStartTime() {
        return screeningsByStartTime.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}
