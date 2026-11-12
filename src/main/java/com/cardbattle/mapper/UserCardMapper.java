package com.cardbattle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cardbattle.entity.UserCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserCardMapper extends BaseMapper<UserCard> {

    /** 批量 INSERT 新手卡牌: 一条 SQL 多行 VALUES, 省 11 次 DB 往返 */
    @Insert("<script>INSERT INTO user_cards(user_id, card_id) VALUES " +
            "<foreach collection='list' item='uc' separator=','>" +
            "(#{uc.userId}, #{uc.cardId})" +
            "</foreach></script>")
    int insertBatch(@Param("list") List<UserCard> list);
}
