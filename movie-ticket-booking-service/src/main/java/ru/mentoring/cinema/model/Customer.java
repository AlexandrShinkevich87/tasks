package ru.mentoring.cinema.model;

/**
 * Контакты покупателя для оформления заказа и уведомления.
 *
 * @param id идентификатор клиента
 * @param phone номер телефона
 * @param email адрес электронной почты
 */
public record Customer(String id, String phone, String email) {
    /**
     * Проверяет заполнение контактов и простой формат email.
     *
     * @param id идентификатор клиента
     * @param phone номер телефона
     * @param email адрес электронной почты
     */
    public Customer {
        id = Checks.requireNonBlankText(id, "customerId");
        phone = Checks.requireNonBlankText(phone, "phone");
        email = Checks.requireNonBlankText(email, "email");
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Invalid email");
    }
}
