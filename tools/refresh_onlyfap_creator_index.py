#!/usr/bin/env python3
"""Refresh ZEROCHILL's bundled OnlyFap creator-name index.

Development-time only. The tool reads the APK's bundled source configuration,
collects public creator handles from Common Crawl plus source-confirmed names
from configured creator listings/search, merges them with the reviewed seed
already in the APK, and writes a compact name/alias TSV.

No media is downloaded and no runtime source behavior is changed.
"""

from __future__ import annotations

import argparse
import gzip
import itertools
import json
import pathlib
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import zlib
from html.parser import HTMLParser

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "app/src/main/assets/source_config_defaults.json"
DEFAULT_OUTPUT = ROOT / "app/src/main/assets/onlyfap_creators.tsv"
QUERY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

CREATOR_LINK_TEXT = re.compile(
    r"(?i)^(?:see|view)\s+(?:all\s+)?content\s+(?:of|from)\s+(.+)$"
)
RESERVED_FAPELLO_SLUG = re.compile(
    r"(?i)^(?:search|search_v2|s|new|hot|videos|trending|popular|ajax|video|welcome|"
    r"login|signup|sign-up|tags|random|forum|report|dmca|contacts|language|"
    r"privacy|terms|add-model|upload|posts|comments|recent-comments|2257|"
    r"what-is-fapello|daily-search-ranking|popular-videos|popular_videos|"
    r"video-player|forgot-password)$"
)
BLOCKED_MARKERS = (
    "cdn-cgi/challenge-platform",
    "cf-chl-",
    "checking your browser",
    "verify you are human",
    "attention required",
    "just a moment",
)
FAPELLO_CURSOR = re.compile(r"^# Fapello new listing next page:\s*([0-9]+)\s*$")
COMMON_CRAWL_INDEX_LIST = "https://index.commoncrawl.org/collinfo.json"
COMMON_CRAWL_INDEX_ID = re.compile(r"^CC-MAIN-[0-9]{4}-[0-9]{2}$")
ONLYFANS_HANDLE = re.compile(r"^[A-Za-z0-9_]{1,64}$")
ONLYFANS_RESERVED = {
    "about", "api", "bookmarks", "cards", "collections", "contact", "creators",
    "help", "home", "login", "messages", "my", "notifications", "payments",
    "privacy", "referrals", "search", "settings", "signup", "subscriptions",
    "terms",
}


def normalized(value: str) -> str:
    value = unicodedata.normalize("NFKD", (value or "").casefold())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return " ".join(re.findall(r"[^\W_]+", value, re.UNICODE))


def clean(value: object, max_length: int = 160) -> str:
    text = "" if value is None else str(value)
    text = " ".join(text.replace("\t", " ").replace("\r", " ").replace("\n", " ").split())
    return text[:max_length].strip()


def load_seed(path: pathlib.Path) -> tuple[dict[str, tuple[str, set[str]]], int]:
    records: dict[str, tuple[str, set[str]]] = {}
    fapello_next_page = 1
    if not path.exists():
        return records, fapello_next_page
    for raw in path.read_text(encoding="utf-8").splitlines():
        cursor = FAPELLO_CURSOR.fullmatch(raw)
        if cursor:
            fapello_next_page = max(1, int(cursor.group(1)))
            continue
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
    return records, fapello_next_page

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


def source_config(config_path: pathlib.Path, source_name: str) -> dict:
    root = json.loads(config_path.read_text(encoding="utf-8"))
    source = root["sources"][source_name]
    if not source.get("enabled", True):
        raise RuntimeError(f"{source_name} is disabled in the bundled source configuration")
    return source


def configured_headers(source: dict, json_response: bool = False) -> dict[str, str]:
    configured = source.get("ajaxHeaders" if json_response else "requestHeaders") or {}
    headers = {
        str(key): str(value)
        for key, value in configured.items()
        if key and value and str(key).lower() != "accept-encoding"
    }
    user_agent = clean(source.get("userAgent"))
    if user_agent:
        headers["User-Agent"] = user_agent
    headers["Accept"] = (
        "application/json,text/plain,*/*"
        if json_response
        else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    )
    return headers


def request_bytes(url: str, headers: dict[str, str], retries: int = 2) -> bytes:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=20) as response:
                payload = response.read(3_000_000)
                encoding = (response.headers.get("Content-Encoding") or "").lower()
            if encoding == "gzip":
                payload = gzip.decompress(payload)
            elif encoding == "deflate":
                try:
                    payload = zlib.decompress(payload)
                except zlib.error:
                    payload = zlib.decompress(payload, -zlib.MAX_WBITS)
            return payload
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, OSError) as error:
            last_error = error
            if attempt + 1 < retries:
                time.sleep(0.75 * (2 ** attempt))
    raise RuntimeError(f"source request failed: {last_error}")


def request_text(url: str, headers: dict[str, str], retries: int = 2) -> str:
    return request_bytes(url, headers, retries).decode("utf-8", errors="replace")


def request_json(url: str, headers: dict[str, str], retries: int = 2) -> object:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            return json.loads(request_text(url, headers, 1))
        except (RuntimeError, json.JSONDecodeError) as error:
            last_error = error
            if attempt + 1 < retries:
                time.sleep(0.75 * (2 ** attempt))
    raise RuntimeError(f"creator API request failed: {last_error}")


def looks_blocked(body: str) -> bool:
    lower = (body or "").lower()
    return any(marker in lower for marker in BLOCKED_MARKERS)


def fapello_model_slug(url: str, base_url: str) -> str:
    try:
        absolute = urllib.parse.urljoin(base_url, url)
        base = urllib.parse.urlparse(base_url)
        parsed = urllib.parse.urlparse(absolute)
        if parsed.scheme not in ("http", "https") or parsed.hostname != base.hostname:
            return ""
        parts = [part for part in parsed.path.split("/") if part]
        if len(parts) != 1:
            return ""
        slug = parts[0]
        if not slug or slug.lower().startswith("top-") or RESERVED_FAPELLO_SLUG.fullmatch(slug):
            return ""
        return slug
    except ValueError:
        return ""


class FapelloCreatorParser(HTMLParser):
    def __init__(self, base_url: str):
        super().__init__(convert_charrefs=True)
        self.base_url = base_url
        self.active_href: str | None = None
        self.active_text: list[str] = []
        self.creators: list[tuple[str, str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "a" or self.active_href is not None:
            return
        values = {key.lower(): value or "" for key, value in attrs}
        href = values.get("href", "").strip()
        if href:
            self.active_href = href
            self.active_text = []

    def handle_data(self, data: str) -> None:
        if self.active_href is not None:
            self.active_text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() != "a" or self.active_href is None:
            return
        text = clean(" ".join(self.active_text))
        match = CREATOR_LINK_TEXT.fullmatch(text)
        if match:
            name = clean(match.group(1))
            slug = fapello_model_slug(self.active_href, self.base_url)
            if name and slug:
                self.creators.append((name, slug))
        self.active_href = None
        self.active_text = []


def common_crawl_headers() -> dict[str, str]:
    return {
        "Accept": "application/json,text/plain,*/*",
        "User-Agent": (
            "ZEROCHILL-creator-catalog/1.0 "
            "(https://github.com/Addy37/CrazyShitAndroid)"
        ),
    }


def common_crawl_indexes(max_crawls: int) -> tuple[list[str], int]:
    payload = request_json(COMMON_CRAWL_INDEX_LIST, common_crawl_headers(), 3)
    if not isinstance(payload, list):
        raise RuntimeError("Common Crawl index list was malformed")
    indexes: list[str] = []
    for row in payload:
        if not isinstance(row, dict):
            continue
        value = clean(row.get("id"), 80)
        if COMMON_CRAWL_INDEX_ID.fullmatch(value):
            indexes.append(value)
            if len(indexes) >= max(1, max_crawls):
                break
    if not indexes:
        raise RuntimeError("Common Crawl returned no usable crawl indexes")
    return indexes, 1


def request_json_lines(
    url: str,
    headers: dict[str, str],
    retries: int = 2,
) -> list[dict]:
    body = request_text(url, headers, retries)
    rows: list[dict] = []
    for raw in body.splitlines():
        raw = raw.strip()
        if not raw:
            continue
        try:
            value = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if isinstance(value, dict):
            rows.append(value)
    return rows


def onlyfans_handle_from_url(value: object) -> str:
    url = clean(value, 500)
    if not url:
        return ""
    try:
        parsed = urllib.parse.urlparse(url)
    except ValueError:
        return ""
    host = (parsed.hostname or "").lower()
    if host not in ("onlyfans.com", "www.onlyfans.com"):
        return ""
    parts = [
        urllib.parse.unquote(part).strip()
        for part in parsed.path.split("/")
        if part.strip()
    ]
    if len(parts) != 1:
        return ""
    handle = parts[0]
    if not ONLYFANS_HANDLE.fullmatch(handle):
        return ""
    if handle.casefold() in ONLYFANS_RESERVED:
        return ""
    return handle


def common_crawl_query_url(
    index_id: str,
    *,
    page: int | None = None,
    show_pages: bool = False,
) -> str:
    params: list[tuple[str, str]] = [
        ("url", "onlyfans.com/*"),
        ("output", "json"),
        ("fl", "url"),
        ("filter", "status:200"),
        ("collapse", "urlkey"),
        ("pageSize", "1"),
    ]
    if show_pages:
        params.append(("showNumPages", "true"))
    if page is not None:
        params.append(("page", str(page)))
    return (
        "https://index.commoncrawl.org/"
        + urllib.parse.quote(index_id, safe="-")
        + "-index?"
        + urllib.parse.urlencode(params)
    )


def fetch_common_crawl(
    records: dict[str, tuple[str, set[str]]],
    target: int,
    max_crawls: int,
    max_pages_per_crawl: int,
    pause: float,
) -> tuple[int, int, list[str]]:
    """Harvest public OnlyFans profile handles from Common Crawl URL metadata."""

    indexes, requests = common_crawl_indexes(max_crawls)
    added = 0
    used_indexes: list[str] = []
    headers = common_crawl_headers()
    polite_pause = max(1.0, pause)

    for index_id in indexes:
        if len(records) >= target:
            break
        try:
            page_info = request_json(
                common_crawl_query_url(index_id, show_pages=True),
                headers,
                3,
            )
            requests += 1
        except RuntimeError as error:
            print(f"Skipping Common Crawl {index_id}: {error}")
            time.sleep(polite_pause * 2)
            continue

        if not isinstance(page_info, dict):
            print(f"Skipping Common Crawl {index_id}: invalid page count response")
            continue
        try:
            total_pages = max(0, int(page_info.get("pages", 0)))
        except (TypeError, ValueError):
            total_pages = 0
        if total_pages <= 0:
            print(f"Common Crawl {index_id}: no matching URL pages")
            continue

        used_indexes.append(index_id)
        pages_to_read = min(total_pages, max(1, max_pages_per_crawl))
        for page in range(pages_to_read):
            if len(records) >= target:
                break
            try:
                rows = request_json_lines(
                    common_crawl_query_url(index_id, page=page),
                    headers,
                    3,
                )
                requests += 1
            except RuntimeError as error:
                print(f"Common Crawl {index_id} page {page} failed: {error}")
                time.sleep(polite_pause * 2)
                continue

            new_this_page = 0
            for row in rows:
                handle = onlyfans_handle_from_url(row.get("url"))
                if not handle:
                    continue
                if add_record(records, handle):
                    added += 1
                    new_this_page += 1
                    if len(records) >= target:
                        break

            print(
                f"Common Crawl {index_id} page {page + 1}/{pages_to_read}: "
                f"{len(rows)} URL records, {new_this_page} new, {len(records)} total"
            )
            if len(records) < target:
                time.sleep(polite_pause)

    return added, requests, used_indexes


def fapello_listing_url(source: dict, listing: str, page: int) -> str:
    base = clean(source.get("baseUrl"))
    routes = source.get("routes") or {}
    if listing == "new":
        route = routes.get("listingNewFirst" if page == 1 else "listingNewPage", "")
    elif listing == "popular":
        route = routes.get("listingPopularFirst" if page == 1 else "listingPopularPage", "")
    else:
        route = routes.get("listingHotFirst" if page == 1 else "listingHotPage", "")
    route = clean(route).replace("{page}", str(page))
    return urllib.parse.urljoin(base, route)


def fetch_fapello(
    config_path: pathlib.Path,
    records: dict[str, tuple[str, set[str]]],
    target: int,
    max_requests: int,
    pause: float,
    start_page: int,
) -> tuple[int, int, int]:
    source = source_config(config_path, "fapello")
    base = clean(source.get("baseUrl"))
    if not base:
        raise RuntimeError("Fapello baseUrl is missing")
    headers = configured_headers(source, False)
    added = 0
    requests = 0
    next_new_page = max(1, start_page)

    for listing in ("new", "popular", "hot"):
        stale_pages = 0
        page = next_new_page if listing == "new" else 1
        while len(records) < target and requests < max_requests and stale_pages < 3:
            url = fapello_listing_url(source, listing, page)
            requests += 1
            try:
                body = request_text(url, headers)
                if looks_blocked(body):
                    raise RuntimeError("Fapello returned a browser challenge")
            except RuntimeError as error:
                stale_pages += 1
                print(f"Skipping Fapello {listing} page {page}: {error}")
                if listing == "new":
                    next_new_page = page + 1
                page += 1
                time.sleep(max(0.75, pause * 5))
                continue

            parser = FapelloCreatorParser(base)
            parser.feed(body)
            new_this_page = 0
            for name, slug in parser.creators:
                aliases = {slug} if normalized(slug) != normalized(name) else set()
                if add_record(records, name, aliases):
                    added += 1
                    new_this_page += 1

            print(
                f"Fapello {listing} page {page}: "
                f"{len(parser.creators)} creators, {new_this_page} new, {len(records)} total"
            )
            stale_pages = stale_pages + 1 if new_this_page == 0 else 0
            if listing == "new":
                next_new_page = page + 1
            page += 1
            if pause > 0:
                time.sleep(pause)

        if len(records) >= target or requests >= max_requests:
            break

    return added, requests, next_new_page


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


def ingest_onlyhaven_rows(
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
    source = source_config(config_path, "onlyhaven")
    base = clean(source.get("baseUrl"))
    route = clean((source.get("routes") or {}).get("creatorSearchApi"))
    if not base or not route:
        raise RuntimeError("OnlyHaven baseUrl or creatorSearchApi route is missing")
    headers = configured_headers(source, True)
    harvested = 0
    requests = 0
    failed_requests = 0
    consecutive_failures = 0

    for query in query_terms():
        if len(records) >= target or requests >= max_requests:
            break
        offset = 0
        for _ in range(2):
            if len(records) >= target or requests >= max_requests:
                break
            relative = (
                route.replace("{query}", urllib.parse.quote(query, safe=""))
                .replace("{limit}", str(page_size))
                .replace("{offset}", str(offset))
            )
            url = urllib.parse.urljoin(base, relative)
            try:
                rows = creator_rows(request_json(url, headers))
            except RuntimeError as error:
                failed_requests += 1
                consecutive_failures += 1
                requests += 1
                print(f"Skipping OnlyHaven query {query!r} at offset {offset}: {error}")
                if consecutive_failures >= 6:
                    print("Stopping OnlyHaven harvest after 6 consecutive source failures")
                    if failed_requests:
                        print(f"OnlyHaven transient failures skipped: {failed_requests}")
                    return harvested, requests
                time.sleep(max(0.75, pause * 5))
                break
            consecutive_failures = 0
            requests += 1
            if not rows:
                break

            harvested += ingest_onlyhaven_rows(rows, query, records, target)
            offset += len(rows)
            if len(rows) < page_size:
                break
            if pause > 0:
                time.sleep(pause)

    if failed_requests:
        print(f"OnlyHaven transient failures skipped: {failed_requests}")
    return harvested, requests


def write_index(
    path: pathlib.Path,
    records: dict[str, tuple[str, set[str]]],
    fapello_next_page: int,
    common_crawl_used: list[str],
) -> None:
    crawl_label = ",".join(common_crawl_used) if common_crawl_used else "none"
    lines = [
        "# Source-confirmed OnlyFap creator names; name<TAB>aliases.",
        "# Generated at development time from reviewed seed, Common Crawl URL metadata, and configured sources.",
        f"# Common Crawl indexes used: {crawl_label}",
        f"# Fapello new listing next page: {max(1, fapello_next_page)}",
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
    parser.add_argument("--target", type=int, default=20_000)
    parser.add_argument("--min-count", type=int, default=15_000)
    parser.add_argument("--page-size", type=int, default=50)
    parser.add_argument("--pause", type=float, default=0.10)
    parser.add_argument("--max-requests", type=int, default=1_000)
    parser.add_argument("--common-crawl-crawls", type=int, default=6)
    parser.add_argument("--common-crawl-max-pages", type=int, default=20)
    parser.add_argument("--common-crawl-pause", type=float, default=1.0)
    args = parser.parse_args()

    if min(
        args.target,
        args.min_count,
        args.page_size,
        args.max_requests,
        args.common_crawl_crawls,
        args.common_crawl_max_pages,
    ) < 1:
        parser.error("target, min-count, page-size and request limits must be positive")
    if args.min_count > args.target:
        parser.error("min-count cannot exceed target")
    if args.common_crawl_pause < 0:
        parser.error("common-crawl-pause cannot be negative")

    records, fapello_start_page = load_seed(args.output)
    seed_count = len(records)
    request_budget = args.max_requests

    common_crawl_added = 0
    common_crawl_requests = 0
    common_crawl_used: list[str] = []
    if len(records) < args.target and request_budget > 0:
        try:
            common_crawl_added, common_crawl_requests, common_crawl_used = fetch_common_crawl(
                records,
                args.target,
                args.common_crawl_crawls,
                min(args.common_crawl_max_pages, request_budget),
                args.common_crawl_pause,
            )
        except RuntimeError as error:
            print(f"Common Crawl catalog source unavailable: {error}")
    request_budget = max(0, request_budget - common_crawl_requests)

    fapello_added = 0
    fapello_requests = 0
    fapello_next_page = fapello_start_page
    if len(records) < args.target and request_budget > 0:
        try:
            fapello_added, fapello_requests, fapello_next_page = fetch_fapello(
                args.config,
                records,
                args.target,
                request_budget,
                max(0.0, args.pause),
                fapello_start_page,
            )
        except RuntimeError as error:
            print(f"Fapello catalog source unavailable: {error}")

    request_budget = max(0, request_budget - fapello_requests)
    onlyhaven_added = 0
    onlyhaven_requests = 0
    if len(records) < args.target and request_budget > 0:
        try:
            onlyhaven_added, onlyhaven_requests = fetch_onlyhaven(
                args.config,
                records,
                args.target,
                min(args.page_size, 250),
                max(0.0, args.pause),
                request_budget,
            )
        except RuntimeError as error:
            print(f"OnlyHaven catalog source unavailable: {error}")

    total_requests = common_crawl_requests + fapello_requests + onlyhaven_requests
    write_index(args.output, records, fapello_next_page, common_crawl_used)

    if len(records) < args.min_count:
        raise SystemExit(
            f"Catalog below promotion floor: got {len(records):,}, "
            f"minimum is {args.min_count:,} after {total_requests:,} source requests "
            f"(Common Crawl +{common_crawl_added:,}, Fapello +{fapello_added:,}, "
            f"OnlyHaven +{onlyhaven_added:,}). Partial progress was written."
        )

    print(
        f"Wrote {len(records):,} creators to {args.output} "
        f"(seed {seed_count:,}, Common Crawl +{common_crawl_added:,}, "
        f"Fapello +{fapello_added:,}, OnlyHaven +{onlyhaven_added:,}, "
        f"requests {total_requests:,}, Common Crawl indexes {len(common_crawl_used)}, "
        f"next Fapello page {fapello_next_page:,})"
    )


if __name__ == "__main__":
    main()
