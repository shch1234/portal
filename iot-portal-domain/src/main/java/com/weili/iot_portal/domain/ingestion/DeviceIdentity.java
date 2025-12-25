package com.weili.iot_portal.domain.ingestion;

/**
 * 设备身份信息
 * 用于标识设备的ID和工厂ID
 *
 * @param deviceInfoId 设备信息ID
 * @param orgFactoryId 工厂ID
 */
public record DeviceIdentity(Long deviceInfoId, Long orgFactoryId) {
}

