package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.system.DictDataDO;
import com.weili.iot_portal.dal.ddd.system.DictDataPageQuery;
import com.weili.iot_portal.dal.mapper.system.DictDataMapper;
import com.weili.iot_portal.dal.repository.system.IDictDataRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author luying
 * @className DictDataRepository
 * @description
 * @date 2025-07-15 18:04
 **/
@Repository
public class DictDataRepository extends ServiceImpl<DictDataMapper, DictDataDO> implements IDictDataRepository {


    @Override
    public void create(DictDataDO data) {
        super.save(data);
    }

    @Override
    public void update(DictDataDO data) {
        super.updateById(data);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public DictDataDO selectById(Long id) {
        return super.getById(id);
    }

    @Override
    public DictDataDO selectByDictTypeAndValue(String dictType, String value) {
        return super.getOne(new LambdaQueryWrapper<DictDataDO>().eq(DictDataDO::getDictType, dictType).eq(DictDataDO::getValue, value));
    }

    @Override
    public DictDataDO selectByDictTypeAndLabel(String dictType, String label) {
        return super.getOne(new LambdaQueryWrapper<DictDataDO>().eq(DictDataDO::getDictType, dictType).eq(DictDataDO::getLabel, label));
    }

    @Override
    public List<DictDataDO> selectByDictTypeAndValues(String dictType, Collection<String> values) {
        return super.list(new LambdaQueryWrapper<DictDataDO>().eq(DictDataDO::getDictType, dictType).in(DictDataDO::getValue, values));
    }

    @Override
    public long selectCountByDictType(String dictType) {
        return super.count(new LambdaQueryWrapper<DictDataDO>().eq(DictDataDO::getDictType, dictType));
    }

    @Override
    public PageResult<DictDataDO> selectPage(DictDataPageQuery reqVO) {
        LambdaQueryWrapper<DictDataDO> queryWrapper = new LambdaQueryWrapperX<DictDataDO>()
                .likeIfPresent(DictDataDO::getLabel, reqVO.getLabel())
                .eqIfPresent(DictDataDO::getDictType, reqVO.getDictType())
                .eqIfPresent(DictDataDO::getStatus, reqVO.getStatus())
                .orderByDesc(Arrays.asList(DictDataDO::getDictType, DictDataDO::getSort));
        Page<DictDataDO> page = super.page(new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), queryWrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public List<DictDataDO> selectListByStatusAndDictType(Integer status, String dictType) {
        return super.list(new LambdaQueryWrapperX<DictDataDO>()
                .eqIfPresent(DictDataDO::getStatus, status)
                .eqIfPresent(DictDataDO::getDictType, dictType));
    }

    @Override
    public List<DictDataDO> selectList(LambdaQueryWrapper<DictDataDO> queryWrapper) {
        return super.list(queryWrapper);
    }
}
