package com.researchagent.repository;

import com.researchagent.entity.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RunRepository extends JpaRepository<RunEntity, String> {
    List<RunEntity> findByStatus(String status);
    List<RunEntity> findAllByOrderByStartedAtDesc();

    /** Sum cost_paise for all runs that started at or after the given Instant (Audit B4). */
    @Query("SELECT COALESCE(SUM(r.costPaise), 0) FROM RunEntity r WHERE r.startedAt >= :startedAt")
    long sumCostPaiseByStartedAtAfter(@Param("startedAt") Instant startedAt);
}
