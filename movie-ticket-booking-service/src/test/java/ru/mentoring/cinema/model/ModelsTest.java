package ru.mentoring.cinema.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет данные кинотеатра, стоимость билетов и правила выбора мест.
 */
class ModelsTest {
    private static final Customer CUSTOMER = new Customer("alice", "+77000000000", "alice@example.test");

    /**
     * Проверяет, что координаты однозначно определяют кресло, а места упорядочены по ряду и номеру.
     */
    @Test
    public void immutableSeatKeysHaveValueEqualityAndNaturalOrder() {
        var seats = new HashSet<>(List.of(new Seat(2, 1), new Seat(2, 1), new Seat(1, 5)));
        assertEquals(2, seats.size());
        assertTrue(seats.contains(new Seat(2, 1)));
        assertEquals(List.of(new Seat(1, 5), new Seat(2, 1)), new TreeSet<>(seats).stream().toList());
    }

    /**
     * Проверяет отказ при пустой покупке или повторе кресла.
     */
    @Test
    public void rejectsDuplicatesInsteadOfSilentlyChangingThePurchase() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("r", CUSTOMER, 1,
                LocalDate.of(2030, 1, 2), 101, List.of(new Seat(1, 1), new Seat(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("r", CUSTOMER, 1,
                LocalDate.of(2030, 1, 2), 101, List.of()));
    }

    /**
     * Проверяет, что изменение первоначального выбора мест не меняет уже поданную заявку.
     */
    @Test
    public void requestTakesAnImmutableCopyOfTheSeatList() {
        var source = new ArrayList<>(List.of(new Seat(1, 1)));
        var request = new BookingRequest("r", CUSTOMER, 1, LocalDate.of(2030, 1, 2), 101, source);
        source.add(new Seat(1, 2));
        assertEquals(1, request.seats().size());
        assertThrows(UnsupportedOperationException.class, () -> request.seats().clear());
    }

    /**
     * Проверяет обязательные контакты и точность денежных расчетов.
     */
    @Test
    public void validatesContactAndMoneyWithoutBinaryFloatingPoint() {
        assertThrows(IllegalArgumentException.class, () -> new Customer("a", " ", "a@example.test"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("a", "123", "bad-email"));
        assertThrows(IllegalArgumentException.class, () -> new Seat(0, 1));
        assertEquals(new BigDecimal("3999.80"),
                Checks.validateAndNormalizePrice(new BigDecimal("1999.90")).multiply(BigDecimal.valueOf(2)));
        assertThrows(ArithmeticException.class, () -> Checks.validateAndNormalizePrice(new BigDecimal("1.001")));
    }

    /**
     * Проверяет включение первого и последнего дня проката.
     */
    @Test
    public void releaseWindowIncludesBothDates() {
        LocalDate first = LocalDate.of(2030, 1, 1);
        var movie = new Movie(1, "Film", "", Set.of("drama"), first, first.plusDays(2));
        assertTrue(movie.isShowingOn(first));
        assertTrue(movie.isShowingOn(first.plusDays(2)));
        assertFalse(movie.isShowingOn(first.plusDays(3)));
    }
}
