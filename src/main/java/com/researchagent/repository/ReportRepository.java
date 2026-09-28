package com.researchagent.repository;

import com.researchagent.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, String> {
    List<ReportEntity> findByRunIdOrderByVersionDesc(String runId);
    Optional<ReportEntity> findTopByRunIdOrderByVersionDesc(String runId);
}
