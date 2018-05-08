package cdr.repository;

import cdr.domain.CallDataRecord;

import java.util.List;

public interface CDRRepository {
    int addCDR(List<CallDataRecord> callDataRecords, int batchSize);
    int totalCDRCount();
}
