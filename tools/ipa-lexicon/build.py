"""
Gera os léxicos de pronúncia do Virgil (engine/speech/src/main/assets/ipa/{lang}.tsv.gz) a partir
da extração do Wiktionary inglês (wiktextract, CC BY-SA 4.0), e mede a cobertura.

Entrada: um .jsonl por idioma com {"word","pos","sounds":[{"ipa","tags","note","raw"}],"form_of"}
(filtrado do dump DataDock/wiktextract; ver README.md desta pasta).

Regras (nenhuma transcrição é gerada por regra ortográfica; tudo vem dos editores do Wiktionary):
  - transcrições fonêmicas /…/; a fonética […] só quando o verbete não tem a fonêmica (alemão,
    sobretudo), exibida com colchetes — nunca convertida em barras;
  - variante do Virgil: inglês = Received Pronunciation/UK; português = Brasil (sem marca de cidade);
    espanhol = a primeira transcrição do modelo es-pr, que é a castelhana (distinção θ/s, yeísmo);
    alemão = padrão alemão (sem Áustria/Suíça/regionais); francês = França (sem Québec/Louisiana…);
    italiano = padrão (sem "regional"/"traditional"/"dialectal");
  - homógrafos: transcrição padrão = a da primeira entrada; alternativas por classe (n, v, adj, past)
    quando a classe tem uma única transcrição e ela difere da padrão;
  - palavras com espaço (locuções) ficam de fora; consulta é por palavra.
"""
import gzip, json, sys, collections, os, re

BAD = {"Quebec", "Louisiana", "Canada", "Belgium", "Switzerland", "Austria", "Southern-Germany", "Bavaria",
       "regional", "dialectal", "traditional", "proscribed", "nonstandard", "obsolete", "archaic", "Early", "Modern",
       "Portugal", "Northern", "Southern", "Central", "Caipira", "São-Paulo", "Rio-de-Janeiro", "Northeast-Brazil",
       "Angola", "Azores", "Mozambique", "Latin-America", "Andalusia", "Canary-Islands", "Argentina", "Uruguay",
       "US", "General-American", "Canada", "Australia", "New-Zealand", "South-Asia", "Ireland", "Scotland", "India",
       "Northern-England", "Cockney", "Geordie", "Wales", "letter", "name", "colloquial", "informal", "rare",
       "Milan", "Tuscany", "Rome", "Italy"}
PREFER = {
    "en": [{"Received-Pronunciation"}, {"UK"}, {"British"}, {"England"}],
    "pt": [{"Brazil"}],
    "de": [{"Germany"}, {"standard"}],
    "fr": [{"France"}, {"Paris"}],
    "es": [{"Spain"}],
    "it": [{"standard"}, {"Standard"}],
}
POS = {"noun": "n", "verb": "v", "adj": "adj"}


OLD = {"Early", "Modern", "obsolete", "archaic", "proscribed", "letter", "dated", "nonstandard"}


PHONEMIC = re.compile(r"^(/[^/\[\]]+/)")
PHONETIC = re.compile(r"^(\[[^\[\]/]+\])$")


def candidates(sounds, rx):
    out = []
    for s in sounds:
        if "verify" in s.get("note", ""):
            continue
        m = rx.match(s["ipa"].strip())
        if m:
            out.append((m.group(1), set(s["tags"]) | set(s["raw"])))
    return out


def choose(lang, ph):
    for want in PREFER.get(lang, []):
        for ipa, tags in ph:
            if want <= tags and not tags & OLD:
                return ipa
    if lang == "pt":
        return None  # sem a marca "Brazil" pode ser a pronúncia europeia: melhor não mostrar
    for ipa, tags in ph:
        if not tags & BAD:
            return ipa
    return None


def pick(lang, sounds):
    """Transcrição fonêmica /…/ da variante preferida; uma marca preferida basta (ex.: "Australia,
    New-Zealand, UK" é também a pronúncia britânica). Só quando o verbete não tem nenhuma
    transcrição fonêmica usa a fonética […] escrita pelo editor (comum no alemão), mantendo os colchetes."""
    return choose(lang, candidates(sounds, PHONEMIC)) or (
        None if candidates(sounds, PHONEMIC) else choose(lang, candidates(sounds, PHONETIC)))


def build(lang, src):
    entries = collections.OrderedDict()   # word -> list[(pos, ipa, is_form_of_past)]
    for line in open(src, encoding="utf-8"):
        e = json.loads(line)
        w = (e.get("word") or "").strip()
        if not w or " " in w or len(w) > 40 or w.startswith("-") or w.endswith("-"):  # afixos não são palavras
            continue
        ipa = pick(lang, e["sounds"])
        if not ipa:
            continue
        ipa = ipa.replace("\t", " ")
        entries.setdefault(w, []).append((e.get("pos"), ipa, bool(e.get("form_of"))))
    out = {}
    for w, items in entries.items():
        default = items[0][1]
        by = collections.defaultdict(set)
        for pos, ipa, form_of in items:
            tag = "past" if (lang == "en" and pos == "verb" and form_of) else POS.get(pos)
            if tag:
                by[tag].add(ipa)
        # Para "read": a entrada lematizada (presente) é a padrão; o passado é alternativa.
        if lang == "en" and items[0][2] and len(items) > 1:
            default = next((i for p, i, f in items if not f), default)
        alts = {t: next(iter(v)) for t, v in by.items() if len(v) == 1 and next(iter(v)) != default}
        if alts:
            # Com homógrafos, o adjetivo entra mesmo igual ao padrão: "is present" (adj) ≠ "has read" (passado).
            if "adj" in by and len(by["adj"]) == 1:
                alts["adj"] = next(iter(by["adj"]))
            if lang == "en" and "past" in alts and "v" not in alts:
                alts["v"] = default
            if alts.get("past") is not None and alts.get("past") == alts.get("v"):
                del alts["past"]
            if set(alts.values()) == {default}:
                alts = {}
        out[w] = (default, alts)
    return out


def write(lang, lex, dst):
    rows = []
    for w, (d, alts) in lex.items():
        row = f"{w}\t{d}"
        if alts:
            row += "\t" + ";".join(f"{k}={v}" for k, v in sorted(alts.items()))
        rows.append(row)
    rows.sort(key=lambda r: r.split("\t", 1)[0].encode("utf-16-be"))
    head = f"# Virgil IPA {lang} · Wiktionary (en.wiktionary.org) via wiktextract · CC BY-SA 4.0 · {len(rows)} palavras\n"
    with gzip.open(dst, "wt", encoding="utf-8", compresslevel=9) as f:
        f.write(head + "\n".join(rows) + "\n")


def coverage(lang, lex, top=(1000, 5000, 20000)):
    from wordfreq import top_n_list
    keys = set(lex) | {k.lower() for k in lex}
    res = {}
    for n in top:
        words = [w for w in top_n_list(lang, n) if w.isalpha()]
        res[n] = sum(1 for w in words if w in keys or w.capitalize() in keys) / len(words)
    # cobertura ponderada pela frequência (≈ fração das palavras de um texto corrido que têm IPA)
    from wordfreq import word_frequency
    words = [w for w in top_n_list(lang, 50000) if w.isalpha()]
    tot = sum(word_frequency(w, lang) for w in words)
    res["tokens"] = sum(word_frequency(w, lang) for w in words if w in keys or w.capitalize() in keys) / tot
    return res


if __name__ == "__main__":
    src_dir, dst_dir = sys.argv[1], sys.argv[2]
    os.makedirs(dst_dir, exist_ok=True)
    report = {}
    for lang in ["en", "fr", "de", "it", "es", "pt"]:
        lex = build(lang, f"{src_dir}/{lang}.jsonl")
        dst = f"{dst_dir}/{lang}.tsv.gz"
        write(lang, lex, dst)
        cov = coverage(lang, lex)
        homo = sum(1 for d, a in lex.values() if a)
        report[lang] = {"words": len(lex), "homographs": homo, "bytes": os.path.getsize(dst), **{(f"top{k}" if isinstance(k, int) else k): round(v * 100, 1) for k, v in cov.items()}}
        print(lang, report[lang], flush=True)
    json.dump(report, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "coverage-wordfreq.json"), "w"), indent=1)
