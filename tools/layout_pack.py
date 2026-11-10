"""Copies the RepYS book into run-layout/pack, the book of the 'layout' autotest scenario.

Only the shape of the book matters there: ids, chapters, groups, dependencies, optional and layout stay as they are.
Tasks become a checkbox, rewards go and icons of other mods turn into paper, so that the book loads without the
pack's mods. The pack instance is only read. Run again to pick up changes of the book (files are overwritten, never removed):

    python tools/layout_pack.py [path to the instance]
"""
import json
import os
import shutil
import sys

instance = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\dmitr\curseforge\minecraft\Instances\RepYS'
source = os.path.join(instance, 'kubejs')
target = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'run-layout', 'pack')

KEPT_REWARDS = {'rysquests:item', 'rysquests:choice', 'rysquests:random', 'rysquests:xp', 'rysquests:currency'}


def vanilla(value):
    """The same reward with items of other mods turned into paper and nothing else that names them."""
    if isinstance(value, list):
        return [vanilla(item) for item in value]
    if not isinstance(value, dict):
        return value
    out = {}
    for key, item in value.items():
        if key == 'components':
            continue
        if key == 'item' and isinstance(item, str) and not item.startswith('minecraft:'):
            out[key] = 'minecraft:paper'
        else:
            out[key] = vanilla(item)
    return out


book_in = os.path.join(source, 'data', 'repys', 'rysquests')
book_out = os.path.join(target, 'data', 'repys', 'rysquests')
os.makedirs(book_out, exist_ok=True)
shutil.copy(os.path.join(book_in, 'book.json'), book_out)

for chapter in os.listdir(os.path.join(book_in, 'chapters')):
    chapter_in = os.path.join(book_in, 'chapters', chapter)
    if not os.path.isdir(chapter_in):
        continue
    chapter_out = os.path.join(book_out, 'chapters', chapter)
    os.makedirs(chapter_out, exist_ok=True)
    for name in os.listdir(chapter_in):
        if not name.endswith('.json'):
            continue
        with open(os.path.join(chapter_in, name), encoding='utf-8') as f:
            data = json.load(f)
        if name != '_chapter.json':
            data['tasks'] = [{'type': 'rysquests:checkbox'}]
            data['rewards'] = [vanilla(reward) for reward in data.get('rewards', []) if reward.get('type') in KEPT_REWARDS]
        icon = data.get('icon')
        if isinstance(icon, str) and not icon.startswith('minecraft:'):
            data['icon'] = 'minecraft:paper'
        with open(os.path.join(chapter_out, name), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

lang_out = os.path.join(target, 'assets', 'repys', 'lang')
shutil.copytree(os.path.join(source, 'assets', 'repys', 'lang'), lang_out, dirs_exist_ok=True)
with open(os.path.join(target, 'pack.mcmeta'), 'w', encoding='ascii') as f:
    f.write('{"pack":{"pack_format":48,"description":"RepYS book copy for layout tests"}}')
print('book copied to', os.path.normpath(target))
