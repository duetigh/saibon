#!/usr/bin/env python3
"""Continuously aggregates Hypixel Auction House sales into a fair-price
snapshot, `data/fair_prices.json`, published the same way `data/items.json`/
`data/recipes.json` already are. This is what lets a brand-new Saibon
install show real Auction House flips on day one: Hypixel's public
`auctions_ended` endpoint only ever exposes a rolling ~60s window per
request, so no client can back-fill real sales history locally — only a
process that has been polling for real wall-clock time can. This script is
meant to run on a schedule (see `.github/workflows/aggregate-fair-prices.yml`)
on GitHub's own infrastructure, not any player's machine.

Each run:
  1. Fetches `https://api.hypixel.net/v2/skyblock/auctions_ended` (public,
     keyless).
  2. Decodes each sale's `item_bytes` (base64 gzip'd NBT) with `decode_item_bytes`
     below — a Python port of the mod's `AuctionItemDecoder.kt`. Keep the two
     in sync: they must produce byte-identical SKU keys
     (`"<itemId>"` / `"<itemId>|<modifierSignature>"`) or the client's local
     buckets and this snapshot won't line up. `_item_modifiers` is a separate
     port of the Kotlin file's `itemModifiers()` — likewise kept in sync; it
     decomposes an item into atomic, individually-priceable upgrades (reforge/
     potato-books/recomb/dungeon-stars/enchants plus gemstones, accessory
     enrichment, ability scrolls, Art of War/Peace, Wood Singularity, Farming
     for Dummies), and `_modifier_signature` is derived from that same list
     (each upgrade's `kind:key`, sorted and joined) rather than re-parsing
     `extra` on its own — so a modifier kind only has to be taught to
     `_item_modifiers` once to be excluded from "plain item" bucketing too.
  3. Appends new sales into `data/.sales_history_raw.json` (per-SKU) and
     `data/.modifier_deltas_raw.json` (per-modifier, pooled across items —
     see `_apply_modifier_deltas`) — this script's own rolling cross-run
     state (bounded the same way as the client's in-memory buffers: 14-day
     age cap, `MAX_SAMPLES_PER_SKU` count cap). Neither is published in
     `data/index.json` — purely working state.
  4. Recomputes a `FairPriceResult`-shaped entry per SKU/modifier with
     `compute_fair_price` — a port of `FairPriceCalculator.kt`. Keep the two
     in sync; same reasoning as the decoder.
  5. Writes the compact client-facing `data/fair_prices.json` and
     `data/modifier_values.json`, bumping each of `data/index.json`'s
     `fair_prices`/`modifier_values` entries (version/sha256/url) if their
     content actually changed (see `update_manifest`).

Usage:
    python scripts/aggregate_fair_prices.py [--source path/to/cached-auctions-ended.json] [--dry-run]

Without --source, fetches live from the Hypixel API. --dry-run computes and
prints a summary but doesn't write any files (for local testing).
"""
from __future__ import annotations

import argparse
import base64
import gzip
import json
import math
import struct
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parent.parent
RAW_STATE_PATH = REPO_ROOT / "data" / ".sales_history_raw.json"
FAIR_PRICES_PATH = REPO_ROOT / "data" / "fair_prices.json"
MODIFIER_DELTAS_RAW_PATH = REPO_ROOT / "data" / ".modifier_deltas_raw.json"
MODIFIER_VALUES_PATH = REPO_ROOT / "data" / "modifier_values.json"
INDEX_PATH = REPO_ROOT / "data" / "index.json"
AUCTIONS_ENDED_URL = "https://api.hypixel.net/v2/skyblock/auctions_ended"

MAX_SAMPLE_AGE_MILLIS = 14 * 24 * 3_600_000
MAX_SAMPLES_PER_SKU = 300

# --- NBT decoding (port of AuctionItemDecoder.kt) --------------------------

_TAG_END, _TAG_BYTE, _TAG_SHORT, _TAG_INT, _TAG_LONG = 0, 1, 2, 3, 4
_TAG_FLOAT, _TAG_DOUBLE, _TAG_BYTE_ARRAY, _TAG_STRING = 5, 6, 7, 8
_TAG_LIST, _TAG_COMPOUND, _TAG_INT_ARRAY, _TAG_LONG_ARRAY = 9, 10, 11, 12


class _NbtReader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def read_byte(self) -> int:
        v = self.data[self.pos]
        self.pos += 1
        return v

    def read_ushort(self) -> int:
        v = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        return v

    def read_int(self) -> int:
        v = struct.unpack_from(">i", self.data, self.pos)[0]
        self.pos += 4
        return v

    def read_long(self) -> int:
        v = struct.unpack_from(">q", self.data, self.pos)[0]
        self.pos += 8
        return v

    def read_float(self) -> float:
        v = struct.unpack_from(">f", self.data, self.pos)[0]
        self.pos += 4
        return v

    def read_double(self) -> float:
        v = struct.unpack_from(">d", self.data, self.pos)[0]
        self.pos += 8
        return v

    def read_string(self) -> str:
        length = self.read_ushort()
        s = self.data[self.pos:self.pos + length].decode("utf-8", errors="replace")
        self.pos += length
        return s

    def read_payload(self, tag_id: int) -> Any:
        if tag_id == _TAG_END:
            return None
        if tag_id == _TAG_BYTE:
            return self.read_byte()
        if tag_id == _TAG_SHORT:
            v = struct.unpack_from(">h", self.data, self.pos)[0]
            self.pos += 2
            return v
        if tag_id == _TAG_INT:
            return self.read_int()
        if tag_id == _TAG_LONG:
            return self.read_long()
        if tag_id == _TAG_FLOAT:
            return self.read_float()
        if tag_id == _TAG_DOUBLE:
            return self.read_double()
        if tag_id == _TAG_BYTE_ARRAY:
            length = self.read_int()
            arr = self.data[self.pos:self.pos + length]
            self.pos += length
            return arr
        if tag_id == _TAG_STRING:
            return self.read_string()
        if tag_id == _TAG_LIST:
            item_id = self.read_byte()
            length = self.read_int()
            return [self.read_payload(item_id) for _ in range(length)]
        if tag_id == _TAG_COMPOUND:
            result: dict[str, Any] = {}
            while True:
                child_id = self.read_byte()
                if child_id == _TAG_END:
                    break
                name = self.read_string()
                result[name] = self.read_payload(child_id)
            return result
        if tag_id == _TAG_INT_ARRAY:
            length = self.read_int()
            return [self.read_int() for _ in range(length)]
        if tag_id == _TAG_LONG_ARRAY:
            length = self.read_int()
            return [self.read_long() for _ in range(length)]
        raise ValueError(f"Unknown NBT tag id {tag_id}")

    def read_root(self) -> dict[str, Any]:
        tag_id = self.read_byte()
        if tag_id != _TAG_COMPOUND:
            raise ValueError("Root NBT tag is not a compound")
        self.read_string()  # root name, conventionally empty
        return self.read_payload(_TAG_COMPOUND)


def decode_item_bytes(item_bytes_b64: str) -> tuple[str, str, list[tuple[str, str]]] | None:
    """Mirrors AuctionItemDecoder.decode: returns (itemId, modifierSignature, modifiers) or None."""
    try:
        raw = gzip.decompress(base64.b64decode(item_bytes_b64))
        root = _NbtReader(raw).read_root()
        items = root.get("i") or []
        if not items:
            return None
        item = items[0] or {}
        tag = item.get("tag") or {}
        extra = tag.get("ExtraAttributes") or {}
        item_id = extra.get("id")
        if not item_id:
            return None
        return item_id, _modifier_signature(extra), _item_modifiers(extra)
    except Exception:
        return None


def _modifier_signature(extra: dict[str, Any]) -> str:
    """Exact-match bucketing key — mirrors AuctionItemDecoder.kt's `modifierSignature()` byte-for-byte: derived from `_item_modifiers` (each `(kind, key)` pair's `f"{kind}:{key}"`, sorted and joined), not computed independently. Do not change this format lightly; it's a published cache key. (Previously this re-parsed `extra` on its own checking only reforge/potato/recomb/stars/enchants, so a sale carrying gems/enrichment/an ability scroll/etc. still got an empty signature and was miscounted as a plain sale, inflating the published "clean item" fair price for anything commonly sold upgraded.)"""
    return "|".join(sorted(f"{kind}:{key}" for kind, key in _item_modifiers(extra)))


def _item_modifiers(extra: dict[str, Any]) -> list[tuple[str, str]]:
    """Atomic per-upgrade decomposition — mirrors AuctionItemDecoder.kt's `itemModifiers()`. Each `(kind, key)` pair's `f"{kind}:{key}"` join is the pooled cross-item delta-table key (`ItemModifier.poolKey` client-side)."""
    modifiers: list[tuple[str, str]] = []

    modifier = extra.get("modifier")
    if modifier:
        modifiers.append(("reforge", str(modifier)))

    hot_potato = extra.get("hot_potato_count") or 0
    if hot_potato > 0:
        modifiers.append(("potato", str(hot_potato)))

    if (extra.get("rarity_upgrades") or 0) > 0:
        modifiers.append(("recomb", "applied"))

    dungeon_level = extra.get("dungeon_item_level") or 0
    if dungeon_level > 0:
        modifiers.append(("stars", str(dungeon_level)))

    enchantments = extra.get("enchantments") or {}
    for name in sorted(enchantments.keys()):
        modifiers.append(("ench", f"{name}{enchantments[name]}"))

    gems = extra.get("gems") or {}
    for slot in sorted(gems.keys()):
        if slot == "unlocked_slots" or slot.endswith("_gem"):
            continue
        quality = gems.get(slot)
        if not isinstance(quality, str):
            continue
        gem_type = gems.get(f"{slot}_gem")
        key = f"{slot}:{gem_type}:{quality}" if isinstance(gem_type, str) else f"{slot}:{quality}"
        modifiers.append(("gem", key))

    enrichment = extra.get("talisman_enrichment")
    if enrichment:
        modifiers.append(("enrich", str(enrichment)))

    for scroll in extra.get("ability_scroll") or []:
        if isinstance(scroll, str):
            modifiers.append(("scroll", scroll))

    if (extra.get("art_of_war_count") or 0) > 0:
        modifiers.append(("book", "art_of_war"))

    if (extra.get("art_of_peace_applied") or 0) > 0:
        modifiers.append(("book", "art_of_peace"))

    if (extra.get("wood_singularity_count") or 0) > 0:
        modifiers.append(("upgrade", "wood_singularity"))

    farming_for_dummies = extra.get("farming_for_dummies_count") or 0
    if farming_for_dummies > 0:
        modifiers.append(("upgrade", f"farming_for_dummies:{farming_for_dummies}"))

    return modifiers


# --- Fair-price statistics (port of FairPriceCalculator.kt) ----------------

_HALF_LIFE_HOURS = 36.0
_TARGET_SAMPLE_SIZE = 20
_STALENESS_CUTOFF_HOURS = 72.0
_WEEK_MILLIS = 7 * 24 * 3_600_000
_DECAY_LAMBDA = math.log(2.0) / _HALF_LIFE_HOURS


def _percentile(sorted_prices: list[int], pct: float) -> float:
    if len(sorted_prices) == 1:
        return float(sorted_prices[0])
    index = (pct / 100.0) * (len(sorted_prices) - 1)
    lower_index = int(index)
    upper_index = min(lower_index + 1, len(sorted_prices) - 1)
    frac = index - lower_index
    return sorted_prices[lower_index] + (sorted_prices[upper_index] - sorted_prices[lower_index]) * frac


def _strip_outliers(samples: list[list[int]]) -> list[list[int]]:
    if len(samples) < 4:
        return samples
    sorted_prices = sorted(price for price, _ in samples)
    q1 = _percentile(sorted_prices, 25.0)
    q3 = _percentile(sorted_prices, 75.0)
    iqr = q3 - q1
    lower, upper = q1 - 1.5 * iqr, q3 + 1.5 * iqr
    kept = [s for s in samples if lower <= s[0] <= upper]
    return kept or samples


def _median(sorted_prices: list[int]) -> float:
    mid = len(sorted_prices) // 2
    if len(sorted_prices) % 2 == 0:
        return (sorted_prices[mid - 1] + sorted_prices[mid]) / 2.0
    return float(sorted_prices[mid])


def _weighted_mean(samples: list[list[int]], now_ms: int) -> float:
    weighted_sum, weight_total = 0.0, 0.0
    for price, ts in samples:
        age_hours = max(now_ms - ts, 0) / 3_600_000.0
        weight = math.exp(-_DECAY_LAMBDA * age_hours)
        weighted_sum += price * weight
        weight_total += weight
    if weight_total > 0:
        return weighted_sum / weight_total
    return sum(price for price, _ in samples) / len(samples)


def _stddev(prices: list[int], mean: float) -> float:
    if len(prices) < 2:
        return 0.0
    variance = sum((p - mean) ** 2 for p in prices) / len(prices)
    return math.sqrt(variance)


def compute_fair_price(samples: list[list[int]], now_ms: int) -> dict[str, Any] | None:
    """Port of FairPriceCalculator.compute (Kotlin) — keep the two in sync."""
    if not samples:
        return None

    filtered = _strip_outliers(samples)
    if not filtered:
        return None

    sorted_prices = sorted(price for price, _ in filtered)
    median_value = _median(sorted_prices)
    weighted_mean_value = _weighted_mean(filtered, now_ms)
    mean_value = sum(sorted_prices) / len(sorted_prices)
    sd = _stddev(sorted_prices, mean_value)
    cv = sd / mean_value if mean_value > 0 else 0.0

    size_factor = min(max(len(filtered) / _TARGET_SAMPLE_SIZE, 0.5), 1.0)
    cv_factor = min(max(1.0 - cv, 0.0), 1.0)
    alpha = size_factor * cv_factor
    fair_price = alpha * weighted_mean_value + (1 - alpha) * median_value

    sample_term = min(max(len(filtered) / _TARGET_SAMPLE_SIZE, 0.0), 1.0) * 45.0
    most_recent = max(ts for _, ts in samples)
    hours_since_last_sale = max(now_ms - most_recent, 0) / 3_600_000.0
    recency_term = min(max(1.0 - hours_since_last_sale / _STALENESS_CUTOFF_HOURS, 0.0), 1.0) * 35.0
    tightness_term = min(max(1.0 - cv, 0.0), 1.0) * 20.0
    confidence = int(min(max(sample_term + recency_term + tightness_term, 0.0), 100.0))

    volume_per_week = sum(1 for _, ts in filtered if now_ms - ts <= _WEEK_MILLIS)

    return {
        "fairPrice": fair_price,
        "median": median_value,
        "weightedMean": weighted_mean_value,
        "sampleCount": len(filtered),
        "volumePerWeek": volume_per_week,
        "confidence": confidence,
        "stddev": sd,
    }


# --- Aggregation driver ------------------------------------------------------

def fetch_auctions_ended() -> dict[str, Any]:
    with urllib.request.urlopen(AUCTIONS_ENDED_URL, timeout=30) as response:
        return json.loads(response.read())


def load_raw_state(path: Path = RAW_STATE_PATH) -> dict[str, list[list[int]]]:
    if not path.exists():
        return {}
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def apply_sales(
    raw_state: dict[str, list[list[int]]], auctions: list[dict[str, Any]], now_ms: int
) -> tuple[int, list[tuple[str, list[tuple[str, str]], int, int]]]:
    """Returns (applied count, modifier-bearing sales for `apply_modifier_deltas`)."""
    applied = 0
    modifier_sales: list[tuple[str, list[tuple[str, str]], int, int]] = []
    for sale in auctions:
        price = sale.get("price", 0)
        if price <= 0:
            continue
        decoded = decode_item_bytes(sale.get("item_bytes", ""))
        if decoded is None:
            continue
        item_id, signature, modifiers = decoded
        item_id = item_id.upper()
        timestamp = sale.get("timestamp") or now_ms

        # Item-level bucket must stay modifier-free (mirrors
        # AuctionSalesHistoryRepository.kt): a lowest-BIN listing is almost always a
        # plain copy, so mixing upgraded (reforged/starred/gemmed/scrolled/...) sale
        # prices in here inflates the fair price far above what a plain copy
        # actually sells for.
        if signature:
            _record(raw_state, f"{item_id}|{signature}", price, timestamp)
        else:
            _record(raw_state, item_id, price, timestamp)
        if 1 <= len(modifiers) <= 2:
            modifier_sales.append((item_id, modifiers, price, timestamp))
        applied += 1
    return applied, modifier_sales


def apply_modifier_deltas(
    modifier_raw_state: dict[str, list[list[int]]],
    raw_state: dict[str, list[list[int]]],
    modifier_sales: list[tuple[str, list[tuple[str, str]], int, int]],
) -> int:
    """Mirrors AuctionSalesHistoryRepository.recordModifierDeltas: for each 1-2-modifier
    sale, splits `salePrice - plainFairPrice` evenly across its modifiers into the pooled,
    cross-item `"kind:key"` bucket. Uses this *run's final* plain-item snapshot (in `raw_state`,
    already updated by `apply_sales` above) as the plain-price reference — an accepted
    approximation of the client's point-in-time online estimate, not a bug: both are proxies
    for "reference price near time of sale," and a plain item's fair price rarely swings
    sharply within one aggregator run.
    """
    applied = 0
    plain_price_cache: dict[str, float | None] = {}
    for item_id, modifiers, price, timestamp in modifier_sales:
        if item_id not in plain_price_cache:
            plain_result = compute_fair_price(raw_state.get(item_id, []), timestamp)
            plain_price_cache[item_id] = plain_result["fairPrice"] if plain_result else None
        plain_price = plain_price_cache[item_id]
        if plain_price is None:
            continue
        delta = round((price - plain_price) / len(modifiers))
        for kind, key in modifiers:
            _record(modifier_raw_state, f"{kind}:{key}", delta, timestamp)
        applied += 1
    return applied


def _record(raw_state: dict[str, list[list[int]]], key: str, price: int, timestamp: int) -> None:
    samples = raw_state.setdefault(key, [])
    samples.append([price, timestamp])
    cutoff = int(time.time() * 1000) - MAX_SAMPLE_AGE_MILLIS
    samples[:] = [s for s in samples if s[1] >= cutoff]
    if len(samples) > MAX_SAMPLES_PER_SKU:
        del samples[:len(samples) - MAX_SAMPLES_PER_SKU]
    if not samples:
        del raw_state[key]


def compute_snapshot(raw_state: dict[str, list[list[int]]], now_ms: int) -> dict[str, Any]:
    snapshot = {}
    for key, samples in raw_state.items():
        result = compute_fair_price(samples, now_ms)
        if result is not None:
            snapshot[key] = result
    return snapshot


def update_manifest(dataset_name: str, filename: str, content_json: str) -> None:
    """Bumps `data/index.json`'s entry for one dataset (version/sha256/url) if its
    published content actually changed. Both `fair_prices` and `modifier_values` route
    through this same function so the version bump stays automatic — this project has
    twice forgotten to hand-bump `index.json` after a dataset change, so any new
    published dataset should always go through here rather than a manual edit.
    """
    import hashlib

    if not INDEX_PATH.exists():
        return
    with open(INDEX_PATH, encoding="utf-8") as f:
        index = json.load(f)

    sha256 = hashlib.sha256(content_json.encode("utf-8")).hexdigest()
    current = index.get("datasets", {}).get(dataset_name, {})
    if current.get("sha256") == sha256:
        return  # content unchanged, don't bump the version for no reason

    index.setdefault("datasets", {})[dataset_name] = {
        "version": current.get("version", 0) + 1,
        "url": f"https://raw.githubusercontent.com/duetigh/saibon/master/data/{filename}",
        "sha256": sha256,
    }
    with open(INDEX_PATH, "w", encoding="utf-8") as f:
        json.dump(index, f, indent=2)
        f.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", help="Path to a cached auctions_ended response, instead of a live fetch")
    parser.add_argument("--dry-run", action="store_true", help="Compute and summarize, but don't write any files")
    args = parser.parse_args()

    if args.source:
        with open(args.source, encoding="utf-8") as f:
            response = json.load(f)
    else:
        response = fetch_auctions_ended()

    if not response.get("success"):
        print("auctions_ended request did not report success, aborting", file=sys.stderr)
        return 1

    now_ms = int(time.time() * 1000)
    raw_state = load_raw_state(RAW_STATE_PATH)
    modifier_raw_state = load_raw_state(MODIFIER_DELTAS_RAW_PATH)

    applied, modifier_sales = apply_sales(raw_state, response.get("auctions", []), now_ms)
    deltas_applied = apply_modifier_deltas(modifier_raw_state, raw_state, modifier_sales)

    snapshot = compute_snapshot(raw_state, now_ms)
    modifier_snapshot = compute_snapshot(modifier_raw_state, now_ms)

    print(
        f"Applied {applied} new sales ({deltas_applied} contributed a modifier delta). "
        f"Tracking {len(raw_state)} SKUs ({len(snapshot)} priced), "
        f"{len(modifier_raw_state)} modifiers ({len(modifier_snapshot)} priced)."
    )

    if args.dry_run:
        return 0

    RAW_STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(RAW_STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(raw_state, f, separators=(",", ":"))
    with open(MODIFIER_DELTAS_RAW_PATH, "w", encoding="utf-8") as f:
        json.dump(modifier_raw_state, f, separators=(",", ":"))

    fair_prices_json = json.dumps(snapshot, indent=2, sort_keys=True) + "\n"
    with open(FAIR_PRICES_PATH, "w", encoding="utf-8") as f:
        f.write(fair_prices_json)
    modifier_values_json = json.dumps(modifier_snapshot, indent=2, sort_keys=True) + "\n"
    with open(MODIFIER_VALUES_PATH, "w", encoding="utf-8") as f:
        f.write(modifier_values_json)

    update_manifest("fair_prices", "fair_prices.json", fair_prices_json)
    update_manifest("modifier_values", "modifier_values.json", modifier_values_json)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
