package doctors.schedule.parser;

import doctors.schedule.model.TimeSlot;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.util.Arrays.asList;

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
                    System.out.println(fullName);
                } else {
                    List<String> rangeParts = splitTime(doctorsSchedule[i][j], RANGE_DELIMITER);
                    if (rangeParts.size() == 2) {
                        String from = rangeParts.get(0).trim();
                        String to = rangeParts.get(1).trim();

                        LocalTime fromTime = LocalTime.parse(from);
                        LocalTime toTime = LocalTime.parse(to);

                        TimeSlot timeSlot = new TimeSlot(fromTime, toTime);
                        System.out.println(timeSlot);
                    }
                }
            }
        }
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
