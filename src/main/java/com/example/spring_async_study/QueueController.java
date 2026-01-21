package com.example.spring_async_study;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QueueController {

    private final QueueWorker queueWorker;

    @GetMapping("/queue-test")
    public String queueTest() {
        for (int i = 0; i < 100; i++) {
            queueWorker.submit(new QueueTask(i));
        }
        return "QUEUE 요청 완료";
    }
}
