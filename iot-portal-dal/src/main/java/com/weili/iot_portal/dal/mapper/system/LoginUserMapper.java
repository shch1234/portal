package com.weili.iot_portal.dal.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.iot_portal.dal.dataobject.system.LoginUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @InterfaceName: LoginUserMapper
 * @Description:
 * @Author: luying
 **/
@Mapper
public interface LoginUserMapper extends BaseMapper<LoginUserDO> {
}
