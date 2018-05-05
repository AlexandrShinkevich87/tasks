package doctors.schedule.model;

import java.time.DayOfWeek;
import java.util.*;

public class Doctor {
    private String fullName;
    private Map<DayOfWeek, List<TimeSlot>> schedule;

    private List<TimeSlot> sort(Collection<TimeSlot> timeSlots) {
        List<TimeSlot> result = new ArrayList<>(timeSlots);
        Collections.sort(result, TimeSlot::compareTo);
        return result;
    }
}
