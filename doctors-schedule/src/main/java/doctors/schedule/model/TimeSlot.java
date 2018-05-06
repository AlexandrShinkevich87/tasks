package doctors.schedule.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TimeSlot {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final LocalTime start;
    private final LocalTime end;


    public TimeSlot(LocalTime start, LocalTime end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        this.start = start;
        this.end = end;
    }

    boolean doesIntersectWith(TimeSlot otherTimeSlot) {
        return !isBefore(otherTimeSlot) && !isAfter(otherTimeSlot);
    }

    private boolean isBefore(TimeSlot otherTimeSlot) {
        return end.isBefore(otherTimeSlot.start) || end.equals(otherTimeSlot.start);
    }

    private boolean isAfter(TimeSlot otherTimeSlot) {
        return start.isAfter(otherTimeSlot.end) || start.equals(otherTimeSlot.end);
    }

    int compareTo(TimeSlot timeSlot) {
        return start.compareTo(timeSlot.start);
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return start.format(TIME_FORMATTER) + " - " + end.format(TIME_FORMATTER);
    }
}
