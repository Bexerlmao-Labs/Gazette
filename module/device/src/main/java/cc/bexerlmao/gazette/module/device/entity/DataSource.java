package cc.bexerlmao.gazette.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 外部数据源凭证（GLM / DeepSeek），见 t_data_source。敏感字段 AES 加密存储。 */
@Data
@TableName("t_data_source")
public class DataSource {

    /** 数据源命名空间：glm / deepseek */
    @TableId(type = IdType.INPUT)
    private String provider;

    /** API Key（AES 加密存储） */
    private String apiKey;

    /** 认证方式：api_key / platform_cookie / platform_password */
    private String authType;

    /** 平台登录账号（手机号/邮箱，明文；不涉密） */
    private String account;

    /** 平台登录密码（AES 加密存储） */
    private String secretEnc;

    /** 平台登录态 Cookie（AES 加密存储） */
    private String cookieEnc;

    private LocalDateTime updatedAt;
}
