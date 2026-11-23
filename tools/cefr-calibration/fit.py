#!/usr/bin/env python3
"""Calibra o modelo CEFR do Virgil (engine/analysis/src/main/resources/cefr-model.json).

Entrada: TSV exportado por CefrCorpusEvaluation.exportarCaracteristicas (características calculadas
pelo próprio código Kotlin do app, sobre textos de leitura com nível CEFR conhecido).

Modelo: regressão linear regularizada (ridge) sobre características padronizadas, com pesos compartilhados\nmais um ajuste por idioma (para idiomas com dados) e viés por idioma,
com amostras ponderadas para equilibrar os níveis; os cinco pontos de corte são então ajustados para
maximizar o acerto exato (corrige a compressão típica da regressão em direção ao meio da escala).
Validação: 5 dobras estratificadas (repetidas) e "deixar uma fonte de fora".

Uso: python3 fit.py features.tsv cefr-model.json relatorio.md
"""
import csv, json, math, random, sys
import numpy as np

LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
LANGS = ["en", "de", "es", "fr"]


def load(path):
    rows = list(csv.DictReader(open(path), delimiter="\t"))
    feats = [k for k in rows[0].keys() if k not in ("source", "lang", "gold", "words")]
    X = np.array([[float(r[f]) for f in feats] for r in rows])
    y = np.array([int(r["gold"]) for r in rows])
    lang = [r["lang"] for r in rows]
    src = [r["source"] for r in rows]
    return feats, X, y, lang, src


def design(Xz, lang):
    """[características compartilhadas | um bloco de características por idioma | viés por idioma]."""
    D = np.zeros((len(lang), len(LANGS)))
    for i, l in enumerate(lang):
        if l in LANGS:
            D[i, LANGS.index(l)] = 1
    blocks = [Xz * D[:, [j]] for j in range(len(LANGS))]
    return np.hstack([Xz] + blocks + [D])


def fit(X, y, lang, alpha=15.0):
    mean, std = X.mean(0), X.std(0) + 1e-9
    Xz = np.clip((X - mean) / std, -4, 4)
    A = design(Xz, lang)
    # Pesos inversos à frequência do nível (evita que A1 e C2, mais raros, sejam engolidos pelo meio).
    counts = np.bincount(y, minlength=6).astype(float)
    w = 1.0 / counts[y]
    w = w / w.mean()
    W = np.sqrt(w)[:, None]
    p = Xz.shape[1]
    R = np.diag([alpha] * (p * (1 + len(LANGS))) + [1e-6] * len(LANGS))
    coef = np.linalg.solve((A * W).T @ (A * W) + R, (A * W).T @ (y * np.sqrt(w)))
    pred = A @ coef
    cuts = tune_cuts(pred, y)
    return dict(mean=mean, std=std, coef=coef, cuts=cuts, p=p)


def predict_raw(model, X, lang):
    Xz = np.clip((X - model["mean"]) / model["std"], -4, 4)
    return design(Xz, lang) @ model["coef"]


def to_level(raw, cuts):
    return np.searchsorted(np.array(cuts), raw)


def to_scale(raw, cuts):
    cuts = np.array(cuts)
    gap = (cuts[-1] - cuts[0]) / 4
    out = []
    for v in raw:
        if v <= cuts[0]:
            s = 1.5 - (cuts[0] - v) / gap
        elif v >= cuts[-1]:
            s = 5.5 + (v - cuts[-1]) / gap
        else:
            k = int(np.searchsorted(cuts, v)) - 1
            s = k + 1.5 + (v - cuts[k]) / (cuts[k + 1] - cuts[k])
        out.append(min(6, max(1, s)))
    return np.array(out)


def tune_cuts(pred, y):
    """Ajusta os limiares (ordem preservada) por busca coordenada: acerto exato + 0,3 × acerto ±1."""
    cuts = [k + 0.5 for k in range(5)]
    def score(c):
        lv = to_level(pred, c)
        return np.mean(lv == y) + 0.3 * np.mean(np.abs(lv - y) <= 1)
    best = score(cuts)
    for _ in range(4):
        for i in range(5):
            lo = cuts[i - 1] + 0.05 if i > 0 else pred.min() - 1
            hi = cuts[i + 1] - 0.05 if i < 4 else pred.max() + 1
            for v in np.linspace(lo, hi, 60):
                c = cuts.copy(); c[i] = v
                s = score(c)
                if s > best:
                    best, cuts = s, c
    return cuts


def metrics(y, lv):
    return dict(n=len(y), exact=float(np.mean(lv == y)), adj=float(np.mean(np.abs(lv - y) <= 1)), mae=float(np.mean(np.abs(lv - y))))


def cross_validate(X, y, lang, folds=5, repeats=3, seed=7):
    rnd = random.Random(seed)
    preds = np.zeros((repeats, len(y)))
    for r in range(repeats):
        idx_by_level = {k: [i for i in range(len(y)) if y[i] == k] for k in range(6)}
        fold_of = np.zeros(len(y), dtype=int)
        for k, ids in idx_by_level.items():
            rnd.shuffle(ids)
            for j, i in enumerate(ids):
                fold_of[i] = j % folds
        for f in range(folds):
            tr, te = fold_of != f, fold_of == f
            m = fit(X[tr], y[tr], [lang[i] for i in np.where(tr)[0]])
            preds[r, te] = to_scale(predict_raw(m, X[te], [lang[i] for i in np.where(te)[0]]), m["cuts"])
    return preds


def main():
    feats, X, y, lang, src = load(sys.argv[1])
    out_json, out_md = sys.argv[2], sys.argv[3]
    lines = ["# Calibração do modelo CEFR (gerado por tools/cefr-calibration/fit.py)", ""]
    lines += [f"Textos: {len(y)} · por nível: " + ", ".join(f"{LEVELS[k]} {int(np.sum(y == k))}" for k in range(6)),
              "Por idioma: " + ", ".join(f"{l} {lang.count(l)}" for l in sorted(set(lang))), ""]

    # Linha de base: a v1 (âncoras manuais) foi medida à parte em 36,5% exato / 80,5% ±1.
    cv = cross_validate(X, y, lang)
    cont = cv.mean(0)
    lv = np.rint(cv - 1).clip(0, 5).astype(int)  # nível previsto em cada repetição
    all_m = metrics(np.tile(y, cv.shape[0]), lv.ravel())
    lines += ["## Validação cruzada estratificada (5 dobras × 3 repetições)", "",
              f"- Acerto exato: **{all_m['exact']*100:.1f}%** · ±1 nível: **{all_m['adj']*100:.1f}%** · erro médio: {all_m['mae']:.2f} nível", ""]
    lines += ["| Idioma | n | exato | ±1 nível |", "|---|---|---|---|"]
    for l in sorted(set(lang)):
        mask = np.array([x == l for x in lang])
        mm = metrics(np.tile(y[mask], cv.shape[0]), lv[:, mask].ravel())
        lines.append(f"| {l} | {mm['n']//cv.shape[0]} | {mm['exact']*100:.1f}% | {mm['adj']*100:.1f}% |")
    conf = np.zeros((6, 6), dtype=int)
    for g, p in zip(np.tile(y, cv.shape[0]), lv.ravel()):
        conf[g, p] += 1
    lines += ["", "Matriz de confusão (linhas = nível real, colunas = previsto), somada nas repetições:", "", "| | " + " | ".join(LEVELS) + " |", "|---" * 7 + "|"]
    for k in range(6):
        lines.append(f"| **{LEVELS[k]}** | " + " | ".join(str(v) for v in conf[k]) + " |")

    # Generalização entre corpora: treina sem uma fonte, testa nela.
    lines += ["", "## Validação entre corpora (treina sem a fonte, testa nela)", "", "| Fonte | n | exato | ±1 nível |", "|---|---|---|---|"]
    for s in sorted(set(src)):
        te = np.array([x == s for x in src]); tr = ~te
        m = fit(X[tr], y[tr], [lang[i] for i in np.where(tr)[0]])
        p = np.rint(to_scale(predict_raw(m, X[te], [lang[i] for i in np.where(te)[0]]), m["cuts"]) - 1).clip(0, 5).astype(int)
        mm = metrics(y[te], p)
        lines.append(f"| {s} | {mm['n']} | {mm['exact']*100:.1f}% | {mm['adj']*100:.1f}% |")

    # Faixa e confiança empíricas a partir da validação cruzada.
    err = cv - (y + 1)  # escala contínua: nível k ocupa [k+0.5, k+1.5)
    def coverage_for(h, mask):
        lo = np.rint(cv[:, mask] - h - 1).clip(0, 5); hi = np.rint(cv[:, mask] + h - 1).clip(0, 5)
        g = np.tile(y[mask], (cv.shape[0], 1))
        return float(np.mean((g >= lo) & (g <= hi))), float(np.mean(hi - lo + 1))
    half, cov = {}, {}
    for key in ["*"] + sorted(set(lang)):
        mask = np.ones(len(y), bool) if key == "*" else np.array([x == key for x in lang])
        # menor meia-largura cuja faixa contém o nível real em pelo menos 75% dos casos
        h = next((h for h in np.arange(0.25, 1.01, 0.05) if coverage_for(h, mask)[0] >= 0.75), 1.0)
        half[key] = round(float(h), 2)
        # cobertura por largura da faixa exibida (1, 2, 3 níveis)
        widths = []
        for w in range(1, 4):
            lo = np.rint(cv[:, mask] - 1).clip(0, 5)
            g = np.tile(y[mask], (cv.shape[0], 1))
            widths.append(float(np.mean(np.abs(g - lo) <= (w - 1) / 2 + (0.5 if w == 2 else 0)))) if False else None
        c1, _ = coverage_for(0.0, mask)
        c2, _ = coverage_for(0.5, mask)
        c3, _ = coverage_for(1.0, mask)
        cov[key] = [round(c1, 3), round(c2, 3), round(c3, 3)]
    lines += ["", "## Faixa exibida e confiança", "",
              "Meia-largura (níveis) escolhida para que a faixa contenha o nível real em ≥ 75% dos textos de validação:", "",
              ", ".join(f"{k}: {v}" for k, v in half.items()), "",
              "Cobertura empírica de faixas com 1 / 2 / 3 níveis: " + "; ".join(f"{k}: {v}" for k, v in cov.items())]

    # Modelo final com todos os dados.
    m = fit(X, y, lang)
    P = m["p"]
    w = m["coef"][:P]
    lang_w = {l: [float(v) for v in m["coef"][P * (1 + i): P * (2 + i)]] for i, l in enumerate(LANGS)}
    bias = {l: float(m["coef"][P * (1 + len(LANGS)) + i]) for i, l in enumerate(LANGS)}
    mean_bias = float(np.mean(list(bias.values())))
    order = np.argsort(-np.abs(w))
    lines += ["", "## Pesos do modelo final (características padronizadas)", "",
              "Peso efetivo = compartilhado + ajuste do idioma (italiano e português usam só o compartilhado).", "",
              "| Característica | compartilhado | en | de | es | fr |", "|---|---|---|---|---|---|"]
    lines += [f"| {feats[i]} | {w[i]:+.3f} | " + " | ".join(f"{w[i] + lang_w[l][i]:+.3f}" for l in LANGS) + " |" for i in order]
    model = dict(
        version=2, features=feats, mean=[float(v) for v in m["mean"]], std=[float(v) for v in m["std"]],
        weights=[float(v) for v in w], languageWeights=lang_w, intercept=mean_bias,
        languageBias={l: b - mean_bias for l, b in bias.items()},
        cuts=[float(c) for c in m["cuts"]], halfWidth=half, coverage=cov,
        validation={"cv_exact": f"{all_m['exact']:.3f}", "cv_adjacent": f"{all_m['adj']:.3f}", "texts": str(len(y)),
                    "corpora": "UniversalCEFR: cambridge_exams_en, elg_cefr_en, elg_cefr_de, hablacultura_es, kwiziq_es, kwiziq_fr"},
    )
    json.dump(model, open(out_json, "w"), ensure_ascii=False, indent=1)
    open(out_md, "w").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
