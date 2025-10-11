package com.weili.example.service.impl;

import com.weili.basic.common.util.BeanUtils;
import com.weili.example.dal.entity.ExampleDO;
import com.weili.example.dal.repository.IExampleRepository;
import com.weili.example.domain.vo.ExampleSaveReqVO;
import com.weili.example.domain.vo.ExampleVO;
import com.weili.example.service.IExampleBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author luying
 * @className ExampleBizService
 * @description
 * @date 2025-10-11 11:42
 **/
@Service
public class ExampleBizService implements IExampleBizService {

    @Resource
    private IExampleRepository exampleRepository;

    @Override
    public void save(ExampleSaveReqVO saveVO) {
        exampleRepository.create(BeanUtils.toBean(saveVO, ExampleDO.class));
    }

    @Override
    public ExampleVO getById(Long id) {
        ExampleDO exampleDO = exampleRepository.getById(id);
        return BeanUtils.toBean(exampleDO, ExampleVO.class);
    }
}
