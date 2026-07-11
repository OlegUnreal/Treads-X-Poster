package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftRepository extends JpaRepository<DraftEntity, Long> {
    List<DraftEntity> findByOrderByCreatedAtDesc();
}
