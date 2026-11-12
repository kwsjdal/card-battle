import com.cardbattle.battle.BattlePlayer;
import com.cardbattle.battle.BattleRoom;
import com.cardbattle.common.BizException;
import com.cardbattle.entity.Card;
import com.cardbattle.util.DeckCodeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 卡组分享码 + 英雄技能 自检
 * 运行: java -cp "target/classes;tools/out" DeckCodeCheck
 */
public class DeckCodeCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  [通过] " + name); }
        else { fail++; System.out.println("  [失败] " + name + " -> " + detail); }
    }

    /** 由 (id, 张数) 造 30 张卡组 */
    static List<Long> ids(Object... pairs) {
        List<Long> r = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            long id = ((Number) pairs[i]).longValue();
            int n = ((Number) pairs[i + 1]).intValue();
            for (int k = 0; k < n; k++) r.add(id);
        }
        return r;
    }

    public static void main(String[] args) {
        System.out.println("=== 卡组分享码自检 ===");
        // 1. 编解码往返
        {
            List<Long> deck = ids(1, 3, 2, 3, 7, 2, 12, 4, 33, 5, 40, 6, 55, 7);
            String code = DeckCodeUtil.encode(deck);
            Map<Long, Integer> back = DeckCodeUtil.decode(code);
            boolean ok = back.size() == 7 && back.get(1L) == 3 && back.get(55L) == 7;
            check("编码->解码 内容一致", ok, "解码结果=" + back);
            System.out.println("      生成码: " + code + "  (长度 " + code.length() + ")");
        }
        // 2. 同一副卡组(顺序不同)生成同一串码
        {
            List<Long> a = ids(5, 2, 9, 3, 20, 25);
            List<Long> b = new ArrayList<>();
            b.addAll(ids(20, 25)); b.addAll(ids(9, 3)); b.addAll(ids(5, 2));
            check("顺序无关(同一卡组同一码)", DeckCodeUtil.encode(a).equals(DeckCodeUtil.encode(b)),
                    DeckCodeUtil.encode(a) + " vs " + DeckCodeUtil.encode(b));
        }
        // 3. 空卡组拒绝
        {
            boolean threw = false;
            try { DeckCodeUtil.encode(new ArrayList<>()); } catch (BizException e) { threw = true; }
            check("空卡组拒绝编码", threw, "未抛异常");
        }
        // 4. 非法前缀
        {
            boolean threw = false;
            try { DeckCodeUtil.decode("XX1ABCABCABCA"); } catch (BizException e) { threw = true; }
            check("非法前缀被拒绝", threw, "未抛异常");
        }
        // 5. 抄漏一个字符 -> 校验位/长度能发现
        {
            List<Long> deck = ids(3, 2, 8, 3, 15, 4, 22, 5, 31, 6, 44, 10);
            String code = DeckCodeUtil.encode(deck);
            String broken = code.substring(0, code.length() - 2) + code.substring(code.length() - 1);
            boolean threw = false;
            try { DeckCodeUtil.decode(broken); } catch (BizException e) { threw = true; }
            check("漏抄字符被校验位拦截", threw, "错误校验通过(校验强度不足)");
        }
        // 6. 非 30 张拒绝
        {
            boolean threw = false;
            try { DeckCodeUtil.decode(DeckCodeUtil.encode(ids(1, 2, 2, 3))); } catch (BizException e) {
                threw = true;
            }
            check("不足30张拒绝解码", threw, "未抛异常");
        }
        // 7. 大小写与空格兼容
        {
            List<Long> deck = ids(6, 3, 11, 3, 26, 4, 37, 5, 48, 6, 60, 9);
            String code = DeckCodeUtil.encode(deck);
            String dirty = " " + code.substring(0, 4) + " " + code.substring(4) + "\n";
            Map<Long, Integer> back = DeckCodeUtil.decode(dirty);
            check("含空格/换行仍可解析", back.size() == 6, "解析结果=" + back);
        }

        System.out.println("=== 英雄技能自检 ===");
        {
            BattlePlayer a = new BattlePlayer(); a.setUserId(1L); a.setNickname("A");
            a.setDeck(new ArrayList<>());
            BattlePlayer b = new BattlePlayer(); b.setUserId(2L); b.setNickname("B");
            b.setDeck(new ArrayList<>());
            BattleRoom r = new BattleRoom("h1", a, b);
            r.start();
            a.setMana(2);
            // 8. 打脸 1 点
            int hpBefore = b.getHp();
            r.heroPower(1L, -1);
            check("英雄技能: 对敌方英雄 1 点伤害", b.getHp() == hpBefore - 1, "hp=" + b.getHp());
            check("英雄技能: 扣 2 费", a.getMana() == 0, "mana=" + a.getMana());
            // 9. 每回合限一次
            a.setMana(2);
            boolean threw = false;
            try { r.heroPower(1L, -1); } catch (BizException e) { threw = e.getMessage().contains("已经使用过"); }
            check("英雄技能: 每回合限一次", threw, "未拦截");
            // 10. 费用不足(单独房间, 排除"已使用过"干扰)
            {
                BattlePlayer a2 = new BattlePlayer(); a2.setUserId(1L); a2.setNickname("A2");
                a2.setDeck(new ArrayList<>());
                BattlePlayer b2 = new BattlePlayer(); b2.setUserId(2L); b2.setNickname("B2");
                b2.setDeck(new ArrayList<>());
                BattleRoom r2 = new BattleRoom("h2", a2, b2);
                r2.start();
                boolean noMana = false;
                try { r2.heroPower(1L, -1); } catch (BizException e) { noMana = e.getMessage().contains("法力值不足"); }
                check("英雄技能: 费用不足拦截", noMana, "未拦截");
            }
            // 11. 新回合恢复可用 + 点杀 1 血随从
            a.setMana(2);
            Card m = new Card(); m.setId(99L); m.setName("靶子"); m.setCost(1);
            m.setAttack(1); m.setHp(1); m.setEffect(""); m.setRarity(1); m.setImg("/cards/1.jpg");
            b.getBoard().add(m);
            r.endTurn(1L);   // 轮到 B
            r.endTurn(2L);   // 回到 A, 新回合技能恢复
            a.setMana(2);
            long cur = r.getPlayers()[r.getCurrent()].getUserId();
            System.out.println("      (当前行动方=" + cur + ", 技能已用=" + a.isHeroPowerUsed() + ")");
            if (cur == 1L) {
                r.heroPower(1L, 0);
                check("英雄技能: 点杀 1 血随从", b.getBoard().isEmpty(), "场上还有 " + b.getBoard().size());
            } else {
                check("英雄技能: 点杀 1 血随从", false, "回合切换异常");
            }
        }
        // 12. 嘲讽在场时技能不受限制(技能是法术类, 可越过嘲讽直接打脸/点随从)
        {
            BattlePlayer ta = new BattlePlayer(); ta.setUserId(1L); ta.setNickname("TA");
            ta.setDeck(new ArrayList<>());
            BattlePlayer tb = new BattlePlayer(); tb.setUserId(2L); tb.setNickname("TB");
            tb.setDeck(new ArrayList<>());
            BattleRoom rt = new BattleRoom("h3", ta, tb);
            rt.start();
            Card wall = new Card(); wall.setId(98L); wall.setName("嘲讽墙"); wall.setCost(2);
            wall.setAttack(1); wall.setHp(5); wall.setEffect("taunt:0:0");
            wall.setRarity(1); wall.setImg("/cards/1.jpg");
            tb.getBoard().add(wall);
            ta.setMana(2);
            int hpB = tb.getHp();
            try {
                rt.heroPower(1L, -1);
                check("英雄技能: 越嘲讽打脸", tb.getHp() == hpB - 1, "hp=" + tb.getHp());
            } catch (BizException e) {
                check("英雄技能: 越嘲讽打脸", false, "被拦: " + e.getMessage());
            }
            // 对照: 同样情形下随从攻击打脸应被嘲讽拦截
            {
                BattlePlayer ca = new BattlePlayer(); ca.setUserId(1L); ca.setNickname("CA");
                ca.setDeck(new ArrayList<>());
                BattlePlayer cb = new BattlePlayer(); cb.setUserId(2L); cb.setNickname("CB");
                cb.setDeck(new ArrayList<>());
                BattleRoom rc = new BattleRoom("h4", ca, cb);
                rc.start();
                Card w2 = new Card(); w2.setId(97L); w2.setName("嘲讽墙2"); w2.setCost(2);
                w2.setAttack(1); w2.setHp(5); w2.setEffect("taunt:0:0"); w2.setRarity(1);
                w2.setImg("/cards/1.jpg");
                cb.getBoard().add(w2);
                Card atk = new Card(); atk.setId(96L); atk.setName("小兵"); atk.setCost(1);
                atk.setAttack(1); atk.setHp(3); atk.setEffect(""); atk.setRarity(1);
                atk.setImg("/cards/1.jpg");
                ca.getBoard().add(atk);
                boolean blocked = false;
                try { rc.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("嘲讽"); }
                check("对照: 随从攻击被嘲讽拦截", blocked, "未拦截");
            }
        }

        System.out.println("=== 疲劳机制自检 ===");
        {
            BattlePlayer fa = new BattlePlayer(); fa.setUserId(1L); fa.setNickname("FA");
            fa.setDeck(new ArrayList<>());
            BattlePlayer fb = new BattlePlayer(); fb.setUserId(2L); fb.setNickname("FB");
            fb.setDeck(new ArrayList<>());
            BattleRoom rf = new BattleRoom("h5", fa, fb);
            rf.start();   // 先手进入 beginTurn, 牌库空 -> 疲劳第1层
            long first = rf.getPlayers()[rf.getCurrent()].getUserId();
            int hpAfterStart = first == 1L ? fa.getHp() : fb.getHp();
            check("疲劳: 首次空抽扣1点血", hpAfterStart == 29, "hp=" + hpAfterStart);
            check("疲劳: 层数记为1", (first == 1L ? fa.getFatigue() : fb.getFatigue()) == 1,
                    "fatigue=" + (first == 1L ? fa.getFatigue() : fb.getFatigue()));

            // 再过 4 个回合: 双方各再抽 2 次, 应为 2 点 + 3 点
            for (int i = 0; i < 4; i++) {
                if (rf.getPhase() == BattleRoom.Phase.GAME_OVER) break;
                rf.endTurn(rf.getPlayers()[rf.getCurrent()].getUserId());
            }
            check("疲劳: 层数递增到3", Math.max(fa.getFatigue(), fb.getFatigue()) == 3,
                    "a=" + fa.getFatigue() + " b=" + fb.getFatigue());
            // 累计伤害 = 1 + 2 + 3 = 6
            check("疲劳: 累计扣血正确(1+2+3=6)", 30 - Math.min(fa.getHp(), fb.getHp()) == 6,
                    "a=" + fa.getHp() + " b=" + fb.getHp());

            // 继续过回合, 疲劳递增最终必然致死 -> 对局自然收敛, 不用等 45 分钟上限
            int guard = 0;
            while (rf.getPhase() != BattleRoom.Phase.GAME_OVER && guard++ < 60) {
                rf.endTurn(rf.getPlayers()[rf.getCurrent()].getUserId());
            }
            check("疲劳: 对局最终自然结束", rf.getPhase() == BattleRoom.Phase.GAME_OVER,
                    "共" + guard + "回合仍未结束");
            check("疲劳: 结束时双方血量<=0或一方胜出",
                    rf.getWinnerId() != null && (fa.getHp() <= 0 || fb.getHp() <= 0),
                    "winner=" + rf.getWinnerId() + " a=" + fa.getHp() + " b=" + fb.getHp());
        }

        // 12. 观战脱敏: 观战快照只给手牌张数, 绝不下发任何一方的手牌内容
        {
            BattlePlayer sa = new BattlePlayer(); sa.setUserId(1L); sa.setNickname("SA");
            sa.setDeck(new ArrayList<>(List.of(
                    card(77L, "我方手牌", 1, 1, 1, ""), card(78L, "我方手牌2", 2, 2, 2, ""))));
            BattlePlayer sb = new BattlePlayer(); sb.setUserId(2L); sb.setNickname("SB");
            sb.setDeck(new ArrayList<>(List.of(card(79L, "对手手牌", 1, 1, 1, ""))));
            BattleRoom rs = new BattleRoom("h6", sa, sb);
            rs.start();

            Map<String, Object> sv = rs.spectatorView();
            Map<String, Object> v1 = (Map<String, Object>) sv.get("p1");
            Map<String, Object> v2 = (Map<String, Object>) sv.get("p2");
            check("观战: 我方只给手牌张数不给牌面", v1.containsKey("handCount") && !v1.containsKey("hand"),
                    "keys=" + v1.keySet());
            check("观战: 对手同样不给牌面", v2.containsKey("handCount") && !v2.containsKey("hand"),
                    "keys=" + v2.keySet());

            // 对照: 玩家自己的视角必须能看到自己的手牌(否则观战脱敏会误伤正常功能)
            Map<String, Object> my = rs.snapshotFor(1L);
            Map<String, Object> mine = (Map<String, Object>) my.get("p1");
            check("对照: 玩家自己仍能看到手牌", mine.containsKey("hand"), "keys=" + mine.keySet());
        }

        System.out.println();
        System.out.println("结果: " + pass + " 通过 / " + fail + " 失败");
        if (fail > 0) System.exit(1);
    }

    /** 构造一张测试卡(tool 内复用) */
    private static Card card(long id, String name, int cost, int atk, int hp, String effect) {
        Card c = new Card();
        c.setId(id); c.setName(name); c.setCost(cost); c.setAttack(atk); c.setHp(hp);
        c.setEffect(effect); c.setRarity(1); c.setImg("/cards/1.jpg");
        return c;
    }
}
