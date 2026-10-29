package ru.mentoring.cinema.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сведения о покупке и сеансе на момент оформления заказа.
 *
 * @param id          номер заказа
 * @param requestId   ключ исходного запроса
 * @param customer    покупатель
 * @param movieId     идентификатор фильма
 * @param movieTitle  название фильма на момент покупки
 * @param screeningId идентификатор сеанса
 * @param date        локальная дата сеанса
 * @param startsAt    момент начала сеанса
 * @param cinemaName  название кинотеатра
 * @param hallName    название зала
 * @param seats       купленные кресла
 * @param totalPrice  полная стоимость покупки
 * @param createdAt   момент оформления заказа
 */
public record Order(UUID id, String requestId, Customer customer, long movieId, String movieTitle,
                    long screeningId, LocalDate date, Instant startsAt, String cinemaName,
                    String hallName, List<Seat> seats, BigDecimal totalPrice, Instant createdAt) {
    /**
     * Проверяет обязательные сведения об оформленном заказе и его стоимость.
     *
     * @param id          номер заказа
     * @param requestId   ключ исходного запроса
     * @param customer    покупатель
     * @param movieId     идентификатор фильма
     * @param movieTitle  название фильма на момент покупки
     * @param screeningId идентификатор сеанса
     * @param date        локальная дата сеанса
     * @param startsAt    момент начала сеанса
     * @param cinemaName  название кинотеатра
     * @param hallName    название зала
     * @param seats       купленные кресла
     * @param totalPrice  полная стоимость покупки
     * @param createdAt   момент оформления заказа
     */
    public Order {
        Objects.requireNonNull(id);
        Objects.requireNonNull(customer);
        seats = List.copyOf(seats);
        totalPrice = Checks.validateAndNormalizePrice(totalPrice);
        Objects.requireNonNull(createdAt);
    }
}
