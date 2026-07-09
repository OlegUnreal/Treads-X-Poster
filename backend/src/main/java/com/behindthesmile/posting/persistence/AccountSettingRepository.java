package com.behindthesmile.posting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountSettingRepository extends JpaRepository<AccountSetting, String> {
    List<AccountSetting> findByIdIn(List<String> ids);

    List<AccountSetting> findBySource(String source);
}

