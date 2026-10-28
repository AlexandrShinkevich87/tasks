package ru.mentoring.cinema.model;

import java.util.List;

/**
 * Текущие состояния outbox, без накопления истории прежних неудачных циклов.
 *
 * @param queued число заданий PENDING, ожидающих захвата
 * @param inFlight число заданий IN_FLIGHT, уже занятых отправщиками
 * @param completed результаты заданий SENT и FAILED
 */
public record Report(int queued, int inFlight, List<Delivery> completed) {
    /**
     * Создает независимый снимок результатов отправки.
     *
     * @param queued ожидающие задания
     * @param inFlight выполняемые задания
     * @param completed текущие завершенные результаты
     */
    public Report {
        if (queued < 0 || inFlight < 0) throw new IllegalArgumentException("Negative job count");
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
     * Считает уведомления с исчерпанными попытками текущего цикла.
     *
     * @return число заданий FAILED
     */
    public long countFailedNotifications() {
        return completed.size() - countDeliveredNotifications();
    }
}
