package cc.bexerlmao.gazette.module.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 设备创建请求：sn/key 由服务端生成 */
@Data
public class DeviceCreateRequest {

    @NotBlank(message = "设备名称不能为空")
    private String name;

    private String model;

    @Min(1)
    private Integer width;

    @Min(1)
    private Integer height;

    /** 初始绑定的场景，可为空 */
    private Long currentSceneId;
}
