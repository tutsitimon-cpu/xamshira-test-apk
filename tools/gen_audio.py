# -*- coding: utf-8 -*-
"""Azure Speech orqali savol-javoblarni MP3 ga aylantiradi.

Ishga tushirish (GitHub Actions ichida):
  AZURE_SPEECH_KEY=... AZURE_SPEECH_REGION=... python tools/gen_audio.py www/index.html www/data out/ [old_manifest.json] [limit]

- Faqat yangi yoki matni o'zgargan fayllar yaratiladi (manifest.json dagi hash bo'yicha).
- out/manifest.json — barcha fayllar va ularning hash'lari.
- limit — bir ishga tushirishda nechta fayl yaratish (bepul limitdan oshmaslik uchun).
"""
import json, os, sys, time
import urllib.request, urllib.error

sys.path.insert(0, os.path.dirname(__file__))
from audio_texts import build

KEY = os.environ["AZURE_SPEECH_KEY"].strip()
REGION = os.environ["AZURE_SPEECH_REGION"].strip()
URL = f"https://{REGION}.tts.speech.microsoft.com/cognitiveservices/v1"
FORMAT = "audio-24khz-48kbitrate-mono-mp3"


class QuotaStop(Exception):
    pass


def synth(ssml_text):
    req = urllib.request.Request(URL, data=ssml_text.encode("utf-8"), method="POST", headers={
        "Ocp-Apim-Subscription-Key": KEY,
        "Content-Type": "application/ssml+xml",
        "X-Microsoft-OutputFormat": FORMAT,
        "User-Agent": "tibbiy-yordamchi-audio",
    })
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            body = e.read()[:300]
            if e.code == 403 or (e.code == 429 and attempt >= 3):
                # bepul limit tugagan bo'lishi mumkin — tayyor fayllarni saqlab, to'xtaymiz
                raise QuotaStop(f"{e.code}: {body!r}")
            if e.code in (429, 500, 502, 503, 504):
                wait = 10 * (attempt + 1)
                print(f"  {e.code}, {wait}s kutamiz...", flush=True)
                time.sleep(wait)
                continue
            raise SystemExit(f"Azure xatosi {e.code}: {body!r}")
        except urllib.error.URLError as e:
            print(f"  tarmoq xatosi: {e}, qayta urinamiz", flush=True)
            time.sleep(10)
    raise SystemExit("Azure bir necha urinishdan keyin ham javob bermadi")


def main():
    index_html, data_dir, out_dir = sys.argv[1:4]
    old = {}
    if len(sys.argv) > 4 and os.path.exists(sys.argv[4]):
        old = json.load(open(sys.argv[4], encoding="utf-8"))
    limit = int(sys.argv[5]) if len(sys.argv) > 5 else 10**9
    os.makedirs(out_dir, exist_ok=True)
    items = build(index_html, data_dir)
    manifest = dict(old)
    todo = [it for it in items if old.get(it["name"]) != it["hash"]]
    print(f"Jami {len(items)}, yaratiladigan {len(todo)}, bu safar ko'pi bilan {limit}", flush=True)
    done = 0; chars = 0
    names = {it["name"] for it in items}
    def save_manifest():
        m = {k: v for k, v in manifest.items() if k in names}
        json.dump(m, open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    for it in todo[:limit]:
        try:
            audio = synth(it["ssml"])
        except QuotaStop as e:
            print(f"Azure limiti tugadi yoki rad etdi ({e}). Tayyorlari saqlanadi, qolgani keyingi safar.", flush=True)
            break
        open(os.path.join(out_dir, it["name"]), "wb").write(audio)
        manifest[it["name"]] = it["hash"]
        done += 1; chars += it["chars"]
        print(f"[{done}] {it['name']} {len(audio)//1024} KB", flush=True)
        if done % 20 == 0:
            save_manifest()
        time.sleep(2.5)  # bepul tarif: daqiqasiga 20 so'rovdan oshmaslik uchun
    # ro'yxatdan chiqib ketgan fayllarni manifestdan olib tashlaymiz
    save_manifest()
    left = len([it for it in items if manifest.get(it["name"]) != it["hash"]])
    print(f"Tayyor: {done} ta fayl, {chars} belgi. Qolgani: {left}", flush=True)


if __name__ == "__main__":
    main()
