package cdr.processing;

import cdr.domain.CallDataRecord;
import cdr.parse.CDRDataParser;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
@Scope("prototype")
@Setter
public class Producer extends Thread {

    private static final int batchSize = 10;

    private BlockingQueue<CallDataRecord> sharedQueue;
    private String path;

    public Producer(/*final String path, BlockingQueue<CallDataRecord> queue*/) {
        super("PRODUCER");
//        this.sharedQueue = queue;
//        this.path = path;
    }

    public void run() {
        try {
            parseCDR(path);
        } catch (IOException e) {
            log.info(e.getMessage());
        }
    }

    public void parseCDR(final String path) throws IOException {
        final LineIterator it = FileUtils.lineIterator(new File(path), "UTF-8");
        try {
            int row = 0;
            List<String> lines = new ArrayList<>(batchSize);
            while (it.hasNext()) {
                String line = it.nextLine();

                log.info(line);
                lines.add(line);
                row++;

                if (row == batchSize) {
                    populateQueue(lines);
                    row = 0;
                    lines = new ArrayList<>(batchSize);
                }
            }
            if (!CollectionUtils.isEmpty(lines)) {
                populateQueue(lines);
                // adding CallDataRecord with id equal null to exit message
                try {
                    sharedQueue.put(CallDataRecord.builder().build());
                } catch (InterruptedException e) {
                    log.info(e.getMessage());
                }
            }

        } finally {
            LineIterator.closeQuietly(it);
        }
    }

    private void populateQueue(List<String> lines) {
        List<CallDataRecord> callDataRecordList = new CDRDataParser(lines).parseData();
        for (CallDataRecord callDataRecord : callDataRecordList) {
            try {
                sharedQueue.put(callDataRecord);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }
}
