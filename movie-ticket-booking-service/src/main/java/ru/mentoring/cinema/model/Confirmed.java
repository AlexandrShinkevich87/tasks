package ru.mentoring.cinema.model;

/**
 * Сохраняет исходный запрос и результат для повторных обращений.
 *
 * @param request первый подтвержденный запрос
 * @param order   созданный заказ
 */
public record Confirmed(BookingRequest request, Order order) {
}
