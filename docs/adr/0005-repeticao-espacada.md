# ADR-0005 — Algoritmo de repetição espaçada

- **Status:** aceito
- **Data:** 2026-11-22

## Alternativas

- **SM-2** (SuperMemo, 1987): simples e conhecido; intervalos fixos por fator de facilidade, sem
  modelar a probabilidade de lembrança; tende a "inferno de facilidade" (ease hell).
- **FSRS** (Free Spaced Repetition Scheduler, licença MIT; padrão no Anki desde a versão 23.10):
  modela estabilidade, dificuldade e recuperabilidade; no benchmark público
  *open-spaced-repetition/srs-benchmark* prevê a lembrança com erro bem menor que o SM-2 para a
  mesma taxa de retenção, reduzindo o número de revisões necessárias.

## Decisão

**FSRS (versão 5) implementado em Kotlin puro**, com os parâmetros padrão publicados, retenção-alvo
de 90% (ajustável) e as quatro avaliações *Esqueci / Difícil / Bom / Fácil*. Cada revisão é
registrada em `review_logs`, o que permite, no futuro, otimizar os parâmetros com o histórico do
próprio usuário. A ficha de revisão prioriza a frase literária original.

## Consequências

- Implementação pequena (~200 linhas) e testável na JVM.
- Usuários que preferirem algo mais simples não terão opção SM-2 na primeira versão.
