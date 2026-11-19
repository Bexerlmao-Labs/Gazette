package cc.bexerlmao.gazette.module.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 场景保存请求（新增/更新共用） */
@Data
public class SceneSaveRequest {

    private Long id;

    @NotBlank(message = "场景名称不能为空")
    private String name;

    @Min(1)
    private Integer width;

    @Min(1)
    private Integer height;

    /** 静态底图（PNG）的 Base64 编码，可为空（纯 DSL 场景） */
    private String baseImageBase64;

    /** 动态元素 DSL 文本 */
    private String overlayDsl;
}
