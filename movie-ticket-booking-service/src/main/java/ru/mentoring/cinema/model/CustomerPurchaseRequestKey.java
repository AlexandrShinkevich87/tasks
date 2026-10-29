package ru.mentoring.cinema.model;

/**
 * Определяет запрос покупки клиента для защиты от повторного оформления заказа.
 *
 * @param customerId идентификатор клиента
 * @param requestId идентификатор запроса покупки, сохраняемый при повторной отправке
 */
public record CustomerPurchaseRequestKey(String customerId, String requestId) {}
