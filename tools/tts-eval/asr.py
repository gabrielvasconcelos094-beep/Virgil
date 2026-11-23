import sherpa_onnx, soundfile as sf, glob, os, re, sys, unicodedata
A = sys.argv[1]; B = sys.argv[2]
texts = {
 "en": "The rain had stopped, and the street was quiet again. She read the letter twice before she understood it.",
 "de": "Der Regen hatte aufgehört, und die Straße war wieder still. Sie las den Brief zweimal, bevor sie ihn verstand.",
 "pt": "A chuva tinha parado, e a rua estava quieta outra vez. Ela leu a carta duas vezes antes de entendê-la.",
 "fr": "La pluie avait cessé, et la rue était de nouveau calme. Elle lut la lettre deux fois avant de la comprendre.",
 "it": "La pioggia era cessata e la strada era di nuovo tranquilla. Lesse la lettera due volte prima di capirla.",
 "es": "La lluvia había cesado y la calle volvía a estar tranquila. Leyó la carta dos veces antes de entenderla.",
}
lang_of = lambda n: "en" if "en" in n.split("_")[1].lower() else n.split("_")[1][:2].lower()
def norm(t): return re.sub(r"[^\w\s]", " ", t.lower()).split()
def wer(r, h):
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        p, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            p, d[j] = d[j], min(d[j] + 1, d[j-1] + 1, p + (r[i-1] != h[j-1]))
    return d[len(h)] / len(r)
for f in sorted(glob.glob(f"{B}/*.wav")):
    name = os.path.basename(f)[:-4]
    l = lang_of(name)
    rec = sherpa_onnx.OfflineRecognizer.from_whisper(encoder=f"{A}/small-encoder.int8.onnx", decoder=f"{A}/small-decoder.int8.onnx", tokens=f"{A}/small-tokens.txt", language=l, task="transcribe", num_threads=4)
    a, sr = sf.read(f, dtype="float32")
    s = rec.create_stream(); s.accept_waveform(sr, a); rec.decode_stream(s)
    hyp = s.result.text.strip()
    print(f"| {name} | {wer(norm(texts[l]), norm(hyp))*100:.0f}% | {hyp} |", flush=True)
