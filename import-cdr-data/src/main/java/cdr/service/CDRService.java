package cdr.service;

import cdr.domain.CallDataRecord;

import java.util.List;

public interface CDRService {
    int addCDR(List<CallDataRecord> callDataRecords, int batchSize);
}
