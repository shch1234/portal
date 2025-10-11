package com.weili.example.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.example.dal.ddd.ExampleQuery;
import com.weili.example.dal.entity.ExampleDO;

import java.util.List;

/**
 * @author luying
 * @className IExampleRepository
 * @description
 * @date 2025-10-11 11:04
 **/
public interface IExampleRepository {

    ExampleDO getById(Long id);

    List<ExampleDO> listAll();

    PageResult<ExampleDO> listPage(ExampleQuery query);

    void create(ExampleDO data);
}
