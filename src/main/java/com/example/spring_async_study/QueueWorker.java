package com.example.spring_async_study;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueWorker {

    private final AsyncTaskLogRepository repository;

    private final BlockingQueue<QueueTask> queue = new LinkedBlockingQueue<>(10);

    @PostConstruct
    public void init() {
        for (int i = 0; i < 10; i++) {
            Thread worker = new Thread(this::consume);
            worker.setName("QUEUE-WORKER-" + i);
            worker.start();
        }
    }

    public void submit(QueueTask task) {
        try {
            queue.put(task);
            log.info("[QUEUE] submit {}", task.taskId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void consume() {
        while (true) {
            try {
                QueueTask task = queue.take();
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Transactional
    public void process(QueueTask task) throws InterruptedException {
        log.info("[{}] QUEUE start {}", Thread.currentThread().getName(), task.taskId());

        Thread.sleep(2000);

        repository.save(new AsyncTaskLog("QUEUE", task.taskId()));

        log.info("[{}] QUEUE end {}", Thread.currentThread().getName(), task.taskId());
    }
}
