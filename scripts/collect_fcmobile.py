#!/usr/bin/env python3
"""Collect FC Mobile SquadMaker player-card snapshots.

The collector intentionally keeps the upstream page responses untouched under
``raw/all`` and ``raw/classes``. The unfiltered listing is the authoritative
card set. Class searches are preserved as many-to-many filter memberships;
the upstream player object does not expose one canonical class id.

No Nexon cookie or verification token is written to disk.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import http.cookiejar
import json
import math
import random
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


BASE_URL = "https://fcmobile.nexon.com"
SQUAD_MAKER_URL = f"{BASE_URL}/DataCenterWeb/SquadMaker"
AJAX_URL = f"{BASE_URL}/datacenterweb/SquadMakerAjaxInfo"
INIT_URL = f"{AJAX_URL}?strMethod=Init"
TOKEN_COOKIE = "_dpvmTldhsfkdls_xhfptm"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151 Safari/537.36"
)


class CollectionError(RuntimeError):
    """Raised when an upstream response cannot be safely collected."""


@dataclass(frozen=True)
class CollectorOptions:
    output_dir: Path
    delay_seconds: float
    timeout_seconds: float
    retries: int
    requested_classes: frozenset[str]
    max_pages: int | None
    workers: int


class SquadMakerClient:
    def __init__(self, timeout_seconds: float, retries: int) -> None:
        self.timeout_seconds = timeout_seconds
        self.retries = retries
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies)
        )
        self.init_data: dict[str, Any] | None = None

    def initialize(self) -> dict[str, Any]:
        self.cookies.clear()
        page_request = urllib.request.Request(
            SQUAD_MAKER_URL,
            headers={"User-Agent": USER_AGENT, "Accept": "text/html"},
        )
        self._open(page_request)
        response = self._post_json(INIT_URL, {})
        self._require_success(response, "Init")
        self.init_data = response
        return response

    def search_class(self, class_id: str, page: int) -> dict[str, Any]:
        payload: dict[str, str | int] = {
            "strMethod": "PlayerSearchList",
            "n8Cid": 0,
            "n4PageNo": page,
            "strPlayerName": "",
            "strClass": (
                json.dumps([class_id], ensure_ascii=False, separators=(",", ":"))
                if class_id
                else ""
            ),
            "strLeagueId": "",
            "strPositionCode": "",
            "strTeamId": "",
            "strNationality": "",
            "n1Force": -1,
            "n1SearchType": 0,
            "n1Trade": 0,
            "n4OvrMin": "",
            "n4OvrMax": "",
            "n8PriceMin": "",
            "n8PriceMax": "",
            "n1MainFoot": "",
            "n1WeakFoot": "",
            "n4SkillMoveLevel": "",
            "n4Stamina": "",
            "n4HeightMin": "",
            "n4HeightMax": "",
            "n4WeightMin": "",
            "n4WeightMax": "",
            "strSkillMove": "",
            "strSkillInfo": "",
            "strTraitCode": "",
            "staticPlayStyles": "",
        }
        operation = f"PlayerSearchList class={class_id or 'ALL'} page={page}"
        last_error: CollectionError | None = None
        for attempt in range(self.retries + 1):
            response = self._post_json(AJAX_URL, payload)
            try:
                self._require_success(response, operation)
                return response
            except CollectionError as error:
                last_error = error
                if attempt >= self.retries:
                    break
                wait_seconds = min(30.0, (2**attempt) + random.uniform(0.1, 0.8))
                print(
                    f"retry {attempt + 1}/{self.retries} with a new session "
                    f"after {wait_seconds:.1f}s: {error}",
                    file=sys.stderr,
                )
                time.sleep(wait_seconds)
                self.initialize()
        raise last_error or CollectionError(f"{operation} failed")

    def _post_json(self, url: str, payload: dict[str, str | int]) -> dict[str, Any]:
        last_error: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                token = self._verification_token()
                form = dict(payload)
                form["__RequestVerificationToken"] = token
                body = urllib.parse.urlencode(form).encode("utf-8")
                request = urllib.request.Request(
                    url,
                    data=body,
                    headers={
                        "Accept": "application/json, text/plain, */*",
                        "Content-Type": "application/x-www-form-urlencoded",
                        "Origin": BASE_URL,
                        "Referer": SQUAD_MAKER_URL,
                        "User-Agent": USER_AGENT,
                        "X-Requested-With": "XMLHttpRequest",
                    },
                    method="POST",
                )
                raw = self._open(request)
                parsed = json.loads(raw.decode("utf-8"))
                if not isinstance(parsed, dict):
                    raise CollectionError("Upstream JSON root was not an object")
                return parsed
            except (CollectionError, json.JSONDecodeError, OSError) as error:
                last_error = error
                if attempt >= self.retries:
                    break
                wait_seconds = min(30.0, (2**attempt) + random.uniform(0.1, 0.8))
                print(
                    f"retry {attempt + 1}/{self.retries} after {wait_seconds:.1f}s: {error}",
                    file=sys.stderr,
                )
                time.sleep(wait_seconds)
        raise CollectionError(f"Request failed after retries: {last_error}")

    def _open(self, request: urllib.request.Request) -> bytes:
        try:
            with self.opener.open(request, timeout=self.timeout_seconds) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            content_type = error.headers.get("Content-Type", "unknown")
            raise CollectionError(
                f"HTTP {error.code} from {request.full_url} ({content_type})"
            ) from error

    def _verification_token(self) -> str:
        for cookie in self.cookies:
            if cookie.name == TOKEN_COOKIE:
                return cookie.value
        raise CollectionError("Verification-token cookie was not issued")

    @staticmethod
    def _require_success(response: dict[str, Any], operation: str) -> None:
        if response.get("ResultCode") != 1:
            message = response.get("ResultMsg", "unknown upstream error")
            raise CollectionError(f"{operation} failed: {message}")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def safe_component(value: str) -> str:
    safe = re.sub(r"[^A-Za-z0-9_.-]+", "_", value)
    return safe or "unknown"


def atomic_write_json(path: Path, value: Any, *, compact: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump(
            value,
            output,
            ensure_ascii=False,
            indent=None if compact else 2,
            separators=(",", ":") if compact else None,
        )
        output.write("\n")
    temporary.replace(path)


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def select_classes(
    init_response: dict[str, Any], requested: frozenset[str]
) -> list[dict[str, Any]]:
    result_data = init_response.get("ResultData")
    if not isinstance(result_data, dict):
        raise CollectionError("Init response did not contain ResultData")
    classes = result_data.get("ClassInfos")
    if not isinstance(classes, list):
        raise CollectionError("Init response did not contain ClassInfos")

    valid = [item for item in classes if isinstance(item, dict) and item.get("id")]
    known = {str(item["id"]) for item in valid}
    unknown = sorted(requested - known)
    if unknown:
        raise CollectionError(f"Unknown class id(s): {', '.join(unknown)}")
    if requested:
        valid = [item for item in valid if str(item["id"]) in requested]
    return valid


def page_path(output_dir: Path, class_id: str, page: int) -> Path:
    return (
        output_dir
        / "raw"
        / "classes"
        / safe_component(class_id)
        / f"page-{page:05d}.json"
    )


def all_page_path(output_dir: Path, page: int) -> Path:
    return output_dir / "raw" / "all" / f"page-{page:05d}.json"


def collect_all(client: SquadMakerClient, options: CollectorOptions) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    first_path = all_page_path(options.output_dir, 1)
    if first_path.exists():
        first_response = read_json(first_path)
        if int(first_response.get("ResultData", {}).get("totalCount") or 0) == 0:
            first_response = client.search_class("", 1)
            atomic_write_json(first_path, first_response, compact=True)
    else:
        first_response = client.search_class("", 1)
        atomic_write_json(first_path, first_response, compact=True)

    data = first_response.get("ResultData", {})
    total_count = int(data.get("totalCount") or 0)
    page_size = int(data.get("pageSize") or 10)
    if total_count <= 0:
        raise CollectionError("Unfiltered player search unexpectedly returned zero cards")
    if page_size <= 0:
        raise CollectionError(f"Invalid unfiltered page size: {page_size}")
    total_pages = math.ceil(total_count / page_size) if total_count else 1
    if options.max_pages is not None:
        total_pages = min(total_pages, options.max_pages)

    missing_pages = [
        page
        for page in range(2, total_pages + 1)
        if not all_page_path(options.output_dir, page).exists()
    ]
    if options.workers == 1:
        for page in missing_pages:
            time.sleep(options.delay_seconds)
            response = client.search_class("", page)
            atomic_write_json(
                all_page_path(options.output_dir, page), response, compact=True
            )
            if page % 50 == 0 or page == total_pages:
                print(f"  all cards page {page}/{total_pages}", flush=True)
    elif missing_pages:
        local_session = threading.local()

        def collect_page(page: int) -> int:
            worker_client = getattr(local_session, "client", None)
            if worker_client is None:
                worker_client = SquadMakerClient(
                    options.timeout_seconds, options.retries
                )
                worker_client.initialize()
                local_session.client = worker_client
            time.sleep(options.delay_seconds)
            response = worker_client.search_class("", page)
            atomic_write_json(
                all_page_path(options.output_dir, page), response, compact=True
            )
            return page

        completed = 0
        with concurrent.futures.ThreadPoolExecutor(
            max_workers=options.workers
        ) as executor:
            futures = [executor.submit(collect_page, page) for page in missing_pages]
            for future in concurrent.futures.as_completed(futures):
                page = future.result()
                completed += 1
                if completed % 50 == 0 or completed == len(missing_pages):
                    print(
                        f"  all cards resumed {completed}/{len(missing_pages)} "
                        f"(latest page {page}/{total_pages})",
                        flush=True,
                    )

    cards: list[dict[str, Any]] = []
    for page in range(1, total_pages + 1):
        source_path = all_page_path(options.output_dir, page)
        response = read_json(source_path)
        page_data = response.get("ResultData", {})
        if int(page_data.get("totalCount") or 0) != total_count:
            raise CollectionError(f"Unfiltered total count changed at page {page}")
        players = page_data.get("PlayerList")
        if not isinstance(players, list):
            raise CollectionError(f"Missing PlayerList for unfiltered page {page}")
        source_observed_at = datetime.fromtimestamp(
            source_path.stat().st_mtime, timezone.utc
        ).isoformat(timespec="seconds")
        cards.extend(
            {"sourceObservedAt": source_observed_at, "player": player}
            for player in players
            if isinstance(player, dict)
        )

    observed_cids = [
        int(item["player"]["cid"])
        for item in cards
        if item["player"].get("cid") is not None
    ]
    summary = {
        "reportedCardCount": total_count,
        "collectedCardCount": len(cards),
        "uniqueCidCount": len(set(observed_cids)),
        "pagesCollected": total_pages,
        "complete": (
            options.max_pages is None
            and len(cards) == total_count
            and len(set(observed_cids)) == total_count
        ),
    }
    return summary, cards


def collect_class(
    client: SquadMakerClient,
    class_info: dict[str, Any],
    options: CollectorOptions,
) -> dict[str, Any]:
    class_id = str(class_info["id"])
    first_path = page_path(options.output_dir, class_id, 1)
    if first_path.exists():
        first_response = read_json(first_path)
    else:
        first_response = client.search_class(class_id, 1)
        atomic_write_json(first_path, first_response, compact=True)

    data = first_response.get("ResultData", {})
    total_count = int(data.get("totalCount") or 0)
    page_size = int(data.get("pageSize") or 10)
    if page_size <= 0:
        raise CollectionError(f"Invalid page size for {class_id}: {page_size}")
    total_pages = math.ceil(total_count / page_size) if total_count else 1
    if options.max_pages is not None:
        total_pages = min(total_pages, options.max_pages)

    for page in range(2, total_pages + 1):
        target = page_path(options.output_dir, class_id, page)
        if target.exists():
            continue
        time.sleep(options.delay_seconds)
        response = client.search_class(class_id, page)
        atomic_write_json(target, response, compact=True)
        returned = len(response.get("ResultData", {}).get("PlayerList", []))
        print(f"  page {page}/{total_pages}: {returned} cards", flush=True)

    cards: list[dict[str, Any]] = []
    for page in range(1, total_pages + 1):
        response = read_json(page_path(options.output_dir, class_id, page))
        players = response.get("ResultData", {}).get("PlayerList")
        if not isinstance(players, list):
            raise CollectionError(f"Missing PlayerList for {class_id} page {page}")
        cards.extend(player for player in players if isinstance(player, dict))

    observed_cids = [int(player["cid"]) for player in cards if player.get("cid") is not None]
    summary = {
        "classId": class_id,
        "className": class_info.get("name"),
        "classImageUrl": class_info.get("image"),
        "reportedCardCount": total_count,
        "collectedCardCount": len(cards),
        "uniqueCidCount": len(set(observed_cids)),
        "pagesCollected": total_pages,
        "complete": options.max_pages is None and len(cards) == total_count,
    }
    atomic_write_json(
        options.output_dir / "classes" / f"{safe_component(class_id)}.json",
        {"class": class_info, "summary": summary, "players": cards},
        compact=True,
    )
    return summary


def build_snapshot(
    output_dir: Path,
    classes: Iterable[dict[str, Any]],
    summaries: list[dict[str, Any]],
    all_summary: dict[str, Any],
    all_players: list[dict[str, Any]],
) -> dict[str, Any]:
    class_memberships: dict[int, list[dict[str, Any]]] = {}
    class_list = list(classes)

    for class_info in class_list:
        class_id = str(class_info["id"])
        source = read_json(
            output_dir / "classes" / f"{safe_component(class_id)}.json"
        )
        for player in source["players"]:
            cid = int(player["cid"])
            class_memberships.setdefault(cid, []).append(class_info)

    by_cid = {int(item["player"]["cid"]): item for item in all_players}
    cards = [
        {
            "sourceClasses": class_memberships.get(cid, []),
            "sourceObservedAt": item["sourceObservedAt"],
            "player": item["player"],
        }
        for cid, item in sorted(by_cid.items())
    ]
    atomic_write_json(output_dir / "cards.json", cards, compact=True)

    duplicate_classes = {
        cid: memberships
        for cid, memberships in class_memberships.items()
        if len(memberships) > 1
    }
    atomic_write_json(
        output_dir / "class-membership-overlaps.json",
        [
            {"cid": cid, "classIds": [str(item["id"]) for item in memberships]}
            for cid, memberships in sorted(duplicate_classes.items())
        ],
        compact=True,
    )

    pid_values = {
        int(item["player"]["pid"])
        for item in cards
        if item["player"].get("pid") is not None
    }
    manifest = {
        "formatVersion": 2,
        "state": "COMPLETE" if all_summary["complete"] else "INCOMPLETE",
        "completedAt": utc_now(),
        "classCount": len(class_list),
        "classSummaries": summaries,
        "allCardsSummary": all_summary,
        "reportedCardCountSum": sum(item["reportedCardCount"] for item in summaries),
        "collectedCardCountSum": sum(item["collectedCardCount"] for item in summaries),
        "uniqueCardCount": len(cards),
        "uniquePlayerCount": len(pid_values),
        "duplicateCidCount": all_summary["collectedCardCount"] - all_summary["uniqueCidCount"],
        "multiClassCardCount": len(duplicate_classes),
        "unclassifiedCardCount": sum(1 for item in cards if not item["sourceClasses"]),
        "complete": all_summary["complete"] and all(item["complete"] for item in summaries),
    }
    atomic_write_json(output_dir / "manifest.json", manifest)
    return manifest


def collect(options: CollectorOptions) -> dict[str, Any]:
    options.output_dir.mkdir(parents=True, exist_ok=True)
    atomic_write_json(
        options.output_dir / "manifest.json",
        {
            "formatVersion": 2,
            "state": "COLLECTING",
            "startedAt": utc_now(),
            "completedAt": None,
            "complete": False,
        },
    )
    client = SquadMakerClient(options.timeout_seconds, options.retries)
    print("initializing anonymous SquadMaker session", flush=True)
    init_response = client.initialize()
    atomic_write_json(options.output_dir / "raw" / "init.json", init_response)

    classes = select_classes(init_response, options.requested_classes)
    atomic_write_json(
        options.output_dir / "metadata.json",
        {
            "collectedAt": utc_now(),
            "update": init_response.get("ResultData", {}).get("update"),
            "today": init_response.get("ResultData", {}).get("today"),
            "ClassInfos": init_response.get("ResultData", {}).get("ClassInfos", []),
            "Leagues": init_response.get("ResultData", {}).get("Leagues", []),
            "Nations": init_response.get("ResultData", {}).get("Nations", []),
            "SkillMoves": init_response.get("ResultData", {}).get("SkillMoves", []),
        },
    )

    summaries: list[dict[str, Any]] = []
    for index, class_info in enumerate(classes, start=1):
        class_id = str(class_info["id"])
        print(
            f"[{index}/{len(classes)}] {class_info.get('name')} ({class_id})",
            flush=True,
        )
        class_cache = options.output_dir / "classes" / f"{safe_component(class_id)}.json"
        if index > 1 and not class_cache.exists():
            time.sleep(options.delay_seconds)
        summary = collect_class(client, class_info, options)
        summaries.append(summary)
        print(
            "  collected="
            f"{summary['collectedCardCount']} reported={summary['reportedCardCount']} "
            f"complete={summary['complete']}",
            flush=True,
        )

    print("collecting authoritative unfiltered card list", flush=True)
    all_summary, all_players = collect_all(client, options)
    print(
        f"  collected={all_summary['collectedCardCount']} "
        f"reported={all_summary['reportedCardCount']} complete={all_summary['complete']}",
        flush=True,
    )
    return build_snapshot(
        options.output_dir, classes, summaries, all_summary, all_players
    )


def parse_args() -> CollectorOptions:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("data/fcmobile/snapshots") / datetime.now().strftime("%Y%m%d-%H%M%S"),
        help="Snapshot directory. Reusing it resumes already-written pages.",
    )
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=0.75,
        help="Minimum delay between upstream page requests (default: 0.75).",
    )
    parser.add_argument("--timeout-seconds", type=float, default=30.0)
    parser.add_argument("--retries", type=int, default=4)
    parser.add_argument(
        "--workers",
        type=int,
        default=1,
        help="Concurrent sessions for the unfiltered initial snapshot (1 or 2).",
    )
    parser.add_argument(
        "--class-id",
        action="append",
        default=[],
        help="Collect only this class id. Repeat to select multiple classes.",
    )
    parser.add_argument(
        "--max-pages",
        type=int,
        default=None,
        help="Development-only page cap. A capped snapshot is marked incomplete.",
    )
    args = parser.parse_args()
    if args.delay_seconds < 0:
        parser.error("--delay-seconds must be non-negative")
    if args.retries < 0:
        parser.error("--retries must be non-negative")
    if args.workers not in (1, 2):
        parser.error("--workers must be 1 or 2")
    if args.max_pages is not None and args.max_pages < 1:
        parser.error("--max-pages must be at least 1")
    return CollectorOptions(
        output_dir=args.output_dir,
        delay_seconds=args.delay_seconds,
        timeout_seconds=args.timeout_seconds,
        retries=args.retries,
        requested_classes=frozenset(args.class_id),
        max_pages=args.max_pages,
        workers=args.workers,
    )


def main() -> int:
    try:
        options = parse_args()
        manifest = collect(options)
        print(json.dumps({
            "formatVersion": manifest["formatVersion"],
            "uniqueCardCount": manifest["uniqueCardCount"],
            "uniquePlayerCount": manifest["uniquePlayerCount"],
            "duplicateCidCount": manifest["duplicateCidCount"],
            "multiClassCardCount": manifest["multiClassCardCount"],
            "unclassifiedCardCount": manifest["unclassifiedCardCount"],
            "complete": manifest["complete"],
        }, ensure_ascii=False, indent=2))
        return 0 if manifest["complete"] else 2
    except (CollectionError, OSError, ValueError) as error:
        print(f"collection failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
