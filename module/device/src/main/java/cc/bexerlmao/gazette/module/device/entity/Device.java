package cc.bexerlmao.gazette.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 墨水屏设备 */
@Data
@TableName("t_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备序列号，全局唯一，固件烧录 */
    private String deviceSn;

    /** 设备访问凭证，拉取画面时校验 */
    private String deviceKey;

    private String name;

    private String model;

    private Integer width;

    private Integer height;

    /** 旋转角度 0/90/180/270 */
    private Integer rotate;

    /** 当前绑定的场景 */
    private Long currentSceneId;

    /** 最终渲染位图哈希；设备轮询比对，一致则无需刷新 */
    private String currentHash;

    /** 0:离线 1:在线 2:未激活 */
    private Integer status;

    /** 最近上报电量（百分比） */
    private Integer battery;

    private LocalDateTime lastOnlineAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
