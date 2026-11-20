"""Rebuild app/src/main/assets/dict/*.json from the guide PDFs (not Arabic).
Usage: PDF_DIR=pdfs python3 tools/extract_pdfs.py   (needs: pip install pdfplumber)"""
import pdfplumber, re, json, sys, os
PDF_DIR=os.environ.get('PDF_DIR','pdfs'); OUT_DIR=os.environ.get('OUT_DIR','app/src/main/assets/dict')
from collections import OrderedDict
LANGS = {
 'de':('aleman','Alemán','German','Deutsch','de-DE'),
 'fr':('frances','Francés','French','Français','fr-FR'),
 'ga':('irlandes','Irlandés','Irish','Gaeilge','ga-IE'),
 'it':('italiano','Italiano','Italian','Italiano','it-IT'),
 'nl':('neerlandes','Neerlandés','Dutch','Nederlands','nl-NL'),
 'pt':('portugues','Portugués','Portuguese','Português','pt-PT'),
 'ru':('ruso','Ruso','Russian','Русский','ru-RU'),
}
ES_COLS={'Español','Significa','Traducción literal'}
NOTE_COLS={'Regla','Error típico','Nota','Cuándo','Cuándo se usa','Mutación','Función','Efecto','Uso','Por qué','Verbo'}
HEAD=re.compile(r'^(\d{1,2})\.\s+([A-ZÁÉÍÓÚ¿][^(]*)')
def clean(s):
    if s is None: return ''
    s=s.replace('\n',' ')
    s=re.sub(r'\s+',' ',s).strip()
    s=re.sub(r'(\w)- (\w)', r'\1-\2', s) if False else s
    return s
def split_ru(w):
    # "этот, эта étot" -> (cyr, translit)
    m=re.match(r'^(.*[\u0400-\u04FF][^A-Za-zÀ-ÿ]*?)\s+(-?[A-Za-zÀ-ÿʼ\'’].*)$', w)
    if m and re.search(r'[\u0400-\u04FF]', m.group(1)) and not re.search(r'[\u0400-\u04FF]', m.group(2)):
        return m.group(1).strip(), m.group(2).strip()
    return w, ''
def run(code):
    slug,lname,en,native,tts=LANGS[code]
    f=PDF_DIR+f'/Palabras_funcionales_{slug}_espanol.pdf'
    rows=[]; sec='Introducción'; prev=None; prev_page=-9
    with pdfplumber.open(f) as pdf:
        for pi,p in enumerate(pdf.pages):
            heads=[(l['top'],HEAD.match(l['text'])) for l in p.extract_text_lines()]
            heads=[(l['top'],HEAD.match(l['text'])) for l in p.extract_text_lines() if l['chars'][0]['size']>14]
            heads=[(t,m) for t,m in heads if m]
            tables=p.find_tables()
            for ti,t in enumerate(sorted(tables,key=lambda t:t.bbox[1])):
                top=t.bbox[1]
                for ht,m in heads:
                    if ht<top: sec=f"{m.group(1)}. {m.group(2).strip()}"
                data=t.extract()
                if not data: continue
                hdr=[clean(x) for x in data[0]]
                if hdr and hdr[0]==lname:
                    cols=hdr; body=data[1:]
                elif prev and pi==prev_page+1 and ti==0 and not any(ht<top for ht,_ in heads) and len(hdr)==len(prev) and not any(h in ('Español','Ejemplo','Tipo','Uso','Persona','Caso') for h in hdr) and re.search(r'[a-zа-яáéíóú]',hdr[0] or ''):
                    cols=prev; body=data
                else:
                    prev=None; continue
                prev=cols; prev_page=pi
                for r in body:
                    r=[clean(x) for x in r]
                    # pair tables
                    if cols.count(lname)==2:
                        for a,b in ((r[0],r[1]),(r[2],r[3])):
                            if a: rows.append(dict(w=a,es=b,sec=sec))
                        continue
                    e=dict(sec=sec)
                    for c,v in zip(cols,r):
                        if c==lname: e['w']=v
                        elif c in ES_COLS: e['es']=(e.get('es','')+'; '+v).strip('; ') if c=='Significa' else v
                        elif c=='Ejemplo': e['ex']=v
                        elif c=='Traducción': e['exEs']=v
                        elif c and v: e['note']=(e.get('note','')+' · ' if e.get('note') else '')+f"{c}: {v}"
                    if e.get('w'): rows.append(e)
            for ht,m in heads: sec=f"{m.group(1)}. {m.group(2).strip()}"
    # merge
    out=OrderedDict()
    for e in rows:
        e['sec']=re.sub(r'\s+$','',e['sec']).rstrip(' (')
        if 'Glosario' in e['sec']: e['sec']='Glosario'
        if code=='ru':
            w,tr=split_ru(e['w']); e['w']=w
            if tr: e['tr']=tr
        key=(e['w'].lower(), e.get('es','').lower())
        k2=e['w'].lower()
        if k2 in out and e['sec']=='Glosario':
            o=out[k2]
            parts=[x.strip().lower() for x in re.split(r'[;,]',e.get('es','')) if x.strip()]
            if parts and not all(x in o.get('es','').lower() for x in parts):
                o.setdefault('alt',[]).append(e['es'])
            continue
        if k2 in out:
            k2=k2+'#'+str(len(out))
        out[k2]=e
    entries=[]
    for e in out.values():
        if e.get('alt'):
            e['es']=e.get('es','')+(' · ' if e.get('es') else '')+'; '.join(e.pop('alt'))
        entries.append({k:v for k,v in e.items() if v})
    doc=dict(code=code,es=lname,en=en,native=native,tts=tts,entries=entries)
    json.dump(doc,open(f'{OUT_DIR}/{code}.json','w'),ensure_ascii=False,separators=(',',':'))
    secs=OrderedDict()
    for e in entries: secs[e['sec']]=secs.get(e['sec'],0)+1
    print(code,len(entries),dict(secs))
for c in (sys.argv[1:] or LANGS): run(c)
