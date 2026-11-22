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
| 2 | Importação EPUB/TXT, biblioteca, leitor, seleção de palavras | próxima |
| 3 | Motor de tradução local (llama.cpp + modelo único) | — |
| 4 | Análise de dificuldade CEFR | — |
| 5 | Vocabulário a partir do leitor, revisão FSRS, widget, estatísticas | — |
| 6 | Polimento, desempenho, testes de ponta a ponta | — |

O que já funciona de verdade nesta versão: navegação entre Biblioteca, Vocabulário, Revisar e
Progresso; vocabulário manual (adicionar, editar, filtrar por idioma/estado/data, marcar como
conhecida, excluir) persistido em Room; níveis por idioma; tema claro/escuro/automático;
preferências tipográficas com prévia ao vivo, persistidas em DataStore; estatísticas lidas do banco.
Ainda **não** há importação de livros, leitor nem tradução — essas telas dizem isso em vez de simular.

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
echo "sdk.dir=/caminho/para/android-sdk" > local.properties
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # APK minificado (assinado com a chave de debug até haver keystore própria)
./gradlew test                   # testes unitários, de banco (Robolectric) e capturas de tela (Roborazzi)
```

Capturas de tela geradas pelos testes ficam em `app/build/outputs/roborazzi/`.

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
