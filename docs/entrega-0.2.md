# Virgil 0.2 — relatório da etapa de aprimoramento (novembro de 2026)

Escopo: pronúncia automática, IPA, velocidade da tradução, modos Frase e Parágrafo, avaliação de
voz neural offline e correção da análise CEFR. Nada foi recriado: o trabalho reaproveita os módulos
existentes (`core:text`, `engine:translation`, `engine:analysis`, vocabulário, preferências) e
acrescenta um único módulo novo, `engine:speech`.

Todos os números abaixo foram medidos neste ambiente de desenvolvimento (CPU x86-64, sem celular).
O que depende de um aparelho físico está marcado como pendente.

## 1. O que foi implementado

| Área | Entrega | Onde |
|---|---|---|
| Pronúncia | Voz do Android no idioma e na variante do livro; toca ao tocar a palavra; Ouvir/Parar para repetir; detecção de voz ausente, com "Instalar voz"; voz e velocidade por idioma em Configurações → Pronúncia; não depende do modelo de tradução | `engine/speech/AndroidSpeechEngine.kt`, `LookupCard.kt`, `SpeechSettingsScreen.kt` |
| IPA | Léxicos offline dos seis idiomas a partir do Wiktionary; cartão na ordem palavra → /IPA/ → tradução → ouvir → salvar; nenhuma transcrição inventada; homógrafos do inglês pelo contexto; fonte Charis SIL para os glifos | `engine/speech/IpaDictionary.kt`, `tools/ipa-lexicon/` |
| Tradução | Diagnóstico medido; aquecimento do modelo ao abrir o leitor; aviso "preparando o modelo"; cache normalizado; janela de contexto em frases longas | `TranslationEngine.kt`, `docs/benchmark/2026-11-latencia.md` |
| Modo Frase | Uma frase por vez, original em destaque e tradução abaixo; anterior/próxima (botões e deslize); posição "n / total"; ouvir/repetir; velocidade; ocultar/revelar; palavra com IPA, voz e tradução em contexto; salvar com a tradução da frase | `StudyViewModel.kt`, `StudyScreen.kt` |
| Modo Parágrafo | Igual, com o parágrafo como unidade e a tradução feita com o parágrafo inteiro como contexto | idem |
| Segmentação | Diálogos (», —, aspas alemãs), continuações em minúscula, reticências, interjeições entre aspas, abreviações | `core/text/TextSegmenter.kt`, `SentenceCasesTest` |
| Voz neural | Kokoro e Piper medidos (velocidade, tamanho, memória, inteligibilidade, licenças); não integrados, com justificativa | `docs/adr/0007-voz-neural-offline.md`, `tools/tts-eval/` |
| CEFR | Cadeia diagnosticada e corrigida; modelo calibrado; faixa e confiança empíricas; indicadores e comparação com o nível do leitor | `docs/cefr-diagnostico.md`, `tools/cefr-calibration/` |

## 2. O que foi otimizado (tradução)

| Medição (TranslateGemma 4B, host) | Antes | Depois |
|---|---|---|
| Primeiro toque com o modelo fora do cache de disco | carga de 83 s somada ao toque | carga feita ao abrir o leitor; o toque custa o mesmo que os seguintes (~2 s no host) |
| Mesma frase com ’ (EPUB) e ' (TXT) | duas inferências | uma (cache normalizado) |
| Frase de 200 palavras como contexto de uma palavra | prompt com a frase inteira | janela de 61 palavras |

O que **não** mudou, e por quê: mover a instrução para antes da frase deixaria a consulta 24–30%
mais rápida, mas os acertos caíram de 15/22 para 12/22. A qualidade foi mantida.

Pendente no aparelho: carga a frio pela memória flash, tokens/s em ARM, memória residente e bateria
(roteiro em `docs/benchmark/2026-11-latencia.md`).

## 3. Causa do problema na análise CEFR e correção

Causa: o classificador v1 usava âncoras escolhidas à mão, sem calibração.

- Contra 2 797 textos com nível conhecido, acertava o nível exato em 36,5% e ficava a ±1 nível em
  80,5%. Nunca previa A1.
- Recusava textos com menos de 120 palavras.
- Engolia exceções: o livro ficava em "Falhou" para sempre, com uma mensagem enganosa.

Correção:

- 21 características linguísticas reais.
- Regressão ordinal calibrada, com ajuste por idioma.
- Faixa e confiança derivadas da validação cruzada.
- Motivo da falha gravado no banco (migração v1→v2), nova tentativa automática e reanálise quando a
  versão muda.
- Mínimo de 40 palavras.

Detalhes completos em `docs/cefr-diagnostico.md`.

## 4. Mecanismo de voz escolhido

**TextToSpeech do Android**, escolhendo por idioma a melhor voz offline da variante certa (en-GB,
fr-FR, de-DE, it-IT, es-ES, pt-BR). Vozes que exigem internet só são usadas se o usuário as escolher.

Motivos:

- funciona em todos os aparelhos, sem download;
- nos aparelhos recentes, as vozes Google/Samsung já são neurais e offline;
- não tem implicação de licença.

As vozes neurais avaliadas, Piper e Kokoro, dependem do espeak-ng (GPL-3), e o Kokoro não tem alemão
(ADR-0007).

## 5. Idiomas com pronúncia

Os seis: inglês britânico, francês, alemão, italiano, espanhol da Espanha e português do Brasil.
A disponibilidade real depende das vozes instaladas no aparelho. O app detecta e avisa:

- "voz não instalada";
- "não oferecido pelo mecanismo";
- "mecanismo indisponível".

## 6. Fonte dos dados fonéticos

Wiktionary em inglês, extraído pelo wiktextract (dump DataDock de 2026-10-01), sob **CC BY-SA 4.0**.
A atribuição aparece na tela Licenças.

Variantes escolhidas:

- inglês: Received Pronunciation/UK;
- espanhol: castelhano (distinção θ/s);
- português: marca "Brazil";
- alemão: padrão da Alemanha;
- francês: França;
- italiano: padrão.

Fontes descartadas:

- ipa-dict: inglês sob GPL-3, acentuação fora do padrão;
- WikiPron: remove as marcas de acento;
- TTS do Android: a API não expõe fonemas;
- regras ortográficas: o pedido proíbe IPA inventado.

## 7. Cobertura por idioma

| Idioma | Palavras no léxico | Tokens de texto corrido (wordfreq) | Tokens em 40 livros do Gutenberg |
|---|---|---|---|
| inglês (RP) | 86 287 | 95,9% | 93,9% |
| francês | 104 672 | 93,2% | 85,7% |
| espanhol (Espanha) | 138 505 | 91,1% | 80,6% |
| italiano | 85 893 | 87,0% | 78,5% |
| português (Brasil) | 60 834 | 86,1% | 69,3% |
| alemão | 75 295 | 85,4% | 75,0% |

Palavra sem entrada no léxico: nenhum IPA é mostrado, e o áudio e a tradução continuam
funcionando.

No alemão faltam sobretudo formas flexionadas. No português, boa parte dos livros do Gutenberg é
europeia e em grafia antiga.

Homógrafos: 1 002 no inglês, com regra pela palavra anterior (*had read* /ɹɛd/, *to use* /juːz/,
*the use* /juːs/, *is present* /ˈpɹɛz.ənt/). Sem pista, fica a pronúncia mais comum.

## 8. Método da análise CEFR

Características calculadas sobre o texto dos capítulos:

- **léxico:** quatro faixas de frequência, V90, palavras desconhecidas, MTLD, comprimento das
  palavras;
- **sintaxe:** comprimento médio e P90 das frases, vírgulas, subordinação;
- **gramática:** passiva, condicional, formas verbais literárias, arcaísmos;
- **estilo:** phrasal verbs/verbos separáveis, expressões idiomáticas, comparações, diálogo, nomes
  próprios.

As características passam por uma regressão ordinal ridge, com pesos compartilhados mais ajustes
por idioma (en, de, es, fr) e cortes otimizados. O livro inteiro é analisado capítulo a capítulo,
com faixa por capítulo.

## 9. Critérios e resultados da validação CEFR

Validação cruzada sobre a coleção UniversalCEFR (usada só para calibrar):

- **geral:** 44,0% de acerto exato (v1: 36,5%) e 90,5% a ±1 nível (v1: 80,5%);
- **por idioma (exato / ±1):** en 50,3%/94,2%, de 40,6%/84,3%, es 39,8%/90,0%, fr 38,8%/89,5%;
- **inglês entre corpora diferentes:** 46–48% exato e 91–94% a ±1;
- **livros sintéticos** de 4 a 72 mil palavras: 16/28 exatos e 27/28 a ±1;
- **240 livros reais:** 0 falhas, 150–350 ms por livro na JVM.

Persistência, retomada após reinício, migração e motivo de falha são cobertos por testes
(`AnalysisPipelineTest`, `MigrationTest`).

Limitações: italiano e português não têm corpus rotulado, então usam só os pesos compartilhados.
Metáfora e ironia não são detectáveis com esse método.

## 10. Tamanhos

| Item | Tamanho |
|---|---|
| APK release 0.2.0 | 17,9 MB (0.1.0: 13,4 MB; +3,9 MB de léxicos IPA e +0,7 MB da fonte Charis SIL) |
| Modelo de tradução padrão (download à parte) | TranslateGemma 4B Q4_K_M, 2,49 GB |
| Modelo compacto (download à parte) | Hy-MT2 1.8B Q4_K_M, 1,13 GB |
| Léxicos IPA (dentro do APK) | 3,6 MB comprimidos; 2–3 MB de memória por idioma carregado, sob demanda |
| Voz neural (avaliada, não incluída) | Piper: 63 MB por voz + 19 MB de espeak-ng-data. Kokoro: 311 MB + 27 MB |

## 11. Licenças acrescentadas

- Charis SIL (OFL 1.1, sem modificação).
- Dados IPA do Wiktionary (CC BY-SA 4.0).
- Ambas listadas na tela Licenças, junto com llama.cpp (MIT), jsoup (MIT) e wordfreq (CC BY-SA 4.0).

Nada sob GPL foi incluído.

## 12. Testes

Suíte completa executada módulo por módulo: **101 testes, 0 falhas**. Os 12 ignorados dependem de
variáveis de ambiente. Os testes com o modelo real foram rodados à parte e passaram:
`EngineHostTest`, `LatencyProfileHostTest`, `LlamaHostTest`.

Testes novos nesta etapa:

- **IPA:** `IpaDictionaryTest`, com os léxicos reais (variantes, homógrafos, ausência, ordenação e
  formato de todas as linhas).
- **Cartão do leitor:** `ReaderTest.traducaoApareceNoCartao…`, que confere a ordem áudio → IPA →
  tradução, Parar/Ouvir e o salvamento.
- **Modo Frase:** `ReaderTest.modoFrase…` (navegação, pré-tradução só da unidade seguinte, cache,
  palavra com IPA e contexto, salvamento com a tradução da frase, posição devolvida ao leitor).
- **Modo Parágrafo:** `ReaderTest.modoParagrafo…` (unidade = parágrafo e ocultar/revelar).
- **Segmentação:** `SentenceCasesTest`, com 14 casos de diálogo e abreviação em seis idiomas.
- **Tradução:** `PromptsTest` (janela de contexto, chave de cache normalizada) e
  `EngineHostTest.aquecimentoAoAbrirOLeitor`.

Capturas geradas pelos testes ficam em `app/build/outputs/roborazzi/`: `leitor-traducao.png` e
`modo-paragrafo.png`.

## 13. Limitações conhecidas

- **Nada foi executado num celular real:** não há aparelho nem emulador (sem KVM) neste ambiente.
  Latência, memória, bateria, a seleção real de vozes e a renderização em aparelho precisam ser
  conferidas.
- Homógrafos só são tratados no inglês, e por regra simples. O IPA de formas flexionadas
  ausentes do Wiktionary não é mostrado.
- A voz depende dos dados instalados no aparelho. A qualidade varia por fabricante.
- A voz neural não foi integrada (ADR-0007).
- A análise CEFR estima, não certifica. IT e PT estão sem calibração própria.

## 14. APK e instalação

- Arquivo: `dist/Virgil-0.2.0.apk` (versionCode 2).
- SHA-256: `229773c9dc490379dee7af6fde8feba6698a380ea2e18febfb5d51b461b6b575`.
- Assinado com a chave de debug. Para distribuir, configure uma keystore própria.

Instalação:

1. Copie o APK para o celular (Android 9 ou mais recente, ARM64).
2. Abra o arquivo e permita "instalar apps desta fonte". Por cima da 0.1, os dados são mantidos:
   o banco migra de v1 para v2 automaticamente.
3. Em Configurações → Tradução, baixe o modelo, se ainda não tiver.
4. Em Configurações → Pronúncia, toque em "Testar" em cada idioma. Se aparecer "Voz não
   instalada", use "Instalar voz", que abre o instalador de vozes do sistema.
5. No leitor, toque no centro da página e use o ícone de tradução da barra superior para abrir os
   modos Frase a frase e Parágrafo a parágrafo.

Para gerar o APK: `./gradlew assembleRelease`.
