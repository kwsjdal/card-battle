package com.cardbattle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cardbattle.entity.RechargeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RechargeLogMapper extends BaseMapper<RechargeLog> {

    /** 充值总额(SQL聚合, 避免全表加载) */
    @Select("SELECT IFNULL(SUM(amount),0) FROM recharge_log")
    long sumAmount();

    /** 按天统计充值额: [{d: yyyy-MM-dd, s: 金额}] */
    @Select("SELECT DATE(create_time) d, IFNULL(SUM(amount),0) s FROM recharge_log " +
            "WHERE create_time >= #{from} GROUP BY DATE(create_time)")
    List<Map<String, Object>> sumByDay(LocalDateTime from);
}
