package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 玩家已购买皮肤 */
@Data
@TableName("user_skins")
public class UserSkin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer skinId;
    private LocalDateTime createTime;
}
