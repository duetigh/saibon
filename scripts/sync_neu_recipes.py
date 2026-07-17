#!/usr/bin/env python3
"""Generate data/recipes.json's CRAFTING entries from NotEnoughUpdates-REPO
(https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO), the community-
standard source most SkyBlock tools use for crafting-table recipes. Hypixel's
own API resource (scripts/sync_hypixel_items.py's source) does not expose
recipe data at all.

Each `items/<ID>.json` file in that repo carries an optional `recipe` field:
a 3x3 crafting-table grid (`A1`..`C3`) of `"ITEM_ID:amount"` strings (empty
string for an unused slot). This script sums duplicate-ingredient cells into
one RecipeIngredient per id, keyed by the file's `internalname`.

Forge recipes are NOT in this repo (only a freeform, non-structured
`crafttext` hint) — see scripts/sync_forge_recipes.py for that dataset's
source instead. NPC-purchase recipes are out of scope for both scripts.

Usage:
    python scripts/sync_neu_recipes.py --neu-repo path/to/NotEnoughUpdates-REPO/items [--out data/recipes.craft.json]

Requires a local checkout of NotEnoughUpdates-REPO's `items/` directory
(sparse-checkout is enough: `git clone --filter=blob:none --sparse
https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO.git && git
sparse-checkout set items`). Not fetched automatically since it's a ~9k-file
one-time snapshot, not something to re-clone on every run.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ITEMS_PATH = REPO_ROOT / "data" / "items.json"
GRID_SLOTS = ["A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"]
CELL_RE = re.compile(r"^([A-Za-z0-9_:\-]+):(\d+)$")


def parse_cell(cell: str) -> tuple[str, int] | None:
    cell = cell.strip()
    if not cell:
        return None
    match = CELL_RE.match(cell)
    if not match:
        return None
    item_id, amount = match.groups()
    return item_id.upper(), int(amount)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--neu-repo", required=True, help="Path to NotEnoughUpdates-REPO's items/ directory")
    parser.add_argument("--out", default=str(REPO_ROOT / "data" / "recipes.craft.json"), help="Output path")
    args = parser.parse_args()

    items_dir = Path(args.neu_repo)
    if not items_dir.is_dir():
        raise SystemExit(f"Not a directory: {items_dir}")

    with open(ITEMS_PATH, encoding="utf-8") as f:
        local_ids = {item["id"].upper() for item in json.load(f)}

    recipes = []
    skipped_no_local_match = 0
    skipped_no_recipe = 0
    ingredient_ids_missing_locally = set()

    for path in sorted(items_dir.glob("*.json")):
        try:
            with open(path, encoding="utf-8") as f:
                item = json.load(f)
        except json.JSONDecodeError:
            continue

        internal_name = (item.get("internalname") or "").upper()
        if not internal_name:
            continue

        grid = item.get("recipe")
        cells = [parse_cell(grid.get(slot, "")) for slot in GRID_SLOTS] if grid else []
        cells = [c for c in cells if c]
        if not cells:
            skipped_no_recipe += 1
            continue

        if internal_name not in local_ids:
            skipped_no_local_match += 1
            continue

        ingredients: dict[str, int] = {}
        for ingredient_id, amount in cells:
            ingredients[ingredient_id] = ingredients.get(ingredient_id, 0) + amount
            if ingredient_id not in local_ids:
                ingredient_ids_missing_locally.add(ingredient_id)

        recipes.append({
            "itemId": internal_name,
            "type": "CRAFTING",
            "ingredients": [{"itemId": iid, "amount": amt} for iid, amt in ingredients.items()],
            "resultCount": int(item.get("count") or 1),
        })

    recipes.sort(key=lambda r: r["itemId"])

    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(recipes, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Wrote {len(recipes)} crafting recipes to {args.out}")
    print(f"Skipped (no recipe field): {skipped_no_recipe}")
    print(f"Skipped (crafted item not in local items.json): {skipped_no_local_match}")
    if ingredient_ids_missing_locally:
        print(f"Ingredient ids referenced but not in local items.json ({len(ingredient_ids_missing_locally)}):", file=sys.stderr)
        for iid in sorted(ingredient_ids_missing_locally):
            print(f"  {iid}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
