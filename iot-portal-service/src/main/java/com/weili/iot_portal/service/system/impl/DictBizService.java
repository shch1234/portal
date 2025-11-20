package com.weili.iot_portal.service.system.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.DictDataDO;
import com.weili.iot_portal.dal.dataobject.system.DictTypeDO;
import com.weili.iot_portal.dal.ddd.DictDataPageQuery;
import com.weili.iot_portal.dal.ddd.DictTypePageQuery;
import com.weili.iot_portal.dal.repository.system.IDictDataRepository;
import com.weili.iot_portal.dal.repository.system.IDictTypeRepository;
import com.weili.iot_portal.domain.system.DictDataPageReqVO;
import com.weili.iot_portal.domain.system.DictDataSaveReqVO;
import com.weili.iot_portal.domain.system.DictTypePageReqVO;
import com.weili.iot_portal.domain.system.DictTypeSaveReqVO;
import com.weili.iot_portal.service.system.IDictBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * @author luying
 * @className DicBizService
 * @description
 * @date 2025-07-05 01:26
 **/
@Service
public class DictBizService implements IDictBizService {

    @Resource
    private IDictTypeRepository dictTypeRepository;
    @Resource
    private IDictDataRepository dictDataRepository;


    @Override
    public PageResult<DictTypeDO> getDictTypePage(DictTypePageReqVO pageReqVO) {
        return dictTypeRepository.selectPage(BeanUtils.toBean(pageReqVO, DictTypePageQuery.class));
    }

    @Override
    public DictTypeDO getDictType(Long id) {
        return dictTypeRepository.getById(id);
    }

    @Override
    public DictTypeDO getDictType(String type) {
        return dictTypeRepository.selectByType(type);
    }

    @Override
    public Long createDictType(DictTypeSaveReqVO createReqVO) {
        validateDictTypeNameUnique(null, createReqVO.getName());
        validateDictTypeUnique(null, createReqVO.getType());
        DictTypeDO dictType = BeanUtils.toBean(createReqVO, DictTypeDO.class);
        dictTypeRepository.create(dictType);
        return dictType.getId();
    }

    @Override
    public void updateDictType(DictTypeSaveReqVO updateReqVO) {
        validateDictTypeExists(updateReqVO.getId());
        validateDictTypeNameUnique(updateReqVO.getId(), updateReqVO.getName());
        validateDictTypeUnique(updateReqVO.getId(), updateReqVO.getType());
        DictTypeDO updateObj = BeanUtils.toBean(updateReqVO, DictTypeDO.class);
        dictTypeRepository.update(updateObj);
    }

    @Override
    public void deleteDictType(Long id) {
        DictTypeDO dictType = validateDictTypeExists(id);
        if (getDictDataCountByDictType(dictType.getType()) > 0) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_HAS_CHILDREN);
        }
        dictTypeRepository.delete(id);
    }

    @Override
    public List<DictTypeDO> getDictTypeList() {
        return dictTypeRepository.selectList();
    }

    @Override
    public Long createDictData(DictDataSaveReqVO createReqVO) {
        validateDictTypeExists(createReqVO.getDictType());
        validateDictDataValueUnique(null, createReqVO.getDictType(), createReqVO.getValue());
        DictDataDO dictData = BeanUtils.toBean(createReqVO, DictDataDO.class);
        dictDataRepository.create(dictData);
        return dictData.getId();
    }

    @Override
    public void updateDictData(DictDataSaveReqVO updateReqVO) {
        validateDictDataExists(updateReqVO.getId());
        validateDictTypeExists(updateReqVO.getDictType());
        validateDictDataValueUnique(updateReqVO.getId(), updateReqVO.getDictType(), updateReqVO.getValue());
        DictDataDO updateObj = BeanUtils.toBean(updateReqVO, DictDataDO.class);
        dictDataRepository.update(updateObj);
    }


    @Override
    public List<DictDataDO> getDictDataList(Integer status, String dictType) {
        List<DictDataDO> list = dictDataRepository.selectListByStatusAndDictType(status, dictType);
        list.sort(Comparator
                .comparing(DictDataDO::getDictType)
                .thenComparingInt(DictDataDO::getSort));
        return list;
    }

    @Override
    public PageResult<DictDataDO> getDictDataPage(DictDataPageReqVO pageReqVO) {
        return dictDataRepository.selectPage(BeanUtils.toBean(pageReqVO, DictDataPageQuery.class));
    }

    @Override
    public DictDataDO getDictData(Long id) {
        return dictDataRepository.selectById(id);
    }

    @Override
    public List<DictDataDO> getDictDataByType(String dictType) {
        return dictDataRepository.selectList(new LambdaQueryWrapper<DictDataDO>().eq(DictDataDO::getDictType, dictType));
    }

    @Override
    public void deleteDictData(Long id) {
        validateDictDataExists(id);
        dictDataRepository.delete(id);
    }

    @Override
    public long getDictDataCountByDictType(String dictType) {
        return dictDataRepository.selectCountByDictType(dictType);
    }


    private void validateDictTypeNameUnique(Long id, String name) {
        DictTypeDO dictType = dictTypeRepository.selectByName(name);
        if (dictType == null) {
            return;
        }
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_NAME_DUPLICATE);
        }
        if (!dictType.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_NAME_DUPLICATE);
        }
    }

    private void validateDictTypeUnique(Long id, String type) {
        if (StrUtil.isEmpty(type)) {
            return;
        }
        DictTypeDO dictType = dictTypeRepository.selectByType(type);
        if (dictType == null) {
            return;
        }
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_TYPE_DUPLICATE);
        }
        if (!dictType.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_TYPE_DUPLICATE);
        }
    }

    private DictTypeDO validateDictTypeExists(Long id) {
        if (id == null) {
            return null;
        }
        DictTypeDO dictType = dictTypeRepository.getById(id);
        if (dictType == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_NOT_EXISTS);
        }
        return dictType;
    }


    private void validateDictDataValueUnique(Long id, String dictType, String value) {
        DictDataDO dictData = dictDataRepository.selectByDictTypeAndValue(dictType, value);
        if (dictData == null) {
            return;
        }
        if (id == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_DATA_VALUE_DUPLICATE);
        }
        if (!dictData.getId().equals(id)) {
            throw new ServiceException(ErrorCodeConstants.DICT_DATA_VALUE_DUPLICATE);
        }
    }

    private void validateDictDataExists(Long id) {
        if (id == null) {
            return;
        }
        DictDataDO dictData = dictDataRepository.selectById(id);
        if (dictData == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_DATA_NOT_EXISTS);
        }
    }

    private void validateDictTypeExists(String type) {
        DictTypeDO dictType = getDictType(type);
        if (dictType == null) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_NOT_EXISTS);
        }
        if (!StatusEnum.ENABLE.getStatus().equals(dictType.getStatus())) {
            throw new ServiceException(ErrorCodeConstants.DICT_TYPE_NOT_ENABLE);
        }
    }
}
