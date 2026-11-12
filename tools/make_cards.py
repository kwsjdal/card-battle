# -*- coding: utf-8 -*-
"""统一 1-20 号卡面：从 ArknightsGameResource 拉官方半身像替换旧混搭图。
与 21-66 号同一素材源（portrait/{char_id}_1.png），压平到 180x360 JPG。"""
import io
import json
import sys
import time
import urllib.request

from PIL import Image

RAW = 'https://raw.githubusercontent.com/yuanyan3060/ArknightsGameResource/main/'
OUT = 'frontend/public/cards/{}.jpg'

# id -> 卡牌名（与数据库一致）
NAMES = {
    1: '玫兰莎', 2: '克洛丝', 3: '米格鲁', 4: '炎熔', 5: '猎蜂',
    6: '拉普兰德', 7: '夜刀', 8: '白面鸮', 9: '星熊', 10: '斯卡蒂',
    11: '年', 12: '临光', 13: '霜叶', 14: '安洁莉娜', 15: '惊蛰',
    16: '赫默', 17: '银灰', 18: '艾雅法拉', 19: '泥岩', 20: '能天使',
}


def fetch(url, tries=4):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            return urllib.request.urlopen(req, timeout=45).read()
        except Exception as e:  # 网络抖动很常见，重试
            last = e
            print(f'  retry{i + 1} {url.rsplit("/", 1)[-1]}: {e}', flush=True)
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
        try:
            data = fetch(RAW + f'portrait/{cid}_1.png')
            im = Image.open(io.BytesIO(data)).convert('RGBA')
            bg = Image.new('RGBA', im.size, (18, 16, 14, 255))
            out = Image.alpha_composite(bg, im).convert('RGB')
            out.save(OUT.format(card_id), 'JPEG', quality=88)
            print(f'[{card_id}] {name} <- {cid} {im.size} OK', flush=True)
            ok.append(name)
        except Exception as e:
            print(f'[{card_id}] {name} ({cid}) 失败: {e}', flush=True)
            fail.append(name)

    print(f'\n完成 {len(ok)}/20, 失败: {fail or "无"}', flush=True)
    sys.exit(1 if fail else 0)


if __name__ == '__main__':
    main()
