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
        // 移除按钮
        menuList.removeIf(menu -> menu.getType().equals(MenuTypeEnum.BUTTON.getType()));
        // 排序，保证菜单的有序性
        menuList.sort(Comparator.comparing(MenuDO::getSort));

        // 构建菜单树
        // 使用 LinkedHashMap 的原因，是为了排序 。实际也可以用 Stream API ，就是太丑了。
        Map<Long, AuthPermissionRespVO.MenuVO> treeNodeMap = new LinkedHashMap<>();
        menuList.forEach(menu -> treeNodeMap.put(menu.getId(), convertTreeNode(menu)));
        // 处理父子关系
        treeNodeMap.values().stream().filter(node -> !node.getParentId().equals(MenuDO.ID_ROOT)).forEach(childNode -> {
            // 获得父节点
            AuthPermissionRespVO.MenuVO parentNode = treeNodeMap.get(childNode.getParentId());
            if (parentNode == null) {
                LoggerFactory.getLogger(getClass()).error("[buildRouterTree][resource({}) 找不到父资源({})]",
                        childNode.getId(), childNode.getParentId());
                return;
            }
            // 将自己添加到父节点中
            if (parentNode.getChildren() == null) {
                parentNode.setChildren(new ArrayList<>());
            }
            parentNode.getChildren().add(childNode);
        });
        // 获得到所有的根节点
        return CollectionUtils.filterList(treeNodeMap.values(), node -> MenuDO.ID_ROOT.equals(node.getParentId()));
    }
}
