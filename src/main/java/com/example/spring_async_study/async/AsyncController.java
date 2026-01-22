package com.example.spring_async_study.async;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AsyncController {

    private final AsyncService asyncService;

    @GetMapping("/async-test")
    public String asyncTest() {
        for (int i = 0; i < 100; i++) {
            asyncService.process(i);
        }
        return "ASYNC 요청 완료";
    }
}
