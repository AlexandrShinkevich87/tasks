package ru.mentoring.cinema.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Данные сеанса для показа расписания и свободных мест.
 *
 * @param screening      сеанс
 * @param movie          фильм
 * @param cinema         кинотеатр
 * @param hall           зал
 * @param availableSeats свободные кресла в порядке ряда и номера
 * @param date           дата сеанса в часовом поясе кинотеатра
 */
public record SessionView(Screening screening, Movie movie, Cinema cinema, Hall hall, List<Seat> availableSeats,
                          LocalDate date) {
}
