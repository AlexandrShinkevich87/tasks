package ru.mentoring.cinema.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Фильм с жанрами и включительными границами проката.
 *
 * @param id идентификатор фильма
 * @param title название фильма
 * @param description описание фильма
 * @param genres непустой набор жанров
 * @param releaseFrom первый день проката
 * @param releaseTo последний день проката
 */
public record Movie(long id, String title, String description, Set<String> genres,
                    LocalDate releaseFrom, LocalDate releaseTo) {
    /**
     * Проверяет название, жанры и период проката фильма.
     *
     * @param id идентификатор фильма
     * @param title название фильма
     * @param description описание фильма
     * @param genres непустой набор жанров
     * @param releaseFrom первый день проката
     * @param releaseTo последний день проката
     */
    public Movie {
        Checks.requirePositiveNumber(id, "movieId");
        title = Checks.requireNonBlankText(title, "title");
        description = Objects.requireNonNull(description);
        genres = Set.copyOf(Objects.requireNonNull(genres));
        if (genres.isEmpty() || genres.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("At least one non-blank genre is required");
        Objects.requireNonNull(releaseFrom);
        Objects.requireNonNull(releaseTo);
        if (releaseTo.isBefore(releaseFrom)) throw new IllegalArgumentException("Invalid release dates");
    }

    /**
     * Проверяет, идет ли фильм в прокате в указанную дату.
     *
     * @param date дата проверки
     * @return true, если дата входит в период проката, включая границы
     */
    public boolean isShowingOn(LocalDate date) {
        return !date.isBefore(releaseFrom) && !date.isAfter(releaseTo);
    }
}
