package doctors.schedule;

import doctors.schedule.parser.APPDataParser;

public class Main {
    private static final String[][] DOCTORS_WEEKLY_SCHEDULE = {
            {"ИВАНОВ ИВАН ИВАНОВИЧ", "12:00-16:00", "12:00-16:00", "12:00-16:00", "12:00-16:00", "12:00-16:00", "12:00-16:00", ""},
            {"ИВАНОВ ИВАН ИВАНОВИЧ", "08:00-12:00", "08:00-10:00", "08:00-16:00", "", "", "", ""},
            {"ПЕТРОВ ПЕТР ПЕТРОВИЧ", "12:00-16:00", "", "12:00-16:00", "", "12:00-16:00", "", ""}

    };

    public static void main(String[] args) {
        APPDataParser.normolizeGrafic(DOCTORS_WEEKLY_SCHEDULE);

    }
}
