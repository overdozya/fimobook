#!/usr/bin/env python3
"""Inventory and download FC Mobile SquadMaker visual assets.

The player snapshot contains direct player/card/class image URLs. The official
SquadMaker frontend constructs the remaining URLs from nation, league, team,
trait, skill, play-style, evolution and training identifiers. This collector
combines both sources into one reproducible manifest and downloads each unique
asset once.

Downloaded binaries are runtime data, not source code. Keep the output outside
Git and publish it through object storage/CDN rather than MariaDB BLOB columns.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import random
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


SQUAD_MAKER_URL = "https://fcmobile.nexon.com/DataCenterWeb/SquadMaker"
APP_LOADER_URL = "https://ssl.nexon.com/s1/fcm/mobile/sqmaker/app.js"
APP_CSS_URL = "https://ssl.nexon.com/s1/fcm/mobile/sqmaker/app.css"
NEXON_ASSET_ROOT = "https://fco.vod.nexoncdn.co.kr/jade_assets"
NEXON_STAGE_ROOT = (
    "https://fco.vod.nexoncdn.co.kr/jade_assets_stage_bQ4IlcltxH6c8s5"
)
SQUAD_MAKER_STATIC_ROOT = "https://ssl.nexon.com/s2/game/fc/mobile/fcm_sqMaker"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151 Safari/537.36"
)
ASSET_SUFFIXES = {
    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
    ".woff", ".woff2", ".json",
}
CARD_COLORS_URL = f"{NEXON_STAGE_ROOT}/card_colors/card_colors.json"
FONT_URLS = (
    "https://fco.vod.nexoncdn.co.kr/fonts/FCOAllSans-Regular.woff2",
    "https://fco.vod.nexoncdn.co.kr/fonts/FCOAllSans-Medium.woff2",
    "https://fco.vod.nexoncdn.co.kr/fonts/FCOAllSans-Bold.woff2",
)


class AssetCollectionError(RuntimeError):
    """Raised when an inventory cannot be constructed safely."""


@dataclass
class Asset:
    source_url: str
    local_path: str
    categories: set[str] = field(default_factory=set)
    required: bool = False

    def as_json(self) -> dict[str, Any]:
        return {
            "sourceUrl": self.source_url,
            "localPath": self.local_path,
            "categories": sorted(self.categories),
            "required": self.required,
        }


@dataclass(frozen=True)
class DownloadOptions:
    output_dir: Path
    timeout_seconds: float
    retries: int
    workers: int


class AssetInventory:
    def __init__(self) -> None:
        self._assets: dict[str, Asset] = {}

    def add(self, category: str, url: Any, *, required: bool = False) -> None:
        if not isinstance(url, str) or not url.startswith(("http://", "https://")):
            return
        normalized = normalize_url(url)
        parsed = urllib.parse.urlsplit(normalized)
        if Path(parsed.path).suffix.lower() not in ASSET_SUFFIXES:
            return
        asset = self._assets.get(normalized)
        if asset is None:
            asset = Asset(normalized, local_path_for_url(normalized))
            self._assets[normalized] = asset
        asset.categories.add(category)
        asset.required = asset.required or required

    def values(self) -> list[Asset]:
        return sorted(self._assets.values(), key=lambda item: item.source_url)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def atomic_write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False, indent=2)
        output.write("\n")
    temporary.replace(path)


def normalize_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url.strip())
    return urllib.parse.urlunsplit(
        (parsed.scheme.lower(), parsed.netloc.lower(), parsed.path, parsed.query, "")
    )


def safe_path_segment(segment: str) -> str:
    decoded = urllib.parse.unquote(segment)
    safe = re.sub(r"[^A-Za-z0-9._-]+", "_", decoded)
    if safe in {"", ".", ".."}:
        return "_"
    return safe


def local_path_for_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    segments = [safe_path_segment(segment) for segment in parsed.path.split("/") if segment]
    if not segments:
        segments = ["index"]
    if parsed.query:
        query_hash = hashlib.sha256(parsed.query.encode("utf-8")).hexdigest()[:12]
        leaf = Path(segments[-1])
        segments[-1] = f"{leaf.stem}-{query_hash}{leaf.suffix}"
    return str(Path("files") / safe_path_segment(parsed.netloc) / Path(*segments))


def fetch_text(url: str, timeout_seconds: float = 30.0) -> str:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "text/html,*/*"},
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return response.read().decode("utf-8", errors="replace")


def discover_frontend_sources() -> tuple[str, str, str]:
    page = fetch_text(SQUAD_MAKER_URL)
    loader_matches = re.findall(
        r'<script[^>]+src="([^"]*/fcm/mobile/sqmaker/app\.js(?:\?[^\"]*)?)"',
        page,
        flags=re.IGNORECASE,
    )
    css_matches = re.findall(
        r'<link[^>]+href="([^"]*/fcm/mobile/sqmaker/app\.css(?:\?[^\"]*)?)"',
        page,
        flags=re.IGNORECASE,
    )
    loader_url = loader_matches[-1] if loader_matches else APP_LOADER_URL
    css_url = css_matches[-1] if css_matches else APP_CSS_URL
    loader = fetch_text(loader_url)
    bundle_match = re.search(r'base\s*\+\s*"([^"]+\.js)"', loader)
    if not bundle_match:
        raise AssetCollectionError("Could not discover the current SquadMaker bundle")
    bundle_url = urllib.parse.urljoin(loader_url, bundle_match.group(1))
    return loader_url, bundle_url, css_url


def extract_trait_names(bundle: str) -> dict[int, str]:
    match = re.search(r"traitNames:\{([^}]+)\}", bundle)
    if not match:
        raise AssetCollectionError("Official trait image mapping was not found")
    return {
        int(key): value
        for key, value in re.findall(r'(\d+):"([A-Z0-9_]+)"', match.group(1))
    }


def extract_skill_icons(bundle: str) -> dict[int, str]:
    pairs = re.findall(
        r'\{"skillid":(\d+),"icon":"([A-Za-z0-9_]+)"\}', bundle
    )
    if not pairs:
        raise AssetCollectionError("Official skill image mapping was not found")
    return {int(skill_id): icon for skill_id, icon in pairs}


def extract_play_style_icons(bundle: str) -> set[str]:
    return set(
        re.findall(
            r'"idStr":"PLAYSTYLE_[^"]+"[^{}]{0,500}"icon":"([A-Za-z0-9_]+)"',
            bundle,
        )
    )


def extract_team_badge_paths(bundle: str) -> set[str]:
    return set(re.findall(r'"imgurl":"(assets/team_badge/[^"]+\.png)"', bundle))


def extract_formation_names(bundle: str) -> set[str]:
    match = re.search(r'sa=\{(.*?)\},[A-Za-z_$][A-Za-z0-9_$]*=', bundle)
    if not match:
        return set()
    return set(re.findall(r'"([A-Za-z0-9_]+)":\[', match.group(1)))


def extract_fixed_css_images(css: str) -> set[str]:
    return set(
        re.findall(
            r'https://[^\s\)\"\']+\.(?:png|jpe?g|gif|webp|svg|ico)',
            css,
            flags=re.IGNORECASE,
        )
    )


def source_class_ids(card: dict[str, Any]) -> set[str]:
    classes = card.get("sourceClasses")
    if not isinstance(classes, list):
        return set()
    return {
        str(item.get("id"))
        for item in classes
        if isinstance(item, dict) and item.get("id")
    }


def build_inventory(
    snapshot_dir: Path,
    bundle: str,
    css: str,
) -> tuple[list[Asset], dict[str, Any]]:
    cards_path = snapshot_dir / "cards.json"
    init_path = snapshot_dir / "raw" / "init.json"
    if not cards_path.exists() or not init_path.exists():
        raise AssetCollectionError(
            f"Snapshot must contain cards.json and raw/init.json: {snapshot_dir}"
        )

    cards = read_json(cards_path)
    init_response = read_json(init_path)
    if not isinstance(cards, list):
        raise AssetCollectionError("cards.json root was not an array")
    result_data = init_response.get("ResultData", {})
    if not isinstance(result_data, dict):
        raise AssetCollectionError("raw/init.json did not contain ResultData")

    inventory = AssetInventory()
    nation_ids: set[int] = set()
    league_ids: set[int] = set()
    team_ids: set[int] = set()
    wc22_team_ids: set[int] = set()
    player_years: set[int] = set()
    used_trait_ids: set[int] = set()
    used_skill_ids: set[int] = set()
    used_play_styles: set[str] = set()

    for card in cards:
        if not isinstance(card, dict):
            continue
        player = card.get("player")
        if not isinstance(player, dict):
            continue
        inventory.add("player", player.get("pimage"), required=True)
        inventory.add("card_background", player.get("bimage"), required=True)
        for class_info in card.get("sourceClasses") or []:
            if isinstance(class_info, dict):
                inventory.add("class_logo", class_info.get("image"), required=True)

        for target, value in (
            (nation_ids, player.get("nationality")),
            (league_ids, player.get("leagueid")),
            (team_ids, player.get("teamid")),
            (player_years, player.get("PlayerYear")),
        ):
            try:
                target.add(int(value))
            except (TypeError, ValueError):
                pass

        if "PROGRAM_FMK_WC22" in source_class_ids(card):
            try:
                wc22_team_ids.add(int(player.get("teamid")))
            except (TypeError, ValueError):
                pass

        for trait in player.get("Trait") or []:
            if isinstance(trait, dict):
                try:
                    used_trait_ids.add(int(trait.get("id")))
                except (TypeError, ValueError):
                    pass
        for skill in player.get("skillInfo") or []:
            if isinstance(skill, dict):
                try:
                    used_skill_ids.add(int(skill.get("id")))
                except (TypeError, ValueError):
                    pass
        for play_style in player.get("staticPlayStyles") or []:
            if isinstance(play_style, str) and play_style:
                used_play_styles.add(play_style)

    for class_info in result_data.get("ClassInfos") or []:
        if isinstance(class_info, dict):
            inventory.add("class_logo", class_info.get("image"), required=True)
    for nation in result_data.get("Nations") or []:
        if isinstance(nation, dict):
            try:
                nation_ids.add(int(nation.get("countryid")))
            except (TypeError, ValueError):
                pass
    for league in result_data.get("Leagues") or []:
        if isinstance(league, dict):
            try:
                league_ids.add(int(league.get("leagueId")))
            except (TypeError, ValueError):
                pass

    for nation_id in nation_ids:
        inventory.add(
            "nation_flag", f"{NEXON_ASSET_ROOT}/flags/flags_64x64/F_{nation_id}.png"
        )
    for league_id in league_ids:
        inventory.add(
            "league_logo",
            f"{NEXON_ASSET_ROOT}/league_logos/league_logos_256x256/L{league_id}.png",
        )
    for team_id in team_ids:
        inventory.add(
            "team_logo",
            f"{NEXON_ASSET_ROOT}/team_logos/team_logos_64x64/L{team_id}.png",
        )
    for team_id in wc22_team_ids:
        inventory.add(
            "world_cup_team_logo",
            f"{NEXON_ASSET_ROOT}/team_logos/wc22/L{team_id}.png",
        )

    trait_names = extract_trait_names(bundle)
    for trait_id, icon_name in trait_names.items():
        inventory.add(
            "trait",
            f"{NEXON_ASSET_ROOT}/traits/playertraits_ICN_TRAIT_{icon_name}.png",
            required=trait_id in used_trait_ids,
        )

    skill_icons = extract_skill_icons(bundle)
    for skill_id, icon_name in skill_icons.items():
        inventory.add(
            "skill",
            f"{NEXON_ASSET_ROOT}/skill/{icon_name}.png",
            required=skill_id in used_skill_ids,
        )

    play_style_icons = extract_play_style_icons(bundle) | used_play_styles
    for icon_name in play_style_icons:
        for size in (32, 64, 128):
            inventory.add(
                "play_style",
                f"{NEXON_ASSET_ROOT}/playstyle/playstyle_{size}/{icon_name}.png",
                required=icon_name in used_play_styles,
            )

    for level in range(16):
        inventory.add(
            "evolution",
            f"{NEXON_ASSET_ROOT}/ui/element/playercard/CraftingIcon/"
            f"CRAFTING_ICON_LV_{level}__KR.png",
            required=True,
        )
    for level in range(11):
        inventory.add(
            "training",
            f"{NEXON_ASSET_ROOT}/ui/element/playercard/TrainingIcon/{level}.png",
            required=True,
        )
        inventory.add(
            "enhance_filter",
            f"{NEXON_ASSET_ROOT}/ui/element/playercard/EnhanceIcon/"
            f"ENHANCE_ICON_LV_S_{level}.png",
        )

    for year in player_years:
        inventory.add(
            "player_fallback",
            f"{NEXON_ASSET_ROOT}/static/players/players_{year}/p0.png",
            required=True,
        )
    inventory.add(
        "fallback_or_badge",
        f"{NEXON_STAGE_ROOT}/flags/flags_64x64/notfound.png",
        required=True,
    )
    for relative in (
        "league_logos/league_logos_256x256/notfound.png",
        "team_logos/team_logos_64x64/notfound.png",
    ):
        # The current official frontend references these paths, but the CDN
        # itself returns 403. Preserve them as optional inventory evidence.
        inventory.add("fallback_or_badge", f"{NEXON_ASSET_ROOT}/{relative}")
    for relative in (
        "ui/element/playercard/Notrade/no_trade.png",
        "ui/element/playercard/MP/FCM26_MP_s.png",
    ):
        inventory.add("fallback_or_badge", f"{NEXON_ASSET_ROOT}/{relative}", required=True)

    for badge_path in extract_team_badge_paths(bundle):
        inventory.add(
            "team_badge",
            f"{NEXON_STAGE_ROOT}/{badge_path.removeprefix('assets/')}",
        )
    formation_names = extract_formation_names(bundle)
    for formation_name in formation_names:
        inventory.add(
            "formation",
            f"{SQUAD_MAKER_STATIC_ROOT}/formation/{formation_name}.png",
        )
    for url in extract_fixed_css_images(css):
        inventory.add("official_ui", url)
    for font_url in FONT_URLS:
        inventory.add("font", font_url, required=True)
    inventory.add("card_theme", CARD_COLORS_URL, required=True)

    assets = inventory.values()
    category_counts: Counter[str] = Counter()
    for asset in assets:
        category_counts.update(asset.categories)
    details = {
        "cardCount": len(cards),
        "nationIdCount": len(nation_ids),
        "leagueIdCount": len(league_ids),
        "teamIdCount": len(team_ids),
        "worldCupTeamIdCount": len(wc22_team_ids),
        "traitMappingCount": len(trait_names),
        "usedTraitIdCount": len(used_trait_ids),
        "skillMappingCount": len(skill_icons),
        "usedSkillIdCount": len(used_skill_ids),
        "playStyleIconCount": len(play_style_icons),
        "usedPlayStyleCount": len(used_play_styles),
        "formationCount": len(formation_names),
        "uniqueAssetCount": len(assets),
        "categoryCounts": dict(sorted(category_counts.items())),
    }
    return assets, details


def detect_asset_format(content: bytes) -> str | None:
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if content.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if content.startswith((b"GIF87a", b"GIF89a")):
        return "gif"
    if content.startswith(b"RIFF") and content[8:12] == b"WEBP":
        return "webp"
    if content.startswith(b"\x00\x00\x01\x00"):
        return "ico"
    if content.startswith(b"PK\x03\x04"):
        # The official EnhanceIcon URLs currently use a .png suffix but return
        # a ZIP container holding a PVR texture. Preserve that upstream binary
        # exactly and expose its real format in the manifest.
        return "zip-pvr"
    if content.startswith(b"wOF2"):
        return "woff2"
    if content.startswith(b"wOFF"):
        return "woff"
    stripped = content.lstrip()
    if stripped.startswith(b"<svg") or (stripped.startswith(b"<?xml") and b"<svg" in stripped[:500]):
        return "svg"
    if stripped.startswith((b"{", b"[")):
        return "json"
    return None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def existing_result(asset: Asset, output_dir: Path) -> dict[str, Any] | None:
    path = output_dir / asset.local_path
    if not path.is_file() or path.stat().st_size <= 0:
        return None
    with path.open("rb") as source:
        header = source.read(512)
    asset_format = detect_asset_format(header)
    if asset_format is None:
        return None
    return {
        **asset.as_json(),
        "status": "existing",
        "httpStatus": None,
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
        "format": asset_format,
        "contentType": None,
        "etag": None,
        "lastModified": None,
        "error": None,
    }


def download_asset(asset: Asset, options: DownloadOptions) -> dict[str, Any]:
    existing = existing_result(asset, options.output_dir)
    if existing is not None:
        return existing

    target = options.output_dir / asset.local_path
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    last_error: Exception | None = None
    last_status: int | None = None

    for attempt in range(options.retries + 1):
        try:
            request = urllib.request.Request(
                asset.source_url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Accept": "*/*",
                    "Referer": SQUAD_MAKER_URL,
                },
            )
            with urllib.request.urlopen(request, timeout=options.timeout_seconds) as response:
                last_status = response.status
                content = response.read()
                asset_format = detect_asset_format(content[:512])
                if not content or asset_format is None:
                    raise AssetCollectionError(
                        f"Response was not a supported visual asset ({response.headers.get('Content-Type')})"
                    )
                with temporary.open("wb") as output:
                    output.write(content)
                temporary.replace(target)
                return {
                    **asset.as_json(),
                    "status": "downloaded",
                    "httpStatus": response.status,
                    "bytes": len(content),
                    "sha256": hashlib.sha256(content).hexdigest(),
                    "format": asset_format,
                    "contentType": response.headers.get("Content-Type"),
                    "etag": response.headers.get("ETag"),
                    "lastModified": response.headers.get("Last-Modified"),
                    "error": None,
                }
        except urllib.error.HTTPError as error:
            last_status = error.code
            last_error = error
            if error.code in {400, 401, 403, 404}:
                break
        except (OSError, AssetCollectionError) as error:
            last_error = error
        if attempt < options.retries:
            time.sleep(min(8.0, (2**attempt) + random.uniform(0.05, 0.4)))

    temporary.unlink(missing_ok=True)
    status = "missing_upstream" if last_status in {403, 404} else "failed"
    return {
        **asset.as_json(),
        "status": status,
        "httpStatus": last_status,
        "bytes": 0,
        "sha256": None,
        "format": None,
        "contentType": None,
        "etag": None,
        "lastModified": None,
        "error": str(last_error) if last_error else "unknown download error",
    }


def append_journal(path: Path, result: dict[str, Any], lock: threading.Lock) -> None:
    line = json.dumps(result, ensure_ascii=False, separators=(",", ":"))
    with lock:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as output:
            output.write(line + "\n")


def summarize_results(results: Iterable[dict[str, Any]]) -> dict[str, Any]:
    result_list = list(results)
    statuses = Counter(str(item["status"]) for item in result_list)
    content_hashes = [str(item["sha256"]) for item in result_list if item.get("sha256")]
    unique_content_hashes = set(content_hashes)
    categories: dict[str, Counter[str]] = defaultdict(Counter)
    for item in result_list:
        for category in item["categories"]:
            categories[category][str(item["status"])] += 1
    return {
        "assetCount": len(result_list),
        "totalBytes": sum(int(item.get("bytes") or 0) for item in result_list),
        "storedAssetCount": len(content_hashes),
        "uniqueContentHashCount": len(unique_content_hashes),
        "duplicateContentAssetCount": len(content_hashes) - len(unique_content_hashes),
        "statusCounts": dict(sorted(statuses.items())),
        "categoryStatusCounts": {
            category: dict(sorted(counts.items()))
            for category, counts in sorted(categories.items())
        },
        "requiredFailureCount": sum(
            1
            for item in result_list
            if item["required"] and item["status"] in {"failed", "missing_upstream"}
        ),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-dir", type=Path, required=True)
    parser.add_argument(
        "--output-dir", type=Path, default=Path("data/fcmobile/assets")
    )
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--limit", type=int)
    parser.add_argument(
        "--category",
        action="append",
        default=[],
        help="Only download this category; repeat for multiple categories",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.workers < 1 or args.workers > 16:
        raise AssetCollectionError("--workers must be between 1 and 16")
    if args.retries < 0:
        raise AssetCollectionError("--retries must be zero or greater")
    if args.limit is not None and args.limit < 1:
        raise AssetCollectionError("--limit must be greater than zero")

    loader_url, bundle_url, css_url = discover_frontend_sources()
    print(f"frontend bundle: {bundle_url}", flush=True)
    bundle = fetch_text(bundle_url, args.timeout)
    css = fetch_text(css_url, args.timeout)
    assets, details = build_inventory(args.snapshot_dir, bundle, css)

    selected_categories = set(args.category)
    if selected_categories:
        assets = [asset for asset in assets if asset.categories & selected_categories]
    if args.limit is not None:
        assets = assets[: args.limit]

    inventory_document = {
        "formatVersion": 1,
        "generatedAt": utc_now(),
        "snapshotDir": str(args.snapshot_dir.resolve()),
        "frontend": {
            "loaderUrl": loader_url,
            "bundleUrl": bundle_url,
            "cssUrl": css_url,
        },
        "details": details,
        "selectedAssetCount": len(assets),
        "selectedCategories": sorted(selected_categories),
        "assets": [asset.as_json() for asset in assets],
    }
    manifests_dir = args.output_dir / "manifests"
    atomic_write_json(manifests_dir / "inventory.json", inventory_document)
    print(json.dumps(details, ensure_ascii=False, indent=2), flush=True)
    if args.inventory_only:
        return 0

    options = DownloadOptions(
        output_dir=args.output_dir,
        timeout_seconds=args.timeout,
        retries=args.retries,
        workers=args.workers,
    )
    journal_path = manifests_dir / "download-journal.jsonl"
    journal_path.unlink(missing_ok=True)
    journal_lock = threading.Lock()
    results: list[dict[str, Any]] = []
    completed = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_to_asset = {
            executor.submit(download_asset, asset, options): asset for asset in assets
        }
        for future in concurrent.futures.as_completed(future_to_asset):
            result = future.result()
            results.append(result)
            append_journal(journal_path, result, journal_lock)
            completed += 1
            if completed % 250 == 0 or completed == len(assets):
                counts = Counter(item["status"] for item in results)
                print(
                    f"assets {completed}/{len(assets)} "
                    f"downloaded={counts['downloaded']} existing={counts['existing']} "
                    f"missing={counts['missing_upstream']} failed={counts['failed']}",
                    flush=True,
                )

    results.sort(key=lambda item: item["sourceUrl"])
    summary = summarize_results(results)
    manifest = {
        "formatVersion": 1,
        "completedAt": utc_now(),
        "snapshotDir": str(args.snapshot_dir.resolve()),
        "frontend": inventory_document["frontend"],
        "inventoryDetails": details,
        "summary": summary,
        "assets": results,
    }
    atomic_write_json(manifests_dir / "latest.json", manifest)
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)
    return 1 if summary["requiredFailureCount"] else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssetCollectionError, OSError, json.JSONDecodeError) as error:
        print(f"asset collection failed: {error}", file=sys.stderr)
        raise SystemExit(1)
