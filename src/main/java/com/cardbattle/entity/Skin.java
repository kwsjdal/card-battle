package com.cardbattle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 卡面边框皮肤(纯视觉, 不影响数值) */
@Data
@TableName("skins")
public class Skin {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer price;
    private String frameClass;
    private String description;
    private String operator;    // 绑定干员名(如"阿米娅"), NULL=通用卡框
}
