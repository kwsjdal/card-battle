# -*- coding: utf-8 -*-
"""录屏前的全链路冒烟自检(只读为主, 不改数据)。

口令不再写在命令行里, 而是从 ws-e2e.cjs 的常量读取, 避免在 shell 历史中明文暴露。
用法: python tools/smoke-check.py

⚠ 重要: 本项目是"单设备登录"——本脚本会用 SMOKE_ACCOUNT(默认 ljh 主号)登录一次,
   **这会把你正在浏览器里使用的同一账号踢下线**。录屏/正用主号时不要跑它;
   需要跑就用 SMOKE_ACCOUNT=testbot 换测试号(部分断言会因测试号没有战绩而失败)。
"""
import json
import os
import re
import sys
import urllib.request
import urllib.error

BASE = 'http://localhost:8080'
WEB = 'http://localhost:5173'
path = __file__
PWD = re.search(r"const PWD = '([^']+)'", open('tools/ws-e2e.cjs', encoding='utf-8').read()).group(1)
ACCOUNT = os.environ.get('SMOKE_ACCOUNT', 'ljh')
if ACCOUNT == 'ljh':
    print('⚠ 将用主号 ljh 登录做自检 —— 若主号此时正在浏览器/手机上使用, 会被挤下线。\n')

ok = fail = 0


def ask(url, token=None, method='GET', body=None):
    req = urllib.request.Request(BASE + url if url.startswith('/api') else url, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', 'Bearer ' + token)
    data = json.dumps(body).encode() if body else None
    try:
        with urllib.request.urlopen(req, data, timeout=10) as r:
            return r.getcode(), json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode('utf-8', 'ignore') or '{}')


def check(name, cond, detail=''):
    global ok, fail
    if cond:
        ok += 1
        print('  [通过] ' + name)
    else:
        fail += 1
        print('  [失败] ' + name + ' -> ' + str(detail))


def page(p):
    try:
        with urllib.request.urlopen(WEB + p, timeout=8) as r:
            return r.getcode()
    except Exception:
        return 0


print('=== 录屏前冒烟自检 ===')

print('-- 页面路由 --')
for p in ('/', '/lobby', '/battle', '/records', '/stats', '/shop', '/spectate', '/deck'):
    check('页面 ' + p, page(p) == 200, page(p))

print('-- 登录与生涯概览 --')
code, d = ask('/api/auth/login', method='POST', body={'username': ACCOUNT, 'password': PWD})
check('账号登录(' + ACCOUNT + ')', code == 200 and d.get('code') == 0, d.get('msg'))
tok = d['data']['token']
code, d = ask('/api/user/stats', tok)
s = d.get('data', {})
u = s.get('user', {})
check('概览可读', code == 200 and (u.get('nickname') == '李家宏' if ACCOUNT == 'ljh' else bool(u.get('nickname'))), u)
print('    排名%s | %s分 | %s胜%s负 | 胜率%s%%' % (u.get('rank'), u.get('rating'), u.get('win'),
                                                 u.get('lose'), u.get('winRate')))
ds = s.get('deckStats', [])
check('卡组胜率有数据', len(ds) >= 3, ds)
for x in ds:
    print('    %-10s %s%%  %s胜%s负 共%s局' % (x['name'], x['winRate'], x['win'], x['lose'], x['total']))
rt = s.get('ratingTrend', [])
check('天梯走势有数据', len(rt) >= 2, len(rt))
print('    走势 %d 点: %s -> %s' % (len(rt), rt[0]['rating'] if rt else '-', rt[-1]['rating'] if rt else '-'))

print('-- 战报 / 回放 / 分享码 --')
code, d = ask('/api/records/my?page=1&size=1', tok)
lst = d.get('data', {}).get('list', [])
check('战报列表可读', len(lst) > 0, d.get('msg'))
rid = lst[0]['id'] if lst else 0
code, d = ask('/api/records/%s/replay' % rid, tok)
rp = d.get('data', {})
check('回放时间线非空', len(rp.get('timeline', [])) > 0, len(rp.get('timeline', [])))
print('    回放 %d 条日志 / %d 回合 / %s VS %s' % (len(rp.get('timeline', [])), len(rp.get('turns', {})),
                                                  rp.get('me', {}).get('nickname'), rp.get('opponent', {}).get('nickname')))
code, d = ask('/api/records/%s/shareCode' % rid, tok)
share = d.get('data', {}).get('code')
check('战报码生成', bool(share), d.get('msg'))
code, d = ask('/api/records/share/' + (share or 'x'), tok)
sv = d.get('data', {})
check('凭码可看(脱敏)', code == 200 and not any('actor' in e for e in sv.get('timeline', [])), d.get('msg'))
print('    战报码 %s -> %s VS %s, %d 条日志' % (share, sv.get('side1'), sv.get('side2'), len(sv.get('timeline', []))))

print('-- 卡组 / 排行榜 / 卡池 --')
code, d = ask('/api/decks', tok)
dk = d.get('data', [])
check('卡组齐全', len(dk) >= 3, len(dk))
print('    ' + ' | '.join('%s%s' % (x['name'], '(默认)' if x['isDefault'] else '') for x in dk))
code, d = ask('/api/rank/top', tok)
rows = d.get('data', [])
rows = rows if isinstance(rows, list) else rows.get('list', [])
check('排行榜可读', len(rows) >= 1, len(rows))
print('    ' + ' | '.join('%s. %s %s分' % (i, x.get('nickname'), x.get('rating')) for i, x in enumerate(rows[:4], 1)))
code, d = ask('/api/cards', tok)
cs = d.get('data', [])
# 主号持有的是"已解锁"卡, 不等于卡池总数(86), 这里是 77/86
check('已解锁卡数量正常', len(cs) >= 77, len(cs))
print('    收藏进度 %d/86 (%.0f%%)' % (len(cs), len(cs) * 100.0 / 86))
news = [c for c in cs if c['id'] >= 67]
check('新卡(67-86)在库', len(news) == 20, len(news))
kw = {}
for c in news:
    for part in (c.get('effect') or '').split(';'):
        k = part.split(':')[0]
        if k in ('rush', 'windfury', 'poisonous'):
            kw[k] = kw.get(k, 0) + 1
print('    新卡异能覆盖: ' + json.dumps(kw, ensure_ascii=False))

print('-- 观战 --')
code, d = ask('/api/battle/live', tok)
check('观战列表接口', code == 200, d.get('msg'))
print('    当前进行中对局: %d 个' % len(d.get('data', []) or []))

print('\n结果: 通过 %d / 失败 %d' % (ok, fail))
