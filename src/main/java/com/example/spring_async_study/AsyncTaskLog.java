package com.example.spring_async_study;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsyncTaskLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String taskType; // ASYNC / QUEUE
    private int taskId;
    private String threadName;

    private LocalDateTime createdAt;

    public AsyncTaskLog(String taskType, int taskId) {
        this.taskType = taskType;
        this.taskId = taskId;
        this.threadName = Thread.currentThread().getName();
        this.createdAt = LocalDateTime.now();
    }
}
