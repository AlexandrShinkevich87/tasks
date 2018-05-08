package cdr.service.impl;

import cdr.domain.CallDataRecord;
import cdr.repository.CDRRepository;
import cdr.service.CDRService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class CDRServiceImpl implements CDRService {
    @Autowired
    private CDRRepository cdrRepository;

    @Override
    @Transactional
    public int addCDR(List<CallDataRecord> callDataRecords, int batchSize) {
        return cdrRepository.addCDR(callDataRecords, batchSize);
    }
}
