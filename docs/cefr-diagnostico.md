# Análise de dificuldade (CEFR) — diagnóstico e correção (versão 2 do analisador)

## O que estava errado

Investigação da cadeia completa (importação → extração → idioma → métricas → classificação →
persistência → interface):

| Etapa | Situação encontrada |
|---|---|
| Extração do texto (EPUB/TXT) | Correta: 240 livros reais do Project Gutenberg extraídos sem falha; a análise usa o texto dos capítulos, não HTML nem metadados. |
| Recursos no APK | Corretos: as listas de frequência estão empacotadas (`freq/*.txt.gz`). |
| Identificação do idioma | Correta em 237/240 livros; as 3 divergências são livros bilíngues com comentários em inglês. |
| **Classificação** | **Defeituosa.** Contra 2 797 textos de leitura com nível conhecido, a v1 acertou o nível exato em **36,5%**, nunca previu A1 e quase nunca C2 (tudo comprimido em B1–C1). Causa: âncoras escolhidas à mão, sem calibração, e uma média fixa de quatro dimensões que puxa tudo para o centro. |
| **Textos curtos** | **Defeituoso.** Exigia 120 palavras: 26% dos textos de teste ficavam sem nível; livros ou capítulos curtos falhavam. |
| **Tratamento de falhas** | **Defeituoso.** Exceções eram engolidas sem registro; o livro ficava "Failed" para sempre e a tela dizia "texto curto demais" para qualquer falha. |
| Reanálise | Livros analisados por versão antiga não eram refeitos depois de uma falha; resultados de capítulos antigos podiam se misturar aos novos. |

## O que foi feito

1. **Novas características** calculadas sobre o texto real: vocabulário em quatro faixas de frequência,
   cobertura (V90), palavras desconhecidas, diversidade lexical (MTLD), comprimento médio de palavras,
   comprimento médio e P90 das frases, vírgulas e subordinação por frase, voz passiva, condicionais,
   formas verbais literárias, arcaísmos, phrasal verbs/verbos separáveis, expressões idiomáticas,
   comparações ("como se", "like a…") como indício de linguagem figurada, diálogo e nomes próprios.
2. **Modelo calibrado** (`engine/analysis/src/main/resources/cefr-model.json`, gerado por
   `tools/cefr-calibration/fit.py`): regressão ordinal regularizada com pesos compartilhados + ajuste
   por idioma, amostras balanceadas por nível e pontos de corte otimizados. Detalhes e pesos em
   [`tools/cefr-calibration/relatorio.md`](../tools/cefr-calibration/relatorio.md).
3. **Faixa e confiança empíricas**: a largura da faixa e a confiança mostradas vêm do erro medido na
   validação cruzada ("faixas como esta continham o nível real em X% dos textos de validação").
4. **Cadeia robusta**: motivo da falha gravado (`books.analysis_error`, migração automática v1→v2),
   nova tentativa em erros transitórios, reanálise automática quando a versão do analisador muda,
   limpeza de resultados de capítulos antigos, mínimo de 40 palavras.
5. **Interface**: resumo do principal fator de dificuldade, indicadores (léxico, sintaxe, gramática,
   expressões e estilo), métricas concretas, mapa por capítulo e quatro interpretações em relação ao
   nível do leitor (leitura confortável, desafio moderado, desafio elevado, muito acima do nível atual).

## Resultados medidos

Dados de calibração: coleção UniversalCEFR (licenças CC BY-NC/BY-NC-SA; usados apenas para ajustar e
validar — os textos não fazem parte do app nem do repositório): Cambridge exams e ELG (inglês), ELG
(alemão), HablaCultura e Kwiziq (espanhol), Kwiziq (francês) — 2 622 textos com ≥ 40 palavras.

| | v1 (anterior) | v2 (validação cruzada) |
|---|---|---|
| Nível exato | 36,5% | **44,0%** |
| ±1 nível | 80,5% | **90,5%** |
| Prevê A1 / C2 | não / quase nunca | sim / sim |

Por idioma (v2, validação cruzada): inglês 50,3% exato / 94,2% ±1; alemão 40,6% / 84,3%;
espanhol 39,8% / 90,0%; francês 38,8% / 89,5%. Entre corpora diferentes do inglês (treina numa fonte,
testa na outra): 46–48% exato, 91–94% ±1.

**Comprimento de livro.** Textos do mesmo nível concatenados em "livros" de 4 mil a 72 mil palavras
(capítulos de ~1 500 palavras, analisados como o app analisa um livro): 16/28 no nível exato e
27/28 dentro de ±1, sempre em ordem crescente dentro de cada fonte.

**Livros reais (Project Gutenberg, 40 por idioma, 8 mil a 400 mil palavras, analisados por inteiro):**
0 falhas, mediana de 159 ms por livro na JVM. Distribuição dos níveis estimados:

| Idioma | A1 | A2 | B1 | B2 | C1 | C2 |
|---|---|---|---|---|---|---|
| de | 0 | 0 | 4 | 8 | 10 | 18 |
| en | 0 | 0 | 0 | 4 | 9 | 27 |
| es | 0 | 0 | 0 | 30 | 9 | 1 |
| fr | 0 | 0 | 0 | 19 | 12 | 9 |
| it | 0 | 0 | 1 | 6 | 9 | 24 |
| pt | 0 | 0 | 0 | 7 | 18 | 15 |

A concentração em C1–C2 corresponde ao que é o Gutenberg (ensaios, poesia, história e romances dos
séculos XVIII–XIX); não há leituras graduadas nessa amostra.

## Limitações

- Italiano e português não têm corpus de leitura rotulado disponível: usam só os pesos compartilhados
  (calibrados nos outros quatro idiomas); a confiança mostrada é a geral, não específica do idioma.
- Os rótulos dos corpora não são perfeitamente comparáveis entre si (um "B1" de uma fonte não é
  idêntico ao de outra); por isso o acerto exato fica em torno de 44% e a interface mostra uma faixa.
- Metáfora e ironia não são detectáveis de forma determinística; o analisador mede apenas indícios
  (comparações explícitas e expressões idiomáticas listadas).
- Os corpora são de textos didáticos e de exames; livros literários estão fora da distribuição de
  treino. A ordem relativa é confiável; o nível absoluto deve ser lido como estimativa.
