#!/usr/bin/env python3
"""Refresh ZEROCHILL's bundled OnlyFap creator-name index.

Development-time only. The tool reads the current bundled source configuration,
queries the configured OnlyHaven creator API, merges source-confirmed creators
with the reviewed seed already in the APK, and writes a compact name/alias TSV.

No media is downloaded and no runtime source behavior is changed.
"""

from __future__ import annotations

import argparse
import itertools
import json
import pathlib
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "app/src/main/assets/source_config_defaults.json"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/onlyfap_creators.tsv"
QUERY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"


def normalized(value: str) -> str:
    value = unicodedata.normalize("NFKD", (value or "").casefold())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return " ".join(re.findall(r"[^\W_]+", value, re.UNICODE))


def clean(value: object, max_length: int = 160) -> str:
    text = "" if value is None else str(value)
    text = " ".join(text.replace("\t", " ").replace("\r", " ").replace("\n", " ").split())
    return text[:max_length].strip()


def first_text(row: dict, *keys: str) -> str:
    for key in keys:
        value = clean(row.get(key))
        if value:
            return value
    return ""


def first_int(row: dict, *keys: str) -> int:
    for key in keys:
        value = row.get(key)
        if isinstance(value, bool) or value is None:
            continue
        try:
            return int(value)
        except (TypeError, ValueError):
            continue
    return -1


def load_seed(path: pathlib.Path) -> dict[str, tuple[str, set[str]]]:
    records: dict[str, tuple[str, set[str]]] = {}
    if not path.exists():
        return records
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        columns = raw.split("\t", 1)
        name = clean(columns[0])
        key = normalized(name)
        if not name or not key:
            continue
        aliases: set[str] = set()
        if len(columns) > 1:
            aliases.update(clean(alias) for alias in columns[1].split("|") if clean(alias))
        records[key] = (name, aliases)
    return records


def add_record(
    records: dict[str, tuple[str, set[str]]],
    name: str,
    aliases: set[str] | None = None,
) -> bool:
    name = clean(name)
    key = normalized(name)
    if not name or not key:
        return False
    previous = records.get(key)
    if previous is None:
        records[key] = (name, set(aliases or ()))
        return True
    previous[1].update(aliases or ())
    return False


def configured_onlyhaven(config_path: pathlib.Path) -> tuple[str, str, dict[str, str]]:
    root = json.loads(config_path.read_text(encoding="utf-8"))
    source = root["sources"]["onlyhaven"]
    if not source.get("enabled", True):
        raise RuntimeError("OnlyHaven is disabled in the bundled source configuration")
    base = clean(source.get("baseUrl"))
    route = clean((source.get("routes") or {}).get("creatorSearchApi"))
    if not base or not route:
        raise RuntimeError("OnlyHaven baseUrl or creatorSearchApi route is missing")
    headers = {
        str(key): str(value)
        for key, value in (source.get("requestHeaders") or {}).items()
        if key and value
    }
    user_agent = clean(source.get("userAgent"))
    if user_agent:
        headers["User-Agent"] = user_agent
    headers["Accept"] = "application/json,text/plain,*/*"
    return base, route, headers


def request_json(url: str, headers: dict[str, str], retries: int = 3) -> object:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=25) as response:
                payload = response.read(2_000_000)
            return json.loads(payload.decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            if attempt + 1 < retries:
                time.sleep(1.0 * (2 ** attempt))
    raise RuntimeError(f"Creator API request failed: {last_error}")


def creator_rows(payload: object) -> list[dict]:
    if isinstance(payload, list):
        return [row for row in payload if isinstance(row, dict)]
    if isinstance(payload, dict):
        for key in ("creators", "items", "results", "data"):
            value = payload.get(key)
            if isinstance(value, list):
                return [row for row in value if isinstance(row, dict)]
    return []


def query_terms() -> list[str]:
    single = list(QUERY_CHARS)
    double = ["".join(pair) for pair in itertools.product(QUERY_CHARS, repeat=2)]
    return [""] + single + double


def matches_query(query: str, name: str, creator_id: str) -> bool:
    wanted = normalized(query).replace(" ", "")
    if not wanted:
        return True
    named = normalized(name).replace(" ", "")
    identifier = normalized(creator_id).replace(" ", "")
    return wanted in named or wanted in identifier


def ingest_rows(
    rows: list[dict],
    query: str,
    records: dict[str, tuple[str, set[str]]],
    target: int,
) -> int:
    added = 0
    for row in rows:
        service = first_text(row, "service")
        creator_id = first_text(row, "id", "creatorId", "creator_id", "user")
        name = first_text(row, "displayName", "display_name", "name", "username") or creator_id
        if not service or not creator_id or not name or not matches_query(query, name, creator_id):
            continue

        post_count = first_int(row, "postCount", "post_count", "postsCount", "posts_count", "posts")
        dm_count = first_int(row, "dmCount", "dm_count", "dmsCount", "dms_count", "dms")
        if post_count == 0 and dm_count == 0:
            continue

        aliases: set[str] = set()
        for alias in (creator_id, first_text(row, "username"), first_text(row, "handle")):
            alias = clean(alias)
            if alias and normalized(alias) != normalized(name):
                aliases.add(alias)

        if add_record(records, name, aliases):
            added += 1
            if len(records) >= target:
                break
    return added


def fetch_onlyhaven(
    config_path: pathlib.Path,
    records: dict[str, tuple[str, set[str]]],
    target: int,
    page_size: int,
    pause: float,
    max_requests: int,
) -> tuple[int, int]:
    base, route, headers = configured_onlyhaven(config_path)
    harvested = 0
    requests = 0

    for query in query_terms():
        if len(records) >= target or requests >= max_requests:
            break

        offset = 0
        for _ in range(3):
            if len(records) >= target or requests >= max_requests:
                break

            relative = (
                route.replace("{query}", urllib.parse.quote(query, safe=""))
                .replace("{limit}", str(page_size))
                .replace("{offset}", str(offset))
            )
            url = urllib.parse.urljoin(base, relative)
            rows = creator_rows(request_json(url, headers))
            requests += 1
            if not rows:
                break

            harvested += ingest_rows(rows, query, records, target)
            offset += len(rows)

            # Most source searches return a bounded result set. Stop when this
            # page is shorter than requested or when the source repeats it.
            if len(rows) < page_size:
                break
            if pause > 0:
                time.sleep(pause)

        if pause > 0:
            time.sleep(pause)

    return harvested, requests


def write_index(path: pathlib.Path, records: dict[str, tuple[str, set[str]]]) -> None:
    lines = [
        "# Source-confirmed OnlyFap creator names; name<TAB>aliases.",
        "# Generated at development time from the reviewed seed and configured creator API.",
    ]
    for key in sorted(records):
        name, aliases = records[key]
        safe_aliases = sorted(
            {
                clean(alias)
                for alias in aliases
                if clean(alias) and normalized(alias) != normalized(name) and "|" not in alias
            },
            key=normalized,
        )
        lines.append(name + ("\t" + "|".join(safe_aliases) if safe_aliases else ""))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=pathlib.Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--target", type=int, default=10_000)
    parser.add_argument("--min-count", type=int, default=5_000)
    parser.add_argument("--page-size", type=int, default=100)
    parser.add_argument("--pause", type=float, default=0.05)
    parser.add_argument("--max-requests", type=int, default=700)
    args = parser.parse_args()

    if min(args.target, args.min_count, args.page_size, args.max_requests) < 1:
        parser.error("target, min-count, page-size and max-requests must be positive")
    if args.min_count > args.target:
        parser.error("min-count cannot exceed target")

    records = load_seed(args.output)
    seed_count = len(records)
    harvested, request_count = fetch_onlyhaven(
        args.config,
        records,
        args.target,
        min(args.page_size, 250),
        max(0.0, args.pause),
        args.max_requests,
    )

    if len(records) < args.min_count:
        raise SystemExit(
            f"Refusing to replace creator index: got {len(records):,}, "
            f"minimum is {args.min_count:,} after {request_count:,} source requests"
        )

    write_index(args.output, records)
    print(
        f"Wrote {len(records):,} creators to {args.output} "
        f"(seed {seed_count:,}, added {harvested:,}, requests {request_count:,})"
    )


if __name__ == "__main__":
    main()
