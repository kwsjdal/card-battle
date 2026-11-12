import com.cardbattle.battle.BattlePlayer;
import com.cardbattle.battle.BattleRoom;
import com.cardbattle.common.BizException;
import com.cardbattle.entity.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 技能系统自检: 嘲讽/圣盾/冲锋/吸血/亡语召唤/战吼buff/多段解析 */
public class AbilityCheck {

    static int pass = 0, fail = 0;

    static Card card(String name, int cost, int atk, int hp, String effect) {
        Card c = new Card();
        c.setId(System.nanoTime());
        c.setName(name);
        c.setCost(cost);
        c.setAttack(atk);
        c.setHp(hp);
        c.setRarity(1);
        c.setEffect(effect);
        c.setImg("/cards/1.jpg");
        return c;
    }

    static BattlePlayer player(Long id, String nick, List<Card> deck) {
        BattlePlayer p = new BattlePlayer();
        p.setUserId(id);
        p.setNickname(nick);
        p.setDeck(new ArrayList<>(deck));
        return p;
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  [通过] " + name); }
        else { fail++; System.out.println("  [失败] " + name + " -> " + detail); }
    }

    public static void main(String[] args) {
        // 1. 嘲讽: 禁止直击英雄, 必须先打嘲讽随从
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t1", a, b);
            r.start();
            b.getBoard().add(card("米格鲁", 2, 1, 4, "taunt:0:0"));
            a.getBoard().add(card("玫兰莎", 1, 2, 1, "charge:0:0"));
            a.getActed().clear();
            boolean blocked = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("嘲讽"); }
            check("嘲讽: 拦截直击英雄", blocked, "未被拦截");
            boolean ok = true;
            try { r.attack(1L, 0, 0); } catch (Exception e) { ok = false; }
            check("嘲讽: 允许攻击嘲讽随从", ok, "攻击被拒绝");
        }

        // 2. 圣盾: 抵消一次伤害后破盾
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t2", a, b);
            r.start();
            Card shielded = card("年", 8, 8, 8, "taunt:0:0;shield:0:0");
            b.getBoard().add(shielded);
            a.getBoard().add(card("玫兰莎", 1, 5, 20, "charge:0:0"));   // 血厚一点, 撑过反击以便测第二次
            a.getActed().clear();
            r.attack(1L, 0, 0);
            check("圣盾: 首次伤害被抵消", shielded.getHp() == 8, "血量=" + shielded.getHp());
            check("圣盾: 破盾后移除关键字", !shielded.getEffect().contains("shield"), "effect=" + shielded.getEffect());
            a.getActed().clear();
            r.attack(1L, 0, 0);
            check("圣盾: 第二次正常受伤", shielded.getHp() == 3, "血量=" + shielded.getHp());
        }

        // 3. 冲锋 vs 召唤失调
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t3", a, b);
            r.start();
            a.setMana(10); a.setMaxMana(10);
            a.getHand().add(card("玫兰莎", 1, 2, 1, "charge:0:0"));
            r.playCard(1L, a.getHand().size() - 1);
            check("冲锋: 上场即可攻击(无召唤失调)", !a.getSleeping().contains(a.getBoard().get(0)), "被判为召唤失调");
            a.getHand().add(card("克洛丝", 1, 1, 2, ""));
            r.playCard(1L, a.getHand().size() - 1);
            check("普通随从: 上场进入召唤失调", a.getSleeping().contains(a.getBoard().get(1)), "未被标记 sleeping");
        }

        // 3b. 召唤失调完整时序: 上场当回合不可攻击 -> 下个回合可攻击 -> 每回合限一次
        {
            BattlePlayer a = player(1L, "A", Arrays.asList(card(" filler", 1, 1, 1, "")));
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t3b", a, b);
            r.start();
            a.setMana(10); a.setMaxMana(10);
            a.getHand().add(card("克洛丝", 1, 3, 2, ""));
            r.playCard(1L, a.getHand().size() - 1);
            boolean blocked = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("召唤失调"); }
            check("召唤失调: 上场当回合攻击被拒", blocked, "未被拦截");
            r.endTurn(1L);            // 交给 B
            r.endTurn(2L);            // 回到 A 的新回合
            boolean ok = true;
            try { r.attack(1L, 0, -1); } catch (Exception e) { ok = false; }
            check("召唤失调: 下个回合可以攻击", ok, "仍被拦截");
            boolean twice = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { twice = e.getMessage().contains("已攻击过"); }
            check("召唤失调: 每回合仍限攻击一次", twice, "未限制重复攻击");
        }

        // 3c. 被 buff/受伤后仍保持召唤失调(实例级判重, 不因属性变化失效)
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t3c", a, b);
            r.start();
            a.setMana(10); a.setMaxMana(10);
            a.getHand().add(card("克洛丝", 1, 2, 2, ""));
            r.playCard(1L, a.getHand().size() - 1);
            a.getHand().add(card("猎蜂", 4, 4, 4, "battlecry:buff_all:1"));
            r.playCard(1L, a.getHand().size() - 1);       // 战吼给克洛丝 +1/+1, 属性已变
            boolean blocked = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("召唤失调"); }
            check("召唤失调: 被 buff 后仍不可攻击", blocked, "属性变化后判定失效");
        }

        // 3d. 召唤出的衍生物同样召唤失调
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t3d", a, b);
            r.start();
            a.setMana(10); a.setMaxMana(10);
            a.getHand().add(card("梅尔", 4, 3, 3, "battlecry:summon:1"));
            r.playCard(1L, a.getHand().size() - 1);
            boolean blocked = false;
            try { r.attack(1L, 1, -1); } catch (BizException e) { blocked = e.getMessage().contains("召唤失调"); }
            check("召唤失调: 衍生物上场当回合不可攻击", blocked, "衍生物可立即攻击");
        }

        // 4. 亡语召唤衍生物
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t4", a, b);
            r.start();
            b.getBoard().add(card("梅尔", 4, 3, 1, "deathrattle:summon:2"));
            a.getBoard().add(card("玫兰莎", 1, 9, 9, "charge:0:0"));
            a.getActed().clear();
            r.attack(1L, 0, 0);
            check("亡语: 召唤 2 个衍生物", b.getBoard().size() == 2, "场上=" + b.getBoard().size());
            check("亡语: 衍生物为战术傀儡", !b.getBoard().isEmpty() && "战术傀儡".equals(b.getBoard().get(0).getName()), "名字不符");
        }

        // 5. 战吼群体 buff
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t5", a, b);
            r.start();
            a.setMana(10);
            a.getBoard().add(card("小兵", 1, 2, 2, ""));
            a.getHand().add(card("猎蜂", 4, 4, 4, "battlecry:buff_all:1"));
            r.playCard(1L, a.getHand().size() - 1);
            check("战吼: 友方随从 +1/+1", a.getBoard().get(0).getAttack() == 3 && a.getBoard().get(0).getHp() == 3,
                    "攻=" + a.getBoard().get(0).getAttack() + " 血=" + a.getBoard().get(0).getHp());
        }

        // 6. 吸血
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t6", a, b);
            r.start();
            a.setHp(10);
            a.getBoard().add(card("斯卡蒂", 7, 5, 5, "charge:0:0;lifesteal:0:0"));
            a.getActed().clear();
            r.attack(1L, 0, -1);
            check("吸血: 攻击英雄后回血", a.getHp() == 15, "HP=" + a.getHp());
            check("吸血: 敌方英雄正常掉血", b.getHp() == 25, "敌方HP=" + b.getHp());
        }

        // 7. 多段效果解析(冲锋 + 战吼抽牌)
        {
            BattlePlayer a = player(1L, "A", Arrays.asList(card("A1", 1, 1, 1, ""), card("A2", 1, 1, 1, "")));
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t7", a, b);
            r.start();
            a.setMana(10);
            int before = a.getHand().size();
            a.getHand().add(card("夜刀", 3, 4, 1, "charge:0:0;battlecry:draw:1"));
            r.playCard(1L, a.getHand().size() - 1);
            check("多段: 战吼抽牌生效", a.getHand().size() == before, "手牌 " + before + " -> " + a.getHand().size());
            check("多段: 冲锋仍可攻击(无召唤失调)", !a.getSleeping().contains(a.getBoard().get(0)), "被判召唤失调");
        }

        // 8. 突袭: 登场当回合可打随从, 但不能打脸; 下回合解锁
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t8", a, b);
            r.start();
            a.setMana(10);
            a.getHand().add(card("卡缇", 1, 2, 2, "rush:0:0"));
            r.playCard(1L, 0);
            check("突袭: 上场不进入召唤失调", !a.getSleeping().contains(a.getBoard().get(0)), "被判召唤失调");
            b.getBoard().add(card("星熊", 6, 1, 8, ""));   // 无嘲讽: 便于后续验证打脸
            boolean blocked = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("突袭"); }
            check("突袭: 登场当回合禁止打脸", blocked, "未被拦截");
            boolean okAtk = true;
            try { r.attack(1L, 0, 0); } catch (Exception e) { okAtk = false; }
            check("突袭: 登场当回合可攻击随从", okAtk, "攻击随从被拒绝");
            // 过一回合: 突袭锁定应被清空(同一张卡缇仍在场, 此时应能打脸)
            r.endTurn(1L);   // A 结束 -> B
            r.endTurn(2L);   // B 结束 -> A, 触发 beginTurn 清空 rushLock
            a.getActed().clear();
            boolean hit = true;
            try { r.attack(1L, 0, -1); } catch (Exception e) { hit = false; }
            check("突袭: 下一回合解除打脸限制", hit, "仍被拦截");
        }

        // 9. 风怒: 每回合可攻击两次, 第三次被拒
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t9", a, b);
            r.start();
            a.getBoard().add(card("流星", 4, 5, 3, "windfury:0:0"));
            a.getActed().clear();
            r.attack(1L, 0, -1);
            boolean can2 = !a.getActed().contains(a.getBoard().get(0));
            check("风怒: 第一次攻击后仍可再攻", can2, "第一次就被锁定");
            r.attack(1L, 0, -1);
            check("风怒: 两次攻击共造成 10 点伤害", b.getHp() == 20, "敌方HP=" + b.getHp());
            boolean blocked = false;
            try { r.attack(1L, 0, -1); } catch (BizException e) { blocked = e.getMessage().contains("已攻击过"); }
            check("风怒: 第三次攻击被拒", blocked, "未限制攻击次数");
        }

        // 10. 剧毒: 对随从造成伤害即摧毁, 双向生效
        {
            BattlePlayer a = player(1L, "A", Arrays.asList());
            BattlePlayer b = player(2L, "B", Arrays.asList());
            BattleRoom r = new BattleRoom("t10", a, b);
            r.start();
            a.getBoard().add(card("夜魔", 2, 1, 1, "poisonous:0:0"));
            b.getBoard().add(card("星熊", 6, 0, 8, "taunt:0:0"));
            a.getActed().clear();
            r.attack(1L, 0, 0);
            check("剧毒: 1点伤害摧毁8血随从", b.getBoard().isEmpty(), "敌方场上=" + b.getBoard().size());

            BattlePlayer c = player(3L, "C", Arrays.asList());
            BattlePlayer d = player(4L, "D", Arrays.asList());
            BattleRoom r2 = new BattleRoom("t10b", c, d);
            r2.start();
            c.getBoard().add(card("米格鲁", 2, 1, 5, ""));
            d.getBoard().add(card("狮蝎", 3, 2, 3, "poisonous:0:0"));
            c.getActed().clear();
            r2.attack(3L, 0, 0);
            check("剧毒: 反击方毒素同样致死", c.getBoard().isEmpty(), "我方场上=" + c.getBoard().size());
        }

        System.out.println("\n结果: 通过 " + pass + " / 失败 " + fail);
        if (fail > 0) System.exit(1);
    }
}
