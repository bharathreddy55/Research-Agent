package com.researchagent.repository;

import com.researchagent.entity.SourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SourceRepository extends JpaRepository<SourceEntity, Long> {
    List<SourceEntity> findByRunIdOrderBySourceNumberAsc(String runId);
}
