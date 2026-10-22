package ru.mentoring.cinema.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Запрос на покупку мест; requestId защищает от повторного оформления.
 *
 * @param requestId   ключ запроса в пределах одного клиента
 * @param customer    контакты покупателя
 * @param movieId     идентификатор фильма
 * @param date        дата сеанса в часовом поясе кинотеатра
 * @param screeningId идентификатор сеанса
 * @param seats       выбранные кресла в порядке клиента
 */
public record BookingRequest(String requestId, Customer customer, long movieId, LocalDate date,
                             long screeningId, List<Seat> seats) {
    /**
     * Отклоняет покупку без мест или с повторным выбором одного кресла.
     *
     * @param requestId   ключ запроса в пределах одного клиента
     * @param customer    контакты покупателя
     * @param movieId     идентификатор фильма
     * @param date        дата сеанса в часовом поясе кинотеатра
     * @param screeningId идентификатор сеанса
     * @param seats       выбранные кресла в порядке клиента
     */
    public BookingRequest {
        requestId = Checks.requireNonBlankText(requestId, "requestId");
        Objects.requireNonNull(customer, "customer");
        Checks.requirePositiveNumber(movieId, "movieId");
        Checks.requirePositiveNumber(screeningId, "screeningId");
        Objects.requireNonNull(date, "date");
        seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
        if (seats.isEmpty()) throw new IllegalArgumentException("Choose at least one seat");
        if (new HashSet<>(seats).size() != seats.size())
            throw new IllegalArgumentException("Duplicate seat in request");
    }
}
