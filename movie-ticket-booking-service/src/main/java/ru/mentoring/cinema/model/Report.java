package ru.mentoring.cinema.model;

import java.util.List;

/**
 * Число ожидающих уведомлений и результаты завершенных отправок.
 *
 * @param queued    число уведомлений, ожидающих отправки
 * @param completed результаты завершенных отправок
 */
public record Report(int queued, List<Delivery> completed) {
    /**
     * Принимает число ожидающих уведомлений и результаты завершенных отправок.
     *
     * @param queued    число ожидающих уведомлений
     * @param completed результаты завершенных отправок
     */
    public Report {
        completed = List.copyOf(completed);
    }

    /**
     * Считает успешно доставленные уведомления.
     *
     * @return число успешных доставок
     */
    public long countDeliveredNotifications() {
        return completed.stream().filter(Delivery::delivered).count();
    }

    /**
     * Считает уведомления с исчерпанными попытками отправки.
     *
     * @return число неуспешных доставок
     */
    public long countFailedNotifications() {
        return completed.size() - countDeliveredNotifications();
    }
}
