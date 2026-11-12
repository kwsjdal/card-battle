package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 对局记录 */
@Data
@TableName("match_records")
public class MatchRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long player1Id;
    private Long p1DeckId;     // 玩家1所用卡组id(历史数据为空, 卡组胜率统计用)
    private Long player2Id;
    private Long p2DeckId;     // 玩家2所用卡组id(机器人/随机卡组为空)
    private Long winnerId;
    private Integer rounds;
    private String detail;     // 战斗过程JSON(预留)
    private LocalDateTime createTime;
}
