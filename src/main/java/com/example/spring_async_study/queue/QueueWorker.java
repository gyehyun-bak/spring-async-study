package com.example.spring_async_study.queue;

import com.example.spring_async_study.AsyncTaskLog;
import com.example.spring_async_study.AsyncTaskLogRepository;
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

    /**
     * 블로킹 방식: 큐가 가득 차면 대기
     */
    public void submitWithPut(QueueTask task) {
        try {
            queue.put(task);
            log.info("[QUEUE-PUT] submit {}", task.taskId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 논블로킹 방식: 큐가 가득 차면 즉시 false 반환
     */
    public boolean submitWithOffer(QueueTask task) {
        boolean added = queue.offer(task);
        if (added) {
            log.info("[QUEUE-OFFER] submit 성공 {}", task.taskId());
        } else {
            log.warn("[QUEUE-OFFER] submit 실패 (큐 가득 참) {}", task.taskId());
        }
        return added;
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
