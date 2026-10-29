package ru.mentoring.cinema.storage;

import ru.mentoring.cinema.model.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Создает каталог фильмов, кинотеатров, залов и сеансов.
 */
public final class SampleCatalogue {
    private SampleCatalogue() {
    }

    /**
     * Создает учебные фильмы, залы и сеансы на завтра относительно заданной даты.
     *
     * @param clock источник текущего времени
     * @return учебный каталог с сеансами на завтра
     */
    public static InMemoryCatalogue createSampleCatalogue(Clock clock) {
        ZoneId zone = ZoneId.of("Asia/Almaty");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate tomorrow = today.plusDays(1);
        List<Movie> movies = List.of(
                new Movie(1, "Interstellar", "A journey beyond Earth", Set.of("sci-fi", "drama"), today.minusDays(5), today.plusDays(20)),
                new Movie(2, "Arrival", "A story about language", Set.of("sci-fi", "drama"), today, today.plusDays(20)),
                new Movie(3, "New Premiere", "Coming soon", Set.of("comedy"), today.plusDays(7), today.plusDays(30)));
        List<Cinema> cinemas = List.of(
                new Cinema(1, "Central Cinema", "Almaty", zone),
                new Cinema(2, "North Cinema", "Astana", zone));
        List<Hall> halls = List.of(
                new Hall(1, 1, "Hall A", createHallSeats(3, 5)),
                new Hall(2, 2, "Hall B", createHallSeats(2, 5)));
        return new InMemoryCatalogue(movies, cinemas, halls, List.of(
                new Screening(101, 1, 1, tomorrow.atTime(20, 0).atZone(zone).toInstant(), new BigDecimal("1999.90")),
                new Screening(102, 2, 1, tomorrow.atTime(22, 0).atZone(zone).toInstant(), new BigDecimal("1500.00")),
                new Screening(103, 1, 2, tomorrow.atTime(21, 0).atZone(zone).toInstant(), new BigDecimal("1800.00")),
                new Screening(104, 1, 1, tomorrow.plusDays(1).atTime(20, 0).atZone(zone).toInstant(), new BigDecimal("1999.90"))));
    }

    /**
     * Создает схему зала с заданным числом рядов и мест в каждом ряду.
     *
     * @param rows    число рядов
     * @param numbers число кресел в каждом ряду
     * @return упорядоченный набор кресел
     */
    private static Set<Seat> createHallSeats(int rows, int numbers) {
        Set<Seat> result = new TreeSet<>();
        for (int row = 1; row <= rows; row++)
            for (int number = 1; number <= numbers; number++) result.add(new Seat(row, number));
        return result;
    }
}
