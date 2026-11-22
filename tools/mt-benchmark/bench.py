#!/usr/bin/env python3
"""Benchmark local de modelos de tradução GGUF via llama-server.

Uso:
  python3 bench.py --server /caminho/llama-server --model hymt2=/m/Hy-MT2.gguf --out results/

Cada modelo é iniciado num llama-server próprio (CPU, 4 threads, contexto 2048),
recebe prompts já formatados com o template de chat do modelo (como o app fará
no Android) e tem medidos: tempo de carregamento, latência por requisição,
tokens de prompt/saída e RSS do processo. Decodificação gulosa (temperature 0)
para resultados determinísticos e cacheáveis.
"""
import argparse, json, os, subprocess, sys, time, urllib.request

LANG_EN = {"en": "English", "fr": "French", "de": "German", "it": "Italian", "es": "Spanish"}
LANG_ZH = {"en": "英语", "fr": "法语", "de": "德语", "it": "意大利语", "es": "西班牙语"}
LANG_CODE_TG = {"en": "en-GB", "fr": "fr", "de": "de", "it": "it", "es": "es-ES"}

# ---------- templates de chat (texto cru, tokens especiais parseados pelo servidor)
def wrap(family, user):
    if family == "hymt":
        return "<｜hy_begin▁of▁sentence｜><｜hy_User｜>" + user + "<｜hy_Assistant｜>"
    if family == "qwen35":
        return "<|im_start|>user\n" + user + "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
    if family == "gemma":
        return "<start_of_turn>user\n" + user + "<end_of_turn>\n<start_of_turn>model\n"
    raise ValueError(family)

# ---------- prompts por operação
def p_sentence(family, lang, text):
    if family == "hymt":
        return ("Translate the following text into Brazilian Portuguese. Note that you should only "
                "output the translated result without any additional explanation:\n\n" + text)
    if family == "qwen35":
        return f"请将以下{LANG_ZH[lang]}文本翻译为巴西葡萄牙语，直接输出翻译结果，不要进行任何解释。\n" + text
    if family == "gemma":
        s, c = LANG_EN[lang], LANG_CODE_TG[lang]
        return (f"You are a professional {s} ({c}) to Portuguese (pt-BR) translator. Your goal is to accurately "
                f"convey the meaning and nuances of the original {s} text while adhering to Portuguese grammar, "
                f"vocabulary, and cultural sensitivities.\nProduce only the Portuguese translation, without any "
                f"additional explanations or commentary. Please translate the following {s} text into Portuguese:\n\n\n" + text)

def p_word_context(family, lang, sentence, word):
    """Estratégia A: o modo 'tradução com contexto' documentado por cada modelo."""
    if family == "hymt":
        return ("[Background Information]\n" + sentence + "\n\nPlease translate the following text into Brazilian "
                "Portuguese, taking the provided background information into consideration.\n\n[Source Text]\n" + word)
    if family == "qwen35":
        return (f"【上下文】\n{sentence}\n\n请结合上下文，将以下{LANG_ZH[lang]}词语翻译为巴西葡萄牙语，"
                f"只输出该词语在此语境中的译文，不要翻译上下文，不要解释。\n\n【待翻译文本】\n{word}")
    if family == "gemma":
        return p_word_direct(family, lang, sentence, word)

def p_word_direct(family, lang, sentence, word):
    """Estratégia B: instrução direta, igual para todos os modelos."""
    return (f"In the following sentence, the {LANG_EN[lang]} word or expression \"{word}\" is used in a specific sense.\n"
            f"Sentence: {sentence}\n"
            f"Give the Brazilian Portuguese translation of \"{word}\" as used in this sentence. "
            f"Output only the translation (one to three words), nothing else.")

# ---------- servidor
def rss_mb(pid):
    try:
        with open(f"/proc/{pid}/status") as f:
            for line in f:
                if line.startswith("VmRSS"):
                    return int(line.split()[1]) / 1024
    except OSError:
        pass
    return None

def post(port, payload, timeout=600):
    req = urllib.request.Request(f"http://127.0.0.1:{port}/completion", data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)

def start(server, model, port, threads):
    t0 = time.time()
    p = subprocess.Popen([server, "-m", model, "--port", str(port), "-c", "2048", "-t", str(threads),
                          "-ngl", "0", "--no-webui", "-np", "1"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    while True:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/health", timeout=2) as r:
                if r.status == 200:
                    break
        except Exception:
            pass
        if p.poll() is not None:
            raise RuntimeError(f"llama-server saiu ao carregar {model}")
        time.sleep(0.2)
    return p, time.time() - t0

def run(family, port, prompt, n_predict):
    payload = {"prompt": wrap(family, prompt), "n_predict": n_predict, "temperature": 0, "top_k": 1,
               "repeat_penalty": 1.05, "cache_prompt": False}
    t0 = time.time()
    r = post(port, payload)
    wall = time.time() - t0
    t = r.get("timings", {})
    return {"out": r["content"].strip(), "wall_s": round(wall, 3),
            "prompt_tokens": t.get("prompt_n"), "prompt_ms": t.get("prompt_ms"),
            "gen_tokens": t.get("predicted_n"), "gen_ms": t.get("predicted_ms")}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", required=True)
    ap.add_argument("--model", action="append", required=True, help="nome:familia=caminho.gguf")
    ap.add_argument("--testset", default=os.path.join(os.path.dirname(__file__), "testset.json"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--threads", type=int, default=4)
    a = ap.parse_args()
    ts = json.load(open(a.testset))
    os.makedirs(a.out, exist_ok=True)
    for spec in a.model:
        name_fam, path = spec.split("=", 1)
        name, family = name_fam.split(":")
        print(f"== {name} ({family})", flush=True)
        proc, load_s = start(a.server, path, 18080, a.threads)
        res = {"model": name, "family": family, "file": os.path.basename(path),
               "file_mb": round(os.path.getsize(path) / 1e6), "load_s": round(load_s, 2),
               "sentences": [], "words_context": [], "words_direct": [], "paragraphs": []}
        try:
            run(family, 18080, p_sentence(family, "en", "Hello."), 8)  # aquecimento
            for s in ts["sentences"]:
                r = run(family, 18080, p_sentence(family, s["lang"], s["src"]), 256); r.update(s)
                res["sentences"].append(r); print(" S", s["lang"], r["wall_s"], r["out"][:90], flush=True)
            for w in ts["words"]:
                r = run(family, 18080, p_word_context(family, w["lang"], w["sentence"], w["word"]), 16); r.update(w)
                res["words_context"].append(r)
                r2 = run(family, 18080, p_word_direct(family, w["lang"], w["sentence"], w["word"]), 16); r2.update(w)
                res["words_direct"].append(r2)
                print(" W", w["word"], "|", r["out"][:40], "|", r2["out"][:40], r["wall_s"], r2["wall_s"], flush=True)
            for p in ts["paragraphs"]:
                r = run(family, 18080, p_sentence(family, p["lang"], p["src"]), 512); r.update(p)
                res["paragraphs"].append(r); print(" P", p["lang"], r["wall_s"], flush=True)
            res["rss_mb_after"] = rss_mb(proc.pid)
        finally:
            proc.terminate(); proc.wait()
        json.dump(res, open(os.path.join(a.out, f"{name}.json"), "w"), ensure_ascii=False, indent=1)

if __name__ == "__main__":
    main()
