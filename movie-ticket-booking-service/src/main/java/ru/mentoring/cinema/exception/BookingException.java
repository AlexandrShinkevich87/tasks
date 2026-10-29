package ru.mentoring.cinema.exception;

import ru.mentoring.cinema.model.Code;

/**
 * Бизнес-ошибка покупки с понятным программным кодом причины.
 */
public final class BookingException extends RuntimeException {
    private final Code code;

    /**
     * Создает ошибку с кодом и описанием причины.
     *
     * @param code    код бизнес-ошибки
     * @param message описание причины отказа
     */
    public BookingException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Возвращает код причины отказа.
     *
     * @return код бизнес-ошибки
     */
    public Code getErrorCode() {
        return code;
    }
}
