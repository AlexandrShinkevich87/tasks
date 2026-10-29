package ru.mentoring.cinema.model;

import java.util.UUID;

/**
 * Отправленное покупателю подтверждение заказа.
 *
 * @param eventId      идентификатор события
 * @param orderId      номер заказа
 * @param destination  email получателя
 * @param text         текст уведомления
 * @param workerThread имя исполнителя отправки подтверждения
 */
public record SentNotification(UUID eventId, UUID orderId, String destination,
                               String text, String workerThread) {
}
