import gzip, json, sys, collections, urllib.request
url = "https://huggingface.co/datasets/DataDock/wiktextract/resolve/main/2026-10-01.jsonl.gz"
LANGS = {"en", "fr", "de", "it", "es", "pt"}
out = {l: open(f"{sys.argv[1]}/{l}.jsonl", "w") for l in LANGS}
n = collections.Counter()
with urllib.request.urlopen(url) as r, gzip.open(r, "rt", encoding="utf-8") as f:
    for line in f:
        # filtro rápido antes do parse
        if '"ipa"' not in line: continue
        try: e = json.loads(line)
        except Exception: continue
        lc = e.get("lang_code")
        if lc not in LANGS: continue
        sounds = [{"ipa": s["ipa"], "tags": s.get("tags", []), "note": s.get("note", ""), "raw": s.get("raw_tags", [])} for s in e.get("sounds", []) if s.get("ipa")]
        if not sounds: continue
        forms = [fm.get("form") for fm in e.get("forms", []) if fm.get("form")][:40]
        out[lc].write(json.dumps({"word": e.get("word"), "pos": e.get("pos"), "sounds": sounds, "forms": forms,
                                  "form_of": [x.get("word") for s in e.get("senses", []) for x in s.get("form_of", []) if x.get("word")][:3]}, ensure_ascii=False) + "\n")
        n[lc] += 1
print(dict(n))
