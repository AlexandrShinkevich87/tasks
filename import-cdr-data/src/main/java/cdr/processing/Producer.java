package cdr.processing;

import cdr.domain.CallDataRecord;
import cdr.parse.CDRDataParser;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${batchSizeProduces}")
    private int batchSize;

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
        final File file = new File(path);
        final LineIterator it = FileUtils.lineIterator(file, "UTF-8");
        log.info(String.format("Parsing file (%s) contains Telecom Call Data/Detail Records: ", file.getName()));
        try {
            int row = 0;
            List<String> linesOfCDR = new ArrayList<>(batchSize);
            while (it.hasNext()) {
                String line = it.nextLine();

                linesOfCDR.add(line);
                row++;

                if (row == batchSize) {
                    populateQueue(linesOfCDR);
                    row = 0;
                    linesOfCDR = new ArrayList<>(batchSize);
                }
            }
            if (!CollectionUtils.isEmpty(linesOfCDR)) {
                populateQueue(linesOfCDR);
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
        log.info(String.format("Put to shared queue %d rows", lines.size()));

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }
}
