#!/usr/bin/env python3
"""Checks a RYSQuests book without starting the game.

Usage:
    python rysquests_check.py <pack root> [--registry rysquests-registry.json] [--lang ru_ru] [--json]

<pack root> holds data/<namespace>/rysquests/. With --registry (written by /rysquests dev registry) item, tag,
entity and other ids are checked against the real pack. With --json the result is printed as JSON for other tools.
Exit code: 0 when there are no errors, 1 otherwise.
"""
import argparse
import json
import sys
from pathlib import Path

KNOWN_TASKS = {"rysquests:item", "rysquests:checkbox", "rysquests:advancement", "rysquests:stat",
               "rysquests:visit", "rysquests:kill", "rysquests:fluid", "rysquests:script"}
KNOWN_REWARDS = {"rysquests:item", "rysquests:xp", "rysquests:command", "rysquests:choice",
                 "rysquests:random", "rysquests:stage", "rysquests:loot_table", "rysquests:script"}
SPECIAL_FILES = {"_chapter.json", "_quests.json"}


class Report:
    def __init__(self):
        self.issues = []

    def add(self, severity, code, subject, detail=""):
        self.issues.append({"severity": severity, "code": code, "subject": subject, "detail": detail})

    def error(self, code, subject, detail=""):
        self.add("error", code, subject, detail)

    def warning(self, code, subject, detail=""):
        self.add("warning", code, subject, detail)


def load_json(path, report):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as error:
        report.error("parse_error", str(path), str(error))
        return None


def read_book(root, report):
    chapters, quests = {}, {}
    for namespace in sorted(p for p in (root / "data").iterdir() if p.is_dir()):
        chapters_dir = namespace / "rysquests" / "chapters"
        if not chapters_dir.is_dir():
            continue
        for folder in sorted(p for p in chapters_dir.iterdir() if p.is_dir()):
            meta_file = folder / "_chapter.json"
            default_chapter = f"{namespace.name}:{folder.name}"
            if meta_file.is_file():
                meta = load_json(meta_file, report)
                if meta and "id" in meta:
                    if meta["id"] in chapters:
                        report.error("duplicate_chapter", meta["id"])
                    chapters[meta["id"]] = meta
                    default_chapter = meta["id"]
            for file in sorted(folder.rglob("*.json")):
                if file.name == "_chapter.json":
                    continue
                data = load_json(file, report)
                for quest in (data if isinstance(data, list) else [data] if data else []):
                    if not isinstance(quest, dict) or "id" not in quest or "title" not in quest:
                        report.error("parse_error", str(file), "a quest needs 'id' and 'title'")
                        continue
                    quest.setdefault("chapter", default_chapter)
                    if quest["id"] in quests:
                        report.error("duplicate_quest", quest["id"])
                    quests[quest["id"]] = quest
    return chapters, quests


def find_cycles(quests, report):
    state = {}
    for start in quests:
        if state.get(start):
            continue
        stack = [(start, iter(quests[start].get("requires", [])))]
        state[start] = "open"
        path = [start]
        while stack:
            node, dependencies = stack[-1]
            for dependency in dependencies:
                if dependency not in quests:
                    continue
                if state.get(dependency) == "open":
                    report.error("dependency_cycle", dependency, " -> ".join(path[path.index(dependency):] + [dependency]))
                elif not state.get(dependency):
                    state[dependency] = "open"
                    stack.append((dependency, iter(quests[dependency].get("requires", []))))
                    path.append(dependency)
                    break
            else:
                state[node] = "done"
                stack.pop()
                path.pop()


def check_item(item, quest_id, registry, report):
    if registry is None:
        return
    if item.startswith("#"):
        if item[1:] not in registry["item_tags"]:
            report.warning("unknown_tag", quest_id, item)
    elif item not in registry["items"]:
        report.error("unknown_item", quest_id, item)


def check_reward(reward, quest_id, registry, report):
    kind = reward.get("type")
    if kind not in KNOWN_REWARDS:
        report.error("unknown_reward_type", quest_id, str(kind))
    if kind == "rysquests:item":
        check_item(reward.get("item", ""), quest_id, registry, report)
    if kind in ("rysquests:choice", "rysquests:random"):
        options = reward.get("of", [])
        if not options:
            report.error("empty_reward", quest_id)
        for option in options:
            check_reward(option, quest_id, registry, report)


def check_task(task, quest_id, registry, report):
    kind = task.get("type")
    if kind not in KNOWN_TASKS:
        report.error("unknown_task_type", quest_id, str(kind))
    if kind == "rysquests:item":
        if ("item" in task) == ("any_of" in task):
            report.error("bad_item_task", quest_id, "needs exactly one of 'item' and 'any_of'")
        for item in ([task["item"]] if "item" in task else task.get("any_of", [])):
            check_item(item, quest_id, registry, report)
    if kind == "rysquests:kill" and registry is not None and task.get("entity") not in registry["entities"]:
        report.error("unknown_entity", quest_id, str(task.get("entity")))
    if kind == "rysquests:advancement" and registry is not None and task.get("advancement") not in registry["advancements"]:
        report.warning("unknown_advancement", quest_id, str(task.get("advancement")))


def check_lang(root, chapters, quests, language, report):
    table = {}
    for namespace in (root / "assets").glob("*"):
        file = namespace / "lang" / f"{language}.json"
        if file.is_file():
            table.update(load_json(file, report) or {})
    def needs(subject, key):
        if key and " " not in key and "." in key and key not in table:
            report.warning("untranslated", subject, key)
    for chapter_id, chapter in chapters.items():
        needs(chapter_id, chapter.get("title"))
    for quest_id, quest in quests.items():
        needs(quest_id, quest.get("title"))
        needs(quest_id, quest.get("text"))


def check(root, registry, language):
    report = Report()
    chapters, quests = read_book(root, report)
    for quest_id, quest in quests.items():
        if quest["chapter"] not in chapters:
            report.error("unknown_chapter", quest_id, quest["chapter"])
        for dependency in quest.get("requires", []):
            if dependency not in quests:
                report.error("missing_dependency", quest_id, dependency)
        for task in quest.get("tasks", []):
            check_task(task, quest_id, registry, report)
        for reward in quest.get("rewards", []):
            check_reward(reward, quest_id, registry, report)
    find_cycles(quests, report)
    if (root / "assets").is_dir():
        check_lang(root, chapters, quests, language, report)
    return chapters, quests, report


def main():
    parser = argparse.ArgumentParser(description="Offline check of a RYSQuests book")
    parser.add_argument("root", type=Path)
    parser.add_argument("--registry", type=Path)
    parser.add_argument("--lang", default="en_us")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    registry = None
    if args.registry:
        registry = {key: set(values) for key, values in json.loads(args.registry.read_text(encoding="utf-8")).items()}
    chapters, quests, report = check(args.root, registry, args.lang)
    errors = sum(issue["severity"] == "error" for issue in report.issues)
    warnings = len(report.issues) - errors
    if args.json:
        json.dump({"chapters": len(chapters), "quests": len(quests), "errors": errors, "warnings": warnings, "issues": report.issues},
                  sys.stdout, ensure_ascii=False, indent=1)
        print()
    else:
        print(f"chapters: {len(chapters)}, quests: {len(quests)}, errors: {errors}, warnings: {warnings}")
        for issue in report.issues:
            print(f"{issue['severity'].upper()} {issue['code']} {issue['subject']} {issue['detail']}".rstrip())
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
