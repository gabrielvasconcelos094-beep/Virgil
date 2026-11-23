# ADR-0007 — Voz: TextToSpeech do Android como padrão; TTS neural avaliado e não integrado (nov/2026)

## Contexto

O Virgil precisa pronunciar palavras, frases e parágrafos em inglês britânico, francês, alemão,
italiano, espanhol da Espanha e português do Brasil, sem internet, e sem depender do modelo de
tradução. O pedido foi investigar vozes neurais offline (Kokoro e alternativas) como download
opcional, separado do APK, e integrar só se fosse viável.

## Medições (reais, nesta máquina de desenvolvimento: CPU x86-64; não há celular disponível)

Motor: sherpa-onnx 1.13.8 (Apache-2.0) com ONNX Runtime na CPU. Texto de teste: duas frases (~20
palavras, 5–6 s de áudio) em cada idioma. RTF = tempo de síntese ÷ duração do áudio (menor é
melhor; < 1 = mais rápido que o tempo real). Inteligibilidade: o áudio gerado foi transcrito por
Whisper small (int8) e comparado com o texto (WER = taxa de erro de palavras). Não houve avaliação
auditiva humana neste ambiente.

| Voz | Idioma | Tamanho | Carga | RTF 1 thread | RTF 4 threads | WER (Whisper) |
|---|---|---|---|---|---|---|
| Piper alba (medium) | en-GB | 63 MB + 19 MB espeak-ng-data | 1,1 s | 0,10 | 0,040 | 0% |
| Piper thorsten (medium) | de-DE | 63 MB + 19 MB | 0,75 s | 0,10 | 0,040 | 0% |
| Piper faber (medium) | pt-BR | 63 MB + 19 MB | 1,1 s | 0,10 | 0,040 | 0% |
| Kokoro v1.0 bf_emma | en-GB | 311 MB (fp32) + 27 MB vozes + 19 MB | 1,4–1,9 s | 0,95 | 0,33 | 0% |
| Kokoro v1.0 ff_siwis | fr | (mesmo modelo) | | 0,86 | 0,32 | 10% ("lut" → "l'use") |
| Kokoro v1.0 if_sara | it | | | 0,81 | 0,31 | 11% ("Lesse" → "L'essere") |
| Kokoro v1.0 pf_dora | pt-BR | | | 0,74 | 0,28 | 0% |
| Kokoro v1.0 ef_dora | es | | | 0,69 | 0,26 | 11% ("Leyó" → "Le dio") |

Memória residente do processo de teste com todos os modelos carregados: 1,1 GB (pico).

Vozes Piper existentes nas seis variantes: en_GB (alan, alba, cori, jenny_dioco, northern_english_male,
semaine, southern_english_female, vctk…), fr_FR (siwis, tom, upmc, gilles, mls), de_DE (thorsten,
kerstin, eva_k, ramona…), it_IT (paola, serena, riccardo), es_ES (davefx, sharvard, carlfm),
pt_BR (faber, cadu, jeff, edresson). Kokoro v1.0 **não tem alemão** (en-US/en-GB, es, fr, it, pt-BR,
hi, ja, zh).

## Licenças

| Componente | Licença | Observação |
|---|---|---|
| sherpa-onnx, ONNX Runtime | Apache-2.0 / MIT | ok |
| Pesos Kokoro-82M | Apache-2.0 | ok |
| Vozes Piper | por voz: thorsten e faber CC0; alba CC BY 4.0, **mas ajustada a partir da voz "lessac"**, cujo corpus (Blizzard 2013) tem termos de uso restritos | exige auditoria voz a voz |
| **espeak-ng** (fonemizador de Piper e do Kokoro fora do inglês) | **GPL-3.0** | integrá-lo ao app obriga a distribuir o Virgil sob GPL-3 |

Desde a versão 2.0.0 o sherpa-onnx **removeu o espeak-ng** ("espeak-ng is no longer used. Use
lexicon … with an external phonemizer"), justamente por isso. Usar Piper/Kokoro hoje exige a
série 1.x (sem evolução) ou compilar e embutir o espeak-ng por conta própria.

## Decisão

**Não integrar uma voz neural nesta versão.** O TextToSpeech do Android continua sendo o mecanismo
(é o único, e não há botão de "voz neural" que não funcione).

Motivos:
1. **Licença:** a única via que cobre os seis idiomas com boa inteligibilidade (Piper) depende do
   espeak-ng (GPL-3). O projeto ainda não tem licença definida; escolher GPL-3 é decisão do dono do
   projeto, não uma escolha técnica a tomar implicitamente.
2. **Kokoro** é mais natural segundo avaliações públicas, mas não tem alemão, é ~9× mais lento
   que Piper na CPU, ocupa ~360 MB e erra formas verbais literárias (passé simple, passato remoto,
   pretérito) justamente o que um leitor de romances encontra.
3. **Sem aparelho** neste ambiente, não há como medir latência, bateria e memória num ARM real ao
   lado do modelo de tradução de 2,5 GB — e a soma (2,5 GB + 0,1–0,4 GB) é crítica em celulares de
   6 GB.
4. As vozes do sistema (Google, Samsung) já são neurais e offline na maioria dos aparelhos recentes,
   cobrem as seis variantes, e a pronúncia funciona sem download extra; o Virgil escolhe a melhor
   voz offline da variante certa e avisa quando falta instalar uma.

## O que falta para integrar no futuro (caminho preparado)

`engine:speech` já isola a voz atrás da interface `SpeechEngine`. Uma implementação neural
precisaria de:
- decisão de licença (GPL-3 para o app inteiro **ou** um fonemizador não-GPL: o léxico IPA do
  Wiktionary já embarcado cobre 75–94% das palavras dos livros testados, mas não as restantes — não
  basta sozinho);
- `PiperSpeechEngine` com sherpa-onnx (AAR Android), uma voz por idioma baixada sob demanda
  (~65 MB cada, mesma infraestrutura de download retomável com SHA-256 do modelo de tradução);
- seletor "Mecanismo de voz: Android / Piper" em Configurações → Pronúncia, Android como padrão;
- medição no aparelho (RTF, tempo até o primeiro som, memória com o tradutor carregado, bateria).

Reprodução: `tools/tts-eval/` (scripts `bench.py` e `asr.py` e instruções).
