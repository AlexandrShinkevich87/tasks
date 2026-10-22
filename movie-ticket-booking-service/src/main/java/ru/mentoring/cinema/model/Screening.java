package ru.mentoring.cinema.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Показ фильма в конкретном зале с ценой одного билета.
 *
 * @param id идентификатор сеанса
 * @param movieId идентификатор фильма
 * @param hallId идентификатор зала
 * @param startsAt момент начала сеанса
 * @param ticketPrice цена одного билета
 */
public record Screening(long id, long movieId, long hallId, Instant startsAt, BigDecimal ticketPrice) {
    /**
     * Проверяет данные сеанса и точность цены до копеек.
     *
     * @param id идентификатор сеанса
     * @param movieId идентификатор фильма
     * @param hallId идентификатор зала
     * @param startsAt момент начала сеанса
     * @param ticketPrice цена одного билета
     */
    public Screening {
        Checks.requirePositiveNumber(id, "screeningId");
        Checks.requirePositiveNumber(movieId, "movieId");
        Checks.requirePositiveNumber(hallId, "hallId");
        Objects.requireNonNull(startsAt);
        ticketPrice = Checks.validateAndNormalizePrice(ticketPrice);
    }
}
