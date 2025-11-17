package com.weili.iot_portal.service.system;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.system.DictDataDO;
import com.weili.iot_portal.dal.dataobject.system.DictTypeDO;
import com.weili.iot_portal.domain.system.DictDataPageReqVO;
import com.weili.iot_portal.domain.system.DictDataSaveReqVO;
import com.weili.iot_portal.domain.system.DictTypePageReqVO;
import com.weili.iot_portal.domain.system.DictTypeSaveReqVO;
import org.springframework.lang.Nullable;

import java.util.List;


/**
 * @InterfaceName: IDictBizService
 * @Description: 数据字典
 * @Author: luying
 **/
public interface IDictBizService {

    /**
     * 创建字典类型
     *
     * @param createReqVO 字典类型信息
     * @return 字典类型编号
     */
    Long createDictType(DictTypeSaveReqVO createReqVO);

    /**
     * 更新字典类型
     *
     * @param updateReqVO 字典类型信息
     */
    void updateDictType(DictTypeSaveReqVO updateReqVO);

    /**
     * 删除字典类型
     *
     * @param id 字典类型编号
     */
    void deleteDictType(Long id);

    /**
     * 获得字典类型分页列表
     *
     * @param pageReqVO 分页请求
     * @return 字典类型分页列表
     */
    PageResult<DictTypeDO> getDictTypePage(DictTypePageReqVO pageReqVO);

    /**
     * 获得字典类型详情
     *
     * @param id 字典类型编号
     * @return 字典类型
     */
    DictTypeDO getDictType(Long id);

    /**
     * 获得字典类型详情
     *
     * @param type 字典类型
     * @return 字典类型详情
     */
    DictTypeDO getDictType(String type);

    /**
     * 获得全部字典类型列表
     *
     * @return 字典类型列表
     */
    List<DictTypeDO> getDictTypeList();


    /**
     * 创建字典数据
     *
     * @param createReqVO 字典数据信息
     * @return 字典数据编号
     */
    Long createDictData(DictDataSaveReqVO createReqVO);

    /**
     * 更新字典数据
     *
     * @param updateReqVO 字典数据信息
     */
    void updateDictData(DictDataSaveReqVO updateReqVO);

    /**
     * 删除字典数据
     *
     * @param id 字典数据编号
     */
    void deleteDictData(Long id);

    /**
     * 获得字典数据列表
     *
     * @param status   状态
     * @param dictType 字典类型
     * @return 字典数据全列表
     */
    List<DictDataDO> getDictDataList(@Nullable Integer status, @Nullable String dictType);

    /**
     * 获得字典数据分页列表
     *
     * @param pageReqVO 分页请求
     * @return 字典数据分页列表
     */
    PageResult<DictDataDO> getDictDataPage(DictDataPageReqVO pageReqVO);

    /**
     * 获得字典数据详情
     *
     * @param id 字典数据编号
     * @return 字典数据
     */
    DictDataDO getDictData(Long id);

    /**
     * 获得字典数据详情
     *
     * @param dictType 字典类型
     * @return 字典数据
     */
    List<DictDataDO> getDictDataByType(String dictType);

    /**
     * 获得指定字典类型的数据数量
     *
     * @param dictType 字典类型
     * @return 数据数量
     */
    long getDictDataCountByDictType(String dictType);
}
