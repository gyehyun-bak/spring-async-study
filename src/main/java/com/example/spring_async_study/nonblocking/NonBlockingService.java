package com.example.spring_async_study.nonblocking;

import com.example.spring_async_study.AsyncTaskLog;
import com.example.spring_async_study.AsyncTaskLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class NonBlockingService {

    private final AsyncTaskLogRepository repository;
    private final Executor fixedThreadPoolExecutor;
    private final Executor virtualThreadExecutor;

    /**
     * 방식 1: @Async + 무제한 큐
     * - 스프링 @Async 사용
     * - 큐가 무제한이므로 거부 없음
     */
    @Async("unboundedQueueExecutor")
    @Transactional
    public void processWithUnboundedQueue(int taskId) {
        log.info("[UNBOUNDED-QUEUE] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("UNBOUNDED-QUEUE", taskId));
        log.info("[UNBOUNDED-QUEUE] end {}", taskId);
    }

    /**
     * 방식 2: CompletableFuture + Fixed Thread Pool
     * - 직접 CompletableFuture로 비동기 실행
     * - 즉시 반환
     */
    public CompletableFuture<Void> processWithCompletableFuture(int taskId) {
        return CompletableFuture.runAsync(() -> {
            log.info("[COMPLETABLE-FUTURE] start {}", taskId);
            sleep(2000);
            saveLog("COMPLETABLE-FUTURE", taskId);
            log.info("[COMPLETABLE-FUTURE] end {}", taskId);
        }, fixedThreadPoolExecutor);
    }

    /**
     * 방식 3: Virtual Thread (Java 21+)
     * - 경량 가상 스레드 사용
     * - 대량의 동시 작업에 적합
     */
    public CompletableFuture<Void> processWithVirtualThread(int taskId) {
        return CompletableFuture.runAsync(() -> {
            log.info("[VIRTUAL-THREAD] start {}", taskId);
            sleep(2000);
            saveLog("VIRTUAL-THREAD", taskId);
            log.info("[VIRTUAL-THREAD] end {}", taskId);
        }, virtualThreadExecutor);
    }

    /**
     * 방식 4: @Async + Virtual Thread
     * - 스프링 @Async와 Virtual Thread 조합
     */
    @Async("virtualThreadExecutor")
    @Transactional
    public void processAsyncWithVirtualThread(int taskId) {
        log.info("[ASYNC-VIRTUAL] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-VIRTUAL", taskId));
        log.info("[ASYNC-VIRTUAL] end {}", taskId);
    }

    @Transactional
    public void saveLog(String type, int taskId) {
        repository.save(new AsyncTaskLog(type, taskId));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
