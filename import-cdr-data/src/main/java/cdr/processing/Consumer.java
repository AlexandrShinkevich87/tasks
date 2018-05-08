package cdr.processing;

import cdr.domain.CallDataRecord;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Component
@Scope("prototype")
@Setter
public class Consumer extends Thread {

    private static final int batchSize = 10;

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

                System.out.println(getName() + " consumed " + callDataRecord);
                callDataRecordList.add(callDataRecord);
                row++;

                if (row == batchSize) {
                    // logic
                    row = 0;
                    callDataRecordList = new ArrayList<>(batchSize);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
