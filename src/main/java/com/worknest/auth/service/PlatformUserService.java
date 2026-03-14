package com.worknest.auth.service;

import com.worknest.master.entity.PlatformUser;

public interface PlatformUserService {

    PlatformUser findByEmailOrThrow(String email);

    PlatformUser save(PlatformUser platformUser);

    boolean emailExists(String email);

    void updateLastLogin(Long userId);
}
