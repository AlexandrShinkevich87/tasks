package ru.mentoring.cinema.model;

/**
 * Один критерий сортировки; критерии применяются в порядке списка.
 *
 * @param field      поле сортировки
 * @param descending true для убывания, false для возрастания
 */
public record Sort(SortField field, boolean descending) {
}
