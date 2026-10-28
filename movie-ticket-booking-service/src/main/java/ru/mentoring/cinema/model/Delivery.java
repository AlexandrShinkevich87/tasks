package ru.mentoring.cinema.model;

/**
 * Итог обработки одного уведомления.
 *
 * @param event     событие покупки
 * @param attempts  количество попыток в последнем завершенном цикле отправки
 * @param delivered признак успешной доставки
 * @param error     причина ошибки или пустая строка при успехе
 */
public record Delivery(OrderCreated event, int attempts, boolean delivered, String error) {
}
