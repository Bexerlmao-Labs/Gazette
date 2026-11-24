package cc.bexerlmao.gazette.module.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 设备注册请求：蓝牙连接后网页端自动调用，SN 由固件提供 */
@Data
public class DeviceRegisterRequest {

    /** 设备序列号（固件烧录，全局唯一） */
    @NotBlank(message = "设备 SN 不能为空")
    private String sn;

    @NotBlank(message = "设备名称不能为空")
    private String name;

    private String model;

    @Min(1)
    private Integer width;

    @Min(1)
    private Integer height;
}
