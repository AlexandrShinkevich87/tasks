package cdr.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * contains Telecom Call Detail/Data Record
 */
@Data
@Builder
public class CallDataRecord {
    //f7c2aed3-8209-472f-afa6-6a1dc4a0e0b6|8131166797|3470914600|2016-03-04T00:10:05.681+05:30|2016-03-04T00:10:05.681+05:30|SMS|0.8428941|ANSWERED
    //ID, CALLING_NUM, CALLED NUMBER, START TIME, END TIME, CALL TYPE, CHARGE, CALL RESULT
    UUID id;
    String callingNum;
    String calledNumber;
    Date startTime;
    Date endTime;
    String callType;
    Float charge;
    String callResult;
}
