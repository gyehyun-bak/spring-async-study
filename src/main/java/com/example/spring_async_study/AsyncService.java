package com.example.spring_async_study;

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

    @Async("asyncExecutor")
    @Transactional
    public void process(int taskId) {
        log.info("start process {}", taskId);

        sleep(2000);

        repository.save(new AsyncTaskLog("ASYNC", taskId));
        log.info("end process {}", taskId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
