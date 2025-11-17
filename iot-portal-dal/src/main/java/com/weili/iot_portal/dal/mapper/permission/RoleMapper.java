package com.weili.iot_portal.dal.mapper.permission;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.permission.RoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @InterfaceName: RoleMapper
 * @Description:
 * @Author: luying
 **/
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {
}
