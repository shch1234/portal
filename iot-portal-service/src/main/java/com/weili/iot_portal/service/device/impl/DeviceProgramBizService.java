package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.domain.device.resp.DeviceProgramRespVO;
import com.weili.iot_portal.service.cache.DeviceProgramCacheService;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
import com.weili.iot_portal.service.device.IDeviceProgramBizService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.weili.iot_portal.service.device.util.DeviceLogContext.*;
import java.util.stream.Collectors;

/**
 * 设备程序信息业务实现
 */
@Slf4j
@Service
public class DeviceProgramBizService implements IDeviceProgramBizService {

    @Resource
    private IDeviceInfoBizService deviceInfoBizService;
    @Resource
    private DeviceProgramCacheService deviceProgramCacheService;

    @Override
    public DeviceProgramRespVO getDeviceProgram(Long deviceId) {
        // 查询设备信息，获取factoryId（Redis key需要factoryId）
        DeviceInfoDO deviceInfo = deviceInfoBizService.getDeviceInfo(deviceId);
        if (deviceInfo == null) {
            log.warn("[DeviceProgramBizService] 设备不存在: deviceId={}", deviceId);
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND, "设备不存在");
        }

        // 设置设备编号到 MDC，使日志能够显示设备编号
        setDeviceCode(deviceInfo);

        try {
            Long factoryId = deviceInfo.getOrgFactoryId();
            if (factoryId == null) {
                log.warn("[DeviceProgramBizService] 设备未关联工厂，无法查询程序信息: deviceId={}", deviceId);
                return null;
            }

            // 从Redis缓存获取程序信息（使用正确的factoryId）
            Map<Object, Object> programData = deviceProgramCacheService.getProgram(factoryId, deviceId);

            if (programData == null || programData.isEmpty()) {
                log.debug("设备程序信息缓存为空: deviceId={}", deviceId);
                return null;
            }

            // 提取程序名称和路径
            String programName = getStringValue(programData, "programName");
            String programPath = getStringValue(programData, "programPath");

            // 提取程序上下文（programCtx），用于存放程序信息（执行代码）
            String programCtx = getStringValue(programData, "programCtx");

            // 提取主G代码和M代码
            String gCode = getStringValue(programData, "gCode");
            String mCode = getStringValue(programData, "mCode");

            // 构建G代码详情：提取所有以 gCode 开头的字段（如 gCode1, gCode2 等）
            String gCodeDetails = buildGCodeDetails(programData, gCode);

            // 构建M代码详情：提取所有以 mCode 开头的字段（如 mCode1, mCode2 等）
            String mCodeDetails = buildMCodeDetails(programData, mCode);

            // 执行代码从 programCtx 中获取
            String executeCode = (programCtx != null && !programCtx.trim().isEmpty())
                    ? programCtx
                    : null;

            return DeviceProgramRespVO.builder()
                    .programName(programName)
                    .programPath(programPath)
                    .executeCode(executeCode)
                    .gCodeDetails(gCodeDetails)
                    .mCodeDetails(mCodeDetails)
                    .build();
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
        }
    }

    /**
     * 构建G代码详情字符串
     * 格式：第1组G代码: GG53 第2组G代码: GG0 ...
     */
    private String buildGCodeDetails(Map<Object, Object> programData, String mainGCode) {
        // 提取所有以 gCode 开头的字段（忽略主 gCode 字段本身）
        Map<String, String> gCodeFields = programData.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> {
                    String key = e.getKey().toString().trim();
                    return key.toLowerCase().startsWith("gcode") &&
                           !key.equalsIgnoreCase("gCode");
                })
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString(),
                        (v1, v2) -> v1
                ));

        if (gCodeFields.isEmpty()) {
            // 如果没有分组的G代码字段，返回主G代码
            return mainGCode != null ? mainGCode : "";
        }

        // 构建详情字符串
        StringBuilder sb = new StringBuilder();

        // 尝试按数字顺序排序 (gCode1, gCode2, ...)
        gCodeFields.entrySet().stream()
                .sorted((e1, e2) -> {
                    String k1 = e1.getKey();
                    String k2 = e2.getKey();
                    // 提取数字部分进行排序
                    Integer n1 = extractNumber(k1);
                    Integer n2 = extractNumber(k2);
                    if (n1 != null && n2 != null) {
                        return n1.compareTo(n2);
                    }
                    return k1.compareTo(k2);
                })
                .forEach(e -> {
                    String key = e.getKey();
                    String value = e.getValue();
                    Integer groupNum = extractNumber(key);

                    if (sb.length() > 0) {
                        sb.append(" ");
                    }

                    if (groupNum != null) {
                        sb.append("第").append(groupNum).append("组G代码: ").append(value);
                    } else {
                        sb.append(key).append(": ").append(value);
                    }
                });

        return sb.toString();
    }

    /**
     * 构建M代码详情字符串
     * 格式：位置: 1 刀具详细数据: ... 数据段1: ... 数据段2: ...
     */
    private String buildMCodeDetails(Map<Object, Object> programData, String mainMCode) {
        // 提取所有以 mCode 开头的字段（忽略主 mCode 字段本身）
        Map<String, String> mCodeFields = programData.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> {
                    String key = e.getKey().toString().trim();
                    return key.toLowerCase().startsWith("mcode") &&
                           !key.equalsIgnoreCase("mCode");
                })
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        e -> e.getValue().toString(),
                        (v1, v2) -> v1
                ));

        if (mCodeFields.isEmpty()) {
            // 如果没有分组的M代码字段，返回主M代码
            return mainMCode != null ? mainMCode : "";
        }

        // 构建详情字符串
        StringBuilder sb = new StringBuilder();

        // 尝试按数字顺序排序 (mCode1, mCode2, ...)
        mCodeFields.entrySet().stream()
                .sorted((e1, e2) -> {
                    String k1 = e1.getKey();
                    String k2 = e2.getKey();
                    // 提取数字部分进行排序
                    Integer n1 = extractNumber(k1);
                    Integer n2 = extractNumber(k2);
                    if (n1 != null && n2 != null) {
                        return n1.compareTo(n2);
                    }
                    return k1.compareTo(k2);
                })
                .forEach(e -> {
                    String key = e.getKey();
                    String value = e.getValue();
                    Integer groupNum = extractNumber(key);

                    if (sb.length() > 0) {
                        sb.append(" ");
                    }

                    if (groupNum != null) {
                        sb.append("数据段").append(groupNum).append(": ").append(value);
                    } else {
                        sb.append(key).append(": ").append(value);
                    }
                });

        return sb.toString();
    }

    /**
     * 从字段名中提取数字（如 gCode1 -> 1, mCode10 -> 10）
     */
    private Integer extractNumber(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        // 移除非数字字符，提取尾部数字
        String numberPart = fieldName.replaceAll("\\D+", "");
        if (numberPart.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从Map中获取字符串值
     */
    private String getStringValue(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
