package ru.mentoring.cinema.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Событие оформленной покупки без контактных данных клиента.
 *
 * @param eventId идентификатор события для защиты от повторной доставки
 * @param orderId номер созданного заказа
 * @param occurredAt момент оформления заказа
 */
public record OrderCreated(UUID eventId, UUID orderId, Instant occurredAt) {}
