package cdr;

import cdr.domain.CallDataRecord;
import cdr.processing.Consumer;
import cdr.processing.Producer;
import cdr.service.CDRService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class AppRunner implements CommandLineRunner {

    @Autowired
    private Producer producer;

    @Autowired
    private Consumer consumer;

    @Autowired
    private CDRService cdrService;

    @Value("${CDRFileName}")
    private String CDRFileName;

    @Override
    public void run(String... strings) throws Exception {

        BlockingQueue<CallDataRecord> sharedQ = new LinkedBlockingQueue<>();

        ClassLoader classLoader = Main.class.getClassLoader();
        File file = new File(classLoader.getResource(CDRFileName).getFile());

//        Producer producer = new Producer(file.getAbsolutePath(), sharedQ);
//        Consumer consumer = new Consumer(sharedQ);
        producer.setPath(file.getAbsolutePath());
        producer.setSharedQueue(sharedQ);

        consumer.setSharedQueue(sharedQ);

        log.info("Start processing ...");
        producer.start();
        consumer.start();

        // waiting until all threads are completed
        producer.join();
        consumer.join();
        log.info(String.format("We stored %d records to db", cdrService.totalCDRCount()));
        log.info("end.");
    }
}
