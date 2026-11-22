# Virgil — relatório de entrega (versão 0.1, novembro de 2026)

## Resumo da arquitetura

App Android nativo (Kotlin, Jetpack Compose, Material 3 com tema próprio), offline por construção,
organizado em 16 módulos Gradle:

```
app ─ feature:{library, reader, vocabulary, review, progress, settings}
        │
        ├─ core:{model, designsystem, database (Room), datastore, data, text}
        └─ engine:{books, llama, translation, analysis, srs}
```

- **Leitura:** parser próprio de EPUB 2/3 e TXT → modelo de blocos em arquivos por capítulo →
  renderização nativa em Compose com seleção precisa por offset de caractere (ADR-0003).
- **Tradução:** um único modelo de linguagem especializado em tradução, executado com llama.cpp
  via JNI próprio, com variantes de CPU escolhidas em tempo de execução (ADR-0001, ADR-0002).
- **Dificuldade:** analisador determinístico por idioma, sem rede neural (ADR-0004).
- **Revisão:** FSRS-5 em Kotlin puro (ADR-0005).
- **Persistência:** Room (15 entidades, esquema exportado) + DataStore; arquivos no armazenamento
  privado; nenhum dado sai do aparelho.

## Funcionalidades implementadas (e verificadas)

| Área | Situação | Verificação |
|---|---|---|
| Importação EPUB 2/3 (OPF, NAV/NCX, capa, imagens, versos, notas, DRM recusado, zip bomb) | ✅ | 6 testes com EPUBs gerados; livro de 4,5 M caracteres em 0,7 s (JVM) |
| Importação TXT (codificação, Gutenberg, capítulos, linhas quebradas) | ✅ | 5 testes |
| Biblioteca (capas, busca, ordenação, filtro, exclusão, duplicatas, progresso de importação) | ✅ | capturas + teste de fumaça |
| Leitor (tipografia, temas, capítulos, sumário com âncoras, marcadores, retomada, modo imersivo) | ✅ | teste de ponta a ponta e de retomada |
| Seleção: palavra, expressão, frase, parágrafo (elisões FR/IT, abreviações) | ✅ | 11 testes de segmentação + toque real no leitor |
| Tradução local: palavra em contexto, expressão, frase, parágrafo, streaming, cache, cancelamento | ✅ | 6 testes com o modelo real (JVM) + teste de interface |
| Download do modelo (retomável, Wi-Fi, SHA-256), instalação por arquivo, remoção | ✅ | compilado e revisado; download real não exercitado nos testes |
| Estimativa CEFR por livro e capítulo, variante, comparação com o nível do leitor | ✅ (preliminar) | 6 testes + análise de ponta a ponta |
| Vocabulário (automático/manual, filtros, edição, estados) | ✅ | testes de banco e de interface |
| Revisão espaçada FSRS com contexto literário | ✅ | 6 testes do algoritmo + 3 do repositório |
| Widget (tamanhos, tema, frequência, abre a ficha) | ✅ | 3 testes com glance-testing |
| Estatísticas (tempo, livros, revisões, aprendidas por idioma) | ✅ | capturas |

Totais na verificação final: **82 testes automatizados, 0 falhas**, mais 6 testes com o modelo real
executados manualmente (todos passaram). Lint de release: 0 erros. Bibliotecas nativas alinhadas
a 16 KB. Auditoria de rede: o único código que acessa a internet é o download do modelo.

## Modelo de tradução

| | Padrão | Compacto |
|---|---|---|
| Modelo | **TranslateGemma 4B** (Google, jan/2026), Q4_K_M | **Hy-MT2 1.8B** (Tencent, mai/2026), Q4_K_M |
| Arquivo | 2 489 909 312 bytes (2,49 GB) | 1 133 080 448 bytes (1,13 GB) |
| SHA-256 | `7f7357c1…09b72` | `dc5f44fc…06699` |
| Licença | Gemma Terms of Use + Prohibited Use Policy (aceite exibido na instalação) | Apache 2.0 |
| Recomendado para | ≥ 6 GB de RAM | 4 GB de RAM |
| Palavra em contexto (22 casos) | 73% | 50% |

**Justificativa:** a consulta de palavra em contexto é a interação central do Virgil, e o
TranslateGemma acertou o sentido contextual em ~73% dos casos contra ~50% do Hy-MT2 e ~36% do
Index-Translate 2B; em frases os três empataram. O custo (2,2× o tamanho, ~2,3× a latência) foi
considerado justificado para aparelhos com 6 GB; o perfil compacto cobre os demais com o mesmo motor.
Detalhes: [benchmark](benchmark/2026-11-traducao.md), [ADR-0001](adr/0001-modelo-de-traducao.md).

Desempenho medido (CPU x86 de 4 núcleos, **não é celular**): palavra 1,4–2,5 s, frase 2,5–3,5 s,
parágrafo 9–14 s; consultas repetidas instantâneas (cache); a segunda palavra na mesma frase
reaproveita metade do prompt. Memória de pico sem repack: 2,66 GB.

## Tamanhos

- APK de release: **13,4 MB** (≈ 12,8 MB de código nativo para 7 variantes de CPU ARM64 e ≈ 5 MB
  de fontes, comprimidos).
- Listas de frequência para a análise: 1,2 MB (embutidas).
- Modelo (baixado à parte): 2,49 GB ou 1,13 GB.

## Funcionalidades pendentes ou limitadas

1. **Medições em aparelho físico** (exigidas no item 21 do pedido): latência por operação, tempo de
   carregamento, RSS com `mmap` sem repack, partida a frio (nos testes, uma primeira consulta após
   o sistema descartar as páginas do modelo levou 11 s; em celulares com armazenamento UFS deve ser
   menor, mas precisa ser medido), bateria e temperatura. Plano em
   [benchmark/2026-11-traducao.md](benchmark/2026-11-traducao.md#pendências-precisam-de-aparelho-físico).
   Se a palavra passar de ~3 s em intermediários, o padrão muda para o Hy-MT2 (sem mudar a arquitetura).
2. **Calibração CEFR** com textos de nível conhecido; até lá, confiança ≤ 0,6 e rótulo "preliminar".
3. **Conjunto de avaliação maior** (≥ 100 palavras por idioma) antes de congelar os prompts.
4. **Leitor:** modo paginado; notas de rodapé em pop-up; destaque de palavras já salvas no texto.
5. **Ficha de vocabulário:** tradução da frase de contexto (hoje só a original é guardada, para não
   gastar uma inferência extra a cada palavra salva).
6. **Dicionário auxiliar** (ADR-0001, item 4), condicionado a uma licença compatível.
7. **Exportação/backup** do vocabulário; teste diagnóstico de nível (só se tecnicamente fundamentado).
8. **Keystore de release** própria (o APK atual usa a chave de depuração).

## Licenças de terceiros

Fontes Literata, Source Serif 4, EB Garamond e Inter (SIL OFL 1.1, `docs/licenses/`); listas de
frequência derivadas do wordfreq (CC BY-SA 4.0, `docs/licenses/wordfreq-NOTICE.md`); llama.cpp
(MIT); jsoup (MIT); bibliotecas AndroidX e Kotlin (Apache 2.0). Os modelos não são distribuídos no
APK; seus termos são apresentados antes do download.
