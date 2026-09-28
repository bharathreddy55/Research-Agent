package com.researchagent.repository;

import com.researchagent.entity.StepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StepRepository extends JpaRepository<StepEntity, String> {
    List<StepEntity> findByRunIdOrderBySeqAsc(String runId);
}
