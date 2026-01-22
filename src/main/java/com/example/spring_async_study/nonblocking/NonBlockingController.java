package com.example.spring_async_study.nonblocking;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/non-blocking")
@RequiredArgsConstructor
public class NonBlockingController {

    private final NonBlockingService service;

    /**
     * 방식 1: @Async + 무제한 큐
     * - 모든 작업이 큐에 들어감 (거부 없음)
     * - 즉시 응답
     */
    @GetMapping("/unbounded-queue")
    public String unboundedQueue() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithUnboundedQueue(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[무제한 큐 + @Async] 즉시 응답 - 소요시간: %dms, 100개 작업 큐에 추가됨", elapsed);
    }

    /**
     * 방식 2: CompletableFuture
     * - 직접 비동기 실행
     * - 즉시 응답
     */
    @GetMapping("/completable-future")
    public String completableFuture() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithCompletableFuture(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[CompletableFuture] 즉시 응답 - 소요시간: %dms, 100개 작업 실행 중", elapsed);
    }

    /**
     * 방식 3: Virtual Thread (Java 21+)
     * - 경량 가상 스레드로 대량 동시 처리
     * - 즉시 응답
     */
    @GetMapping("/virtual-thread")
    public String virtualThread() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processWithVirtualThread(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[Virtual Thread] 즉시 응답 - 소요시간: %dms, 100개 가상 스레드 실행 중", elapsed);
    }

    /**
     * 방식 4: @Async + Virtual Thread
     * - 스프링 @Async와 Virtual Thread 조합
     * - 즉시 응답
     */
    @GetMapping("/async-virtual")
    public String asyncVirtual() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            service.processAsyncWithVirtualThread(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[@Async + Virtual Thread] 즉시 응답 - 소요시간: %dms, 100개 작업 실행 중", elapsed);
    }
}
