"""Cobertura do léxico IPA em livros reais: fração das palavras corridas (tokens) com transcrição,
na mesma regra de consulta do app (forma exata, minúscula, capitalizada)."""
import gzip, re, sys, glob, collections, os, json
assets, books = sys.argv[1], sys.argv[2]
res = {}
for lang in ["en", "fr", "de", "it", "es", "pt"]:
    keys = set()
    for l in gzip.open(f"{assets}/{lang}.tsv.gz", "rt", encoding="utf-8"):
        if not l.startswith("#"):
            keys.add(l.split("\t", 1)[0])
    tot = hit = 0
    nb = 0
    for f in sorted(glob.glob(f"{books}/{lang}_*.txt")):
        text = open(f, encoding="utf-8", errors="ignore").read()
        nb += 1
        for w in re.findall(r"[^\W\d_]+(?:['’][^\W\d_]+)*", text):
            w = w.replace("’", "'")
            tot += 1
            lw = w.lower()
            if lang in ("fr", "it") and "'" in w and w not in keys and lw not in keys:
                w = w.split("'", 1)[1]; lw = w.lower()   # elisão: o app consulta "homme" em "l'homme"
            if w in keys or lw in keys or lw.capitalize() in keys:
                hit += 1
    res[lang] = {"books": nb, "tokens": tot, "covered_pct": round(100 * hit / max(tot, 1), 1)}
    print(lang, res[lang], flush=True)
json.dump(res, open(sys.argv[3], "w"), indent=1)
