#!/usr/bin/env python3
"""Sync data/items.json's material/color/skullTexture fields against Hypixel's
public SkyBlock items resource (https://api.hypixel.net/v2/resources/skyblock/items).

Hypixel's resource endpoint is keyless and id-for-id identical to this repo's
item catalog (every id in data/items.json has a matching entry there). This
script only ever corrects three fields — it never touches name/tier/category/
npcSellPrice/wikiUrl/soulbound, which come from wherever data/items.json's ids
and metadata originally came from:

- Leather armor (LEATHER_BOOTS/CHESTPLATE/LEGGINGS/HELMET): sets `color` from
  Hypixel's per-item dye RGB so each piece renders its real in-game color
  instead of the shared placeholder some entries had.
- Skull items (SKULL_ITEM + durability): remaps the legacy 1.12 Bukkit
  material to the correct modern vanilla skull/head item (player_head,
  skeleton_skull, wither_skeleton_skull, zombie_head, creeper_head,
  dragon_head) instead of the `minecraft:paper` placeholder, and for
  player-head items (durability 3, the common case — custom Mojang skin
  textures) sets `skullTexture` to the base64 skin blob so ItemIcons can
  render the exact custom texture via a resolved GameProfile, same as
  vanilla Minecraft does for any player head — no Hypixel resource-pack
  assets involved.

Usage:
    python scripts/sync_hypixel_items.py [--source path/to/cached-api-response.json]

Without --source, fetches live from the Hypixel API.
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ITEMS_PATH = REPO_ROOT / "data" / "items.json"
API_URL = "https://api.hypixel.net/v2/resources/skyblock/items"

LEATHER_MATERIALS = {
    "LEATHER_BOOTS",
    "LEATHER_CHESTPLATE",
    "LEATHER_LEGGINGS",
    "LEATHER_HELMET",
}

# Legacy Bukkit SKULL_ITEM durability -> modern vanilla skull/head item id.
SKULL_DURABILITY_MATERIAL = {
    "0": "minecraft:skeleton_skull",
    "1": "minecraft:wither_skeleton_skull",
    "2": "minecraft:zombie_head",
    "3": "minecraft:player_head",
    "4": "minecraft:creeper_head",
    "5": "minecraft:dragon_head",
}


def fetch_remote_items(source: str | None) -> list[dict]:
    if source:
        with open(source, encoding="utf-8") as f:
            payload = json.load(f)
    else:
        with urllib.request.urlopen(API_URL) as resp:
            payload = json.load(resp)
    if not payload.get("success", False):
        raise SystemExit(f"Hypixel API returned success=false: {payload}")
    return payload["items"]


def pack_rgb(color: str) -> int:
    r, g, b = (int(part) for part in color.split(","))
    return (r << 16) | (g << 8) | b


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", help="Path to a cached Hypixel API response JSON, instead of fetching live")
    args = parser.parse_args()

    with open(ITEMS_PATH, encoding="utf-8") as f:
        local_items = json.load(f)
    local_by_id = {item["id"]: item for item in local_items}

    remote_items = fetch_remote_items(args.source)

    leather_updated = 0
    skull_updated = 0
    skipped_no_local_match = 0

    for remote in remote_items:
        local = local_by_id.get(remote["id"])
        if local is None:
            skipped_no_local_match += 1
            continue

        material = remote.get("material")

        if material in LEATHER_MATERIALS:
            color = remote.get("color")
            if color:
                new_color = pack_rgb(color)
                if local.get("color") != new_color:
                    local["color"] = new_color
                    leather_updated += 1
            else:
                local.pop("color", None)

        elif material == "SKULL_ITEM":
            durability = str(remote.get("durability", "3"))
            new_material = SKULL_DURABILITY_MATERIAL.get(durability, "minecraft:player_head")
            changed = local.get("material") != new_material
            local["material"] = new_material
            local.pop("color", None)

            skin = remote.get("skin") or {}
            texture = skin.get("value")
            if durability == "3" and texture:
                if local.get("skullTexture") != texture:
                    local["skullTexture"] = texture
                    changed = True
            else:
                local.pop("skullTexture", None)

            if changed:
                skull_updated += 1

    with open(ITEMS_PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(local_items, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Leather armor pieces recolored: {leather_updated}")
    print(f"Skull items retextured/remapped: {skull_updated}")
    if skipped_no_local_match:
        print(f"Remote ids with no local match (skipped): {skipped_no_local_match}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
