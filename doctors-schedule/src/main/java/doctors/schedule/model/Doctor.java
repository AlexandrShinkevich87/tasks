package doctors.schedule.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

@NoArgsConstructor
@Data
@EqualsAndHashCode(exclude = "schedule")
public class Doctor {
    private String fullName;
    /**
     * Map contains Day of week with time slot
     */
    private Map<String, List<TimeSlot>> schedule = new HashMap<>();

    private List<TimeSlot> sort(Collection<TimeSlot> timeSlots) {
        List<TimeSlot> result = new ArrayList<>(timeSlots);
        Collections.sort(result, TimeSlot::compareTo);
        return result;
    }

    //['ИВАНОВ ИВАН ИВАНОВИЧ', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', ''],
    //['ИВАНОВ ИВАН ИВАНОВИЧ', '08:00-12:00', '08:00-10:00', '08:00-16:00', '', '', '', ''],
    public Map<String, List<TimeSlot>> mergeSchedule(Map<String, List<TimeSlot>> schedule) {
        Map<String, List<TimeSlot>> mergedSchedule = new HashMap<>();

        for (Map.Entry<String, List<TimeSlot>> entry : this.schedule.entrySet()) {
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(entry.getKey());
            List<TimeSlot> timeSlots = entry.getValue();

            List<TimeSlot> mergedTimeSlot =
                    mergeTimeSlot(
                            new ArrayList<TimeSlot>() {{
                                if (!timeSlots.contains(null)) {
                                    addAll(timeSlots);
                                }
                                if (!schedule.get(dayOfWeek.name()).contains(null)) {
                                    addAll(schedule.get(dayOfWeek.name()));
                                }
                            }}
                    );
            mergedSchedule.put(dayOfWeek.name(), mergedTimeSlot);
            System.out.println(entry.getKey() + "/" + entry.getValue());
        }
        return mergedSchedule;
    }

    private List<TimeSlot> mergeTimeSlot(List<TimeSlot> intervalTimeSlots) {
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
}
