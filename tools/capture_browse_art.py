import base64
import io
import json
import os
import time
from urllib.parse import urlparse

import requests
from PIL import Image, ImageDraw, ImageFont, ImageOps
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options

PAGES = [
    ("categories", "https://crazyshit.com/categories/", "/category/"),
    ("series", "https://crazyshit.com/series/", "/series/"),
]
OUT = os.environ.get("BROWSE_ART_OUT", "browse-art-bundle.json")
TARGET_W = 480
TARGET_H = 270
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0 Safari/537.36"


def norm(url: str) -> str:
    if not url:
        return ""
    return url.split("#", 1)[0].split("?", 1)[0].rstrip("/").lower()


def is_collection(url: str, marker: str) -> bool:
    try:
        p = urlparse(url)
        return p.netloc.endswith("crazyshit.com") and marker in p.path and p.path.rstrip("/") != marker.rstrip("/")
    except Exception:
        return False


def as_jpeg(raw: bytes) -> bytes:
    img = Image.open(io.BytesIO(raw)).convert("RGB")
    img = ImageOps.fit(img, (TARGET_W, TARGET_H), method=Image.Resampling.LANCZOS, centering=(0.5, 0.45))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=76, optimize=True, progressive=True)
    return out.getvalue()


def placeholder(title: str, kind: str) -> bytes:
    # Permanent local fallback if the site omits a particular card image during capture.
    seed = abs(hash((title, kind)))
    base = (28 + seed % 38, 28 + (seed // 41) % 38, 34 + (seed // 83) % 42)
    img = Image.new("RGB", (TARGET_W, TARGET_H), base)
    draw = ImageDraw.Draw(img)
    for y in range(TARGET_H):
        shade = int(22 * y / TARGET_H)
        draw.line((0, y, TARGET_W, y), fill=(max(0, base[0]-shade), max(0, base[1]-shade), max(0, base[2]-shade)))
    label = (title or kind).upper().strip()
    words = label.split()
    lines, current = [], ""
    for word in words:
        test = (current + " " + word).strip()
        if len(test) > 22 and current:
            lines.append(current)
            current = word
        else:
            current = test
    if current:
        lines.append(current)
    lines = lines[:3]
    y = 92 - (len(lines)-1)*15
    for line in lines:
        bbox = draw.textbbox((0, 0), line)
        x = max(20, (TARGET_W - (bbox[2]-bbox[0])) // 2)
        draw.text((x, y), line, fill=(235, 235, 238))
        y += 30
    out = io.BytesIO()
    img.save(out, "JPEG", quality=74, optimize=True, progressive=True)
    return out.getvalue()


def sweep(driver):
    height = driver.execute_script("return Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)") or 0
    y = 0
    for _ in range(36):
        driver.execute_script("window.scrollTo(0,arguments[0])", y)
        time.sleep(0.10)
        height = max(height, driver.execute_script("return Math.max(document.body.scrollHeight,document.documentElement.scrollHeight)") or height)
        y += 720
        if y > height + 720:
            break
    driver.execute_script("window.scrollTo(0,0)")
    time.sleep(0.5)


def collect_anchors(driver, marker):
    out, seen = [], set()
    for a in driver.find_elements(By.CSS_SELECTOR, f'a[href*="{marker}"]'):
        try:
            href = a.get_attribute("href") or ""
            key = norm(href)
            if not key or key in seen or not is_collection(href, marker):
                continue
            title = (a.text or a.get_attribute("title") or "").strip()
            if not title:
                title = key.rsplit("/", 1)[-1].replace("-", " ").replace("_", " ").strip()
            seen.add(key)
            out.append({"url": href, "key": key, "title": title})
        except Exception:
            continue
    return out


def collect_thumbs(driver):
    js = r"""
const out=[]; const seen=new Set();
const add=u=>{try{u=new URL(u,document.baseURI).href}catch(e){return}; if(!/media\.crazyshit\.com\/thumbs\//i.test(u)||seen.has(u))return;seen.add(u);out.push(u)};
for(const i of document.images){add(i.currentSrc||i.src||i.getAttribute('data-src')||i.getAttribute('data-original')||'')}
for(const e of document.querySelectorAll('*')){try{const bg=getComputedStyle(e).backgroundImage||'';for(const m of bg.matchAll(/url\(["']?([^"')]+)["']?\)/ig))add(m[1])}catch(err){}}
try{for(const r of performance.getEntriesByType('resource'))add(r.name||'')}catch(e){}
return out;
"""
    try:
        return driver.execute_script(js) or []
    except Exception:
        return []


def collect(driver, kind, page_url, marker, session):
    print(f"Loading {kind}: {page_url}", flush=True)
    driver.get(page_url)
    time.sleep(2.5)
    sweep(driver)
    anchors = collect_anchors(driver, marker)
    thumbs = collect_thumbs(driver)
    print(f"Found {len(anchors)} {kind} cards and {len(thumbs)} loaded CDN thumbs", flush=True)

    out = []
    for idx, card in enumerate(anchors):
        jpg = b""
        source = thumbs[idx] if idx < len(thumbs) else ""
        if source:
            try:
                response = session.get(source, timeout=15, headers={"Referer": page_url})
                if response.ok and len(response.content) > 1000:
                    jpg = as_jpeg(response.content)
            except Exception as exc:
                print(f"  download failed {idx}: {exc}", flush=True)
        if not jpg:
            jpg = placeholder(card["title"], kind)
            source = "embedded-placeholder"
        out.append({
            "kind": kind,
            "title": card["title"],
            "url": card["url"],
            "key": card["key"],
            "source": source,
            "jpeg_base64": base64.b64encode(jpg).decode("ascii"),
        })
        print(f"  [{idx+1:02d}/{len(anchors):02d}] {card['title']} -> {len(jpg)//1024} KB", flush=True)
    return out


def main():
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1200,1000")
    options.add_argument("--lang=en-US")
    options.add_argument(f"--user-agent={UA}")

    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"})
    driver = webdriver.Chrome(options=options)
    driver.set_page_load_timeout(30)
    all_items = []
    try:
        for kind, page, marker in PAGES:
            all_items.extend(collect(driver, kind, page, marker, session))
    finally:
        driver.quit()

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"generated_at": int(time.time()), "count": len(all_items), "items": all_items}, f, separators=(",", ":"))
    print(f"Wrote {len(all_items)} bundled browse images to {OUT}", flush=True)
    if len(all_items) < 60:
        raise SystemExit(f"Too few browse cards captured: {len(all_items)}")


if __name__ == "__main__":
    main()
