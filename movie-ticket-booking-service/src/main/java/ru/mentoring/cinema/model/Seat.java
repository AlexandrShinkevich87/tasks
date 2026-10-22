package ru.mentoring.cinema.model;

/**
 * Кресло зала, заданное номером ряда и места.
 *
 * @param row    номер ряда, начиная с 1
 * @param number номер кресла в ряду, начиная с 1
 */
public record Seat(int row, int number) implements Comparable<Seat> {
    /**
     * Проверяет положительные координаты кресла.
     *
     * @param row    номер ряда, начиная с 1
     * @param number номер кресла в ряду, начиная с 1
     */
    public Seat {
        Checks.requirePositiveNumber(row, "row");
        Checks.requirePositiveNumber(number, "number");
    }

    /**
     * Сравнивает кресла сначала по ряду, затем по номеру.
     *
     * @param other кресло для сравнения
     * @return отрицательное число, ноль или положительное число по порядку кресел
     */
    @Override
    public int compareTo(Seat other) {
        int byRow = Integer.compare(row, other.row);
        return byRow != 0 ? byRow : Integer.compare(number, other.number);
    }
}
