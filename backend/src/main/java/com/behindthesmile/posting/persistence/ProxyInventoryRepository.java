package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProxyInventoryRepository extends JpaRepository<ProxyInventoryEntity, String> {
    List<ProxyInventoryEntity> findAllByOrderByUpdatedAtDesc();
}
