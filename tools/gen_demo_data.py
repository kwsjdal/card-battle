# -*- coding: utf-8 -*-
"""生成一套用于录屏演示的对局数据(仅影响 user_id=1 李家宏)。

产出 tools/demo-data.sql:
  1. 为李家宏补 2 套卡组(共 3 套), 让"卡组胜率"面板有多行对比
  2. 重建 25 局战报: 15 胜 10 负, 分散在最近 13 天, 每局带完整时间线(回放/分享码可播)
  3. 天梯分设为 1450(铂金), 胜负场与战报条数一致
执行: python tools/gen_demo_data.py && mysql ... < tools/demo-data.sql
"""
import json
import random
import subprocess
import datetime

MYSQL = ['mysql', '-uroot', '-p${DB_PASSWORD}', '--default-character-set=utf8mb4',
         '-N', '-B', 'card_battle']
UID = 1
NICK = '李家宏'
RATING = 1450

# 对手: (-1=机器人, 其余为真实测试账号) —— 有真人也有陪练, 画面更像真实使用痕迹
OPPONENTS = [(-1, '机器人陪练'), (7, 'USA'), (12, '星野'), (9001, '夜航')]


def q(sql):
    out = subprocess.run(MYSQL + ['-e', sql], capture_output=True)
    txt = out.stdout.decode('utf-8', 'ignore')
    return [line.split('\t') for line in txt.strip().splitlines() if line.strip()]


def main():
    # 卡池与已解锁卡
    cards = {int(r[0]): (int(r[1]), r[2]) for r in q('select id,cost,name from cards')}
    owned = [int(r[0]) for r in q('select distinct card_id from user_cards where user_id=%d' % UID)]
    rnd = random.Random(20261030)

    # ---------- 1. 两套新卡组 ----------
    cheap = sorted([c for c in owned if cards[c][0] <= 3], key=lambda c: cards[c][0])
    heavy = sorted([c for c in owned if cards[c][0] >= 4], key=lambda c: -cards[c][0])
    rush_deck = [c for c in cheap[:14] for _ in (0, 1)][:28] + cheap[14:16]
    tank_deck = ([c for c in heavy[:13] for _ in (0, 1)][:26] + cheap[:4])[:30]
    deck13_ids = [int(x) for x in q('select card_ids from user_decks where id=13')[0][0]
                  .strip('[]').split(',') if x.strip()]
    assert len(rush_deck) == 30 and len(tank_deck) == 30, (len(rush_deck), len(tank_deck))

    decks = [
        (13, '我的第一套卡组', deck13_ids),
        (101, '近卫突袭流', rush_deck),
        (102, '重装防守流', tank_deck),
    ]

    # ---------- 2. 25 局战报 ----------
    # (卡组下标, 场次, 胜场)
    plan = [(0, 10, 7), (1, 8, 4), (2, 7, 4)]
    games = []
    for di, total, wins in plan:
        flags = [1] * wins + [0] * (total - wins)
        rnd.shuffle(flags)
        games += [(di, f) for f in flags]
    rnd.shuffle(games)

    # 时间: 最近 13 天, 每天 1~3 局, 从早到晚
    base = datetime.datetime(2026, 10, 17, 20, 10, 0)
    times = []
    for i in range(len(games)):
        d = i * 13 // len(games)              # 0..12
        t = base + datetime.timedelta(days=d, hours=rnd.randint(0, 3),
                                      minutes=rnd.randint(0, 59))
        times.append(t)
    times.sort()

    def timeline(my_deck, opp_id, opp_nick, win, rounds):
        """造一条可信的战斗日志: 双方轮流上怪、互殴, 最后一击分出胜负"""
        ev = [{'turn': 1, 'actor': 'system', 'text': '战斗开始! %s 先手' % NICK}]
        deck = ([cards[c][1] for c in my_deck] * 2)[:40]
        foe_deck = ([cards[c][1] for c in rnd.sample(sorted(cards), 20)] * 2)[:40]
        mine, theirs = list(deck), list(foe_deck)
        for turn in range(1, rounds + 1):
            me_now = (turn % 2 == 1)
            actor = str(UID) if me_now else str(opp_id)
            mana = min(10, (turn + 1) // 2)
            ev.append({'turn': turn, 'actor': actor, 'text': '回合%d 开始, 法力=%d/%d' % (turn, mana, mana)})
            pool = mine if me_now else theirs
            for _ in range(rnd.randint(1, 2)):
                if not pool:
                    break
                name = pool.pop(rnd.randrange(len(pool)))
                ev.append({'turn': turn, 'actor': actor, 'text': '打出了 ' + name})
            if turn >= 3 and rnd.random() < 0.75:
                a = (mine if me_now else theirs)
                b = (theirs if me_now else mine)
                if a and b:
                    ev.append({'turn': turn, 'actor': actor,
                               'text': '%s 攻击了 %s, 造成%d点伤害, 受到%d点反击'
                                       % (a[0], b[0], rnd.randint(2, 6), rnd.randint(1, 4))})
        winner = UID if win else opp_id
        last_turn = rounds
        ev.append({'turn': last_turn, 'actor': str(winner),
                   'text': '%s 直接攻击对方英雄, 造成%d点伤害' % (NICK if win else opp_nick, rnd.randint(4, 9))})
        ev.append({'turn': last_turn, 'actor': 'system', 'text': '游戏结束! 胜者=%s' % winner})
        return ev

    rows = []
    for idx, ((di, win), ts) in enumerate(zip(games, times)):
        opp_id, opp_nick = OPPONENTS[idx % len(OPPONENTS)] if idx % 3 else OPPONENTS[0]
        rounds = rnd.randint(8, 18)
        my_deck = deck13_ids if di == 0 else decks[di][2]
        detail = json.dumps(timeline(my_deck, opp_id, opp_nick, win, rounds), ensure_ascii=False)
        detail_sql = detail.replace('\\', '\\\\').replace("'", "''")
        rows.append((idx, di, opp_id, win, rounds, ts, detail_sql))

    sql = []
    sql.append('-- 录屏演示数据(user_id=1 李家宏), 由 tools/gen_demo_data.py 生成')
    sql.append('SET NAMES utf8mb4;')
    sql.append('DELETE FROM match_records WHERE player1_id=%d OR player2_id=%d;' % (UID, UID))
    # 2 套新卡组(固定 id, 便于战报直接引用; 可重复执行)
    sql.append("DELETE FROM user_decks WHERE user_id=%d AND id IN (101,102);" % UID)
    for did, name, ids in ((101, '近卫突袭流', rush_deck), (102, '重装防守流', tank_deck)):
        sql.append("INSERT INTO user_decks (id,user_id,name,card_ids,is_default,create_time) VALUES "
                   "(%d,%d,'%s','%s',0,NOW());" % (did, UID, name, json.dumps(ids, ensure_ascii=False)))
    # 战报
    for idx, di, opp_id, win, rounds, ts, detail_sql in rows:
        winner = UID if win else opp_id
        sql.append(
            "INSERT INTO match_records (room_id,player1_id,p1_deck_id,player2_id,p2_deck_id,"
            "winner_id,rounds,detail,create_time) VALUES "
            "('demo%03d',%d,%d,%d,NULL,%d,%d,'%s','%s');"
            % (idx + 1, UID, decks[di][0], opp_id, winner, rounds, detail_sql,
               ts.strftime('%Y-%m-%d %H:%M:%S')))
    # 计数与天梯分
    wins = sum(1 for r in rows if r[3] == 1)
    sql.append("UPDATE users SET rating=%d, win_count=%d, lose_count=%d WHERE id=%d;"
               % (RATING, wins, len(rows) - wins, UID))

    open('tools/demo-data.sql', 'w', encoding='utf-8').write('\n'.join(sql) + '\n')
    print('生成 %d 局: %d 胜 %d 负, 天梯分 %d' % (len(rows), wins, len(rows) - wins, RATING))
    print('产出: tools/demo-data.sql')


if __name__ == '__main__':
    main()
