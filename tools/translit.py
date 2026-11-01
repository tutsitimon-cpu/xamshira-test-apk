# -*- coding: utf-8 -*-
"""O'zbek lotin -> kirill transliteratsiyasi (ilova matnlari uchun)."""
import re

# ---------- Uzbek Latin normalisation ----------
def norm_lat(s):
    s = re.sub(r"([OoGg])['‘’`ʼ]", lambda mm: mm.group(1) + 'ʻ', s)
    s = re.sub(r"['‘’`]", 'ʼ', s)
    return s

# ---------- Latin -> Cyrillic ----------
KEEP = {'ICN', 'pH', 'B12'}
WORD_EXC = {
    'inyeksiya': 'инъекция', 'inyeksiyadan': 'инъекциядан', 'inyeksiyalar': 'инъекциялар',
    'inyeksiyalarni': 'инъекцияларни', 'inyeksiyaga': 'инъекцияга',
    'subyektiv': 'субъектив', 'obyektiv': 'объектив', 'konyunktivit': 'конъюнктивит',
}
STEMS = [  # applied to lowercase latin word start/inside before letter mapping
    (r'sist(it|oskop)', r'цист\1'), (r'sirroz', 'цирроз'), (r'silindr', 'цилиндр'),
    (r'konsentr', 'концентр'), (r'sentr', 'центр'), (r'ksiya', 'кция'), (r'ksion', 'кцион'),
    (r'ts(?=[iey])', 'ц'), (r'^ts', 'ц'),
]
VOW = set('aeiouAEIOUʻаеиоуэюяёў')
DIG = {'sh': 'ш', 'ch': 'ч', 'yo': 'ё', 'yu': 'ю', 'ya': 'я', 'ye': 'е'}
ONE = {'a':'а','b':'б','d':'д','e':'е','f':'ф','g':'г','h':'ҳ','i':'и','j':'ж','k':'к','l':'л','m':'м',
       'n':'н','o':'о','p':'п','q':'қ','r':'р','s':'с','t':'т','u':'у','v':'в','x':'х','y':'й','z':'з','c':'с','w':'в'}

def conv_word(w):
    if w in KEEP:
        return w
    low = w.lower()
    if low in WORD_EXC:
        r = WORD_EXC[low]
        return r.upper() if w.isupper() and len(w) > 1 else (r[0].upper() + r[1:] if w[0].isupper() else r)
    upper_all = w.isupper() and len(w) > 1
    caps = [ch.isupper() for ch in w]
    # placeholders for stems
    s = low.replace('oʻ', '\u0001').replace('gʻ', '\u0002')
    for pat, rep in STEMS:
        s = re.sub(pat, rep, s)
    out = []
    i = 0
    prev = ''
    while i < len(s):
        ch = s[i]
        two = s[i:i+2]
        if ch == '\u0001':
            out.append('ў'); i += 1
        elif ch == '\u0002':
            out.append('ғ'); i += 1
        elif ch == 'ʼ':
            out.append('ъ'); i += 1
        elif two in DIG and not (two == 'ye' and False):
            out.append(DIG[two]); i += 2
        elif ch == 'e':
            # word start or after a vowel -> э ; after ъ -> е
            if i == 0 or (prev and prev in VOW and prev != 'ъ'):
                out.append('э')
            else:
                out.append('е')
            i += 1
        elif ch in ONE:
            out.append(ONE[ch]); i += 1
        else:
            out.append(ch); i += 1
        prev = out[-1][-1] if out else ''
    r = ''.join(out)
    if upper_all:
        return r.upper()
    if caps and caps[0]:
        return r[0].upper() + r[1:]
    return r

def to_cyr(s):
    def rep(mm):
        w = mm.group(0)
        start = mm.start()
        before = s[start-1] if start > 0 else ''
        if len(w) == 1 and w.isalpha() and w.isascii():
            if w in 'uUb':
                return {'U': 'У', 'u': 'у', 'b': 'б'}[w]
            if before == '/' and w == 'l':
                return 'л'
            if w == 'F':
                return 'Ф'
            return w  # vitamin letters, portions A/B/C, °C
        return conv_word(w)
    r = re.sub(r"[A-Za-zʻʼ]+(?:[ʻʼ][A-Za-z]+)*", rep, s)
    for a, b in [('«A» рўйхати', '«А» рўйхати'), ('«B» рўйхати', '«Б» рўйхати'), ('«A» синфи', '«А» синфи'),
                 ('ммол/', 'ммоль/'), ('пулс', 'пульс'), ('Пулс', 'Пульс'), ('Санқвам', 'СанҚваМ'),
                 ('калсий', 'кальций'), ('шпритс', 'шприц'), ('Шпритс', 'Шприц'), ('Севастополская', 'Севастопольская')]:
        r = r.replace(a, b)
    return r

FIX=[('аксина','акцина'),('Сианоз','Цианоз'),('сианоз','цианоз'),('Калсий','Кальций'),('калсий','кальций'),('деменсия','деменция'),('консистенсия','консистенция'),
('контрацепсия','контрацепция'),('Контрацепсия','Контрацепция'),('нитроглисерин','нитроглицерин'),('ускултация','ускультация'),('перисталтика','перистальтика'),
('вентилация','вентиляция'),('орекция','орексия'),('Асфикция','Асфиксия'),('асфикция','асфиксия'),('ипокция','ипоксия'),('циз','тсиз'),
('Инсулт','Инсульт'),('инсулт','инсульт'),('фталмолог','фтальмолог'),('Ултра','Ультра'),('ултра','ультра'),('алпация','альпация'),
('Энсефалит','Энцефалит'),('энсефалит','энцефалит'),('Пулмонолог','Пульмонолог'),('Кобалт','Кобальт'),('Оитс','ОИТС'),('алкогол','алкоголь'),('алкогольь','алкоголь'),
('Пиелонефрит','Пиелонефрит'),('спермитсид','спермицид'),('Спермитсид','Спермицид'),('рифампитсин','рифампицин'),('диссиплинар','дисциплинар'),('Ксодунок','Ходунок'),('онсепсия','онцепция'),('порсия','порция'),('мултидис','мультидис'),('фелдшер','фельдшер'),('улвовагинит','ульвовагинит'),('кариэс','кариес')]

def cyr(x):
    r = to_cyr(norm_lat(x))
    for a, b in FIX:
        r = r.replace(a, b)
    return r
