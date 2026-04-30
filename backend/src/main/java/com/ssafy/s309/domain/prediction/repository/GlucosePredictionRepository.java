package com.ssafy.s309.domain.prediction.repository;

import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlucosePredictionRepository extends JpaRepository<GlucosePrediction, Integer> {}
