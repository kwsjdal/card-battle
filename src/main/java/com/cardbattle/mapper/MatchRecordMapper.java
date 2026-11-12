package com.cardbattle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cardbattle.entity.MatchRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MatchRecordMapper extends BaseMapper<MatchRecord> {

    /** 按天统计对局数: [{d: yyyy-MM-dd, c: 数量}] */
    @Select("SELECT DATE(create_time) d, COUNT(*) c FROM match_records " +
            "WHERE create_time >= #{from} GROUP BY DATE(create_time)")
    List<Map<String, Object>> countByDay(LocalDateTime from);
}
