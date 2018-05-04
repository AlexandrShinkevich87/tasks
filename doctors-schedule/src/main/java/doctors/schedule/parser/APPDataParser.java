package doctors.schedule.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class APPDataParser {
    private APPDataParser() {
    }

    public static String[][] normolizeGrafic(String[][] grafic) {


//        List<String> lines = Arrays.stream(grafic).flatMap(Arrays::stream).collect(Collectors.toList());

        new DoctorsScheduleDataParser(grafic).parse();

        return new String [][]{{}};
    }
}
