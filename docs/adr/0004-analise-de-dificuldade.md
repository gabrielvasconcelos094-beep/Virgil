# ADR-0004 — Estimativa de dificuldade (CEFR) sem modelo neural adicional

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

Ao importar um livro, o Virgil estima o nível CEFR recomendado para lê-lo, globalmente e por
capítulo. Não existe, para os cinco idiomas, um classificador aberto, pequeno e validado que
classifique *literatura* em níveis CEFR. Usar o modelo de tradução para isso seria lento (milhões de
tokens por livro) e não confiável: ele não foi treinado para essa tarefa.

## Decisão

Um **analisador determinístico**, com recursos específicos por idioma e processamento em segundo
plano (WorkManager, capítulo a capítulo, retomável). O livro pode ser lido durante a análise.

Dimensões e indicadores:

- **Vocabulário (peso maior):** cobertura lexical — fração dos tokens contida nas N palavras mais
  frequentes do idioma. Usa listas de frequência derivadas do projeto *wordfreq* (dados CC-BY-SA 4.0,
  com atribuição), ~50 mil formas por idioma, comprimidas nos assets. Os limiares por nível seguem a
  literatura de tamanho de vocabulário por nível CEFR (Milton, 2010; Milton & Alexiou, 2009) e de
  cobertura necessária para leitura (Nation, 2006: ~98% para leitura sem apoio). Mede também
  densidade de palavras raras, proporção de hapax e diversidade lexical (MTLD, robusta ao tamanho).
- **Sintaxe:** comprimento médio e percentil 90 das frases; marcadores de subordinação por frase
  (listas fechadas de conjunções e pronomes relativos por idioma); pontuação de períodos longos.
- **Gramática/morfologia:** marcadores de tempos e modos menos frequentes por regras de sufixo —
  *passé simple* e *subjonctif imparfait* (FR), Konjunktiv II e Präteritum literário (DE),
  *passato remoto* (IT), *pretérito imperfecto de subjuntivo* (ES), passiva e particípios (EN).
- **Expressões:** listas curtas e curadas de idiomatismos e, no inglês, *phrasal verbs*
  (verbo + partícula).
- **Estilo:** arcaísmos e ortografia antiga (ex.: *mußte*, *quedóse*, *thou*), proporção de diálogo,
  inversões e frases nominais.

Combinação: cada dimensão gera uma sub-pontuação em escala CEFR contínua (A1=1 … C2=6); a nota final
é uma média ponderada. A **faixa** (ex.: B2–C1) vem da dispersão entre dimensões e entre capítulos;
a **confiança** cai quando o texto é curto, quando as dimensões discordam muito, ou quando o idioma
não tem recurso completo. Tudo é apresentado como *estimativa de dificuldade de leitura*, nunca como
certificação.

## Validação

Antes da Fase 4 ser considerada concluída, os pesos serão calibrados com textos de nível conhecido
(leituras graduadas com CEFR declarado pelas editoras e textos de exames de proficiência publicados
como amostra), por idioma, com o erro médio registrado em documento. Sem isso, a interface mostrará
confiança baixa.

## Consequências

- Rápido (dezenas de milhares de palavras por segundo), sem bateria extra relevante, explicável
  ("frases longas", "vocabulário raro") e totalmente offline.
- Precisão limitada por natureza; a faixa e a confiança comunicam essa limitação.
- Um idioma novo exige: lista de frequência, listas de conectivos, regras morfológicas e idiomatismos.

## Implementação (versão 1 do analisador)

- `engine/analysis`: `DifficultyAnalyzer` (métricas, pontuação, faixa, observações), `VariantDetector`,
  `AnalysisWorker` (capítulo a capítulo; reanalisa livros quando `DifficultyAnalyzer.VERSION` muda),
  `BookInsights` (dados da tela de análise).
- Listas de frequência: 50 mil formas por idioma, extraídas do wordfreq 3.1.1 (CC BY-SA 4.0,
  `docs/licenses/wordfreq-NOTICE.md`), ~1,2 MB no total.
- Âncoras (A1…C2): cobertura de 95% em 1 000 / 2 000 / 4 000 / 8 000 / 16 000 / 32 000 formas;
  frase média 7 / 10 / 13 / 17 / 22 / 30 palavras; pesos 0,5 vocabulário, 0,2 sintaxe, 0,15 formas
  verbais, 0,15 estilo.
- Sanidade verificada em testes: textos simples escritos para o teste ficam em A2–B1; trechos de
  Woolf/Austen/Conrad, Flaubert/Proust e Kafka ficam entre B2 e C2.
- **Pendente:** calibração com textos de nível conhecido por idioma. Até lá, a confiança informada é
  limitada a 0,6 e a interface mostra "estimativa preliminar".

## Versão 2 (novembro de 2026)

A v1 foi medida contra textos com nível conhecido e se mostrou mal calibrada (36,5% de acerto exato,
previsões comprimidas em B1–C1). Foi substituída por características ampliadas e um modelo ordinal
calibrado em ~2 600 textos (44,0% exato, 90,5% ±1 nível na validação cruzada). Diagnóstico completo,
método e resultados: [docs/cefr-diagnostico.md](../cefr-diagnostico.md).
