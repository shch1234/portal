package com.weili.example.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weili.example.dal.entity.ExampleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @InterfaceName: ExampleMapper
 * @Description: mapper接口独立：更好的代码组织和分层结构、内部类会遇到代理问题
 * @Author: luying
 **/
@Mapper
public interface ExampleMapper extends BaseMapper<ExampleDO> {
}
