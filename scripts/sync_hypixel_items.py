#!/usr/bin/env python3
"""Sync data/items.json's material/color/skullTexture fields against Hypixel's
public SkyBlock items resource (https://api.hypixel.net/v2/resources/skyblock/items).

Hypixel's resource endpoint is keyless and id-for-id identical to this repo's
item catalog (every id in data/items.json has a matching entry there). This
script only ever corrects a handful of fields — it never touches name/tier/
category/npcSellPrice/wikiUrl/soulbound, which come from wherever
data/items.json's ids and metadata originally came from:

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
- Dye-family items (legacy Bukkit INK_SACK + durability, e.g. arrow
  poisons/powders/pet drops): remaps to the correct modern dye/bone-meal/
  cocoa-beans item id instead of the durability-0 (`minecraft:ink_sac`)
  placeholder every one of these got when the legacy id was first
  flattened.
- Every item with a remote `item_model`: copies it verbatim into local
  `itemModel` (kept as informational metadata only — ItemIcons deliberately
  does not consume it, since `hypixel_skyblock:item/...` only resolves
  through Hypixel's own live server resource pack, which not every player
  runs; see ItemIcons.kt's doc comment).
- Items whose only known reskin is that same server-resource-pack
  `item_model` are cross-referenced against the Detexturify mod's public
  item dataset (https://athen.aerii.xyz/items, keyless, fetched here at
  sync time only — never at Saibon runtime, same category as the NEU-REPO
  fallback below) for a real Mojang skull-texture blob: the classic pre-
  resource-pack NEU-era rendering trick, resource-pack-independent (same
  mechanism as the skull-item case above, just sourced from a community
  dataset instead of Hypixel's own API). Covers ~400 of the ~950
  `item_model`-only items as of 2026-07-17.

Usage:
    python scripts/sync_hypixel_items.py [--source path/to/cached-api-response.json] [--detexturify-source path/to/cached-detexturify.json]

Without --source/--detexturify-source, fetches live from the Hypixel API /
Detexturify's dataset respectively. A Detexturify fetch failure is
non-fatal (logged, sync continues without it) since it's a bonus data
source, not the primary one.
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
DETEXTURIFY_URL = "https://athen.aerii.xyz/items"

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

# Legacy Bukkit INK_SACK durability -> modern vanilla dye-family item id
# (the pre-1.13 DyeColor durability table; INK_SACK itself only ever meant
# "black dye" at durability 0, everything else was a sub-id).
INK_SACK_DURABILITY_MATERIAL = {
    "0": "minecraft:ink_sac",
    "1": "minecraft:red_dye",
    "2": "minecraft:green_dye",
    "3": "minecraft:cocoa_beans",
    "4": "minecraft:lapis_lazuli",
    "5": "minecraft:purple_dye",
    "6": "minecraft:cyan_dye",
    "7": "minecraft:light_gray_dye",
    "8": "minecraft:gray_dye",
    "9": "minecraft:pink_dye",
    "10": "minecraft:lime_dye",
    "11": "minecraft:yellow_dye",
    "12": "minecraft:light_blue_dye",
    "13": "minecraft:magenta_dye",
    "14": "minecraft:orange_dye",
    "15": "minecraft:bone_meal",
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


def fetch_detexturify_items(source: str | None) -> dict:
    """Returns {} on any failure — this is a bonus data source, sync must still succeed without it."""
    try:
        if source:
            with open(source, encoding="utf-8") as f:
                return json.load(f)
        with urllib.request.urlopen(DETEXTURIFY_URL, timeout=30) as resp:
            return json.load(resp)
    except Exception as exc:  # noqa: BLE001 - deliberately broad, see docstring
        print(f"Detexturify dataset fetch failed, continuing without it: {exc}", file=sys.stderr)
        return {}


def pack_rgb(color: str) -> int:
    r, g, b = (int(part) for part in color.split(","))
    return (r << 16) | (g << 8) | b


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", help="Path to a cached Hypixel API response JSON, instead of fetching live")
    parser.add_argument(
        "--detexturify-source",
        help="Path to a cached Detexturify items JSON, instead of fetching live (see fetch_detexturify_items)",
    )
    args = parser.parse_args()

    with open(ITEMS_PATH, encoding="utf-8") as f:
        local_items = json.load(f)
    local_by_id = {item["id"]: item for item in local_items}

    remote_items = fetch_remote_items(args.source)
    detexturify_items = fetch_detexturify_items(args.detexturify_source)

    leather_updated = 0
    skull_updated = 0
    dye_updated = 0
    item_model_updated = 0
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

        elif material == "INK_SACK":
            durability = str(remote.get("durability", "0"))
            new_material = INK_SACK_DURABILITY_MATERIAL.get(durability, "minecraft:ink_sac")
            if local.get("material") != new_material:
                local["material"] = new_material
                dye_updated += 1

        item_model = remote.get("item_model")
        if item_model:
            if local.get("itemModel") != item_model:
                local["itemModel"] = item_model
                item_model_updated += 1
        else:
            local.pop("itemModel", None)

    detexturify_retextured = 0
    for item_id, local in local_by_id.items():
        if not local.get("itemModel") or local.get("skullTexture"):
            continue  # only items whose only known reskin is Hypixel's own server pack, not already a skull

        detex_entry = detexturify_items.get(item_id)
        texture = detex_entry.get("texture") if detex_entry else None
        if not texture:
            continue

        if local.get("skullTexture") != texture or local.get("material") != "minecraft:player_head":
            local["material"] = "minecraft:player_head"
            local["skullTexture"] = texture
            local.pop("color", None)
            detexturify_retextured += 1

    with open(ITEMS_PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(local_items, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Leather armor pieces recolored: {leather_updated}")
    print(f"Skull items retextured/remapped: {skull_updated}")
    print(f"Dye-family items remapped: {dye_updated}")
    print(f"Item models set/updated: {item_model_updated}")
    print(f"Items retextured via Detexturify's community skull-texture data: {detexturify_retextured}")
    if skipped_no_local_match:
        print(f"Remote ids with no local match (skipped): {skipped_no_local_match}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
