import sherpa_onnx, time, sys, os, soundfile as sf, resource
M = sys.argv[1]; out = sys.argv[2]
texts = {
 "en": "The rain had stopped, and the street was quiet again. She read the letter twice before she understood it.",
 "de": "Der Regen hatte aufgehört, und die Straße war wieder still. Sie las den Brief zweimal, bevor sie ihn verstand.",
 "pt": "A chuva tinha parado, e a rua estava quieta outra vez. Ela leu a carta duas vezes antes de entendê-la.",
 "fr": "La pluie avait cessé, et la rue était de nouveau calme. Elle lut la lettre deux fois avant de la comprendre.",
 "it": "La pioggia era cessata e la strada era di nuovo tranquilla. Lesse la lettera due volte prima di capirla.",
 "es": "La lluvia había cesado y la calle volvía a estar tranquila. Leyó la carta dos veces antes de entenderla.",
}
def piper(d, threads):
    onnx = [f for f in os.listdir(d) if f.endswith(".onnx")][0]
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{d}/{onnx}", tokens=f"{d}/tokens.txt", data_dir=f"{d}/espeak-ng-data"),
        num_threads=threads, provider="cpu")))
def kokoro(d, threads, lang):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(model=f"{d}/model.onnx", voices=f"{d}/voices.bin", tokens=f"{d}/tokens.txt",
            data_dir=f"{d}/espeak-ng-data", dict_dir=f"{d}/dict", lexicon=f"{d}/lexicon-gb-en.txt,{d}/lexicon-us-en.txt", lang=lang),
        num_threads=threads, provider="cpu")))
cases = [("piper en_GB alba", "vits-piper-en_GB-alba-medium", "en", None),
         ("piper de_DE thorsten", "vits-piper-de_DE-thorsten-medium", "de", None),
         ("piper pt_BR faber", "vits-piper-pt_BR-faber-medium", "pt", None)]
# vozes Kokoro v1.0 (ids do sherpa-onnx): bf_emma=21 (en-GB), ff_siwis=29 (fr), if_sara=33 (it), pf_dora=? (pt-BR), ef_dora=? (es)
kcases = [("kokoro en-GB bf_emma", "en", "", "bf_emma"), ("kokoro fr ff_siwis", "fr", "fr", "ff_siwis"),
          ("kokoro it if_sara", "it", "it", "if_sara"), ("kokoro pt-BR pf_dora", "pt", "pt-br", "pf_dora"), ("kokoro es ef_dora", "es", "es", "ef_dora")]
for threads in (1, 4):
    for label, d, lang, _ in cases:
        t0 = time.time(); tts = piper(M + "/" + d, threads); load = time.time() - t0
        tts.generate("Warm up.", sid=0)
        t0 = time.time(); a = tts.generate(texts[lang], sid=0, speed=1.0); el = time.time() - t0
        dur = len(a.samples) / a.sample_rate
        print(f"| {label} | {threads} | {load*1000:.0f} | {el*1000:.0f} | {dur:.1f} | {el/dur:.3f} |", flush=True)
        if threads == 4: sf.write(f"{out}/{label.replace(' ','_')}.wav", a.samples, a.sample_rate)
    meta = None
    for label, lang, klang, voice in kcases:
        t0 = time.time(); tts = kokoro(M + "/kokoro-multi-lang-v1_0", threads, klang); load = time.time() - t0
        try:
            names = tts.speaker_names if hasattr(tts, "speaker_names") else None
        except Exception: names = None
        sid = {"bf_emma": 21, "ff_siwis": 30, "if_sara": 35, "pf_dora": 42, "ef_dora": 28}[voice]
        tts.generate("Hi.", sid=sid)
        t0 = time.time(); a = tts.generate(texts[lang], sid=sid, speed=1.0); el = time.time() - t0
        if not len(a.samples): print(f'| {label} | {threads} | falhou (lang={klang}) |'); continue
        dur = len(a.samples) / a.sample_rate
        print(f"| {label} | {threads} | {load*1000:.0f} | {el*1000:.0f} | {dur:.1f} | {el/dur:.3f} |", flush=True)
        if threads == 4: sf.write(f"{out}/{label.replace(' ','_')}.wav", a.samples, a.sample_rate)
print("maxrss_MB", resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024)
