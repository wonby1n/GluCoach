package com.ssafy.s309.domain.health.service;

import com.ssafy.s309.domain.health.dto.HealthSnapshotBatchRequest;
import com.ssafy.s309.domain.health.dto.HealthSnapshotBatchResponse;
import com.ssafy.s309.domain.health.repository.HealthSnapshotBatchInserter;
import com.ssafy.s309.domain.health.repository.HealthSnapshotBatchInserter.BatchInsertResult;
import com.ssafy.s309.domain.health.repository.HealthSnapshotBatchInserter.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthSnapshotService {

  private final HealthSnapshotBatchInserter batchInserter;

  @Transactional
  public HealthSnapshotBatchResponse saveBatch(Integer userId, HealthSnapshotBatchRequest request) {
    var items =
        request.items().stream()
            .map(i -> new Item(i.recordedAt(), i.stepsTotal(), i.caloriesBurned(), i.heartRate()))
            .toList();
    BatchInsertResult result = batchInserter.batchInsert(userId, items);
    return new HealthSnapshotBatchResponse(result.inserted(), result.skipped());
  }
}
