package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChromeProfileProxySettingRepository extends JpaRepository<ChromeProfileProxySettingEntity, String> {
    List<ChromeProfileProxySettingEntity> findAllByOrderByDisplayOrderAsc();
}
