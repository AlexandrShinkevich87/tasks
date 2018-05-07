package doctors.schedule.parser;

import doctors.schedule.model.Doctor;
import doctors.schedule.model.DoctorsSchedule;
import doctors.schedule.model.TimeSlot;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

@Slf4j
public class DoctorsScheduleDataParser {

    private static final String RANGE_DELIMITER = "-";

    private String[][] doctorsScheduleData;

    public DoctorsScheduleDataParser(String[][] doctorsScheduleData) {
        this.doctorsScheduleData = doctorsScheduleData;
    }

    public DoctorsSchedule parse() {
        DoctorsSchedule doctorsSchedule = new DoctorsSchedule();
        for (int i = 0; i < doctorsScheduleData.length; ++i) {
            Doctor doctor = new Doctor();
            for (int j = 0; j < doctorsScheduleData[i].length; ++j) {
                if (j == 0) {
                    final String fullName = doctorsScheduleData[i][0];
                    log.info(String.format("Parse doctor %s", fullName));
                    doctor.setFullName(fullName);
                } else {
                    TimeSlot timeSlot = parseTimeSlot(doctorsScheduleData[i][j]);
                    log.info(String.valueOf(String.format("%s: %s", DayOfWeek.of(j).name(), timeSlot)));
                    doctor.getSchedule().put(DayOfWeek.of(j).name(), Collections.singletonList(timeSlot));
                }
            }
            if (doctorsSchedule.getDoctorsSchedule().containsKey(doctor.getFullName())) {
                Doctor editDoctor = doctorsSchedule.getDoctorsSchedule().get(doctor.getFullName());
                log.info(String.format("Merge doctor %s", doctor.getFullName()));
                doctor.setSchedule(editDoctor.mergeSchedule(doctor.getSchedule()));
                doctorsSchedule.getDoctorsSchedule().put(doctor.getFullName(), doctor);
            } else {
                doctorsSchedule.getDoctorsSchedule().put(doctor.getFullName(), doctor);
            }
        }
        log.info(String.format("Parsed %s", doctorsSchedule));
        return doctorsSchedule;
    }

    private TimeSlot parseTimeSlot(String slot) {
        TimeSlot timeSlot;
        List<String> rangeParts = splitTime(slot, RANGE_DELIMITER);
        if (rangeParts.size() == 2) {
            String from = rangeParts.get(0).trim();
            String to = rangeParts.get(1).trim();

            LocalTime fromTime = LocalTime.parse(from);
            LocalTime toTime = LocalTime.parse(to);

            timeSlot = new TimeSlot(fromTime, toTime);
            return timeSlot;
        }
        return null;
    }

    private List<String> splitTime(String time, String delimiter) {
        int lastIndex = time.lastIndexOf(delimiter);
        if (lastIndex == -1) {
            return Collections.emptyList();
        }
        return asList(time.substring(0, lastIndex), time.substring(lastIndex + 1, time.length()));
    }

    public static String[][] convert(DoctorsSchedule doctorsSchedule) {
        List<String> doctorNameKeyList = new ArrayList<>(doctorsSchedule.getDoctorsSchedule().keySet());

        String[][] doctorScheduleConvert = new String[doctorNameKeyList.size()][Calendar.DAY_OF_WEEK + 1];
        for (int i = 0; i < doctorNameKeyList.size(); i++) {
            String doctorKey = doctorNameKeyList.get(i);
            Doctor doctor = doctorsSchedule.getDoctorsSchedule().get(doctorKey);
            log.info(String.valueOf(doctor));

            doctorScheduleConvert[i][0] = doctor.getFullName();
            for (int j = 1; j < doctor.getSchedule().keySet().size() + 1; j++) {
                String dayOfWeek = DayOfWeek.of(j).name();
                List<TimeSlot> timeSlotList = doctor.getSchedule().get(dayOfWeek);

                doctorScheduleConvert[i][j] = timeSlotList.contains(null) ?
                        "" :
                        timeSlotList.stream()
                                .map(TimeSlot::toString)
                                .collect(Collectors.joining(","));
                log.info(dayOfWeek);
                log.info(String.valueOf(timeSlotList));
            }
        }
        return doctorScheduleConvert;
    }
}
