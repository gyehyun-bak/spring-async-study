package com.example.spring_async_study.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("asyncExecutor") // @Async("asyncExecutor") 등으로 비동기 메서드에서 사용할 Executor 지정
    public Executor asyncExecutor() { // Executor -> 스레드 실행을 담당하는 인터페이스. 내부 구현은 ThreadPoolTaskExecutor
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor(); // Spring에서 제공하는 스레드 풀 Executor 구현체, @Async, @Scheduled 등과 잘 연동됨
        executor.setCorePoolSize(3); // 기본으로 유지되는 스레드 수
        executor.setMaxPoolSize(5); // 최대 생성 가능한 스레드 수
        executor.setQueueCapacity(10); // 작업 대기 큐의 크기, 큐가 가득 차면 → 스레드 5개까지 증가, 그래도 넘치면 → RejectedExecutionException 발생
        executor.setThreadNamePrefix("ASYNC-"); // 스레드명 Prefix, 예) ASYNC-1
        executor.initialize(); // 스레드풀 초기화, 빈 생성 전 반드시 필요
        return executor;
    }
}
