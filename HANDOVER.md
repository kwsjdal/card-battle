# 卡牌对战系统 — 交接文档

> 毕业设计：基于 Spring Boot 的卡牌对战游戏匹配与战斗系统的设计与实现
> 学生：李家宏（2364130130）
> 交接时间：2026-10-23

---

## 一、项目概览

明日方舟风格的卡牌对战游戏，含匹配、战斗、抽卡、皮肤、天梯、管理后台。

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 2.7.18 (Java 11, javax 命名空间) + MyBatis-Plus + MySQL 8 + Redis + WebSocket(STOMP) + JWT |
| 前端 | Vue3 + Vite + Element Plus + @stomp/stompjs + axios |
| 美术 | 开源素材 + AI 生成卡面（论文注明来源） |

---

## 二、运行环境

| 项 | 值 |
|---|---|
| MySQL | `localhost:3306` / 库名 `card_battle` / 账号 `root` / 密码 `${DB_PASSWORD}` |
| Redis | `localhost:6379`，无密码，Windows 版在 `D:\environment\redis\` |
| 后端 | `mvn spring-boot:run` → 8080（配置在 `src/main/resources/application.yml`） |
| 前端 | `cd frontend && npm run dev` → 5173 |

**测试账号：**

| 账号 | 密码 | 角色 |
|---|---|---|
| `ljh` | `123456` | 管理员（role=admin） |
| `test2` | `123456` | 玩家（陪练小号） |
| `user` | `123456` | 玩家（手机号 17701581891，2026-10-23 已重置为 123456，原哈希与文档不符） |

> 密码 = MD5(盐 + 明文)，盐在 `JwtUtil.PWD_SALT`，勿直接改明文进库。

---

## 三、已完成功能

### 早期已完成（骨架阶段）
- [x] 工程骨架 + 数据库 + 登录/注册 JWT
- [x] 战斗引擎核心（出牌/攻击/回合/胜负/结算）+ Redis 匹配撮合
- [x] 前端页面：Login / Lobby / DeckEditor / Battle / Shop / Records / Admin
- [x] 抽卡系统（gacha）+ 卡牌商店 + 皮肤系统（6 个卡框皮肤）
- [x] 天梯排行 + 对局记录 + 充值流水

### 本次会话完成（2026-10-23 下午 · 双货币 + 多套卡组 + 扩充）
1. **卡牌扩充 56 → 66 张**：新增 10 位明日方舟干员（id 57–66：阿米娅/伊芙利特/赫拉格/W/凯尔希/铃兰/煌/森蚺/温蒂/夕），img 待生成。
2. **金币/水晶双货币**：`users.crystal` 新字段；水晶仅从签到/任务获得，抽卡每抽比金币贵 100（单抽 💎200、十连 💎1900）；金币充值获得，用于买皮肤与抽卡；重复卡回收仍返金币。
3. **每日签到**：`POST /api/bonus/sign`，基础 50 水晶 + 连续天数 ×10（封顶 +70），Redis SETNX 防重复。
4. **每日任务**：完成对局 +30 / 取得胜利 +50 / 抽卡 1 次 +20 水晶，进度存 Redis（48h 过期免清理），`BonusService` 挂在 `BattleManager.finish` 与 `GachaService.pull` 上自动累计。
5. **多套卡组**：DeckEditor 顶部新增「我的套牌」胶囊列表，支持编辑/另存为新卡组/删除/新建；后端本就支持多行 user_decks，无需改表。
6. **皮肤 6 → 12 款**：新增 6 款明日方舟干员主题卡框（新约阿米娅/医师凯尔希/爆破W/雪境银灰/黄昏史尔特尔/荒野拉普兰德，金币购买），样式在 `frontend/src/skins.css`。
7. **增量 SQL**：`sql/upgrade-2026-10-23.sql`（存量库执行），`init.sql` 已同步（66 卡/12 皮肤/crystal 字段），临时库试跑验证通过。
8. **后端已在 8080 重启生效**；注意本机 shell 有 `SERVER_PORT=51875` 环境变量会覆盖配置，手动启动需 `SERVER_PORT=8080 mvn spring-boot:run`。

### 干员档案页（2026-10-23 晚）
1. **卡牌详情 = 干员档案**：新组件 `frontend/src/components/CardDetail.vue`。
   左侧大立绘（实时套用当前皮肤边框）+ 右侧费用/攻/血/稀有度/职业/档案描述。
   - **战吼 / 亡语**：解析 `cards.effect`（`battlecry:damage_face:2` 之类）渲染成彩色标签 + 中文说明；
     战斗引擎 `BattleRoom.resolveAbility` 本来就执行这些效果（非展示噱头）。
   - 入口：商店卡池点击小卡、卡组编辑页点卡片上的 **ⓘ** 按钮。
2. **时装装配（明日方舟式 · 每干员独立，不共享）**：`skins` 表新增 `operator` 字段（绑定干员，NULL=通用）。
   档案页底部列出该干员的**专属时装 + 全部通用卡框**，可直接购买 / 装配 / 卸下。
   - **新增表 `user_operator_skin`**（`uk_user_oper(user_id, operator)`）记录「哪个干员穿哪款皮肤」，
     旧的全员共享式 `users.skin_id` 全局装配接口 **`/api/skins/equip` 已删除**。
   - 新接口：`GET /api/skins/operator?name=阿米娅`（可用列表）、`GET /api/skins/map`（全干员装配映射）、
     `POST /api/skins/equip-operator {operator, skinId}`、`POST /api/skins/unequip-operator`。
   - 越权校验：专属时装只能给绑定干员穿（`"「医师·凯尔希」是 凯尔希 的专属时装"`），未购买不得装配。
   - `GET /api/skins` 返回改为每款皮肤的 `operator / exclusive / wornBy`（哪些干员正穿着），
     商店皮肤页显示「专属·阿米娅 / 通用」与「已装配：玫兰莎、克洛丝…」。
3. **卡面直接显示时装**：`skin.js` 新增 `cardSkin(name)` / `cardArt(name)`，
   Shop 卡池 / **我的卡包** / DeckEditor / Battle 的卡面都用「该干员当前皮肤边框 + 专属立绘覆盖原图」。
4. `skin.js` 的 MAP 补全到 12 款，并抽出 `skinImg/skinOper` 供预览复用。

### 我的卡包 + 金币换水晶（2026-10-23 深夜）
1. **商店新增「我的卡包」页签**：只列已拥有的卡，卡片放大显示，右上角 👗 标记表示已装时装；点卡面直接进干员档案。
2. **金币 → 水晶兑换**：`POST /api/wallet/exchange {gold}`，比例 **2 : 1**，必须是 100 的整数倍（档位 100/500/1000/2000）。
3. **玩家侧充值流水**：`GET /api/wallet/logs?page&size`（带分页）；对局记录 `GET /api/records/my?result=0|1|-1&page&size` 支持胜负平筛选。
4. 抽卡结果卡面也按干员套用当前皮肤。

### 干员介绍 + 卡面统一 + 验证码登录（2026-10-23 晚）
1. **66 位干员档案介绍**：`sql/update-intro.sql`（幂等）补全 `cards.description`，格式含「XX干员」供前端解析职业标签；已并入 init.sql。
2. **卡面全部统一**：1–20 号旧设计图替换为 ArknightsGameResource 官方立绘（`tools/make_cards.py`），66 张全部 180×360；
   6 张专属时装立绘由 560×560 方图重排为「模糊底 + 完整主体」180×360（原图备份在 `public/skins/raw/`）。
3. **干员详情卡面与卡池统一**：CardDetail 大立绘加同款压暗渐变、底部姓名条、稀有度小字；并新增「干员介绍」区块。
4. **验证码登录**：`POST /api/auth/login-by-code {phone, code}`；`/api/auth/sms` 增加 `scene` 参数
   （register=必须未注册 / login、reset=必须已注册），一个手机号只能注册一次（前端提示 + `uk_phone` 唯一键兜底）。
   Login 页新增「验证码登录」页签。
5. **卡组页交互**：单击卡面 = 打开干员档案（可装配时装），右下角 ＋ = 加入卡组；顶部槽位点击可看档案、悬浮 × 移除。
6. **皮肤预览与实际一致**：通用皮肤预览改为「干员自己的卡面 + 该皮肤卡框」（此前误用主题干员立绘，看起来像会换立绘）。

### 密码安全加固 + 账号注销（2026-10-23 深夜）
1. **密码改为每用户随机盐**：`SHA-256(salt + PWD_SALT + 明文)`，salt 为 16 字节随机（32 位 hex），
   存 `users.salt`。老账号（`MD5(PWD_SALT+明文)`）salt 为 NULL，首次登录成功后自动升级（`UserServiceImpl.login`），无需人工重置。
   `JwtUtil` 拆出 `generateSalt() / hashPassword(raw,salt) / legacyHash(raw)`；
   `password` 与 `salt` 都标 `@JsonProperty(WRITE_ONLY)`，接口不回传。
   效果：相同密码在不同账号下哈希不同，拖库也看不出谁用了同一个密码。
### 管理员查看玩家密码（2026-10-24 追加，用户明确要求「能看见」而不是只能重置）
- 哈希不可逆，所以**登录校验仍走哈希**，额外并存一份可逆密文 `users.pwd_cipher`（AES-128-CBC+随机IV，
  见 `util/CryptoUtil.java`）；三个字段 `password/salt/pwdCipher` 全部 `WRITE_ONLY`，不随用户信息下发。
- 密文写入：注册、登录（空值时借登录明文补齐，存量账号登录一次即可被查看）、自助重置、管理员重置。
- 接口 `POST /api/admin/user-password {id}` → `{password, tip}`；无密文返回 tip 提示；拒绝查看管理员。
- 前端：管理后台操作列新增青色「查看密码」按钮 + 明文弹窗 + 复制。
- ⚠️ 论文口径：这是毕业设计演示用的 GM 功能，生产系统不应提供（密钥泄露 = 明文泄露）；
  如需合规化，删掉 `pwd_cipher` 与 `/admin/user-password` 即可，不影响登录。
### 法力模型 + 技能系统（2026-10-24 上午，核心玩法增强）
1. **法力模型**：引擎本就是炉石式（每回合 maxMana+1，上限 10，回合开始回满，出牌扣费）；
   本次统一了**视觉**：卡面费用=蓝色法力水晶、对战页双方水晶条改成菱形晶体 + 💎 x/y 数字。
2. **统一卡面组件** `frontend/src/components/CardFace.vue`：费用水晶 / 攻击 ⚔ / 生命 ❤ /
   关键字角标（🛡嘲讽 ✨圣盾 ⚡冲锋）/ 嘲讽灰白边框 / 召唤失调灰显，三档尺寸 sm·md·lg。
   已接入 Battle、DeckEditor、Shop(卡池+我的卡包)、CardDetail。
   ⚠️ 组件根元素保留 `card` 类，否则各页面 `.card.sel/.attacking/.hit` 动画选择器会失效。
3. **技能系统**（`cards.effect`，多段用 `;` 分隔，单段 `时机:效果:数值`）：
   - 被动关键字：`taunt` 嘲讽（敌方必须优先攻击）/ `charge` 冲锋（跳过召唤失调）/
     `shield` 圣盾（抵消一次伤害后破盾）/ `lifesteal` 吸血（造成伤害同时回血）。
   - 主动：`battlecry` 战吼 / `deathrattle` 亡语，效果含 damage_face / heal / draw / aoe /
     damage_random（随机敌方随从）/ buff_all（友方全体 +N/+N）/ summon（召唤 1/1 战术傀儡）。
   - 引擎实现见 `BattleRoom.resolveAbilities/resolveOne/hasKeyword/removeKeyword`。
4. **66 位干员全部有技能**：`sql/update-effects.sql`（嘲讽18/冲锋13/圣盾6/吸血8/战吼48/亡语20），已并入 init.sql。
   设计原则：重装多嘲讽、先锋多抽牌冲锋、医疗多治疗、术师多AOE、狙击多直伤、辅助多buff、特种多亡语。
5. **验证**：`tools/AbilityCheck.java` 独立自检程序，19 项断言全部通过（嘲讽拦截/圣盾两段/冲锋与召唤失调/
   亡语召唤/战吼buff/吸血/多段解析）。离线环境缺 surefire 插件，用 `javac + java` 直接跑，不用 mvn test。
6. **召唤失调**（2026-10-24 补强）：随从上场当回合不可攻击，下回合解禁；「冲锋」随从上场即可攻击；
   `battlecry:summon` 召唤的衍生物同样失调；机器人 AI 也会跳过失调随从。
   实现：`BattlePlayer` 内 `acted`(已攻击) 与 `sleeping`(失调) 两个 **IdentityHashMap** 实例级集合
   （不能用普通 HashSet —— Card 是 `@Data`，按值 equals，属性被 buff/受伤改掉后判重会失效）。
   快照下发瞬态字段 `canAct`(=非失调&&未攻击) 与 `sick`；前端 💤 角标 + 点击拦截提示。

### 单设备登录(互踢)（2026-10-24 下午）
- `service/SessionService.java`：Redis `login:token:{userId}` 保存**当前唯一有效 token**（TTL 7 天）。
  登录/注册/验证码登录时登记；若已存在不同 token，先通过 STOMP 向 `/topic/user/{uid}` 推 `kicked` 事件
  （**主动下线**，不用等旧设备发下一个请求才失效），再覆盖。
- `JwtInterceptor` 增加会话校验：token 非当前会话 → 401 `账号已在其他设备登录，当前设备已退出`。
  容错：Redis 里没 key 时**自愈**（认定当前 token 有效），避免 Redis 重启误伤在线玩家。
- 新增 `POST /api/auth/logout`：主动退出时清除会话标记，换设备登录不会被提示"被挤下线"。
- 前端：`ws.js` 收到 kicked → `window` 派发 `kicked` 事件 → `App.vue` 统一弹窗 + 清登录态跳登录页；
  `request.js` 401 分支改为展示后端 msg。

### 界面素材：统一使用明日方舟开源素材（去"AI 风"）
- 头像改为**干员立绘**：`/api/user/avatars` 返回 `/cards/1~12.jpg`；CSS `object-position: 50% 12%`
  让 1:2 半身图在圆形头像里露出面部；存量数据用 SQL 从 `/avatars/N.png` 迁移。
- 大厅 6 个快捷入口图标由 emoji 换成 `/ak/` 下的 Q 版形象（pepe/explore/photo/sit/read/fly）。

### 卡组编辑增强 + 出战卡组（2026-10-24）
1. **拖拽更好用**：卡组槽位区 `position: sticky; top:0` **吸顶**，滚到卡牌库底部也能把卡拖进去；
   卡牌库独立滚动（`max-height:46vh`），页面不再整体被撑长。
2. **右击也能加卡**：卡牌库卡面 `@contextmenu` 弹出菜单（加入 1 张 / 加满至上限 / 从卡组移除 1 张 / 查看档案）；
   卡组内槽位**右击直接移除**。菜单做了视口边界收敛，点空白处关闭。
3. **卡牌库筛选**：搜索（名称/档案/技能关键字）、费用、职业（从 description 里正则提取 `XX干员`）、
   4 种排序、「隐藏已加满」。
4. **套牌管理**：卡组 chip 显示 `张数/30`（不足标红）与 👑 出战标记；支持**重命名（双击卡组名）**、
   **复制卡组**、**一键补满**（按费用从低到高补齐 30 张）、**清空**、删除。
5. **卡组统计**：费用曲线柱状图（0…6 / 7+）、平均费用、合计 ⚔/❤、职业分布条。
6. **出战卡组（后端新增）**：`user_decks.is_default`（`sql/upgrade-deck-default.sql`，存量库已执行）；
   `POST /api/decks/default {id}` 设为出战（同用户其它卡组自动取消）；
   `DeckServiceImpl.getDefault()` 改为**优先出战卡组**，没有才取最新一套（匹配对局用）。
   ⚠️ MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`，升级脚本重复执行会报 duplicate column，忽略即可。

2. **管理员注销账号**：`POST /api/admin/delete-user {id, username}`，级联删除
   `user_cards / user_decks / user_skins / user_operator_skin / gacha_log / recharge_log / match_records`，
   并清理 Redis 天梯榜成员与签到/任务进度；返回各表删除行数。
   安全：不能删自己、不能删管理员、**必须回填正确账号名**；前端 Admin.vue「注销」按钮含输入确认 + confirm 二次确认。
   ⚠️ 设计说明：对局记录涉及双方，注销会连同对手历史里的那几场一起移除（真实系统可选「逻辑删除+昵称匿名化」）。

### 此前完成（2026-10-23 上午）
1. **管理后台 4 项功能**：封禁（原因+时限+二次确认）、用户详情抽屉（天梯/余额/最近对局/充值/卡牌解锁数）、三表搜索筛选+后端分页、7 日统计迷你折线图。
2. **后端 6 项优化**：统计/趋势改 SQL 聚合、`CardCache` 60s 卡池缓存、Redis 往返削减（pipeline/单次 rangeWithScores）、`BanMaintenance` 60s 定时解封、7 个高频索引、日志降噪。
3. **前端登录拦截器修复**（`request.js`）：网络错误兜底、403 封禁分支 return 防穿透、alert 改 ElMessage。
4. **注册接口安全加固**：验证码 5 次尝试上限（Redis DECR）、60s 发送冷却锁（SETNX）、新手卡批量 insert（`UserCardMapper.insertBatch`）、并发唯一键兜底、验证码校验抽成 `verifySmsCode`。
5. **管理员角色前端化**：管理员登录后进大厅，Lobby 显示「管理后台」按钮（`.btn-admin`）。
6. **找回密码**：`POST /auth/reset-password`（短信验证码 → 改密），Login 页新增「找回」页签。
7. **管理后台重置玩家密码**：`POST /admin/reset-password`，生成 8 位随机密码、哈希入库、明文返回给管理员转告玩家（禁止重置管理员）。
8. **卡牌扩充 12 → 56 张**：新增 36 位明日方舟干员（id 21–56），数值已入库。
9. **init.sql 同步**：重写为完整版（9 张表结构 + 56 卡 + 6 皮肤 + 3 测试账号 + 新手卡/默认卡组发放），临时库试跑验证通过；顺带统一三个测试账号密码为 `123456`。

---

## 四、未完成 / 待办（按优先级）

### ✅ P0 — 卡面图已全部就绪（2026-10-23 晚完成，不用 AI 生成图）
- **方案**：改用明日方舟**开源客户端素材仓库** `yuanyan3060/ArknightsGameResource`（AGPL-3.0，干员半身像/头像），
  不消耗生图积分，也不是 AI 图。
- **做法**：下载该仓库 `gamedata/excel/character_table.json`（1387 个干员）→ 用卡牌中文名匹配到 `char_xxx` →
  取 `portrait/{char_xxx}_1.png`（半身像）→ Pillow 缩到 640×960 存 `frontend/public/cards/{id}.jpg`（每张约 20–30KB）。
- **结果**：66 张全部有图（1–20 原图 + 21–66 新拉取），`cards.img` 无 NULL，目录共 4.0MB。
- **回填 SQL**：`UPDATE cards SET img = CONCAT('/cards/', id, '.jpg') WHERE id BETWEEN 21 AND 66;`
  改完等 `CardCache` 60s 刷新或重启后端。
- **版权**：素材版权归鹰角网络，已在 Lobby 页脚与 README 注明来源（论文「素材来源」章节可直接引用）。

### ✅ P1 — init.sql 已同步（2026-10-23 完成）
`sql/init.sql` 已重写为「从真实库导出 + 整理」的完整版本，可直接 `source` 一次性建库：
- 10 张表完整结构：`cards / skins / users / user_cards / user_decks / user_skins / user_operator_skin / match_records / gacha_log / recharge_log`
- `users` 表补全 16 个字段（`role/balance/skin_id/status/banned_until/ban_reason/avatar/phone` 等），含 `uk_phone` 唯一键与 7 个高频索引
- 数据：56 张干员卡 + 6 个皮肤 + 3 个测试账号 + 新手卡（id 1–12）自动发放 + 每账号一套默认卡组
- 测试账号密码统一为 `123456`（原库中 `user` 账号哈希与文档不一致，已一并修正）
- 已在临时库 `card_battle_verify` 试跑验证通过（9 表 / 56 卡 / 6 皮肤 / 3 用户 / 36 条 user_cards / 3 套卡组），验证库已删除
- ⚠️ 脚本含 `DROP TABLE`，重复执行会清空对局记录等运行时数据

### 🟡 P2 — README 开发路线中未完成项
- [ ] 卡牌效果系统（嘲讽/战吼/亡语，`cards.effect` 字段已预留）
- [ ] 对战动画 / 卡面图接入优化
- [ ] 断线重连恢复战场 / 回合超时托管
- [ ] 单元测试 + 部署上线
- [ ] （可选）玩家临时密码登录后强制改密

---

## 五、数据库现状（10 张表）

```
cards               66 张（全部有卡面图，素材来自 ArknightsGameResource）
skins               12 个卡框皮肤（6 阵营通用 + 6 干员专属）
users               3 个（含 crystal 水晶字段）
match_records       32 条
recharge_log        3 条
gacha_log / user_cards / user_decks / user_skins
user_operator_skin  每干员独立装配记录 (user_id, operator, skin_id)
```

---

## 六、技术约定与坑（WorkBuddy 必读）

1. **Result 结构**：`{code:0, msg:"success", data:{...}}`，`code≠0` 为业务失败。
2. **异常**：`BizException` 由 `GlobalExceptionHandler` 捕获，返回 `Result.fail(msg)`，但 **HTTP 状态码仍是 200**。
3. **密码**：`User.password` 有 `@JsonProperty(access=WRITE_ONLY)`，**永不序列化到响应**，管理员 API 也拿不到哈希。
4. **缓存**：`CardCache` 60s 刷新一次卡池；改 `cards` 表后要等 60s 或重启后端才生效。
5. **定时任务**：`BanMaintenance` 60s 扫描解封；`MatchService` 定时撮合天梯分差 ≤200 的玩家。
6. **验证码**：`SmsController` 演示环境**直接返回验证码**（不真实发短信）；`verifySmsCode` 内做 5 次尝试 + 60s 冷却。
7. **前端 http 封装**：`frontend/src/api/request.js` 统一注入 token、处理 403 封禁跳转、BizException 弹 ElMessage。
8. **WebSocket**：STOMP；房间广播 `/topic/battle/{roomId}`，个人通知 `/topic/user/{userId}`。

### PowerShell 环境坑（Windows 上验证接口时）
- `Invoke-RestMethod` 对 BizException（HTTP 200 + code≠0）**不抛异常**，需自行解析 `code` 字段。
- `curl` 是 `Invoke-WebRequest` 别名，参数不兼容；用 `Invoke-RestMethod` + `ConvertTo-Json` 或独立 `.ps1`。
- `.ps1` 默认被脚本执行策略拦截，需 `powershell.exe -ExecutionPolicy Bypass -File xxx.ps1`。
- MySQL 命令行密码含 `@`，必须整体加引号：`mysql -u root -p"${DB_PASSWORD}" ...`，末尾加 `2>$null` 抑制 PowerShell 的 stderr 报错。
- 中文脚本内容存 `.ps1` 可能被 PowerShell 5 误判编码，建议脚本里只放英文 prompt。

---

## 七、关键文件索引

| 模块 | 文件 |
|---|---|
| 后端入口 | `src/main/java/com/cardbattle/CardBattleApplication.java` |
| 战斗引擎 | `src/main/java/com/cardbattle/battle/`（BattleRoom 状态机 / BattleManager / BattleWsController） |
| 认证 | `AuthController.java` + `service/impl/UserServiceImpl.java` |
| 管理后台 | `controller/AdminController.java` |
| 卡池缓存 | `service/CardCache.java` |
| 定时解封 | `service/BanMaintenance.java` |
| 签到/任务 | `service/BonusService.java` + `controller/BonusController.java`（/api/bonus） |
| 干员档案页 | `frontend/src/components/CardDetail.vue` |
| 皮肤状态（每人各穿各的） | `frontend/src/api/skin.js`（`operatorSkinMap / cardSkin / cardArt / setOperatorSkin / loadSkinMap`） |
| 密码哈希 | `util/JwtUtil.java`（hashPassword / PWD_SALT） |
| 前端请求封装 | `frontend/src/api/request.js` |
| 前端页面 | `frontend/src/views/`（Login/Lobby/DeckEditor/Battle/Shop/Records/Admin） |
| 建库脚本（已同步 2026-10-23） | `sql/init.sql` |
| 卡面图目录 | `frontend/public/cards/`（1–66 全部就绪） |
| 官方时装立绘 | `frontend/public/skins/`（6 张，来自 ArknightsGameResource 的 skin/ 目录） |
| 增量升级脚本 | `sql/upgrade-2026-10-23.sql`（存量库执行，含 user_operator_skin） |
