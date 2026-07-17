#!/usr/bin/env python3
"""Generate data/recipes.json's FORGE entries from the Hypixel SkyBlock Fandom
wiki's "The Forge/Table" page (https://hypixel-skyblock.fandom.com/wiki/The_Forge).

Forge recipes have no structured dataset anywhere else this project's other
sync scripts pull from: Hypixel's own API resource doesn't expose recipes at
all (scripts/sync_hypixel_items.py's source), and NotEnoughUpdates-REPO only
carries crafting-table recipes (scripts/sync_neu_recipes.py's source) — forge
items there have just a freeform, non-structured `crafttext` hint like
"Requires: HotM 2". The wiki's forge table is the only place this data is
actually maintained in a parseable form.

Fetches the page's wikitext via the Fandom MediaWiki API (keyless, public)
and hand-parses the "List of Items" wikitable: a depth-aware line walker
(tracking `{{`/`}}` nesting so multi-line `{{RL|...}}` template bodies don't
get mistaken for new table cells) splits it into rows, then regexes out the
item name (`[[Name]]` link), `{{RL|amount name|amount name|...}}` ingredient
list, and duration text from each row's first four cells — the only ones
that matter here. (Later columns carry HotM-tier/profit info behind
`rowspan`-collapsed cells that would need full rowspan-state tracking to
parse positionally; skipped since this script doesn't need them.)

Ingredient/output names are resolved to item ids via a case-insensitive
lookup against data/items.json's `name` field. A "Coins" pseudo-ingredient
(e.g. Travel Scrolls) becomes the recipe's `npcCost` instead of an ingredient
entry, since it isn't a real item.

Usage:
    python scripts/sync_forge_recipes.py [--source path/to/cached-wikitext.json] [--out data/recipes.forge.json]

Without --source, fetches live from the Fandom API.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ITEMS_PATH = REPO_ROOT / "data" / "items.json"
API_URL = (
    "https://hypixel-skyblock.fandom.com/api.php"
    "?action=parse&page=The_Forge/Table&prop=wikitext&format=json"
)
USER_AGENT = "Mozilla/5.0 (compatible; SaibonDataSync/1.0; +https://github.com/duetigh/saibon)"

LINK_RE = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]")
RL_ARG_RE = re.compile(r"\{\{RL(.*?)\}\}", re.DOTALL)
INGREDIENT_RE = re.compile(r"^([\d,]+)\s+(.+)$")
DURATION_UNIT_RE = re.compile(
    r"(\d+(?:\.\d+)?)\s*(day|hour|minute|second)s?", re.IGNORECASE
)
UNIT_SECONDS = {"day": 86400, "hour": 3600, "minute": 60, "second": 1}

# The wiki's forge table isn't internally consistent about item names: some
# rows reference an ingredient by an older/colloquial name that doesn't match
# what that item is actually called in data/items.json today (in one case,
# id DRILL_ENGINE was renamed "Drill Motor" in-game — the row that *produces*
# it uses the new name, but every row that *consumes* it as an ingredient
# still says the old one). Hand-verified against data/items.json ids.
NAME_ALIASES = {
    "sapphire crystal": "saphhire crystal",  # Hypixel's own item name has this typo
    "drill engine": "drill motor",  # renamed in-game; DRILL_ENGINE id kept
    "fuel tank": "fuel canister",  # renamed in-game; FUEL_TANK id kept
    "enchanted block of coal": "enchanted coal block",  # wiki word-order slip
}


def fetch_wikitext(source: str | None) -> str:
    if source:
        with open(source, encoding="utf-8") as f:
            payload = json.load(f)
    else:
        request = urllib.request.Request(API_URL, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request) as resp:
            payload = json.load(resp)
    return payload["parse"]["wikitext"]["*"]


def split_table_rows(wikitext: str) -> list[list[str]]:
    """Depth-aware wikitable row/cell splitter: a leading `|` only starts a new
    cell (or `|-` a new row) when we're not inside an unclosed `{{ }}` template
    — plain per-line `|`-prefix splitting would misfire on multi-line `{{RL\n|x\n}}`
    bodies, whose interior lines also start with `|`."""
    start = wikitext.index("{|")
    end = wikitext.index("|}", start)
    table = wikitext[start:end]

    rows: list[list[str]] = []
    current_row: list[str] = []
    current_cell: list[str] | None = None
    depth = 0

    def flush_cell():
        nonlocal current_cell
        if current_cell is not None:
            current_row.append("\n".join(current_cell))
            current_cell = None

    for line in table.split("\n"):
        stripped = line.strip()
        if depth == 0 and stripped == "|-":
            flush_cell()
            if current_row:
                rows.append(current_row)
            current_row = []
        elif depth == 0 and stripped.startswith("!"):
            pass  # header row
        elif depth == 0 and stripped.startswith("|") and not stripped.startswith("|}"):
            flush_cell()
            current_cell = [line]
        elif current_cell is not None:
            current_cell.append(line)
        depth += line.count("{{") - line.count("}}")

    flush_cell()
    if current_row:
        rows.append(current_row)
    return rows


def parse_duration_seconds(text: str) -> int | None:
    matches = DURATION_UNIT_RE.findall(text)
    if not matches:
        return None
    return sum(int(float(amount) * UNIT_SECONDS[unit.lower()]) for amount, unit in matches)


def parse_ingredients(cell: str, name_to_id: dict[str, str]) -> tuple[list[dict], float | None, list[str]]:
    match = RL_ARG_RE.search(cell)
    if not match:
        return [], None, []

    args = [a.strip() for a in match.group(1).split("|") if a.strip()]
    ingredients: list[dict] = []
    coin_cost: float | None = None
    unresolved: list[str] = []

    for arg in args:
        m = INGREDIENT_RE.match(arg)
        if not m:
            continue
        amount = int(m.group(1).replace(",", ""))
        name = m.group(2).strip()
        if name.lower() == "coins":
            coin_cost = float(amount)
            continue
        key = NAME_ALIASES.get(name.lower(), name.lower())
        item_id = name_to_id.get(key)
        if item_id is None:
            unresolved.append(name)
            continue
        ingredients.append({"itemId": item_id, "amount": amount})

    return ingredients, coin_cost, unresolved


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", help="Path to a cached API response JSON, instead of fetching live")
    parser.add_argument("--out", default=str(REPO_ROOT / "data" / "recipes.forge.json"), help="Output path")
    args = parser.parse_args()

    with open(ITEMS_PATH, encoding="utf-8") as f:
        local_items = json.load(f)
    name_to_id = {item["name"].strip().lower(): item["id"] for item in local_items}

    wikitext = fetch_wikitext(args.source)
    rows = split_table_rows(wikitext)

    recipes = []
    skipped_no_local_match = []
    skipped_no_ingredients = []
    rows_with_unresolved_ingredients = []

    for row in rows:
        if len(row) < 4:
            continue
        name_cell, cost_cell, duration_cell = row[1], row[2], row[3]

        name_match = LINK_RE.search(name_cell)
        if not name_match:
            continue
        display_name = name_match.group(1).strip()
        item_id = name_to_id.get(NAME_ALIASES.get(display_name.lower(), display_name.lower()))
        if item_id is None:
            skipped_no_local_match.append(display_name)
            continue

        ingredients, coin_cost, unresolved = parse_ingredients(cost_cell, name_to_id)
        if unresolved:
            rows_with_unresolved_ingredients.append((display_name, unresolved))
        if not ingredients and coin_cost is None:
            skipped_no_ingredients.append(display_name)
            continue

        recipes.append({
            "itemId": item_id,
            "type": "FORGE",
            "ingredients": ingredients,
            "resultCount": 1,
            **({"npcCost": coin_cost} if coin_cost is not None else {}),
            **({"durationSeconds": parse_duration_seconds(duration_cell)} if parse_duration_seconds(duration_cell) is not None else {}),
        })

    recipes.sort(key=lambda r: r["itemId"])

    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(recipes, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Wrote {len(recipes)} forge recipes to {args.out}")
    print(f"Parsed table rows: {len(rows)}")
    if skipped_no_local_match:
        print(f"Skipped (output item not in local items.json, {len(skipped_no_local_match)}): {', '.join(skipped_no_local_match)}", file=sys.stderr)
    if skipped_no_ingredients:
        print(f"Skipped (no parseable ingredients, {len(skipped_no_ingredients)}): {', '.join(skipped_no_ingredients)}", file=sys.stderr)
    if rows_with_unresolved_ingredients:
        print(f"Rows with an unresolved ingredient name ({len(rows_with_unresolved_ingredients)}):", file=sys.stderr)
        for name, unresolved in rows_with_unresolved_ingredients:
            print(f"  {name}: {unresolved}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
