package doctors.schedule.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    private static List<TimeSlot> sort(Collection<TimeSlot> timeSlots) {
        List<TimeSlot> result = new ArrayList<>(timeSlots);
        Collections.sort(result, TimeSlot::compareTo);
        return result;
    }

    public static List<TimeSlot> merge(List<TimeSlot> intervalTimeSlots) {
        if (intervalTimeSlots.size() <= 1) {
            return intervalTimeSlots;
        }
        List<TimeSlot> intervals = sort(intervalTimeSlots);

        TimeSlot pastInterval = intervals.get(0);

        List<TimeSlot> mergedInterval = new ArrayList<>();

        for (int i = 1; i < intervals.size(); i++) {
            TimeSlot currentInterval = intervals.get(i);

            // if the current time of slot overlaps with the last time of slot, use the later end time of the two
            // currentInterval.start <= pastInterval.end
            if (currentInterval.getStart().isBefore(pastInterval.getEnd()) || currentInterval.getStart().equals(pastInterval.getEnd())) {
                /*// if the first interval can be merged with the currentInterval interval
                // end = max(currentInterval.end, end);
                end = currentInterval.getEnd().isAfter(end) ? currentInterval.getEnd() : end;*/
                if (currentInterval.getEnd().isAfter(pastInterval.getEnd())) {
                    // this means currentInterval finishes outside of the past-intervals limit
                    TimeSlot newInterval = new TimeSlot(pastInterval.getStart(), currentInterval.getEnd());
                    pastInterval = newInterval;
                }
            } else {
                // as the past interval cannot be merged within the current interval, its the beginning of new interval
                mergedInterval.add(pastInterval);
                pastInterval = currentInterval;
            }
        }
        mergedInterval.add(pastInterval);
        return mergedInterval;
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
