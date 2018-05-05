package doctors.schedule.parser;

import doctors.schedule.model.TimeSlot;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;

@Slf4j
public class DoctorsScheduleDataParser {
    private static final String RANGE_DELIMITER = "-";

    private String[][] doctorsSchedule;

    public DoctorsScheduleDataParser(String[][] doctorsSchedule) {
        this.doctorsSchedule = doctorsSchedule;
    }

    public void parse() {
        for (int i = 0; i < doctorsSchedule.length; ++i) {
            for (int j = 0; j < doctorsSchedule[i].length; ++j) {
                if (j == 0) {
                    final String fullName = doctorsSchedule[i][0];
                    log.info(fullName);
                } else {
                    TimeSlot timeSlot = parseTimeSlot(doctorsSchedule[i][j]);
                    log.info(String.valueOf(timeSlot));
                }
            }
        }
    }

    private TimeSlot parseTimeSlot(String slot) {
        TimeSlot timeSlot = null;
        List<String> rangeParts = splitTime(slot, RANGE_DELIMITER);
        if (rangeParts.size() == 2) {
            String from = rangeParts.get(0).trim();
            String to = rangeParts.get(1).trim();

            LocalTime fromTime = LocalTime.parse(from);
            LocalTime toTime = LocalTime.parse(to);

            timeSlot = new TimeSlot(fromTime, toTime);
            return timeSlot;
        }
        return timeSlot;
    }

    private List<String> splitTime(String time, String delimiter) {
        int lastIndex = time.lastIndexOf(delimiter);
        if (lastIndex == -1) {
            return Collections.emptyList();
        }
        return asList(time.substring(0, lastIndex), time.substring(lastIndex + 1, time.length()));
    }

    private String getDailySchedule(LinkedList<String> elementList) {
        return elementList.pollFirst();
    }

    private String getFullName(LinkedList<String> elementList) {
        return elementList.pollFirst();
    }
}
