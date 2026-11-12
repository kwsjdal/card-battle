package com.cardbattle.dto;

import lombok.Data;

/** 前端 -> /app/battle.action 的战斗操作 */
@Data
public class BattleAction {
    private String roomId;
    /**
     * 【已废弃·保留仅为兼容旧报文】
     * 服务端一律忽略本字段: 操作者身份只认 WS 握手时的 JWT(BattleWsController.uidOf)。
     * 早期版本信任报文里的 userId, 导致任意登录账号都能伪造他人身份出牌/认输。
     */
    private Long userId;
    private String type;          // play / attack / end / surrender
    private Integer handIndex;
    private Integer boardIndex;
    private Integer targetIndex;  // -1 表示攻击对方英雄
}
