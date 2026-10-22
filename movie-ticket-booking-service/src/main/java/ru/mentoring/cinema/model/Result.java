package ru.mentoring.cinema.model;

/**
 * Итог конкуренции покупателей за выбранные кресла.
 *
 * @param customers число участников
 * @param purchases число успешных покупок
 * @param conflicts число отказов из-за занятого кресла
 */
public record Result(int customers, int purchases, int conflicts) {}
