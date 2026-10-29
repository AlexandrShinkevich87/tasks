package ru.mentoring.cinema.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Общие проверки значений моделей кинотеатра.
 */
public final class Checks {
    private Checks() {
    }

    /**
     * Проверяет заполнение обязательного текстового поля.
     *
     * @param value проверяемый текст
     * @param field имя поля для сообщения об ошибке
     * @return непустой текст без крайних пробелов
     * @throws IllegalArgumentException если текст отсутствует или состоит из пробелов
     */
    public static String requireNonBlankText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }

    /**
     * Проверяет, что номер или количество больше нуля.
     *
     * @param value проверяемое число
     * @param field имя поля для сообщения об ошибке
     * @return проверенное положительное число
     * @throws IllegalArgumentException если число не положительное
     */
    public static long requirePositiveNumber(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    /**
     * Проверяет, что стоимость неотрицательна и указана с точностью до копеек.
     *
     * @param value проверяемая сумма
     * @return сумма с двумя знаками после запятой
     * @throws IllegalArgumentException если сумма отсутствует или отрицательна
     * @throws ArithmeticException      если нужны доли меньше копейки
     */
    public static BigDecimal validateAndNormalizePrice(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Price must be non-negative");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
