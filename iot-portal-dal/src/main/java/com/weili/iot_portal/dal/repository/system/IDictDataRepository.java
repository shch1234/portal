package com.weili.iot_portal.dal.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.system.DictDataDO;
import com.weili.iot_portal.dal.ddd.DictDataPageQuery;

import java.util.Collection;
import java.util.List;

/**
 * @InterfaceName: IDictDataRepository
 * @Description:
 * @Author: luying
 **/
public interface IDictDataRepository {

    void create(DictDataDO data);

    void update(DictDataDO data);

    void delete(Long id);

    DictDataDO selectById(Long id);

    DictDataDO selectByDictTypeAndValue(String dictType, String value);

    DictDataDO selectByDictTypeAndLabel(String dictType, String label);

    List<DictDataDO> selectByDictTypeAndValues(String dictType, Collection<String> values);

    long selectCountByDictType(String dictType);

    PageResult<DictDataDO> selectPage(DictDataPageQuery reqVO);

    List<DictDataDO> selectListByStatusAndDictType(Integer status, String dictType);

    List<DictDataDO> selectList(LambdaQueryWrapper<DictDataDO> queryWrapper);
}
