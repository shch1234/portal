package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备程序信息响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备程序信息响应")
public class DeviceProgramRespVO {

    @Schema(description = "程序名", example = "program_001.nc")
    private String programName;

    @Schema(description = "程序路径", example = "/cnc/programs/program_001.nc")
    private String programPath;

    @Schema(description = "执行代码", example = "G90")
    private String executeCode;

    @Schema(description = "G代码详情", example = "第1组G代码: GG53 第2组G代码: GG0...")
    private String gCodeDetails;

    @Schema(description = "M代码详情", example = "位置: 1 (号主轴 (?21-146) ) 刀具详细数据...")
    private String mCodeDetails;
}
