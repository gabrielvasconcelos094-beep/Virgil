# ADR-0003 — Leitura de EPUB e renderização do texto

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

O leitor precisa: (1) abrir EPUB 2/3 e TXT, inclusive arquivos grandes e malformados; (2) renderizar
com a tipografia e os temas do Virgil; (3) identificar com precisão a palavra, a expressão, a frase e
o parágrafo tocados; (4) salvar e restaurar posição de forma estável; (5) compartilhar o mesmo texto
com a análise de dificuldade e o vocabulário.

## Alternativas

| Opção | Prós | Contras |
|---|---|---|
| **Readium Kotlin Toolkit** (BSD-3) | Padrão de mercado, EPUB 3 completo, paginação por CSS, decorações | Navegador baseado em `Fragment` + WebView (integração indireta com Compose); seleção de palavra por toque exige JavaScript injetado; tipografia limitada às preferências do Readium; dependência grande |
| WebView próprio (HTML do capítulo + CSS + JS) | Fidelidade de layout, paginação por colunas CSS | Ponte JS↔Kotlin para cada toque, `caretRangeFromPoint` com comportamento variável entre versões do WebView, memória de um processo de renderização, temas e fontes duplicados em CSS |
| **Renderização nativa em Compose** a partir de um modelo de documento próprio | Offsets de caractere exatos (`TextLayoutResult`), segmentação com `BreakIterator` por idioma, mesma fonte de verdade para leitor/análise/vocabulário, temas e fontes nativos, rolagem com `LazyColumn` (virtualizada) | Não reproduz CSS arbitrário (tabelas complexas, floats, layout fixo); paginação exige implementação própria |
| FolioReader, epub4j/epublib | — | Abandonados ou só fazem parsing; epublib é LGPL e sem manutenção |

## Decisão

1. **Parser próprio** de EPUB (ZIP + `container.xml` + OPF + spine + NAV/NCX) usando `java.util.zip`
   e **jsoup** (MIT) para XHTML tolerante a erros. Na importação, cada capítulo é convertido para um
   **modelo de blocos** (título, parágrafo, citação, verso/pré-formatado, lista, imagem, separador)
   com trechos em itálico/negrito/sobrescrito/links de nota, e salvo como arquivo no armazenamento
   privado. Capa e imagens são extraídas para arquivos; nada de BLOBs no banco.
2. **Renderização nativa em Compose**: `LazyColumn` de blocos; cada parágrafo é um `Text` com
   `AnnotatedString`. Toque → `getOffsetForPosition` → palavra via `BreakIterator.getWordInstance(locale)`;
   pressão longa e arraste → expressão; toque duplo/menu → frase (`BreakIterator.getSentenceInstance`)
   e parágrafo.
3. Posição = (capítulo, deslocamento de caractere) — estável a mudanças de fonte e tamanho.
4. Modo inicial: **rolagem contínua por capítulo**. Paginação horizontal será implementada depois,
   medindo o texto com `TextMeasurer` sobre o mesmo modelo de blocos.

## Consequências

- Livros literários (prosa e verso) — o foco do Virgil — são bem atendidos; EPUBs de layout fixo,
  quadrinhos e tabelas elaboradas terão apresentação simplificada. Isso será informado ao usuário.
- Se a fidelidade de layout virar prioridade, o Readium pode ser adotado para *renderizar* mantendo
  o parser e o modelo de blocos para análise e vocabulário.
