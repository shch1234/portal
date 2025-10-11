package com.weili.example.dal.repository.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.example.dal.ddd.ExampleQuery;
import com.weili.example.dal.entity.ExampleDO;
import com.weili.example.dal.mapper.ExampleMapper;
import com.weili.example.dal.repository.IExampleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author luying
 * @className ExampleRepository
 * @description
 * @date 2025-10-11 11:05
 **/
@Repository
public class ExampleRepository extends ServiceImpl<ExampleMapper, ExampleDO> implements IExampleRepository {

    @Override
    public ExampleDO getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<ExampleDO> listAll() {
        return super.list();
    }

    @Override
    public PageResult<ExampleDO> listPage(ExampleQuery query) {
        LambdaQueryWrapperX<ExampleDO> queryWrapper = new LambdaQueryWrapperX<>();
        queryWrapper.likeIfPresent(ExampleDO::getName, query.getName());
        Page<ExampleDO> page = this.page(
                new Page<>(query.getPageNo(), query.getPageSize()),
                queryWrapper
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override
    public void create(ExampleDO data) {
        super.save(data);
    }
}
