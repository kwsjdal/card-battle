package com.cardbattle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cardbattle.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按天统计新增用户: [{d: yyyy-MM-dd, c: 数量}] */
    @Select("SELECT DATE(create_time) d, COUNT(*) c FROM users " +
            "WHERE create_time >= #{from} GROUP BY DATE(create_time)")
    List<Map<String, Object>> countByDay(LocalDateTime from);
}
