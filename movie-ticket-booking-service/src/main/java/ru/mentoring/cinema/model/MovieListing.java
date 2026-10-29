package ru.mentoring.cinema.model;

/**
 * Фильм в афише с числом проданных билетов.
 *
 * @param movie      данные фильма
 * @param popularity число купленных билетов с запуска приложения
 */
public record MovieListing(Movie movie, long popularity) {
}
