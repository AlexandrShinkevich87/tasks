package doctors.schedule.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class DoctorsSchedule {
    private Map<String, Doctor> doctorsSchedule = new HashMap<>();



}
