#!/usr/bin/env python3
"""Merge scripts/sync_neu_recipes.py's and scripts/sync_forge_recipes.py's
outputs into data/recipes.json, the single dataset DataRepository loads.

Usage:
    python scripts/merge_recipes.py [craft.json] [forge.json] [--out data/recipes.json]

Defaults match both scripts' own --out defaults (data/recipes.craft.json,
data/recipes.forge.json), so with no arguments this just merges their
freshly generated output. Errors out if both inputs define a recipe for the
same itemId — that would silently drop one of them.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("craft", nargs="?", default=str(REPO_ROOT / "data" / "recipes.craft.json"))
    parser.add_argument("forge", nargs="?", default=str(REPO_ROOT / "data" / "recipes.forge.json"))
    parser.add_argument("--out", default=str(REPO_ROOT / "data" / "recipes.json"))
    args = parser.parse_args()

    with open(args.craft, encoding="utf-8") as f:
        craft = json.load(f)
    with open(args.forge, encoding="utf-8") as f:
        forge = json.load(f)

    craft_ids = {r["itemId"] for r in craft}
    forge_ids = {r["itemId"] for r in forge}
    overlap = craft_ids & forge_ids
    if overlap:
        raise SystemExit(f"itemIds present in both craft and forge output, needs manual resolution: {sorted(overlap)}")

    merged = sorted(craft + forge, key=lambda r: r["itemId"])
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(merged, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Merged {len(craft)} crafting + {len(forge)} forge recipes -> {len(merged)} entries in {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
