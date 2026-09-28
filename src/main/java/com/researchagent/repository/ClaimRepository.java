package com.researchagent.repository;

import com.researchagent.entity.ClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, String> {
    List<ClaimEntity> findByRunId(String runId);
}
