package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 抽卡流水 */
@Data
@TableName("gacha_log")
public class GachaLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer cost;
    private String results;    // JSON 结果快照
    private LocalDateTime createTime;
}
