# 卡牌对战游戏匹配与战斗系统

毕业设计：基于Spring Boot的卡牌对战游戏匹配与战斗系统的设计与实现
学生：李家宏 2364130130

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 2.7.18 (Java 11) + MyBatis-Plus + MySQL 8 + Redis + WebSocket(STOMP) + JWT |
| 前端 | Vue3 + Vite + Element Plus + @stomp/stompjs + axios |
| 美术 | 明日方舟干员半身像(开源仓库 `yuanyan3060/ArknightsGameResource`) + 开源素材(Kenney/itch.io)；版权归 © Hypergryph，仅用于学习演示，论文中已注明来源 |

## 目录结构

```
card-battle/
├── pom.xml                          # 后端Maven工程
├── sql/init.sql                     # 建库脚本(9张表) + 56张卡牌 + 6个皮肤 + 3个测试账号
├── src/main/java/com/cardbattle/
│   ├── common/          Result统一响应 / 全局异常
│   ├── config/          WebSocket(STOMP) / Redis序列化 / JWT拦截器
│   ├── controller/      auth登录注册 / cards卡牌库 / decks卡组 / match匹配 / rank天梯
│   ├── battle/          战斗引擎: BattleRoom回合状态机 + BattleManager房间管理
│   ├── service/         UserService / DeckService / MatchService(Redis匹配队列)
│   ├── entity/mapper/dto/util/
└── frontend/                        # Vue3前端
    └── src/views/       Login / Lobby / DeckEditor / Battle 四大页面
```

## 运行步骤

### 1. 数据库
```sql
-- Navicat或命令行执行
source sql/init.sql
```
测试账号: ljh / 123456, test2 / 123456 (密码MD5加盐存储)

### 2. Redis (Windows)
已部署在 `D:\environment\redis\`（Redis 5.0.14 for Windows）：
- **临时启动**：双击 `启动Redis.bat`（关闭窗口即停止）
- **推荐：装成服务开机自启**：右键"管理员身份运行" `安装为系统服务-管理员运行.bat`
- 验证：cmd 里执行 `D:\environment\redis\redis-cli.exe ping` 返回 PONG 即正常（端口6379，无密码）

### 3. 后端
```bash
# IDEA直接打开本项目运行CardBattleApplication
# 或命令行:
mvn spring-boot:run
```
注意: application.yml 里MySQL密码要改成你自己的

### 4. 前端
```bash
cd frontend
npm install
npm run dev
# 浏览器打开 http://localhost:5173
```

### 5. 对战测试
双浏览器(或一个正常+一个无痕)分别登录 ljh 和 test2 -> 都点"开始匹配" ->
3秒内撮合进入战斗页 -> 出牌/攻击/结束回合互打, 玩家HP归零判负

## 核心设计(论文素材)

1. **回合制状态机**: BattleRoom.phase (MATCH_FOUND->BATTLE_START->MAIN->GAME_OVER),
   所有操作synchronized串行化; 论文可演进到Redis分布式锁
2. **天梯匹配**: Redis ZSet队列, score=天梯分, 定时任务撮合分差<=200的相邻玩家;
   论文可演进到Pub/Sub或专用匹配网关
3. **消息同步**: STOMP over WebSocket, 房间广播 /topic/battle/{roomId},
   个人通知 /topic/user/{userId}; 断线重连由stompjs reconnectDelay处理
4. **结算一致性**: 对局记录JSON入库 + 天梯分+25/-25 + 房间销毁三步, 论文可讨论幂等

## 开发路线(对应开题进度安排)

- [x] 工程骨架 + 数据库 + 登录注册JWT
- [x] 战斗引擎核心(出牌/攻击/回合/胜负) + Redis匹配撮合
- [x] 前端四大页面骨架
- [ ] 卡牌效果系统(嘲讽/战吼/亡语, cards.effect字段已预留)
- [ ] 对战动画/卡面图片接入
- [x] 管理后台(封禁/用户详情/三表搜索分页/7日统计)
- [ ] 断线重连恢复战场 / 回合超时托管
- [ ] 单元测试 + 部署上线

## 已知简化(论文"不足与展望"素材)

- MVP阶段手牌信息全量广播(双方可见对方手牌), 正式版应做私密通道
- 战斗操作userId由前端携带, 正式版应在WS握手时校验token
- 平局/回合超时/疲劳抽牌逻辑预留TODO
