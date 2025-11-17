package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.system.DictTypeDO;
import com.weili.iot_portal.dal.ddd.DictTypePageQuery;
import com.weili.iot_portal.dal.mapper.system.DictTypeMapper;
import com.weili.iot_portal.dal.repository.system.IDictTypeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author luying
 * @className DictTypeRepository
 * @description
 * @date 2025-07-16 09:27
 **/
@Repository
public class DictTypeRepository extends ServiceImpl<DictTypeMapper, DictTypeDO> implements IDictTypeRepository {
    @Override
    public void create(DictTypeDO data) {
        super.save(data);
    }

    @Override
    public void update(DictTypeDO data) {
        super.updateById(data);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public PageResult<DictTypeDO> selectPage(DictTypePageQuery reqVO) {
        LambdaQueryWrapperX<DictTypeDO> queryWrapperX = new LambdaQueryWrapperX<DictTypeDO>()
                .likeIfPresent(DictTypeDO::getName, reqVO.getName())
                .likeIfPresent(DictTypeDO::getType, reqVO.getType())
                .eqIfPresent(DictTypeDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DictTypeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DictTypeDO::getId);
        Page<DictTypeDO> page = super.page(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), queryWrapperX);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public DictTypeDO selectByType(String type) {
        return super.getOne(new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getType, type));
    }

    @Override
    public DictTypeDO getById(Long id) {
        return super.getById(id);
    }

    @Override
    public DictTypeDO selectByName(String name) {
        return super.getOne(new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getName, name));
    }

    @Override
    public List<DictTypeDO> selectList() {
        return super.list();
    }
}
