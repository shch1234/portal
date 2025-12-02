package com.weili.iot_portal.dal.repository.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.system.LoginUserDO;
import com.weili.iot_portal.dal.ddd.system.LoginUserPageQuery;


/**
 * @InterfaceName: ILoginUserRepository
 * @Description:
 * @Author: luying
 **/
public interface ILoginUserRepository {

    void create(LoginUserDO loginUserDO);

    void update(LoginUserDO loginUserDO);

    void delete(Long id);

    PageResult<LoginUserDO> selectPage(LoginUserPageQuery query);

    LoginUserDO getById(Long id);

    LoginUserDO getByEmpId(Long id);

}
