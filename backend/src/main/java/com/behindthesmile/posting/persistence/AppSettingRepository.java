package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
    Optional<AppSetting> findByKey(String key);
}

