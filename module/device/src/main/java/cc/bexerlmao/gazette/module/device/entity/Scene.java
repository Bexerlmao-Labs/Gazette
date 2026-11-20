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

    /* ---- 位图定义方案（转换模板，目标单片机如何解读位图字节）---- */

    /** 每像素位宽 1/2/3/4，NULL 按编码自动推导 */
    private Integer bitmapBits;

    /** 字节内位序 msb/lsb */
    private String bitmapBitOrder;

    /** 各颜色编码（十六进制单字符 0-F），NULL=不支持该色 */
    private String bitmapBlack;
    private String bitmapWhite;
    private String bitmapRed;
    private String bitmapYellow;
    private String bitmapBlue;
    private String bitmapGreen;

    /** 1:启用 0:已删除(软删) */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
