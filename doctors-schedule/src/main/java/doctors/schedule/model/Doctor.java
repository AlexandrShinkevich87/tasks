package doctors.schedule.model;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public class Doctor {
    private String fullName;
    private Map<DayOfWeek, List<TimeSlot>> schedule;
}
