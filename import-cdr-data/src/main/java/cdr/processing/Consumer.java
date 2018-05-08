package cdr.processing;

import cdr.domain.CallDataRecord;
import cdr.service.CDRService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Component
@Scope("prototype")
@Setter
@Slf4j
public class Consumer extends Thread {

    @Autowired
    private CDRService cdrService;

    @Value("${batchSizeConsumer}")
    private int batchSize;

    private BlockingQueue<CallDataRecord> sharedQueue;

    public Consumer(/*BlockingQueue<CallDataRecord> aQueue*/) {
        super("CONSUMER");
//        this.sharedQueue = aQueue;
    }

    public void run() {
        try {
            CallDataRecord callDataRecord;
            List<CallDataRecord> callDataRecordList = new ArrayList<>(batchSize);
            int row = 0;
            // consuming messages until null value in callDataRecord.id message is received
            while ((callDataRecord = sharedQueue.take()).getId() != null) {

//                System.out.println(getName() + " consumed " + callDataRecord);
                callDataRecordList.add(callDataRecord);
                row++;

                if (row == batchSize) {
                    // logic
                    int numberProcessedRows = cdrService.addCDR(callDataRecordList, batchSize);
                    row = 0;
                    callDataRecordList = new ArrayList<>(batchSize);
                    log.info(String.format("Put to db %d rows", numberProcessedRows));
                }
            }

            if (!CollectionUtils.isEmpty(callDataRecordList)) {
                int numberProcessedRows = cdrService.addCDR(callDataRecordList, batchSize);
                log.info(String.format("Put to db %d rows", numberProcessedRows));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
