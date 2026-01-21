package com.example.spring_async_study;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AsyncTaskLogRepository extends JpaRepository<AsyncTaskLog, Integer> {
}
