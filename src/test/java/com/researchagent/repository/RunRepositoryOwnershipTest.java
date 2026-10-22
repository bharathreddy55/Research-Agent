package com.researchagent.repository;

import com.researchagent.entity.RunEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RunRepositoryOwnershipTest {
    @Autowired RunRepository repository;

    @Test
    void databaseQueriesFilterOwnershipBeforePaginationAndLookup() {
        save("a-old", "alice", Instant.parse("2026-10-20T00:00:00Z"));
        save("b-new", "bob", Instant.parse("2026-10-22T00:00:00Z"));
        save("a-new", "alice", Instant.parse("2026-10-21T00:00:00Z"));
        assertThat(repository.findByIdAndUserId("a-new", "alice")).isPresent();
        assertThat(repository.findByIdAndUserId("a-new", "bob")).isEmpty();
        assertThat(repository.findByIdAndUserId("missing", "alice")).isEmpty();
        var page = repository.findByUserId("alice", PageRequest.of(0, 1, Sort.by("startedAt").descending()));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(RunEntity::getId).containsExactly("a-new");
        assertThat(repository.findByUserId("alice", page.nextPageable()).getContent())
                .extracting(RunEntity::getId).containsExactly("a-old");
    }

    private void save(String id, String owner, Instant started) {
        RunEntity run = new RunEntity();
        run.setId(id); run.setUserId(owner); run.setQuery("Test research");
        run.setDepth("QUICK"); run.setStatus("PENDING"); run.setStartedAt(started);
        repository.saveAndFlush(run);
    }
}
