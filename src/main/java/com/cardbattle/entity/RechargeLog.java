package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 模拟充值流水 */
@Data
@TableName("recharge_log")
public class RechargeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer amount;        // 充值金额(金币)
    private Integer balanceAfter;  // 充值后余额
    private String remark;         // 渠道备注(模拟)
    private LocalDateTime createTime;
}
