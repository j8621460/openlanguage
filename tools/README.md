The dictionary data in `app/src/main/assets/dict/` was extracted from the eight
"Palabras funcionales" PDF guides. `extract_pdfs.py` rebuilds the seven
Latin/Cyrillic-script languages. The Arabic PDF's font has a broken text map,
so Arabic headwords were taken from the transliteration and written in Arabic
script by hand (`arabic_script_map.py`); its examples are shown in transliteration.
