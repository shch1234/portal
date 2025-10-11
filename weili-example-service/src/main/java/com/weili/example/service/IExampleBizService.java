package com.weili.example.service;

import com.weili.example.domain.vo.ExampleSaveReqVO;
import com.weili.example.domain.vo.ExampleVO;

/**
 * @InterfaceName: IExampleBizService
 * @Description:
 * @Author: luying
 **/
public interface IExampleBizService {

    void save(ExampleSaveReqVO saveVO);

    ExampleVO getById(Long id);

}
