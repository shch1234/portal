package com.weili.iot_portal.web.converter;

import cn.hutool.core.collection.CollUtil;
import com.weili.basic.common.util.CollectionUtils;
import com.weili.iot_portal.common.enums.MenuTypeEnum;
import com.weili.iot_portal.dal.dataobject.system.MenuDO;
import com.weili.iot_portal.dal.dataobject.system.RoleDO;
import com.weili.iot_portal.domain.permission.AuthPermissionRespVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author luying
 * @description: TODO
 * @date 2025/6/18 17:01
 */
@Mapper
public interface AuthLoginConvert {
    AuthLoginConvert INSTANCE = Mappers.getMapper(AuthLoginConvert.class);


    default AuthPermissionRespVO convertNoUser(List<RoleDO> roleList, List<MenuDO> menuList) {
        AuthPermissionRespVO respVO = new AuthPermissionRespVO();
        respVO.setRoles(CollectionUtils.convertSet(roleList, RoleDO::getCode));
        respVO.setPermissions(CollectionUtils.convertSet(menuList, MenuDO::getPermission));
        respVO.setMenus(this.buildMenuTree(menuList));
        return respVO;
    }

    @Mapping(target = "children", ignore = true)
    AuthPermissionRespVO.MenuVO convertTreeNode(MenuDO menu);


    default List<AuthPermissionRespVO.MenuVO> buildMenuTree(List<MenuDO> menuList) {
        if (CollUtil.isEmpty(menuList)) {
            return Collections.emptyList();
        }
        menuList.removeIf(menu -> menu.getType().equals(MenuTypeEnum.BUTTON.getType()));
        menuList.sort(Comparator.comparing(MenuDO::getSort));

        Map<Long, AuthPermissionRespVO.MenuVO> treeNodeMap = new LinkedHashMap<>();
        menuList.forEach(menu -> treeNodeMap.put(menu.getId(), convertTreeNode(menu)));
        treeNodeMap.values().stream().filter(node -> !node.getParentId().equals(MenuDO.ID_ROOT)).forEach(childNode -> {
            AuthPermissionRespVO.MenuVO parentNode = treeNodeMap.get(childNode.getParentId());
            if (parentNode == null) {
                LoggerFactory.getLogger(getClass()).error("[buildRouterTree][resource({}) 找不到父资源({})]",
                        childNode.getId(), childNode.getParentId());
                return;
            }
            if (parentNode.getChildren() == null) {
                parentNode.setChildren(new ArrayList<>());
            }
            parentNode.getChildren().add(childNode);
        });
        return CollectionUtils.filterList(treeNodeMap.values(), node -> MenuDO.ID_ROOT.equals(node.getParentId()));
    }
}
