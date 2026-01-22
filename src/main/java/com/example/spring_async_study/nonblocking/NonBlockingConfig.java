package com.example.spring_async_study.nonblocking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class NonBlockingConfig {

    /**
     * 무제한 큐 Executor
     * - queueCapacity를 매우 크게 설정하여 거부 없이 모든 작업 수용
     * - 메모리 주의 필요
     */
    @Bean("unboundedQueueExecutor")
    public Executor unboundedQueueExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(Integer.MAX_VALUE); // 무제한 큐
        executor.setThreadNamePrefix("UNBOUNDED-");
        executor.initialize();
        return executor;
    }

    /**
     * Virtual Thread Executor (Java 21+)
     * - 경량 스레드로 대량의 동시 작업 처리
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Fixed Thread Pool
     * - CompletableFuture용 고정 스레드 풀
     */
    @Bean("fixedThreadPoolExecutor")
    public Executor fixedThreadPoolExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
