package com.weili.iot_portal.dal.repository.system.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.mybatis.query.LambdaQueryWrapperX;
import com.weili.iot_portal.dal.dataobject.system.LoginUserDO;
import com.weili.iot_portal.dal.ddd.system.LoginUserPageQuery;
import com.weili.iot_portal.dal.repository.system.ILoginUserRepository;
import com.weili.iot_portal.dal.mapper.system.LoginUserMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * @author luying
 * @className LoginUserRepository
 * @description
 * @date 2025-07-15 16:39
 **/
@Repository
public class LoginUserRepository extends ServiceImpl<LoginUserMapper, LoginUserDO> implements ILoginUserRepository {

    @Override
    public void create(LoginUserDO loginUserDO) {
        loginUserDO.setUpdateTime(LocalDateTime.now());
        loginUserDO.setCreateTime(LocalDateTime.now());
        super.save(loginUserDO);
    }

    @Override
    public void update(LoginUserDO loginUserDO) {
        loginUserDO.setUpdateTime(LocalDateTime.now());
        super.updateById(loginUserDO);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public PageResult<LoginUserDO> selectPage(LoginUserPageQuery query) {
        LambdaQueryWrapperX<LoginUserDO> queryWrapper = new LambdaQueryWrapperX<>();

        // 搜索条件（用户名、工号、手机号）
        if (StringUtils.isNotBlank(query.getSearchKey())) {
            queryWrapper.like(LoginUserDO::getUsername, query.getSearchKey())
                    .or()
                    .like(LoginUserDO::getJobNumber, query.getSearchKey())
                    .or()
                    .like(LoginUserDO::getMobile, query.getSearchKey());
        }

        // 时间范围条件（确保数组不为null且长度为2）
        if (query.getCreateTime() != null && query.getCreateTime().length == 2) {
            queryWrapper.between(LoginUserDO::getCreateTime,
                    query.getCreateTime()[0],
                    query.getCreateTime()[1]);
        }

        Page<LoginUserDO> page = this.page(
                new Page<>(query.getPageNo(), query.getPageSize()),
                queryWrapper
        );
        return new PageResult<>(page.getRecords(), page.getTotal());
    }


    @Override
    public LoginUserDO getByUserId(Long userId) {
        LambdaQueryWrapperX<LoginUserDO> queryWrapper = new LambdaQueryWrapperX<>();
        queryWrapper.eq(LoginUserDO::getUserId, userId);
        return super.getOne(queryWrapper);
    }
}
