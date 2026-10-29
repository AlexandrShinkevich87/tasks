package ru.mentoring.cinema;

import ru.mentoring.cinema.demo.CinemaDemo;

/**
 * Сервис заказа билетов.
 */
public final class CinemaApplication {

    private CinemaApplication() {
    }

    /**
     * Запускает демонстрационный сценарий покупки билетов.
     *
     * @param args пустой список или единственный аргумент --demo
     * @throws Exception если конкурентная покупка или ожидание уведомлений завершились ошибкой
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1 && !args[0].equals("--demo"))) {
            throw new IllegalArgumentException("Usage: [--demo]");
        }
        CinemaDemo.runCinemaDemo();
    }
}
