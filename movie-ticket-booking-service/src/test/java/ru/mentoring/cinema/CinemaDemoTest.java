package ru.mentoring.cinema;

import org.junit.jupiter.api.Test;
import ru.mentoring.cinema.demo.ConcurrentCustomers;
import ru.mentoring.cinema.model.Seat;
import ru.mentoring.cinema.model.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет согласованность учебного каталога и конкурентную демонстрацию.
 */
class CinemaDemoTest {
    /**
     * Проверяет одну локальную дату для афиши и дат проката.
     */
    @Test
    public void posterAndSampleReleaseDatesUseTheSameLocalBusinessDay() {
        try (var runtime = new CinemaRuntime(Clock.fixed(Instant.parse("2030-01-01T23:00:00Z"), ZoneOffset.UTC))) {
            var movies = runtime.movies.findMoviesForPoster(
                    Status.CURRENT, null, List.of());
            assertEquals(2, movies.size());
        }
    }

    /**
     * Проверяет итог гонки ста покупателей и уведомление победителя.
     *
     * @throws Exception если покупку или отправку подтверждения не удалось завершить
     */
    @Test
    public void demoRaceRunsToCompletionAndAllWinnersReceiveNotifications() throws Exception {
        try (var runtime = new CinemaRuntime(Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC))) {
            var result = ConcurrentCustomers.simulateConcurrentPurchases(runtime, 100, List.of(new Seat(1, 1)));
            assertEquals(1, result.purchases());
            assertEquals(99, result.conflicts());
            assertTrue(runtime.store.awaitProcessedNotifications(1, Duration.ofSeconds(3)));
            assertEquals(1, runtime.sender.getSentNotifications().size());
        }
    }
}
