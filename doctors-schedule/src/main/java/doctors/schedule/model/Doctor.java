package doctors.schedule.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * class contains information about a doctor with his weekly schedule
 */
@NoArgsConstructor
@Data
@EqualsAndHashCode(exclude = "schedule")
@Slf4j
public class Doctor {
    private String fullName;
    /**
     * Map contains Day of week with time slot
     */
    private Map<String, List<TimeSlot>> schedule = new HashMap<>();

    public Map<String, List<TimeSlot>> mergeSchedule(Map<String, List<TimeSlot>> schedule) {
        Map<String, List<TimeSlot>> mergedSchedule = new HashMap<>();

        log.info(String.format("schedule 1: %s", this.schedule));
        log.info(String.format("schedule 2: %s", schedule));

        for (Map.Entry<String, List<TimeSlot>> entry : this.schedule.entrySet()) {
            DayOfWeek dayOfWeek = DayOfWeek.valueOf(entry.getKey());
            List<TimeSlot> timeSlots = entry.getValue();

            List<TimeSlot> mergedTimeSlot =
                    TimeSlot.merge(
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
        }

        log.info(String.format("merged schedule %s: ", mergedSchedule));

        return mergedSchedule;
    }
}
