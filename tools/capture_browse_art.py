import base64
import io
import json
import os
import time
from urllib.parse import urlparse

from PIL import Image, ImageOps
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


def norm(url: str) -> str:
    if not url:
        return ""
    url = url.split("#", 1)[0].split("?", 1)[0].rstrip("/")
    return url.lower()


def is_collection(url: str, marker: str) -> bool:
    try:
        p = urlparse(url)
        return p.netloc.endswith("crazyshit.com") and marker in p.path and p.path.rstrip("/") != marker.rstrip("/")
    except Exception:
        return False


def crop_jpeg(png_bytes: bytes) -> bytes:
    img = Image.open(io.BytesIO(png_bytes)).convert("RGB")
    img = ImageOps.fit(img, (TARGET_W, TARGET_H), method=Image.Resampling.LANCZOS, centering=(0.5, 0.45))
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=78, optimize=True, progressive=True)
    return out.getvalue()


def find_visual(driver, anchor):
    script = r"""
const a=arguments[0];
function area(el){const r=el.getBoundingClientRect();return Math.max(0,r.width)*Math.max(0,r.height)}
function useful(el){
  if(!el) return false;
  const r=el.getBoundingClientRect();
  if(r.width<90||r.height<55) return false;
  if(el.tagName==='IMG'){
    const s=(el.currentSrc||el.src||'').toLowerCase();
    if(s && !/(logo|avatar|sprite|icon|blank|placeholder)/.test(s)) return true;
  }
  try{
    const bg=(getComputedStyle(el).backgroundImage||'').toLowerCase();
    if(bg && bg!=='none' && !/(logo|avatar|sprite|icon|blank|placeholder)/.test(bg)) return true;
  }catch(e){}
  return false;
}
let n=a;
for(let d=0;d<7&&n;d++,n=n.parentElement){
  const els=[n,...n.querySelectorAll('img,[style*="background"],picture,figure,div,span')];
  let best=null,bestArea=0;
  for(const el of els){
    if(!useful(el)) continue;
    const ar=area(el);
    if(ar>bestArea){best=el;bestArea=ar;}
  }
  if(best) return best;
}
return a;
"""
    return driver.execute_script(script, anchor)


def collect(driver, kind, page_url, marker):
    print(f"Loading {kind}: {page_url}", flush=True)
    driver.get(page_url)
    time.sleep(3.0)

    # Walk the full document once so viewport/intersection lazy loaders get a chance to populate.
    height = driver.execute_script("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)") or 0
    step = 700
    y = 0
    while y <= height + step:
        driver.execute_script("window.scrollTo(0, arguments[0]);", y)
        time.sleep(0.12)
        new_h = driver.execute_script("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight)") or height
        height = max(height, new_h)
        y += step
    driver.execute_script("window.scrollTo(0,0)")
    time.sleep(0.5)

    anchors = driver.find_elements(By.CSS_SELECTOR, f'a[href*="{marker}"]')
    seen = set()
    out = []
    for idx, a in enumerate(anchors):
        try:
            href = a.get_attribute("href") or ""
            key = norm(href)
            if not key or key in seen or not is_collection(href, marker):
                continue
            title = (a.text or a.get_attribute("title") or "").strip()
            if not title:
                title = key.rsplit("/", 1)[-1].replace("-", " ").replace("_", " ").strip()
            seen.add(key)

            driver.execute_script("arguments[0].scrollIntoView({block:'center',inline:'center'});", a)
            time.sleep(0.18)
            visual = find_visual(driver, a)
            try:
                driver.execute_script("arguments[0].scrollIntoView({block:'center',inline:'center'});", visual)
            except Exception:
                pass
            time.sleep(0.12)
            png = visual.screenshot_as_png
            if not png or len(png) < 1000:
                png = a.screenshot_as_png
            jpg = crop_jpeg(png)
            out.append({
                "kind": kind,
                "title": title,
                "url": href,
                "key": key,
                "jpeg_base64": base64.b64encode(jpg).decode("ascii"),
            })
            print(f"  [{len(out):02d}] {title} -> {len(jpg)//1024} KB", flush=True)
        except Exception as exc:
            print(f"  skip {idx}: {exc}", flush=True)
    return out


def main():
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1200,1000")
    options.add_argument("--lang=en-US")
    options.add_argument("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0 Safari/537.36")

    driver = webdriver.Chrome(options=options)
    driver.set_page_load_timeout(35)
    all_items = []
    try:
        for kind, page, marker in PAGES:
            all_items.extend(collect(driver, kind, page, marker))
    finally:
        driver.quit()

    payload = {
        "generated_at": int(time.time()),
        "count": len(all_items),
        "items": all_items,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, separators=(",", ":"))
    print(f"Wrote {len(all_items)} bundled browse images to {OUT}", flush=True)
    if len(all_items) < 60:
        raise SystemExit(f"Too few browse cards captured: {len(all_items)}")


if __name__ == "__main__":
    main()
