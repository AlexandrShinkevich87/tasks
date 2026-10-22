package ru.mentoring.cinema.demo;

import ru.mentoring.cinema.CinemaRuntime;
import ru.mentoring.cinema.exception.BookingException;
import ru.mentoring.cinema.model.Code;
import ru.mentoring.cinema.model.Result;
import ru.mentoring.cinema.model.Seat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Показывает одновременные попытки нескольких клиентов купить билеты.
 */
public final class ConcurrentCustomers {
    private ConcurrentCustomers() {
    }

    /**
     * Моделирует одновременную покупку выбранных мест разными клиентами.
     *
     * @param runtime   кинотеатр, в котором клиенты покупают билеты
     * @param customers число покупателей
     * @param choices   непустой список кресел сеанса 101
     * @return число успешных покупок и конфликтов
     * @throws Exception если задача завершилась неожиданной ошибкой или превысила время ожидания
     */
    public static Result simulateConcurrentPurchases(CinemaRuntime runtime, int customers, List<Seat> choices) throws Exception {
        if (customers <= 0 || choices.isEmpty()) throw new IllegalArgumentException("Customers and seats required");
        CountDownLatch ready = new CountDownLatch(customers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < customers; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        runtime.bookings.purchaseTickets(runtime.createBookingRequest("user-" + index, UUID.randomUUID().toString(),
                                101, List.of(choices.get(index % choices.size()))));
                        return true;
                    } catch (BookingException conflict) {
                        if (conflict.getErrorCode() != Code.SEAT_TAKEN) throw conflict;
                        return false;
                    }
                }));
            }
            try {
                if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Customers did not reach gate");
            } finally {
                start.countDown();
            }
            int purchases = 0;
            for (Future<Boolean> future : futures)
                if (future.get(10, TimeUnit.SECONDS)) purchases++;
            return new Result(customers, purchases, customers - purchases);
        }
    }
}
