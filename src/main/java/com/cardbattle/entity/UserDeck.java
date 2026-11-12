package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户卡组, cardIds为30张卡的id JSON数组 */
@Data
@TableName("user_decks")
public class UserDeck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String cardIds;    // JSON字符串: [1,1,2,3,...]
    /** 是否为出战卡组(每个玩家最多 1 套): 匹配对局时使用 */
    private Integer isDefault;
    private LocalDateTime createTime;
}
