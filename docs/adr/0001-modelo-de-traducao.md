# ADR-0001 — Modelo único de tradução

- **Status:** aceito (confirmado em 2026-11-22)
- **Data:** 2026-11-22
- **Evidências:** [benchmark de novembro/2026](../benchmark/2026-11-traducao.md)

## Contexto

O Virgil precisa traduzir offline, para PT-BR, palavras em contexto, expressões, frases e
parágrafos de literatura em EN-GB, FR, DE, IT e ES, com **um único modelo** neural, em celulares
Android comuns. A consulta de palavra é a interação mais frequente e a mais exigente: o modelo
precisa escolher o sentido certo da palavra **dentro da frase**.

Modelos encoder-decoder clássicos (Marian/OPUS-MT, Bergamot, NLLB, M2M-100) traduzem frases bem,
mas não seguem instruções sobre "a palavra X desta frase"; obter o equivalente exato exigiria
alinhamento palavra-a-palavra (outro modelo, como awesome-align/mBERT) ou heurísticas frágeis.
Os modelos de tradução derivados de LLMs (2025–2026) aceitam instruções e contexto.

## Alternativas avaliadas

| Opção | Por que não (ou por que sim) |
|---|---|
| Mozilla Bergamot / Firefox Translations | Rápido e leve (~20–40 MB por par), com alinhamentos; mas **um modelo por par** e pivô pelo inglês para FR/DE/IT/ES→PT (perde nuance e soma modelos). Viola o requisito de modelo único. |
| OPUS-MT / Tatoeba | Mesma limitação por par; os multilíngues ("mul") têm qualidade baixa. |
| NLLB-200 600M distilled | Modelo único, mas licença **CC-BY-NC 4.0** (não comercial) e sem instruções. |
| M2M-100 418M | MIT, modelo único, mas qualidade inferior ao NLLB e sem instruções. |
| HY-MT1.5 1.8B | Bom e leve, mas licença com **exclusão territorial** (UE, Reino Unido, Coreia do Sul) — incompatível com um usuário que lê inglês britânico e pode viajar/residir na Europa. Superado pelo Hy-MT2. |
| **Hy-MT2 1.8B** | Apache-2.0, GGUF oficial, 1,13 GB, rápido. Tradução de frases boa; palavra em contexto ~50%. |
| Index-Translate 2B | Apache-2.0, mas consumo de memória maior (arquitetura híbrida Qwen3.5), saída fora do idioma-alvo e palavra em contexto ~36%. |
| **TranslateGemma 4B** | Gemma Terms of Use; 2,49 GB; ~2,3× mais lento; palavra em contexto ~73%; aceita códigos regionais (`en-GB`, `es-ES`, `pt-BR`). |
| TranslateGemma 12B, Hy-MT2 7B, Index 9B, MADLAD 3B | Qualidade maior, mas 4–7 GB: inviáveis em celulares comuns. |

## Decisão

1. **Modelo padrão: TranslateGemma 4B, quantização Q4_K_M (GGUF), executado pelo llama.cpp.**
   Justificativa: é o único candidato que acerta a maioria das consultas de palavra em contexto,
   que é o coração da experiência; sua qualidade em frases empata com os demais. O custo extra
   (≈2,5 GB de disco, ≈2,7 GB de RSS) é aceitável em aparelhos com ≥ 6 GB de RAM, a faixa
   predominante entre intermediários vendidos no Brasil desde 2024.
2. **Perfil compacto: Hy-MT2 1.8B Q4_K_M**, oferecido quando o aparelho tem < 6 GB de RAM
   (ou por escolha do usuário). **Apenas um modelo fica instalado**; trocar de perfil remove o
   anterior. O restante do app não muda: o motor isola o modelo atrás de `TranslationEngine` e cada
   modelo declara seu `PromptProfile` (templates de chat e de operação).
3. As quatro operações (palavra, expressão, frase, parágrafo) usam o mesmo modelo; o tipo de
   operação é definido pelo prompt e pelo contexto enviado.
4. Um **dicionário local pequeno** (listas bilíngues abertas) poderá complementar a consulta de
   palavra — por exemplo, oferecendo sentidos candidatos ao modelo — sem introduzir outra rede neural.
   Fica para a Fase 3, condicionado a uma licença compatível.

## Consequências

- O APK não inclui o modelo; o download (2,49 GB) acontece uma vez, retomável, com verificação
  SHA-256 e revisão fixada (`7c93846…` no repositório `bullerwins/translategemma-4b-it-GGUF`;
  SHA-256 `7f7357c1…09b72`). O original do Google é "gated" (exige aceite e login no Hugging Face);
  por isso também haverá **importação manual de arquivo .gguf** pelo seletor do Android.
- Os Termos do Gemma exigem repassar os termos e a política de uso proibido ao usuário: a tela de
  instalação do modelo exibirá ambos e o aceite ficará registrado localmente.
- Risco de **alucinação** do TranslateGemma em frases: mitigado por decodificação gulosa,
  limite de tokens proporcional ao tamanho da entrada, detecção de saída fora do idioma e de
  repetição, e pela apresentação sempre ao lado do original.
- Se o benchmark no aparelho mostrar latência de palavra > 3 s em intermediários mesmo com cache de
  prefixo, a decisão será revista (troca do padrão para o perfil compacto), sem alterar a arquitetura.
