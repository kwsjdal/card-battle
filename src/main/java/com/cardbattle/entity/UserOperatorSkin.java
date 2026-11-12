package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每干员独立装配的皮肤(明日方舟模式): 一个干员同一时间只能穿一款 */
@Data
@TableName("user_operator_skin")
public class UserOperatorSkin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String operator;   // 干员名(即卡牌名)
    private Integer skinId;    // 装配的皮肤 id
    private LocalDateTime createTime;
}
