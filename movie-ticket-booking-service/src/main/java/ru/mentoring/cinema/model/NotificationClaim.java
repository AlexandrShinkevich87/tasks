package ru.mentoring.cinema.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Право одной попытки обработки завершить или вернуть уведомление в ожидание.
 *
 * @param event неизменное событие покупки
 * @param token уникальный идентификатор захвата, новый при каждом получении задания
 */
public record NotificationClaim(OrderCreated event, UUID token) {
    /**
     * Проверяет наличие события и идентификатора захвата.
     *
     * @param event событие покупки
     * @param token идентификатор текущего захвата
     */
    public NotificationClaim {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(token, "token");
    }
}
