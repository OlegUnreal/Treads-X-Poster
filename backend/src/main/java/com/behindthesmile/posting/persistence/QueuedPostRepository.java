package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueuedPostRepository extends JpaRepository<QueuedPostEntity, String> {
    List<QueuedPostEntity> findByAccountIdOrderByDisplayOrderAsc(String accountId);
    List<QueuedPostEntity> findByAccountIdAndStatus(String accountId, String status);
    List<QueuedPostEntity> findByAccountIdAndPlatformScopeOrderByDisplayOrderAsc(String accountId, String platformScope);
    List<QueuedPostEntity> findByAccountIdAndPlatformScopeAndStatusOrderByDisplayOrderAsc(String accountId, String platformScope, String status);
    void deleteByAccountIdAndPlatformScope(String accountId, String platformScope);
}
