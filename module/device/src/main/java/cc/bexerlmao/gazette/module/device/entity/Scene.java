package cc.bexerlmao.gazette.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 画面场景：静态底图 + 动态元素 DSL，见 docs/dynamic-content-design.md */
@Data
@TableName("t_scene")
public class Scene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer width;

    private Integer height;

    /** 静态底图（PNG 或 1bpp 打包位图） */
    private byte[] baseImage;

    /** 底图内容哈希 */
    private String baseHash;

    /** 动态元素 DSL 文本，一行一个元素 */
    private String overlayDsl;

    /** 1:启用 0:已删除(软删) */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
