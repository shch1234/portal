package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;

public interface ToolCompensationRepository {

    /**
     * 查询当前有效的刀补记录
     */
    ToolCompensationDO findActive(String tenantId, String deviceId, String toolHolderNo);

    /**
     * 查询设备的所有有效刀补记录（可按工厂过滤）
     */
    java.util.List<ToolCompensationDO> findActiveByDevice(String tenantId, String factoryId, String deviceId);

    void insert(ToolCompensationDO record);

    void updateById(ToolCompensationDO record);
}


