package com.cardbattle.service;

import java.util.Map;

/** 个人数据中心: 战绩 / 收藏 / 消费 / 趋势 一站式聚合 */
public interface StatsService {
    Map<String, Object> overview(Long userId);
}
