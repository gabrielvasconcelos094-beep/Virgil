# Virgil

Leitor literário para Android com ferramentas de aprendizagem de idiomas que funcionam **inteiramente
no aparelho**: consulta de palavras em contexto, tradução de frases e parágrafos, estimativa de
dificuldade (CEFR) por livro e por capítulo, vocabulário pessoal e revisão espaçada.

Sem conta, sem assinatura, sem anúncios, sem nuvem. A internet só é usada para baixar, uma vez, o
modelo de tradução (e, se o usuário quiser, vozes do próprio Android).

Idiomas: inglês britânico, francês, alemão, italiano e espanhol europeu → português brasileiro.
Livros em português também podem ser lidos.

## Estado atual

| Fase | Conteúdo | Estado |
|---|---|---|
| 0 | Diagnóstico, benchmark de modelos de tradução, decisões de arquitetura | ✅ |
| 1 | Projeto multi-módulo, identidade visual, tema claro/escuro, tipografia, navegação, Room, DataStore | ✅ |
| 2 | Importação EPUB/TXT, biblioteca, leitor, seleção de palavras | ✅ |
| 3 | Motor de tradução local (llama.cpp + modelo único) | ✅ (falta medir no aparelho) |
| 4 | Análise de dificuldade CEFR | ✅ (calibrada na 0.2) |
| 5 | Vocabulário a partir do leitor, revisão FSRS, widget, estatísticas | ✅ |
| 6 | Polimento, verificação, testes de ponta a ponta, APK | ✅ (medições no aparelho pendentes) |
| 0.2 | Pronúncia, IPA, modos Frase/Parágrafo, velocidade da tradução, CEFR calibrado | ✅ ([relatório](docs/entrega-0.2.md)) |

O que já funciona de verdade nesta versão:

- **Biblioteca:** importação de EPUB 2/3 e TXT pelo seletor do Android, em segundo plano e com
  progresso; capas extraídas (ou tipográficas); detecção de duplicatas; recusa clara de EPUB com DRM;
  busca, ordenação, filtro por idioma; exclusão com limpeza dos arquivos.
- **Leitor:** renderização nativa com a tipografia escolhida, hifenização no idioma do livro,
  capítulos, sumário (com âncoras internas), marcadores, retomada automática da posição, progresso,
  modo imersivo, painel "Aa" (fonte, tamanho, entrelinha, parágrafos, margens, alinhamento, tema).
- **Seleção:** toque = palavra (com elisões do francês/italiano), pressão longa + arraste =
  expressão, e expansão para frase ou parágrafo no cartão de consulta; salvar no vocabulário com a
  frase original, o livro e o capítulo.
- **Pronúncia e IPA:** ao tocar numa palavra, ela é pronunciada pela voz do Android na variante do
  livro (inglês britânico, espanhol da Espanha…), com a transcrição fonética do Wiktionary
  (/ˈwɔː.tə/), sem depender do modelo de tradução; Ouvir/Parar; voz e velocidade por idioma.
- **Modos Frase a frase e Parágrafo a parágrafo:** uma unidade por vez com a tradução abaixo
  (ocultável), ouvir e repetir, velocidade, posição "n / total", palavras tocáveis; tradução
  progressiva com pré-tradução só da unidade seguinte.
- **Dificuldade estimada (CEFR):** análise em segundo plano após a importação, com 21
  características (léxico, sintaxe, gramática, expressões e estilo) e modelo calibrado em textos de
  nível conhecido (44% de acerto exato, 90,5% a ±1 nível na validação cruzada), faixa por livro e
  por capítulo, confiança medida e comparação com o nível declarado pelo leitor.
- **Revisão espaçada (FSRS-5):** palavras vencidas + até 15 novas por dia, sempre com a frase do
  livro; respostas Esqueci/Difícil/Bom/Fácil com o próximo intervalo visível; palavras com memória
  estável (≥ 90 dias) passam a "aprendidas".
- **Widget:** palavra do vocabulário com tradução e frase de origem; frequência de troca e tema
  configuráveis; abre a ficha da palavra ao toque; funciona offline.
- **Progresso:** livros, concluídos, tempo de leitura, minutos por dia e revisões por dia (14 dias),
  leituras em andamento, palavras aprendidas por idioma.
- **Vocabulário, níveis e preferências** persistidos localmente.

- **Tradução local:** um único modelo (TranslateGemma 4B, ou Hy-MT2 1.8B em aparelhos com 4 GB),
  baixado uma vez em segundo plano (retomável, só em Wi-Fi por padrão, SHA-256 verificado) ou
  instalado a partir de um arquivo; executado com llama.cpp no próprio aparelho. Palavra e expressão
  em contexto, frase e parágrafo com texto aparecendo aos poucos; cache local; a última consulta
  cancela a anterior; o modelo é descarregado após inatividade ou sob pressão de memória.

## Instalação

1. Baixe o APK (`dist/Virgil-0.2.0.apk`, ou `app/build/outputs/apk/release/app-release.apk` após
   compilar) para o celular Android (9 ou superior, ARM64).
2. Abra o arquivo e permita "instalar apps desta fonte" quando o Android pedir.
3. Abra o Virgil. Em **Configurações → Tradução**, baixe o modelo recomendado para o seu aparelho
   (TranslateGemma 4B, 2,49 GB, para 6 GB de RAM ou mais; Hy-MT2 1.8B, 1,13 GB, para 4 GB). Por
   padrão o download só acontece em Wi-Fi, pode ser interrompido e retomado, e é verificado por
   SHA-256. Sem internet no aparelho, copie o arquivo `.gguf` para ele e use
   **Instalar a partir de arquivo**.

> O APK desta versão é assinado com a chave de depuração. Para distribuir, gere uma keystore
> própria e configure `signingConfigs` em `app/build.gradle.kts`.

## Como usar

- **Importar:** Biblioteca → **+** → escolha um EPUB ou TXT. O livro aparece com capa (ou capa
  tipográfica) e, em segundo plano, recebe a estimativa de dificuldade.
- **Ler:** toque na capa. Toque no centro da página para mostrar/ocultar os controles (sumário,
  marcadores, capítulos, **Aa** para fonte, tamanho, entrelinha, margens, alinhamento e tema).
- **Consultar:** toque numa palavra para ouvi-la, ver o IPA e a tradução em contexto; pressione e arraste para selecionar
  uma expressão; no cartão, escolha **Frase** ou **Parágrafo** para traduções maiores. As palavras
  consultadas vão para o vocabulário automaticamente (desative em Configurações).
- **Estudar frase a frase:** no leitor, toque no centro da página e use o ícone de tradução da
  barra superior (Frase a frase / Parágrafo a parágrafo). Ao sair, o leitor continua dali.
- **Dificuldade:** pressione a capa e escolha **Sobre o livro e dificuldade** (ou o ícone ⓘ no
  leitor). Informe seu nível em cada idioma em Configurações → Seus níveis para ver a comparação.
- **Revisar:** a aba Revisar mostra as palavras do dia; tente lembrar, revele e avalie
  (Esqueci/Difícil/Bom/Fácil).
- **Widget:** na tela inicial do Android, toque e segure → Widgets → Virgil.

## Pendências conhecidas

Detalhes e justificativas em [docs/entrega.md](docs/entrega.md).

- Medições no celular (latência, memória, bateria, partida a frio do modelo) — não há aparelho
  neste ambiente de desenvolvimento.
- Voz neural offline: avaliada e não integrada (licença GPL do espeak-ng; ver ADR-0007).
- IPA: inglês 94% das palavras dos livros testados; alemão 75%, português 69% (formas flexionadas).
- Modo paginado (o leitor usa rolagem por capítulo); notas de rodapé não abrem em pop-up.
- Tradução da frase de contexto ao salvar uma palavra (a ficha guarda a frase original).
- Exportação/backup manual do vocabulário; keystore de release.

## Documentação

- [Relatório da versão 0.2](docs/entrega-0.2.md) — pronúncia, IPA, modos de estudo, latência, CEFR
- [Relatório de entrega 0.1](docs/entrega.md) — o que foi feito, verificado e o que falta
- [Diagnóstico da análise CEFR](docs/cefr-diagnostico.md) · [Latência da tradução](docs/benchmark/2026-11-latencia.md)

- [Arquitetura e análise técnica](docs/arquitetura.md) — princípios, fluxo de consulta, política de memória, riscos
- [Benchmark de tradução (nov/2026)](docs/benchmark/2026-11-traducao.md) — medições reais de 3 modelos
- Decisões (ADR): [modelo de tradução](docs/adr/0001-modelo-de-traducao.md) ·
  [runtime](docs/adr/0002-runtime-de-inferencia.md) · [EPUB](docs/adr/0003-renderizacao-epub.md) ·
  [dificuldade CEFR](docs/adr/0004-analise-de-dificuldade.md) · [repetição espaçada](docs/adr/0005-repeticao-espacada.md) ·
  [stack e módulos](docs/adr/0006-stack-e-modulos.md) · [voz neural offline](docs/adr/0007-voz-neural-offline.md)
- [Identidade visual](docs/design/identidade-visual.md)

## Compilar

Requisitos: JDK 21, Android SDK com `platforms;android-37.2` e `build-tools;36.0.0`
(a partir da Fase 3 também `ndk;28.2.13676358` e `cmake;3.31.6`).

```bash
git submodule update --init --depth 1      # llama.cpp (third_party/llama.cpp)
echo "sdk.dir=/caminho/para/android-sdk" > local.properties
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # APK minificado (assinado com a chave de debug até haver keystore própria)
./gradlew test                   # testes unitários, de banco (Robolectric) e capturas de tela (Roborazzi)
```

Capturas de tela geradas pelos testes ficam em `app/build/outputs/roborazzi/`.

Testes com o modelo real (opcionais, rodam na JVM do computador):

```bash
cmake -S engine/llama/src/main/cpp -B build-host -DCMAKE_BUILD_TYPE=Release && cmake --build build-host -j
VIRGIL_LLAMA_HOST_LIB=$PWD/build-host/libvirgil_llama.so \
VIRGIL_TEST_MODEL=/caminho/translategemma-4b-it-Q4_K_M.gguf \
./gradlew :engine:llama:testDebugUnitTest :engine:translation:testDebugUnitTest
```

## Estrutura

```
app/                 Application, MainActivity, navegação, AppContainer (injeção manual)
build-logic/         Convention plugins do Gradle
core/model           Tipos de domínio (Kotlin puro)
core/designsystem    Cores, tipografia (Literata, Source Serif 4, EB Garamond, Inter), componentes
core/database        Room: 15 entidades, DAOs, esquema exportado em core/database/schemas
core/datastore       Preferências
core/data            Repositórios
engine/speech        Voz do Android e léxicos IPA (assets/ipa)
feature/*            Biblioteca, Leitor (e modos Frase/Parágrafo), Vocabulário, Revisar, Progresso, Configurações
tools/mt-benchmark   Harness e conjunto de testes usados para escolher o modelo de tradução
tools/cefr-calibration  Calibração do modelo CEFR · tools/ipa-lexicon  geração dos léxicos IPA
tools/tts-eval       Avaliação de vozes neurais (ADR-0007)
docs/                Arquitetura, ADRs, benchmark, design, licenças das fontes
```

## Privacidade

Livros, palavras, traduções, revisões, estatísticas e preferências ficam no armazenamento privado do
app. O Virgil não tem servidor, não coleta telemetria e não envia trechos de livros a lugar nenhum.
O backup automático do Android (se o usuário o ativar no sistema) inclui banco e preferências e
exclui livros e o modelo.
