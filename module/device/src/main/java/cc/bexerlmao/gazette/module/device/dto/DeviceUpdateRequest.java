package cc.bexerlmao.gazette.module.device.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 设备更新请求（改名/绑定场景） */
@Data
public class DeviceUpdateRequest {

    private String name;

    /** 绑定的场景 id，null 表示解绑 */
    private Long currentSceneId;

    @NotNull
    private Boolean bindScene;
}
