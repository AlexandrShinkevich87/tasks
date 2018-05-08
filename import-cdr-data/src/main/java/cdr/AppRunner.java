package cdr;

import cdr.domain.CallDataRecord;
import cdr.processing.Consumer;
import cdr.processing.Producer;
import cdr.service.CDRService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class AppRunner implements CommandLineRunner {


    @Autowired
    private CDRService cdrService;

    @Autowired
    private Producer producer;

    @Autowired
    private Consumer consumer;
    @Override
    public void run(String... strings) throws Exception {

        BlockingQueue<CallDataRecord> sharedQ = new LinkedBlockingQueue<CallDataRecord>();

        String pathCDRFile = "cdr-data-generator.cdr";
//        InputStream in = Main.class.getClassLoader().getResourceAsStream("cdr-data-generator.cdr");

        ClassLoader classLoader = Main.class.getClassLoader();
        File file = new File(classLoader.getResource(pathCDRFile).getFile());

//        Producer producer = new Producer(file.getAbsolutePath(), sharedQ);
//        Consumer consumer = new Consumer(sharedQ);
        /*try {
            producer.parseCDR(file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        producer.setPath(file.getAbsolutePath());
        producer.setSharedQueue(sharedQ);
        producer.start();
//        consumer.start();
    }
}
