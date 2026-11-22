#!/usr/bin/env python3
"""Estratégia C para tradução contextual de palavras: traduz a frase inteira com a palavra
selecionada envolvida por uma marcação, e extrai o trecho marcado da saída.

Vantagens sobre pedir "o significado da palavra X": usa a habilidade principal do modelo
(traduzir frases), devolve de brinde a tradução da frase (útil para a ficha de vocabulário)
e não depende de o modelo seguir instruções meta-linguísticas.
"""
import argparse, json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from bench import start, run, LANG_EN, LANG_ZH, LANG_CODE_TG  # noqa: E402

def mark(sentence, word, open_t, close_t):
    i = sentence.find(word)
    if i < 0:
        i = sentence.lower().find(word.lower())
    return sentence[:i] + open_t + sentence[i:i + len(word)] + close_t + sentence[i + len(word):]

def prompt(family, lang, sentence, word):
    if family == "hymt":
        s = mark(sentence, word, "<sn>", "</sn>")
        return ("将以下<source></source>之间的文本翻译为巴西葡萄牙语，注意只需要输出翻译后的结果，不要额外解释，"
                "原文中的<sn></sn>标签表示标签内文本包含格式信息，需要在译文中相应的位置尽量保留该标签。"
                "输出格式为：<target>str</target>\n\n<source>" + s + "</source>"), "sn"
    if family == "qwen35":
        s = mark(sentence, word, "<sn>", "</sn>")
        return (f"请将以下{LANG_ZH[lang]}文本翻译为巴西葡萄牙语，直接输出翻译结果，不要进行任何解释。"
                f"原文中的<sn></sn>标签必须在译文中对应的位置原样保留。\n{s}"), "sn"
    if family == "gemma":
        s = mark(sentence, word, "<b>", "</b>")
        n, c = LANG_EN[lang], LANG_CODE_TG[lang]
        return (f"You are a professional {n} ({c}) to Portuguese (pt-BR) translator. Your goal is to accurately "
                f"convey the meaning and nuances of the original {n} text while adhering to Portuguese grammar, "
                f"vocabulary, and cultural sensitivities.\nProduce only the Portuguese translation, without any "
                f"additional explanations or commentary. Keep the <b></b> tags around the corresponding words. "
                f"Please translate the following {n} text into Portuguese:\n\n\n" + s), "b"

def extract(out, tag):
    m = re.search(rf"<{tag}>(.*?)</{tag}>", out, re.S)
    sentence = re.sub(r"</?(sn|b|target|source)>", "", out).strip()
    return (m.group(1).strip() if m else None), sentence

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", required=True)
    ap.add_argument("--model", action="append", required=True)
    ap.add_argument("--testset", default=os.path.join(os.path.dirname(__file__), "testset.json"))
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    ts = json.load(open(a.testset))
    for spec in a.model:
        name_fam, path = spec.split("=", 1)
        name, family = name_fam.split(":")
        proc, _ = start(a.server, path, 18081, 4)
        rows = []
        try:
            for w in ts["words"]:
                p, tag = prompt(family, w["lang"], w["sentence"], w["word"])
                r = run(family, 18081, p, 160)
                gloss, sent = extract(r["out"], tag)
                r.update(w); r["gloss"] = gloss; r["sentence_pt"] = sent
                rows.append(r)
                print(name, w["word"], "->", gloss, "|", sent[:80], "|", r["wall_s"], flush=True)
        finally:
            proc.terminate(); proc.wait()
        json.dump(rows, open(os.path.join(a.out, f"{name}.tagged.json"), "w"), ensure_ascii=False, indent=1)

if __name__ == "__main__":
    main()
