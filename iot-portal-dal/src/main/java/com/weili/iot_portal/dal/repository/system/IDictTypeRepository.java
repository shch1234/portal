package com.weili.iot_portal.dal.repository.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.system.DictTypeDO;
import com.weili.iot_portal.dal.ddd.system.DictTypePageQuery;

import java.util.List;

/**
 * @InterfaceName: IDictDataRepository
 * @Description:
 * @Author: luying
 **/
public interface IDictTypeRepository {

    void create(DictTypeDO data);

    void update(DictTypeDO data);

    void delete(Long id);

    PageResult<DictTypeDO> selectPage(DictTypePageQuery reqVO);

    DictTypeDO selectByType(String type);

    DictTypeDO getById(Long id);

    DictTypeDO selectByName(String name);

    List<DictTypeDO> selectList();
}
