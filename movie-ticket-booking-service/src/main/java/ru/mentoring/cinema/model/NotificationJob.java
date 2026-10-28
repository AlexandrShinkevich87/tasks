package ru.mentoring.cinema.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Неизменяемый снимок задания outbox; само задание не удаляется при отправке.
 *
 * @param event событие покупки, одинаковое при всех повторах
 * @param state состояние задания
 * @param claimToken идентификатор владельца только для IN_FLIGHT, иначе null
 * @param delivery итог последнего завершенного цикла отправки только для SENT/FAILED, иначе null
 */
public record NotificationJob(OrderCreated event, NotificationState state,
                              UUID claimToken, Delivery delivery) {
    /**
     * Не допускает противоречивых сочетаний состояния, владельца и результата.
     *
     * @param event событие покупки
     * @param state состояние задания
     * @param claimToken владелец текущего захвата или null
     * @param delivery результат завершенного цикла или null
     */
    public NotificationJob {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(state, "state");
        if ((state == NotificationState.IN_FLIGHT) == (claimToken == null))
            throw new IllegalArgumentException("Only IN_FLIGHT has an owner");
        boolean terminal = state == NotificationState.SENT || state == NotificationState.FAILED;
        if (terminal == (delivery == null))
            throw new IllegalArgumentException("Only terminal jobs have a delivery result");
        if (delivery != null && (!event.equals(delivery.event()) || delivery.attempts() <= 0
                || delivery.error() == null || delivery.delivered() != (state == NotificationState.SENT)))
            throw new IllegalArgumentException("Delivery does not match the job");
    }
}
