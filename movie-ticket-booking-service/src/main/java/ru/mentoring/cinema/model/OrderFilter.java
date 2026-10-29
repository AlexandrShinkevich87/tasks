package ru.mentoring.cinema.model;

import java.time.Instant;
import java.util.Set;

/**
 * Условия поиска заказов.
 *
 * @param from        начало периода включительно или null
 * @param to          конец периода исключительно или null
 * @param movieId     фильм или null для всех фильмов
 * @param customerIds клиенты; пустой набор или null означает всех
 */
public record OrderFilter(Instant from, Instant to, Long movieId, Set<String> customerIds) {
    /**
     * Проверяет корректность периода поиска заказов.
     *
     * @param from        начало периода включительно или null
     * @param to          конец периода исключительно или null
     * @param movieId     фильм или null для всех фильмов
     * @param customerIds клиенты; пустой набор или null означает всех
     */
    public OrderFilter {
        customerIds = customerIds == null ? Set.of() : Set.copyOf(customerIds);
        if (from != null && to != null && !from.isBefore(to))
            throw new IllegalArgumentException("from must be earlier than to");
        if (movieId != null) ru.mentoring.cinema.model.Checks.requirePositiveNumber(movieId, "movieId");
    }

    /**
     * Проверяет все заданные условия поиска заказа.
     *
     * @param order проверяемый заказ
     * @return true, если заказ соответствует каждому условию
     */
    public boolean matchesOrder(Order order) {
        return (from == null || !order.createdAt().isBefore(from))
                && (to == null || order.createdAt().isBefore(to))
                && (movieId == null || movieId == order.movieId())
                && (customerIds.isEmpty() || customerIds.contains(order.customer().id()));
    }
}
