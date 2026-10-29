package ru.mentoring.cinema.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.mentoring.cinema.model.*;
import ru.mentoring.cinema.storage.*;

import static org.junit.jupiter.api.Assertions.*;
/** Проверяет афишу, расписание, доступность мест и фильтры заказов. */
class CatalogueAndFiltersTest {
    final BookingFlowTest fixture = new BookingFlowTest();
    final CatalogueService catalogue = new CatalogueService(fixture.catalogue, fixture.store, BookingFlowTest.CLOCK);

    /** Проверяет текущий прокат, будущие премьеры и фильтр жанра. */
    @Test public void listsCurrentAndUpcomingMoviesAndFiltersGenre() {
        assertEquals(List.of("Arrival", "Interstellar"), catalogue.findMoviesForPoster(Status.CURRENT, "sci-fi",
                List.of(new Sort(SortField.TITLE, false))).stream().map(v -> v.movie().title()).toList());
        assertEquals(List.of(3L), catalogue.findMoviesForPoster(Status.UPCOMING, null, List.of())
                .stream().map(v -> v.movie().id()).toList());
        assertTrue(catalogue.findMoviesForPoster(Status.CURRENT, "comedy", List.of()).isEmpty());
    }

    /** Проверяет приоритет сортировок и популярность по числу билетов. */
    @Test public void sortsByMultipleFieldsAndPopularityCountsTicketsNotOrders() {
        fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "1", 101, new Seat(1, 1), new Seat(1, 2)));
        var results = catalogue.findMoviesForPoster(Status.CURRENT, null,
                List.of(new Sort(SortField.POPULARITY, true), new Sort(SortField.TITLE, false)));
        assertEquals(1L, results.getFirst().movie().id());
        assertEquals(2, results.getFirst().popularity());
        var tie = catalogue.findMoviesForPoster(Status.ALL, null,
                List.of(new Sort(SortField.GENRE, false), new Sort(SortField.TITLE, false)));
        assertEquals(List.of("New Premiere", "Arrival", "Interstellar"),
                tie.stream().map(v -> v.movie().title()).toList());
    }

    /** Проверяет расписание по городу и местной дате кинотеатра. */
    @Test public void scheduleUsesCinemaLocalDateAndOnlyRequestedCity() {
        LocalDate localDate = LocalDate.of(2030, 1, 2);
        assertEquals(List.of(101L), catalogue.findScreeningsByMovieCityAndDate(1, "ALMATY", localDate)
                .stream().map(v -> v.screening().id()).toList());
        assertEquals(List.of(103L), catalogue.findScreeningsByMovieCityAndDate(1, "Astana", localDate)
                .stream().map(v -> v.screening().id()).toList());
        assertTrue(catalogue.findScreeningsByMovieCityAndDate(1, "Almaty", localDate.minusDays(1)).isEmpty());
        assertTrue(catalogue.findScreeningsByMovieCityAndDate(1, "Unknown", localDate).isEmpty());
    }

    /** Проверяет свободные кресла и совместное применение фильтров заказов. */
    @Test public void availabilityReflectsSalesAndOrdersCanBeCombinedFiltered() {
        var a = fixture.service.purchaseTickets(fixture.createBookingRequest("alice", "1", 101, new Seat(1, 1)));
        fixture.service.purchaseTickets(fixture.createBookingRequest("bob", "1", 102, new Seat(1, 1)));
        fixture.service.purchaseTickets(fixture.createBookingRequest("charlie", "1", 101, new Seat(1, 2)));
        assertEquals(13, catalogue.findScreeningsByMovieCityAndDate(1, "Almaty", a.date()).getFirst().availableSeats().size());
        var selected = fixture.service.findOrdersByFilter(new OrderFilter(a.createdAt(), a.createdAt().plusSeconds(1),
                1L, Set.of("alice", "bob")));
        assertEquals(List.of(a), selected);
        assertEquals(3, fixture.service.findOrdersByFilter(new OrderFilter(null, null, null, Set.of())).size());
        assertTrue(fixture.service.findOrdersByFilter(new OrderFilter(null, a.createdAt(), null, Set.of())).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new OrderFilter(a.createdAt(), a.createdAt(), null, null));
    }

    /** Проверяет порядок сеансов, сохранность сведений каталога и уникальность номеров. */
    @Test public void catalogueIsChronologicallyOrderedAndDoesNotLeakMutableCollections() {
        var schedule = fixture.catalogue.findAllScreeningsOrderedByStartTime();
        assertEquals(schedule.stream().map(Screening::startsAt).sorted().toList(),
                schedule.stream().map(Screening::startsAt).toList());
        assertThrows(UnsupportedOperationException.class, () -> fixture.catalogue.findAllMovies().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixture.catalogue.findHallById(1).orElseThrow().seats().clear());
        assertThrows(IllegalArgumentException.class, () -> new InMemoryCatalogue(
                List.of(fixture.catalogue.findMovieById(1).orElseThrow(), fixture.catalogue.findMovieById(1).orElseThrow()),
                List.of(), List.of(), List.of()));
    }
}
