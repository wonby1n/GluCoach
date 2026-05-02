package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.StepRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRecordRepository extends JpaRepository<StepRecord, Long> {}
