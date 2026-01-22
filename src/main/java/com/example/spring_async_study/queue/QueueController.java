package com.example.spring_async_study.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QueueController {

    private final QueueWorker queueWorker;

    /**
     * 블로킹 방식 (put)
     * - 큐가 가득 차면 요청 스레드가 대기
     * - 모든 태스크가 큐에 들어갈 때까지 응답하지 않음
     */
    @GetMapping("/queue-put")
    public String queuePut() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            queueWorker.submitWithPut(new QueueTask(i));
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("PUT 방식 완료 - 소요시간: %dms (블로킹 발생)", elapsed);
    }

    /**
     * 논블로킹 방식 (offer)
     * - 큐가 가득 차면 즉시 실패 반환
     * - 요청 스레드가 대기하지 않음
     */
    @GetMapping("/queue-offer")
    public String queueOffer() {
        long start = System.currentTimeMillis();

        int success = 0;
        int fail = 0;

        for (int i = 0; i < 100; i++) {
            if (queueWorker.submitWithOffer(new QueueTask(i))) {
                success++;
            } else {
                fail++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("OFFER 방식 완료 - 성공: %d, 실패: %d, 소요시간: %dms (논블로킹)",
                success, fail, elapsed);
    }
}
