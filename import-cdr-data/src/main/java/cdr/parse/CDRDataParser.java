package cdr.parse;

import cdr.domain.CallDataRecord;
import org.springframework.util.Assert;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;


import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public class CDRDataParser extends AbstractDataParser<CallDataRecord> {

    private static final String FIELD_DELIMITER = "|";

    public CDRDataParser(List<String> lines) {
        super(lines);
    }

    @Override
    protected List<CallDataRecord> parse() {

        final List<CallDataRecord> callDataRecordList = new ArrayList<>();

        for (final String line : lines) {

            callDataRecordList.add(parse(line));
        }

        return callDataRecordList;
    }

    private CallDataRecord parse(String line) {
        validateLineItem(line);

        final String array[] = StringUtils.split(line, FIELD_DELIMITER);
        final LinkedList<String> elementList = new LinkedList<>(Arrays.asList(array));

        final UUID id = getUUID(elementList);
        final String callingNum = getString(elementList);
        final String calledNum = getString(elementList);
        final Date startTime = getDate(elementList);
        final Date endTime = getDate(elementList);
        final String callType = getString(elementList);
        final Float charge = getFloat(elementList);
        final String callResult = getString(elementList);

        return CallDataRecord.builder()
                .id(id)
                .callingNum(callingNum)
                .calledNumber(calledNum)
                .startTime(startTime)
                .endTime(endTime)
                .callType(callType)
                .charge(charge)
                .callResult(callResult)
                .build();
    }

    private void validateLineItem(String line) {
        Assert.hasText(line, "data line item must be not-null");
    }

    private UUID getUUID(LinkedList<String> elementList) {
        return UUID.fromString(elementList.pollFirst());
    }

    private String getString(LinkedList<String> elementList) {
        return elementList.pollFirst();
    }

    private Date getDate(LinkedList<String> elementList) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
        TemporalAccessor accessor = timeFormatter.parse(elementList.pollFirst());

        Date date = Date.from(Instant.from(accessor));
        return date;
    }

    private Float getFloat(LinkedList<String> elementList) {
        return NumberUtils.toFloat(elementList.pollFirst());
    }
}
