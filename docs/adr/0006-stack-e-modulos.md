# ADR-0006 — Stack, modularização e injeção de dependências

- **Status:** aceito
- **Data:** 2026-11-22

## Decisão

- **Kotlin 2.4 + Jetpack Compose (BOM 2026.11) + Material 3** com tema próprio; **AGP 9.5**
  (Kotlin embutido), **Gradle 9.9**, `compileSdk`/`targetSdk` 36, **`minSdk` 28** (Android 9):
  cobre a grande maioria dos aparelhos ativos e garante APIs estáveis de `mmap`, fontes e
  `BreakIterator` do ICU.
- **Room 2.8** com esquema exportado e migrações explícitas (sem migração destrutiva);
  **DataStore** para preferências; **WorkManager** para importação, análise e download do modelo;
  **Glance** para o widget; **Storage Access Framework** para importar arquivos.
- **Módulos** por camada (`core:*`), por funcionalidade (`feature:*`) e por motor (`engine:*`, a
  partir da Fase 2), com *convention plugins* em `build-logic/`.
- **Injeção de dependências manual** (um `AppContainer` criado na `Application`), em vez de Hilt:
  o grafo é pequeno, explícito e evita processamento de anotações extra no build. Revisitar se o
  grafo crescer a ponto de dificultar testes.
- Sem bibliotecas de rede no app além do necessário para baixar o modelo (cliente HTTP da
  plataforma), sem analytics, sem anúncios, sem conta.

## Módulos

```
app                      Application, MainActivity, navegação, AppContainer
core:model               Tipos de domínio (Kotlin puro)
core:designsystem        Cores, tipografia, fontes, componentes
core:database            Room: entidades, DAOs, migrações
core:datastore           Preferências
core:data                Repositórios (domínio ⇄ persistência)
feature:library|vocabulary|review|progress|settings
engine:books       (F2)  Importação EPUB/TXT, modelo de blocos, armazenamento
engine:llama       (F3)  JNI + llama.cpp (NDK)
engine:translation (F3)  TranslationEngine, prompts, cache, agendamento
engine:models      (F3)  Catálogo, download, verificação, remoção
engine:analysis    (F4)  Idioma, variante, dificuldade CEFR
engine:srs         (F5)  FSRS
widget             (F5)  Widget Glance
```
