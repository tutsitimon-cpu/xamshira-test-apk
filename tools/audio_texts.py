# -*- coding: utf-8 -*-
"""Ilovadagi savol-javoblardan ovozga aylantiriladigan matnlar ro'yxatini tuzadi.

Har bir element: {"name": "<fayl nomi>.mp3", "ssml": "<SSML>", "chars": N}
Fayl nomlari ilova ichida ham xuddi shu qoida bilan hosil qilinadi:
  oral-<toifa kaliti>-<id>.mp3      (masalan oral-oliy-toifa-12.mp3)
  test-<mutaxassislik>-<id>.mp3     (masalan test-hamshiralik-ishi-5.mp3)
"""
import json, re, sys, hashlib
from xml.sax.saxutils import escape

VOICE = "uz-UZ-MadinaNeural"
ORAL_SCRIPTS = [("oliy-toifa", "oral-data-oliy"), ("1-toifa", "oral-data-birinchi"), ("2-toifa", "oral-data-ikkinchi"),
                # patronaj: faqat yangi (boshqa bankdan olinmagan) savollar; nom "audio" maydonidan olinadi
                ("oliy-toifa", "oral-data-pat-oliy"), ("1-toifa", "oral-data-pat-birinchi"), ("2-toifa", "oral-data-pat-ikkinchi")]
TEST_SPECIALTIES = ["hamshiralik-ishi", "hamshiralik-patronaj"]


def clean_lines(text):
    """Matnni gaplar ro'yxatiga ajratadi: ro'yxat belgilarini olib tashlaydi."""
    out = []
    for line in text.replace("\r", "").split("\n"):
        line = re.sub(r"^\s*[-•*–—]\s*", "", line).strip()
        if line:
            out.append(line)
    return out


def para(lines, pause_ms=450):
    parts = []
    for ln in lines:
        parts.append(escape(ln))
        parts.append(f'<break time="{pause_ms}ms"/>')
    return "".join(parts)


def ssml(body):
    return ("<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='uz-UZ'>"
            f"<voice name='{VOICE}'><prosody rate='-6%'>{body}</prosody></voice></speak>")


def build(index_html, data_dir):
    s = open(index_html, encoding="utf-8").read()
    items = []
    for key, script_id in ORAL_SCRIPTS:
        m = re.search(r'<script id="%s" type="application/json">(.*?)</script>' % script_id, s, re.S)
        for x in json.loads(m.group(1)):
            if x.get("src"):
                continue  # ovozi asl bankdagi fayldan olinadi
            q, a = x["uz_lat"]["q"], x["uz_lat"]["a"]
            body = (f"Savol. <break time='300ms'/>{para(clean_lines(q), 400)}"
                    f"<break time='900ms'/>Javob. <break time='300ms'/>{para(clean_lines(a))}")
            items.append({"name": x.get("audio") or f"oral-{key}-{x['id']}.mp3", "ssml": ssml(body), "chars": len(q) + len(a)})
    for sp in TEST_SPECIALTIES:
        data = json.load(open(f"{data_dir}/{sp}.json", encoding="utf-8"))
        for x in data:
            q = x["uz_lat"]["q"]; ans = x["uz_lat"]["opts"][x["correct_index"]]
            body = (f"Savol. <break time='300ms'/>{para(clean_lines(q), 400)}"
                    f"<break time='900ms'/>Toʻgʻri javob: <break time='300ms'/>{escape(ans)}")
            items.append({"name": f"test-{sp}-{x['id']}.mp3", "ssml": ssml(body), "chars": len(q) + len(ans)})
    for it in items:
        it["hash"] = hashlib.sha1(it["ssml"].encode("utf-8")).hexdigest()[:12]
    return items


if __name__ == "__main__":
    items = build(sys.argv[1], sys.argv[2])
    print(len(items), "ta fayl,", sum(i["chars"] for i in items), "belgi")
