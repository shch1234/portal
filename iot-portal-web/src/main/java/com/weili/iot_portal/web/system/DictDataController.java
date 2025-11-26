package com.weili.iot_portal.web.system;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.enums.StatusEnum;
import com.weili.iot_portal.dal.dataobject.system.DictDataDO;
import com.weili.iot_portal.dal.dataobject.system.DictTypeDO;
import com.weili.iot_portal.domain.system.*;
import com.weili.iot_portal.service.system.IDictBizService;
import com.weili.iot_portal.web.annotation.PermRequired;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "字典数据")
@RestController
@RequestMapping("/system")
@Validated
public class DictDataController {

    @Resource
    private IDictBizService dictBizService;

    @PostMapping("/dict-data/create")
    @Operation(summary = "新增字典数据")
    @PermRequired(permission = "system:dict:create")
    public CommonResult<Long> createDictData(@Valid @RequestBody DictDataSaveReqVO createReqVO) {
        Long dictDataId = dictBizService.createDictData(createReqVO);
        return CommonResult.success(dictDataId);
    }

    @PutMapping("/dict-data/update")
    @Operation(summary = "修改字典数据")
    @PermRequired(permission = "system:dict:update")
    public CommonResult<Boolean> updateDictData(@Valid @RequestBody DictDataSaveReqVO updateReqVO) {
        dictBizService.updateDictData(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-data/delete")
    @Operation(summary = "删除字典数据")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PermRequired(permission = "system:dict:delete")
    public CommonResult<Boolean> deleteDictData(Long id) {
        dictBizService.deleteDictData(id);
        return CommonResult.success(true);
    }

    @GetMapping(value = "/dict-data/simple-list")
    @Operation(summary = "获得全部字典数据列表", description = "一般用于管理后台缓存字典数据在本地")
    public CommonResult<List<DictDataSimpleRespVO>> getSimpleDictDataList() {
        List<DictDataDO> list = dictBizService.getDictDataList(
                StatusEnum.ENABLE.getStatus(), null);
        return CommonResult.success(BeanUtils.toBean(list, DictDataSimpleRespVO.class));
    }

    @GetMapping("/dict-data/page")
    @Operation(summary = "获得字典类型的分页列表")
    public CommonResult<PageResult<DictDataRespVO>> getDictTypePage(@Valid DictDataPageReqVO pageReqVO) {
        PageResult<DictDataDO> pageResult = dictBizService.getDictDataPage(pageReqVO);
        return CommonResult.success(BeanUtils.toBean(pageResult, DictDataRespVO.class));
    }

    @GetMapping(value = "/dict-data/get")
    @Operation(summary = "查询字典数据详细")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    public CommonResult<DictDataRespVO> getDictData(@RequestParam("id") Long id) {
        DictDataDO dictData = dictBizService.getDictData(id);
        return CommonResult.success(BeanUtils.toBean(dictData, DictDataRespVO.class));
    }

    @GetMapping(value = "/dict-data/get/dictType")
    @Operation(summary = "查询字典数据详细")
    @Parameter(name = "dictType", description = "字典类型", required = true, example = "1024")
    public CommonResult<List<DictDataRespVO>> getDictDataByType(@RequestParam("dictType") String dictType) {
        List<DictDataDO> dictData = dictBizService.getDictDataByType(dictType);
        return CommonResult.success(BeanUtils.toBean(dictData, DictDataRespVO.class));
    }


    @PostMapping("/dict-type/create")
    @Operation(summary = "创建字典类型")
    @PermRequired(permission = "system:dict:create")
    public CommonResult<Long> createDictType(@Valid @RequestBody DictTypeSaveReqVO createReqVO) {
        Long dictTypeId = dictBizService.createDictType(createReqVO);
        return CommonResult.success(dictTypeId);
    }

    @PutMapping("/dict-type/update")
    @Operation(summary = "修改字典类型")
    @PermRequired(permission = "system:dict:update")
    public CommonResult<Boolean> updateDictType(@Valid @RequestBody DictTypeSaveReqVO updateReqVO) {
        dictBizService.updateDictType(updateReqVO);
        return CommonResult.success(true);
    }

    @DeleteMapping("/dict-type/delete")
    @Operation(summary = "删除字典类型")
    @Parameter(name = "id", description = "编号", required = true)
    @PermRequired(permission = "system:dict:delete")
    public CommonResult<Boolean> deleteDictType(String id) {
        dictBizService.deleteDictType(Long.valueOf(id));
        return CommonResult.success(true);
    }

    @GetMapping("/dict-type/page")
    @Operation(summary = "获得字典类型的分页列表")
    public CommonResult<PageResult<DictTypeRespVO>> pageDictTypes(@Valid DictTypePageReqVO pageReqVO) {
        PageResult<DictTypeDO> pageResult = dictBizService.getDictTypePage(pageReqVO);
        return CommonResult.success(BeanUtils.toBean(pageResult, DictTypeRespVO.class));
    }

    @Operation(summary = "查询字典类型详细")
    @Parameter(name = "id", description = "编号", required = true)
    @GetMapping(value = "/dict-type/get")
    public CommonResult<DictTypeRespVO> getDictType(@RequestParam("id") Long id) {
        DictTypeDO dictType = dictBizService.getDictType(id);
        return CommonResult.success(BeanUtils.toBean(dictType, DictTypeRespVO.class));
    }

    @GetMapping(value = "/dict-type/list-all-simple")
    @Operation(summary = "获得全部字典类型列表", description = "包括开启 + 禁用的字典类型，主要用于前端的下拉选项")
    public CommonResult<List<DictTypeSimpleRespVO>> getSimpleDictTypeList() {
        List<DictTypeDO> list = dictBizService.getDictTypeList();
        return CommonResult.success(BeanUtils.toBean(list, DictTypeSimpleRespVO.class));
    }
}
