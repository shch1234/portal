package com.weili.iot_portal.dal.mapper.device;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.domain.device.resp.AlarmManageRespVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceAlarmHistoryMapper extends BaseMapper<DeviceAlarmHistoryDO> {

    /**
     * 查询报警管理列表总数
     */
    Long countAlarmManageList(@Param("deviceCode") String deviceCode,
                              @Param("deviceType") String deviceType,
                              @Param("isActive") Integer isActive,
                              @Param("startTime") Long startTime,
                              @Param("endTime") Long endTime);

    /**
     * 查询报警管理列表（分页）
     */
    List<AlarmManageRespVO> selectAlarmManageList(@Param("deviceCode") String deviceCode,
                                                    @Param("deviceType") String deviceType,
                                                    @Param("isActive") Integer isActive,
                                                    @Param("startTime") Long startTime,
                                                    @Param("endTime") Long endTime,
                                                    @Param("offset") Integer offset,
                                                    @Param("limit") Integer limit);
}


