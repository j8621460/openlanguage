"""Extractor for the newer guides (Norwegian, Yoruba): columns may include
empty spacer columns; words carry a pronunciation column."""
import pdfplumber, re, json, sys, os, unicodedata
from collections import OrderedDict
PDF_DIR=os.environ.get('PDF_DIR','pdfs'); OUT_DIR=os.environ.get('OUT_DIR','app/src/main/assets/dict')
LANGS={'no':('noruego','Noruego','Norwegian','Norsk','nb-NO'),'yo':('yoruba','Yoruba','Yoruba','Yorùbá','yo-NG')}
HEAD=re.compile(r'^(\d{1,2})\.\s+([A-ZÁÉÍÓÚ¿][^(]*)')
def clean(s):
    s=unicodedata.normalize('NFC',(s or '').replace('\n',' '))
    return re.sub(r'\s+',' ',s).strip()
COLMAP={'Pron.':'pron','Pronunciación':'pron','Español':'es','Ejemplo':'ex','Traducción':'exEs'}
def br(x):
    m=re.match(r'^(.*?)\s*\[(.*)\]$', x or '')
    return (m.group(1).strip(), m.group(2).strip()) if m else ((x or '').strip(), '')
def special(cols, r, rows, sec):
    nz=[(h,v) for h,v in zip(cols,r) if h]
    names=[h for h,_ in nz]; vals=[v for _,v in nz]
    if names[:6]==['Sujeto','Pron.','Español','Objeto','Pron.','Español']:
        for a in (0,3):
            if vals[a] and vals[a+2]: rows.append(dict(w=vals[a],pron=vals[a+1].strip('[]'),es=vals[a+2],sec=sec))
    elif names[:5]==['Corto','Pron.','Enfático','Pron.','Español']:
        e=dict(w=f"{vals[0]} · {vals[2]}",pron=f"{vals[1].strip('[]')} · {vals[3].strip('[]')}",es=vals[4],sec=sec,
               note='Forma corta (pegada al verbo) · forma enfática (sola o para resaltar)')
        if len(vals)>6: e['ex']=vals[5]; e['exEs']=vals[6]
        if vals[0]: rows.append(e)
    elif names[:2]==['Persona','Objeto'] and 'Posesivo' in names:
        i=names.index('Posesivo')
        w,p=br(vals[1])
        if w and not w.startswith('('): rows.append(dict(w=w,pron=p,es=f"objeto de «{vals[0]}» (me, te, lo…)",ex=vals[2],exEs=vals[3],sec=sec))
        w,p=br(vals[i])
        if w: rows.append(dict(w=w,pron=p,es=f"posesivo de «{vals[0]}» (mi, tu, su…)",ex=vals[i+1],exEs=vals[i+2],sec=sec))
def run(code):
    slug,lname,en,native,tts=LANGS[code]
    rows=[]; state={'sec':'Introducción','num':-1}
    def set_sec(m):
        n=int(m.group(1))
        if n>=state['num']:
            state['num']=n; state['sec']=f"{n}. {m.group(2).strip()}"
    with pdfplumber.open(f'{PDF_DIR}/Las_palabras_funcionales_del_{slug}.pdf') as pdf:
        cols=None
        for pi,p in enumerate(pdf.pages):
            lines=[l for l in p.extract_text_lines() if l['chars'][0]['size']>14]
            heads=sorted([(l['top'],l['text']) for l in lines])
            tables=sorted(p.find_tables(),key=lambda t:t.bbox[1])
            events=[(t,'h',txt) for t,txt in heads]+[(t.bbox[1],'t',t) for t in tables]
            events.sort(key=lambda x:(x[0],x[1]))
            first_table=True
            for _,kind,obj in events:
                if kind=='h':
                    m=HEAD.match(obj)
                    if m: set_sec(m)
                    elif obj.strip().startswith('Glosario'): state['sec']='Glosario'; state['num']=999
                    cols=None
                    continue
                data=obj.extract(x_tolerance=50, keep_blank_chars=True)
                if not first_table: cols=None
                first_table=False
                for raw in data:
                    r=[clean(x) for x in raw]
                    if lname in r and 'Español' in r:
                        cols=r; continue
                    if ('Sujeto' in r and 'Objeto' in r) or ('Corto' in r and 'Enfático' in r) or ('Persona' in r and 'Posesivo' in r):
                        cols=r; continue
                    if cols and len(r)==len(cols) and not (lname in cols):
                        special(cols, r, rows, state['sec'])
                        continue
                    # headings swallowed by a table box
                    for cell in raw:
                        for line in (cell or '').split('\n'):
                            m=HEAD.match(line.strip())
                            if m and len(line)<80: set_sec(m); cols=None
                    if not cols or len(r)!=len(cols): continue
                    idx={}
                    for i,h in enumerate(cols):
                        if h==lname: idx.setdefault('w',i)
                        elif h in COLMAP: idx.setdefault(COLMAP[h],i)
                    e={k:r[i] for k,i in idx.items() if i<len(r) and r[i]}
                    if not e.get('w') or not e.get('es') or len(e['w'])>60: continue
                    if e.get('pron'): e['pron']=re.sub(r'-\s+','-',e['pron'].strip('[]').strip())
                    e['sec']=state['sec']
                    rows.append(e)
    for e in rows:
        if 'Glosario' in e['sec']: e['sec']='Glosario'
    out=OrderedDict()
    for e in rows:
        k=e['w'].lower()
        if k in out:
            o=out[k]
            if e['sec']=='Glosario':
                parts=[x.strip().lower() for x in re.split(r'[;,]',e['es']) if x.strip()]
                if parts and not all(x in o['es'].lower() for x in parts): o['es']+=' · '+e['es']
                continue
            k=k+'#'+str(len(out))
        out[k]=e
    entries=list(out.values())
    doc=dict(code=code,es=lname,en=en,native=native,tts=tts,entries=entries)
    json.dump(doc,open(f'{OUT_DIR}/{code}.json','w'),ensure_ascii=False,separators=(',',':'))
    secs=OrderedDict()
    for e in entries: secs[e['sec']]=secs.get(e['sec'],0)+1
    print(code,len(entries),dict(secs))
for c in (sys.argv[1:] or LANGS): run(c)
