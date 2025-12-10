package com.weili.iot_portal.web.alarm;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.alarm.AlarmItemVO;
import com.weili.iot_portal.domain.alarm.request.AlarmListQueryReq;
import com.weili.iot_portal.service.alarm.AlarmListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 报警列表控制器
 * 
 * <p>对应需求：4.3.2 报警列表
 */
@Tag(name = "报警列表", description = "报警列表查询相关接口")
@RestController
@RequestMapping("/alarm/list")
@RequiredArgsConstructor
public class AlarmListController {

    private final AlarmListService alarmListService;

    @Operation(
            summary = "查询当前正在报警的列表",
            description = "查询所有正在报警的设备的报警详情列表。\n" +
                    "用于点击'当前报警设备数量'后展示所有正在报警的设备及其报警内容。\n" +
                    "如果一个设备有多条报警，会返回多条记录。\n" +
                    "- 自动筛选：isActive = true（仅显示正在报警的记录）\n" +
                    "- 字段：设备编号、设备类型/子类型、报警号、报警内容、开始时间、持续时间等")
    @GetMapping("/current")
    public CommonResult<PageResult<AlarmItemVO>> getCurrentAlarmList(
            @RequestParam("factoryId") String factoryId,
            @RequestParam(value = "workshopId", required = false) String workshopId,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        // 构建查询请求，自动设置 isActive = true
        AlarmListQueryReq request = new AlarmListQueryReq();
        request.setFactoryId(factoryId);
        request.setWorkshopId(workshopId);
        request.setIsActive(true); // 仅查询正在报警的记录
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        
        PageResult<AlarmItemVO> result = alarmListService.getAlarmList(request);
        return CommonResult.success(result);
    }

    @Operation(
            summary = "查询报警列表（完整筛选）",
            description = "查询报警详情列表，支持按设备编号、时间范围、是否报警中等条件筛选。\n" +
                    "- 筛选条件：设备编号、时间范围、是否报警中\n" +
                    "- 字段：设备编号、设备类型/子类型、报警号、报警内容、开始时间、结束时间、持续时间、是否报警中\n" +
                    "- 进行中报警：结束时间显示null，持续时间实时累加")
    @PostMapping("/query")
    public CommonResult<PageResult<AlarmItemVO>> getAlarmList(
            @RequestBody AlarmListQueryReq request) {
        PageResult<AlarmItemVO> result = alarmListService.getAlarmList(request);
        return CommonResult.success(result);
    }
}

