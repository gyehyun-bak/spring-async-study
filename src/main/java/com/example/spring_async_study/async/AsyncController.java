package com.example.spring_async_study.async;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AsyncController {

    private final AsyncService asyncService;

    /**
     * AbortPolicy (기본)
     * - 큐가 가득 차면 RejectedExecutionException 발생
     * - 즉시 응답, 예외로 실패 감지
     */
    @GetMapping("/async-default")
    public String asyncDefault() {
        long start = System.currentTimeMillis();

        int success = 0;
        int fail = 0;

        for (int i = 0; i < 100; i++) {
            try {
                asyncService.processDefault(i);
                success++;
            } catch (Exception e) {
                fail++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[AbortPolicy] 성공: %d, 실패: %d, 소요시간: %dms", success, fail, elapsed);
    }

    /**
     * CallerRunsPolicy
     * - 큐가 가득 차면 요청 스레드가 직접 태스크 실행 (블로킹)
     */
    @GetMapping("/async-caller-runs")
    public String asyncCallerRuns() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            asyncService.processCallerRuns(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[CallerRunsPolicy] 완료, 소요시간: %dms", elapsed);
    }

    /**
     * DiscardPolicy
     * - 큐가 가득 차면 태스크를 조용히 버림
     * - 즉시 응답, 실패 감지 불가
     */
    @GetMapping("/async-discard")
    public String asyncDiscard() {
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            asyncService.processDiscard(i);
        }

        long elapsed = System.currentTimeMillis() - start;
        return String.format("[DiscardPolicy] 완료, 소요시간: %dms (일부 유실 가능)", elapsed);
    }
}
