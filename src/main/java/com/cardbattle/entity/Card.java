package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 卡牌 */
@Data
@TableName("cards")
public class Card {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer cost;      // 法力消耗
    private Integer attack;    // 攻击力
    private Integer hp;        // 生命值
    private String effect;     // 效果(预留)
    private String img;        // 卡面图(预留)
    private Integer rarity;    // 1普通2稀有3史诗4传说
    private String description;

    @TableField(exist = false)
    private Boolean canAct;    // 战斗内瞬态: 该随从本回合是否可攻击(不落库)
    @TableField(exist = false)
    private Boolean sick;      // 战斗内瞬态: 召唤失调中(本回合刚上场且无冲锋), 下回合才能攻击(不落库)
    @TableField(exist = false)
    private Boolean rushLock;  // 战斗内瞬态: 突袭随从登场当回合(可打随从, 不能打脸)(不落库)

    /** 战斗内复制: 同名卡多张必须为独立实例(hp扣减/亡语按实例结算) */
    public Card copy() {
        Card c = new Card();
        c.setId(id);
        c.setName(name);
        c.setCost(cost);
        c.setAttack(attack);
        c.setHp(hp);
        c.setEffect(effect);
        c.setImg(img);
        c.setRarity(rarity);
        c.setDescription(description);
        return c;
    }
}
