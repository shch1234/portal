package com.weili.iot_portal.service.system.impl;

import com.weili.iot_portal.service.system.IPermissionBizService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author luying
 * @className PermissionService
 * @description
 * @date 2025-11-17 09:38
 **/
@Service
@Slf4j
public class PermissionBizService implements IPermissionBizService {
    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return false;
    }
}
