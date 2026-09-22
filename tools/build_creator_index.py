#!/usr/bin/env python3
"""Build the APK's small name index from reviewed, source-confirmed CSV exports.

Input columns: name, aliases (optional, pipe separated). Do not import unverified
names, media, avatar URLs, or private user data. Run at development time only.
"""
import argparse
import csv
import pathlib
import re
import unicodedata


def normalized(value):
    value = unicodedata.normalize("NFKD", value.casefold())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return " ".join(re.findall(r"[^\W_]+", value, re.UNICODE))


def build(paths, popular=None):
    records = {}
    if popular:
        text = popular.read_text(encoding="utf-8").split("private static final Creator[] CREATORS = {", 1)[1].split("};", 1)[0]
        for name, alias in re.findall(r'new Creator\("([^"\\]+)"(?:, "([^"\\]+)")?\)', text):
            records[normalized(name)] = (name, {alias} if alias else set())
    for path in paths:
        with path.open(newline="", encoding="utf-8-sig") as source:
            for row in csv.DictReader(source):
                name = (row.get("name") or "").strip()
                if not name or any(c in name for c in "\t\r\n"):
                    continue
                key = normalized(name)
                if not key:
                    continue
                aliases = [s.strip() for s in (row.get("aliases") or "").split("|")]
                aliases = {a for a in aliases if a and not any(c in a for c in "\t\r\n|")}
                if key not in records:
                    records[key] = (name, set())
                records[key][1].update(aliases)
    return [name + ("\t" + "|".join(sorted(aliases)) if aliases else "")
            for name, aliases in (records[key] for key in sorted(records))]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=pathlib.Path, nargs="*", help="reviewed source exports")
    parser.add_argument("--popular", type=pathlib.Path,
                        help="include the existing curated PopularCreatorRepository names")
    parser.add_argument("--output", type=pathlib.Path,
                        default=pathlib.Path("app/src/main/assets/onlyfap_creators.tsv"))
    args = parser.parse_args()
    if not args.csv and not args.popular:
        parser.error("provide reviewed CSV files or --popular")
    rows = build(args.csv, args.popular)
    args.output.write_text("# Reviewed creator names; name<TAB>aliases.\n" +
                           "\n".join(rows) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} creators to {args.output}")
