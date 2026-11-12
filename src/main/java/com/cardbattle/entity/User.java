package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)   // 永远不返回给前端
    private String password;

    /** 每用户随机盐(SHA-256 哈希用), 存量老账号为 null, 首次登录自动升级 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)   // 同样不返回给前端
    private String salt;

    /** 密码的可逆密文(AES), 仅供"管理员查看密码"; 存量账号为 null, 首次登录自动补齐 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)   // 不返回给前端
    private String pwdCipher;

    private String nickname;
    private Integer rating;
    private Integer winCount;
    private Integer loseCount;
    private String role;       // user / admin
    private Integer balance;   // 金币(充值获得, 可买皮肤/抽卡)
    private Integer crystal;   // 水晶(每日签到/任务获得, 可抽卡, 比金币贵100/抽)
    private Integer skinId;    // 当前装配的卡面皮肤(0=默认)
    private Integer status;    // 0正常 / 1封禁
    private LocalDateTime bannedUntil;  // 封禁截止时间(null=永久), 到期自动解封
    private String banReason;          // 封禁原因(管理端填写)
    private String avatar;     // 头像路径(预设 /avatars/N.png)
    private String phone;      // 手机号(注册必填, 唯一)
    private LocalDateTime createTime;
}
