# Avaliação de vozes neurais offline (ADR-0007)

```bash
python -m venv venv && venv/bin/pip install "sherpa-onnx<2" soundfile
# modelos: github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
#   vits-piper-en_GB-alba-medium, vits-piper-de_DE-thorsten-medium, vits-piper-pt_BR-faber-medium,
#   kokoro-multi-lang-v1_0   (extrair em <modelos>)
venv/bin/python bench.py <modelos> <saida-wav>          # RTF com 1 e 4 threads, grava os .wav
# whisper: github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models → sherpa-onnx-whisper-small
venv/bin/python asr.py <pasta-whisper> <saida-wav>      # transcreve e calcula o WER
```

A série 1.x do sherpa-onnx é necessária porque a 2.x removeu o espeak-ng (GPL-3).
