# -*- coding: utf-8 -*-
"""tools_src.py dagi matnlardan 4 tilli JSON yasab, www/index.html ichidagi
<script id="tools-data" type="application/json"> blokini yangilaydi.
Ishga tushirish:  python3 tools/build_tools.py
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import tools_src as S
from translit import cyr, norm_lat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def L(t):
    uz, ru, en = t
    lat = norm_lat(uz)
    return {"uz_lat": lat, "uz_cyr": cyr(uz), "ru": ru, "en": en}


def interp(rows):
    return [{"min": a, "max": b, "lvl": lvl, "text": L(t)} for a, b, lvl, t in rows]


data = {
    "ui": {k: L(v) for k, v in S.UI.items()},
    "colors": S.INTERP_LEVEL,
    "scales": [{
        "key": s["key"], "title": L(s["title"]), "desc": L(s["desc"]),
        "items": [{"t": L(it["t"]), "o": [{"v": v, "t": L(t)} for v, t in it["o"]]} for it in s["items"]],
        "interp": interp(s["interp"]),
    } for s in S.SCALES],
    "pain": {"title": L(S.PAIN["title"]), "desc": L(S.PAIN["desc"]), "interp": interp(S.PAIN["interp"])},
    "bmi": {"title": L(S.BMI["title"]), "interp": interp(S.BMI["interp"])},
    "vitals": [{"age": L(a), "hr": hr, "rr": rr, "sbp": sbp} for a, hr, rr, sbp in S.VITALS],
    "adult_bp": [{"lvl": l, "text": L(t)} for l, t in S.ADULT_BP],
    "temp": [{"lvl": l, "text": L(t)} for l, t in S.TEMP],
    "spo2": [{"lvl": l, "text": L(t)} for l, t in S.SPO2],
    "dict": [{"uz_lat": norm_lat(u), "uz_cyr": cyr(u), "ru": r, "lat": la} for u, r, la in S.DICT],
}

blob = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
p = os.path.join(ROOT, "www", "index.html")
s = open(p, encoding="utf-8").read()
tag = '<script id="tools-data" type="application/json">'
if tag in s:
    s = re.sub(re.escape(tag) + r".*?</script>", lambda m: tag + blob + "</script>", s, count=1, flags=re.S)
else:
    anchor = re.search(r'<script id="oral-data-pat-ikkinchi" type="application/json">.*?</script>', s, re.S)
    s = s[:anchor.end()] + "\n" + tag + blob + "</script>" + s[anchor.end():]
open(p, "w", encoding="utf-8").write(s)
print("tools-data:", len(blob), "bayt;", len(data["dict"]), "ta atama;", len(data["scales"]), "ta shkala")
