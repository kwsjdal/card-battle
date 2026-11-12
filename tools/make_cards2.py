# -*- coding: utf-8 -*-
"""为 67-86 号新卡拉取官方半身像。

与 make_cards.py 同一素材源（ArknightsGameResource/portrait/{char_id}_1.png）。
区别：raw.githubusercontent.com 在本机被墙，改走 ghproxy.net 镜像；
      channel 优先 _1（精英0），缺失时退到 _2（精英1）。
"""
import io
import json
import sys
import time
import urllib.request

from PIL import Image

MIRROR = 'https://ghproxy.net/https://raw.githubusercontent.com/yuanyan3060/ArknightsGameResource/main/'
OUT = 'frontend/public/cards/{}.jpg'

# id -> 卡牌名（与数据库 cards 表一致）
NAMES = {
    67: '卡缇', 68: '黑角', 69: '杜林', 70: '夜魔', 71: '狮蝎',
    72: '白雪', 73: '流星', 74: '砾', 75: '安比尔', 76: '崖心',
    77: '角峰', 78: '幽灵鲨', 79: '刻俄柏', 80: '送葬人', 81: '灰喉',
    82: '阿', 83: '帕拉斯', 84: '炎客', 85: '棘刺', 86: '贾维',
}


def fetch(url, tries=4):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            return urllib.request.urlopen(req, timeout=60).read()
        except Exception as e:
            last = e
            print(f'  retry{i + 1}: {e}', flush=True)
            time.sleep(2 * (i + 1))
    raise last


def main():
    table = json.load(open('tools/character_table.json', encoding='utf-8'))
    name2id = {}
    for cid, c in table.items():
        n = c.get('name')
        if n and n not in name2id:
            name2id[n] = cid

    ok, fail = [], []
    for card_id, name in NAMES.items():
        cid = name2id.get(name)
        if not cid:
            print(f'[{card_id}] {name}: 查不到 char id!', flush=True)
            fail.append(name)
            continue
        done = False
        for ch in ('_1', '_2'):          # 精英0 优先, 没有就用精英1
            try:
                data = fetch(MIRROR + f'portrait/{cid}{ch}.png')
                im = Image.open(io.BytesIO(data)).convert('RGBA')
                bg = Image.new('RGBA', im.size, (18, 16, 14, 255))
                out = Image.alpha_composite(bg, im).convert('RGB')
                out.save(OUT.format(card_id), 'JPEG', quality=88)
                print(f'[{card_id}] {name} <- {cid}{ch} {im.size} OK', flush=True)
                ok.append(name)
                done = True
                break
            except Exception as e:
                print(f'[{card_id}] {name} ({cid}{ch}) 失败: {e}', flush=True)
        if not done:
            fail.append(name)

    print(f'\n完成 {len(ok)}/{len(NAMES)}, 失败: {fail or "无"}', flush=True)
    sys.exit(1 if fail else 0)


if __name__ == '__main__':
    main()
