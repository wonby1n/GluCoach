package com.ssafy.s309.domain.health.repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * health_snapshots 배치 INSERT 전용 컴포넌트.
 *
 * <p>1분마다 모은 메트릭(steps, calories, heart_rate)을 5분 단위 batch로 INSERT. Hibernate {@code saveAll}은 batch
 * 내 중복 1건이 발생하면 전체 트랜잭션이 실패하므로 Postgres {@code INSERT ... ON CONFLICT DO NOTHING}을 JdbcTemplate
 * batchUpdate로 실행한다.
 *
 * <p>모든 메트릭 컬럼은 nullable — SDK가 특정 메트릭을 반환 못 하는 경우 (예: 워치 미착용 시 heart_rate)에 대비.
 */
@Repository
@RequiredArgsConstructor
public class HealthSnapshotBatchInserter {

  private static final String INSERT_SQL =
      """
      INSERT INTO health_snapshots
          (user_id, recorded_at, steps_total, calories_burned, heart_rate)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (user_id, recorded_at) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;

  public BatchInsertResult batchInsert(Integer userId, List<Item> items) {
    int[] affected =
        jdbcTemplate.batchUpdate(
            INSERT_SQL,
            new BatchPreparedStatementSetter() {
              @Override
              public void setValues(PreparedStatement ps, int i) throws SQLException {
                Item item = items.get(i);
                ps.setInt(1, userId);
                ps.setTimestamp(2, Timestamp.valueOf(item.recordedAt()));
                if (item.stepsTotal() != null) ps.setInt(3, item.stepsTotal());
                else ps.setNull(3, Types.INTEGER);
                if (item.caloriesBurned() != null) ps.setBigDecimal(4, item.caloriesBurned());
                else ps.setNull(4, Types.NUMERIC);
                if (item.heartRate() != null) ps.setBigDecimal(5, item.heartRate());
                else ps.setNull(5, Types.NUMERIC);
              }

              @Override
              public int getBatchSize() {
                return items.size();
              }
            });

    int inserted = 0;
    for (int n : affected) {
      // ON CONFLICT DO NOTHING은 충돌 시 0, 신규 INSERT는 1 반환
      if (n > 0) inserted += n;
    }
    int skipped = items.size() - inserted;
    return new BatchInsertResult(inserted, skipped);
  }

  public record Item(
      LocalDateTime recordedAt,
      Integer stepsTotal,
      BigDecimal caloriesBurned,
      BigDecimal heartRate) {}

  public record BatchInsertResult(int inserted, int skipped) {}
}
