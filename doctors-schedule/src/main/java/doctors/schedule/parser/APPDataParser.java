package doctors.schedule.parser;

import doctors.schedule.model.DoctorsSchedule;

/**
 * parse schedule of doctors
 */
public final class APPDataParser {
    private APPDataParser() {
    }

    public static String[][] normolizeGrafic(String[][] grafic) {
        DoctorsSchedule doctorsSchedule = new DoctorsScheduleDataParser(grafic).parse();
        return DoctorsScheduleDataParser.convert(doctorsSchedule);
    }
}
