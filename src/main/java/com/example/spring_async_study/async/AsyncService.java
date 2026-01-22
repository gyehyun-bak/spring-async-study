package com.example.spring_async_study.async;

import com.example.spring_async_study.AsyncTaskLog;
import com.example.spring_async_study.AsyncTaskLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncService {

    private final AsyncTaskLogRepository repository;

    /**
     * AbortPolicy (기본)
     * - 큐가 가득 차면 RejectedExecutionException 발생
     */
    @Async("defaultAbortExecutor")
    @Transactional
    public void processDefault(int taskId) {
        log.info("[ABORT] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-ABORT", taskId));
        log.info("[ABORT] end {}", taskId);
    }

    /**
     * CallerRunsPolicy
     * - 큐가 가득 차면 호출 스레드가 직접 실행
     */
    @Async("callerRunsExecutor")
    @Transactional
    public void processCallerRuns(int taskId) {
        log.info("[CALLER-RUNS] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-CALLER-RUNS", taskId));
        log.info("[CALLER-RUNS] end {}", taskId);
    }

    /**
     * DiscardPolicy
     * - 큐가 가득 차면 태스크를 조용히 버림
     */
    @Async("discardExecutor")
    @Transactional
    public void processDiscard(int taskId) {
        log.info("[DISCARD] start {}", taskId);
        sleep(2000);
        repository.save(new AsyncTaskLog("ASYNC-DISCARD", taskId));
        log.info("[DISCARD] end {}", taskId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
