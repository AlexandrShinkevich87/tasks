package doctors.schedule.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
@EqualsAndHashCode(exclude = "schedule")
public class Doctor {
    private String fullName;
    /**
     * Map contains Day of week with time slot
     */
    private Map<String, List<TimeSlot>> schedule = new HashMap<>();

    //['ИВАНОВ ИВАН ИВАНОВИЧ', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', '12:00-16:00', ''],
    //['ИВАНОВ ИВАН ИВАНОВИЧ', '08:00-12:00', '08:00-10:00', '08:00-16:00', '', '', '', ''],
    public Map<String, List<TimeSlot>> mergeSchedule(Map<String, List<TimeSlot>> schedule) {
        Map<String, List<TimeSlot>> mergedSchedule = new HashMap<>();

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
            System.out.println(entry.getKey() + "/" + entry.getValue());
        }
        return mergedSchedule;
    }
}
