package com.cardbattle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 卡牌对战游戏匹配与战斗系统
 * 启动前: 1)执行 sql/init.sql  2)确认MySQL/Redis已启动
 */
@EnableScheduling
@SpringBootApplication
public class CardBattleApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardBattleApplication.class, args);
        System.out.println(">>> CardBattle 后端启动成功: http://localhost:8080");
    }
}
