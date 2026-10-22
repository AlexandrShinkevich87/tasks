package ru.mentoring.cinema.model;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Кинотеатр с городом и часовым поясом расписания.
 *
 * @param id   идентификатор кинотеатра
 * @param name название кинотеатра
 * @param city город
 * @param zone часовой пояс сеансов
 */
public record Cinema(long id, String name, String city, ZoneId zone) {
    /**
     * Проверяет идентификатор и обязательные данные кинотеатра.
     *
     * @param id   идентификатор кинотеатра
     * @param name название кинотеатра
     * @param city город
     * @param zone часовой пояс сеансов
     */
    public Cinema {
        Checks.requirePositiveNumber(id, "cinemaId");
        name = Checks.requireNonBlankText(name, "name");
        city = Checks.requireNonBlankText(city, "city");
        Objects.requireNonNull(zone);
    }
}
