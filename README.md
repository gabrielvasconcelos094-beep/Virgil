# Virgil

Leitor literário para Android com ferramentas de aprendizagem de idiomas que funcionam **inteiramente
no aparelho**: consulta de palavras em contexto, tradução de frases e parágrafos, estimativa de
dificuldade (CEFR) por livro e por capítulo, vocabulário pessoal e revisão espaçada.

Sem conta, sem assinatura, sem anúncios, sem nuvem. A internet só é usada para baixar, uma vez, o
modelo de tradução.

Idiomas: inglês britânico, francês, alemão, italiano e espanhol europeu → português brasileiro.
Livros em português também podem ser lidos.

## Estado atual

| Fase | Conteúdo | Estado |
|---|---|---|
| 0 | Diagnóstico, benchmark de modelos de tradução, decisões de arquitetura | ✅ |
| 1 | Projeto multi-módulo, identidade visual, tema claro/escuro, tipografia, navegação, Room, DataStore | ✅ |
| 2 | Importação EPUB/TXT, biblioteca, leitor, seleção de palavras | ✅ |
| 3 | Motor de tradução local (llama.cpp + modelo único) | ✅ (falta medir no aparelho) |
| 4 | Análise de dificuldade CEFR | ✅ (calibração pendente) |
| 5 | Vocabulário a partir do leitor, revisão FSRS, widget, estatísticas | ✅ |
| 6 | Polimento, desempenho, testes de ponta a ponta | em andamento |

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
- **Dificuldade estimada (CEFR):** análise determinística em segundo plano após a importação
  (vocabulário por cobertura de frequência, sintaxe, formas verbais literárias por idioma, estilo,
  variante linguística), faixa por livro e por capítulo, observações em português, comparação com o
  nível declarado pelo leitor. Apresentada como estimativa preliminar até a calibração.
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

## Documentação

- [Arquitetura e análise técnica](docs/arquitetura.md) — princípios, fluxo de consulta, política de memória, riscos
- [Benchmark de tradução (nov/2026)](docs/benchmark/2026-11-traducao.md) — medições reais de 3 modelos
- Decisões (ADR): [modelo de tradução](docs/adr/0001-modelo-de-traducao.md) ·
  [runtime](docs/adr/0002-runtime-de-inferencia.md) · [EPUB](docs/adr/0003-renderizacao-epub.md) ·
  [dificuldade CEFR](docs/adr/0004-analise-de-dificuldade.md) · [repetição espaçada](docs/adr/0005-repeticao-espacada.md) ·
  [stack e módulos](docs/adr/0006-stack-e-modulos.md)
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
feature/*            Biblioteca, Vocabulário, Revisar, Progresso, Configurações
tools/mt-benchmark   Harness e conjunto de testes usados para escolher o modelo de tradução
docs/                Arquitetura, ADRs, benchmark, design, licenças das fontes
```

## Privacidade

Livros, palavras, traduções, revisões, estatísticas e preferências ficam no armazenamento privado do
app. O Virgil não tem servidor, não coleta telemetria e não envia trechos de livros a lugar nenhum.
O backup automático do Android (se o usuário o ativar no sistema) inclui banco e preferências e
exclui livros e o modelo.
