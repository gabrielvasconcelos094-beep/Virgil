# Calibração do modelo CEFR (gerado por tools/cefr-calibration/fit.py)

Textos: 2622 · por nível: A1 88, A2 366, B1 749, B2 762, C1 424, C2 233
Por idioma: de 509, en 1043, es 726, fr 344

## Validação cruzada estratificada (5 dobras × 3 repetições)

- Acerto exato: **44.0%** · ±1 nível: **90.5%** · erro médio: 0.66 nível

| Idioma | n | exato | ±1 nível |
|---|---|---|---|
| de | 509 | 40.6% | 84.3% |
| en | 1043 | 50.3% | 94.2% |
| es | 726 | 39.8% | 90.0% |
| fr | 344 | 38.8% | 89.5% |

Matriz de confusão (linhas = nível real, colunas = previsto), somada nas repetições:

| | A1 | A2 | B1 | B2 | C1 | C2 |
|---|---|---|---|---|---|---|
| **A1** | 26 | 208 | 29 | 1 | 0 | 0 |
| **A2** | 8 | 593 | 424 | 69 | 4 | 0 |
| **B1** | 0 | 348 | 1021 | 816 | 45 | 17 |
| **B2** | 0 | 59 | 645 | 1293 | 177 | 112 |
| **C1** | 0 | 21 | 187 | 653 | 206 | 205 |
| **C2** | 0 | 0 | 4 | 197 | 177 | 321 |

## Validação entre corpora (treina sem a fonte, testa nela)

| Fonte | n | exato | ±1 nível |
|---|---|---|---|
| cambridge_exams.json | 331 | 46.5% | 91.2% |
| elg-cefr-de.json | 509 | 13.6% | 40.5% |
| elg-cefr-en.json | 712 | 47.6% | 93.8% |
| hablacultura.json | 520 | 4.2% | 26.3% |
| kwiziq-es.json | 206 | 24.8% | 72.8% |
| kwiziq-fr.json | 344 | 4.4% | 35.5% |

## Faixa exibida e confiança

Meia-largura (níveis) escolhida para que a faixa contenha o nível real em ≥ 75% dos textos de validação:

*: 0.55, de: 0.65, en: 0.4, es: 0.65, fr: 0.65

Cobertura empírica de faixas com 1 / 2 / 3 níveis: *: [0.44, 0.747, 0.905]; de: [0.406, 0.703, 0.843]; en: [0.503, 0.823, 0.942]; es: [0.398, 0.69, 0.9]; fr: [0.388, 0.702, 0.895]

## Pesos do modelo final (características padronizadas)

Peso efetivo = compartilhado + ajuste do idioma (italiano e português usam só o compartilhado).

| Característica | compartilhado | en | de | es | fr |
|---|---|---|---|---|---|
| logP90SentLen | +0.369 | +0.794 | +0.432 | +0.272 | +0.346 |
| fracOver1k | -0.265 | -0.261 | -0.253 | -0.237 | -0.573 |
| fracOver3k | +0.242 | +0.582 | +0.134 | +0.078 | +0.414 |
| mtld | +0.184 | +0.360 | +0.125 | +0.230 | +0.204 |
| logV90 | +0.162 | +0.310 | +0.112 | +0.224 | +0.165 |
| subPerSent | +0.155 | +0.351 | +0.252 | +0.008 | +0.164 |
| conditionalPer1k | +0.147 | +0.444 | +0.050 | +0.179 | +0.064 |
| passivePer1k | +0.125 | +0.063 | +0.137 | +0.180 | +0.244 |
| logMeanSentLen | +0.119 | -0.094 | +0.303 | +0.233 | +0.154 |
| archaicPer1k | +0.116 | +0.183 | +0.018 | +0.151 | +0.228 |
| literaryPer1k | +0.104 | +0.064 | -0.016 | +0.064 | +0.405 |
| commasPerSent | +0.103 | +0.118 | +0.378 | -0.072 | +0.093 |
| meanWordLen | +0.099 | +0.013 | +0.518 | -0.047 | +0.013 |
| fracOver8k | +0.061 | +0.147 | +0.307 | -0.035 | -0.115 |
| idiomsPer1k | +0.041 | +0.066 | +0.027 | -0.009 | +0.120 |
| particlesPer1k | +0.040 | +0.145 | -0.026 | +0.040 | +0.040 |
| similesPer1k | +0.027 | +0.061 | +0.152 | +0.109 | -0.189 |
| properFrac | +0.026 | -0.148 | +0.083 | -0.038 | +0.232 |
| dialogueFrac | +0.018 | +0.176 | -0.033 | -0.068 | +0.017 |
| fracUnknown | +0.017 | +0.094 | -0.033 | +0.126 | -0.105 |
| fracOver20k | -0.006 | -0.218 | -0.118 | +0.082 | +0.226 |
