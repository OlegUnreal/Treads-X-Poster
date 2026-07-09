package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostingJobRepository extends JpaRepository<PostingJobEntity, String> {
    List<PostingJobEntity> findAllByOrderByAccountIdAscPlatformAsc();
}

