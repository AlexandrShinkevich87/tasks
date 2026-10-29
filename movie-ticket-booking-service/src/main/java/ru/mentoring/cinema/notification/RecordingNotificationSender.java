package ru.mentoring.cinema.notification;

import ru.mentoring.cinema.model.Order;
import ru.mentoring.cinema.model.OrderCreated;
import ru.mentoring.cinema.model.SentNotification;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Имитирует отправку покупателям подтверждений заказа без реальных писем.
 */
public class RecordingNotificationSender {
    private final ConcurrentHashMap<UUID, SentNotification> sentNotificationsByEventId = new ConcurrentHashMap<>();

    /**
     * Подтверждает покупку клиенту; повторная обработка не отправляет второе подтверждение.
     * Сообщить клиенту сведения о покупке
     *
     * @param event событие покупки
     * @param order оформленный заказ с контактами клиента
     * @throws InterruptedException если отправка подтверждения прервана
     */
    public void sendOrderConfirmation(OrderCreated event, Order order) throws InterruptedException {
        sentNotificationsByEventId.putIfAbsent(event.eventId(), new SentNotification(event.eventId(), order.id(),
                order.customer().email(), "Tickets for " + order.movieTitle() + ": " + order.seats(),
                Thread.currentThread().getName()));
    }

    /**
     * Возвращает отправленные подтверждения в порядке номеров заказов.
     *
     * @return отправленные подтверждения покупок
     */
    public List<SentNotification> getSentNotifications() {
        return sentNotificationsByEventId.values().stream().sorted(Comparator.comparing(SentNotification::orderId)).toList();
    }
}
