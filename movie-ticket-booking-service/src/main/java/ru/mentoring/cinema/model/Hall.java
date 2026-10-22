package ru.mentoring.cinema.model;

import java.util.Objects;
import java.util.Set;

/**
 * Зал кинотеатра с заданным количеством кресел.
 *
 * @param id       идентификатор зала
 * @param cinemaId идентификатор кинотеатра
 * @param name     название зала
 * @param seats    кресла зала без повторов
 */
public record Hall(long id, long cinemaId, String name, Set<Seat> seats) {
    /**
     * Проверяет сведения о зале и наличие кресел.
     *
     * @param id       идентификатор зала
     * @param cinemaId идентификатор кинотеатра
     * @param name     название зала
     * @param seats    кресла зала без повторов
     */
    public Hall {
        Checks.requirePositiveNumber(id, "hallId");
        Checks.requirePositiveNumber(cinemaId, "cinemaId");
        name = Checks.requireNonBlankText(name, "name");
        seats = Set.copyOf(Objects.requireNonNull(seats));
        if (seats.isEmpty()) throw new IllegalArgumentException("Hall must contain seats");
    }
}
