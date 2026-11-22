# Benchmark de tradução local — novembro de 2026

Objetivo: escolher **um único modelo** de tradução local para o Virgil, com base em medições reais,
não em reputação. Harness e conjunto de testes estão em `tools/mt-benchmark/` (reproduzíveis).

## Condições do teste (leia antes dos números)

- **Hardware:** VM Linux, Intel Xeon 2,1 GHz, **4 vCPUs**, sem GPU, AVX-512. **Não é um celular.**
  As latências abaixo servem para comparar modelos entre si; os tempos no Android precisam ser
  medidos no aparelho (ver "Pendências").
- **Runtime:** llama.cpp `8d60873` (22/11/2026), o mesmo que será embarcado no app. CPU, 4 threads,
  contexto 2048, decodificação gulosa (temperatura 0) — determinística e cacheável.
- **Prompts formatados manualmente** com o template de chat de cada modelo, como o app fará.
- **Conjunto:** 20 frases literárias (EN, FR, DE, IT, ES; Woolf, Austen, Dickens, Conrad, Proust,
  Flaubert, Maupassant, Kafka, Goethe, Manzoni, Pirandello, Verga, Cervantes, Clarín, Unamuno —
  todos em domínio público) + frases construídas com falsos cognatos, verbos separáveis e
  expressões idiomáticas; 22 testes de **palavra em contexto**; 5 parágrafos.
- **Avaliação de qualidade:** manual, por um avaliador (o agente que conduziu o teste), com as
  referências de `testset.json`. Amostra pequena: diferenças de 1–2 pontos não são significativas.

## Candidatos

| Modelo | Arquivo testado | Tamanho | Licença | Idiomas p/ PT | Observação |
|---|---|---|---|---|---|
| **TranslateGemma 4B** (Google, jan/2026) | `translategemma-4b-it-Q4_K_M.gguf` (GGUF comunitário, bullerwins) | 2 490 MB | Gemma Terms of Use (redistribuição permitida com termos e política de uso) | 55 idiomas, códigos regionais (`pt-BR`, `en-GB`, `es-ES`) | Original "gated" no HF |
| **Hy-MT2 1.8B** (Tencent, mai/2026) | `Hy-MT2-1.8B-Q4_K_M.gguf` (oficial) | 1 133 MB | **Apache-2.0** | 33 idiomas (`pt` sem variante) | Sucessor do HY-MT1.5 (que tinha licença com exclusão territorial UE/RU/Coreia) |
| **Index-Translate 2B** (bilibili, set/2026) | `Index-Translate-2B.Q4_K_M.gguf` (oficial) | 1 312 MB | Apache-2.0 | 150 idiomas | Base Qwen3.5 (atenção híbrida) |

Descartados sem benchmark completo (motivo documentado no ADR-0001): NLLB-200 (CC-BY-NC, não
comercial), M2M-100 418M (MIT, qualidade inferior e sem capacidade de instrução), MADLAD-400 3B
(grande para a qualidade), OPUS-MT e Mozilla Bergamot/Firefox Translations (um modelo **por par**
de idiomas, pivotando pelo inglês para FR/DE/IT/ES→PT: viola o requisito de modelo único),
Hy-MT2 7B / Index-Translate 9B / TranslateGemma 12B (inviáveis em celulares comuns),
Hy-MT2 1.8B 2-bit (depende de kernel "STQ" fora do llama.cpp principal), Google ML Kit
(fechado, um modelo por idioma, pivô pelo inglês).

## Desempenho (CPU x86, 4 threads)

`llama-bench`, t/s = tokens por segundo (média de 3 execuções):

| Modelo | prompt 32 tok | prompt 128 tok | geração | RSS pico (sem repack) |
|---|---|---|---|---|
| Hy-MT2 1.8B Q4_K_M | 187 t/s | 231 t/s | **20,6 t/s** | **1 266 MB** |
| Index-Translate 2B Q4_K_M | 162 t/s | 165 t/s | 14,1 t/s | — (servidor: muito maior)¹ |
| TranslateGemma 4B Q4_K_M | 87 t/s | 115 t/s | 9,1 t/s | 2 658 MB |

¹ Com repack de pesos (padrão do llama.cpp em CPUs com instruções vetoriais), o pico de RSS
praticamente dobra (2 159 MB e 5 062 MB), porque os pesos são copiados para memória anônima além
do arquivo mapeado. **No Android o app desativará o repack** para manter os pesos apenas como
páginas de arquivo (descartáveis pelo sistema sob pressão de memória). Custo: ~15–25% de
velocidade; a ser medido no aparelho.

Latência média ponta a ponta por consulta (mesmo hardware):

| Operação | Hy-MT2 1.8B | Index 2B | TranslateGemma 4B |
|---|---|---|---|
| Palavra em contexto | 0,5–0,9 s | 0,8–1,2 s | 1,3–2,1 s |
| Frase (10–45 palavras) | 0,8–4,3 s | 1,2–5,5 s | 2,5–9,6 s |
| Parágrafo (~60–100 palavras) | 5,0–8,1 s | 5,0–8,2 s | 9,2–13,7 s |

## Qualidade — palavra em contexto (22 itens)

Pontuação: correto = 1, aceitável/parcial = 0,5, errado ou fora do formato = 0.

| Estratégia | Hy-MT2 1.8B | Index 2B | TranslateGemma 4B |
|---|---|---|---|
| A — template "contexto" do próprio modelo | 9,5 (43%) | 8 (36%) | — |
| B — instrução direta ("traduza X como usado nesta frase") | 11 (50%) | 8 (36%) | **16 (73%)** |
| C — frase com a palavra marcada por tags | baixa: tags perdidas, palavras não traduzidas | baixa | média: tags perdidas em 4/22 |
| D — template "Personalization" (Hy-MT2) | 10 (45%) | — | — |

Exemplos reveladores:

| Frase | Palavra | Hy-MT2 (B) | Index (B) | TranslateGemma (B) |
|---|---|---|---|---|
| She could scarcely believe… | scarcely | quase | (frase inteira) | **mal** |
| …must be in want of a wife | want | carecer por | (frase inteira) | **falta** |
| Il a passé l'examen hier | passé | passado | resultado | feito (≈ fez) |
| Sie schlug das Buch auf | schlug | abriu o livro | abriu | **abriu** |
| des lambeaux d'armée en déroute | lambeaux | túneis militares | barris de guerra | **Restos** |
| el piso de mis tíos | piso | quarto | chão | **apartamento** |
| che volge a mezzogiorno | mezzogiorno | **sul** | meio-dia | meio-dia |
| Ho preso un granchio | preso un granchio | tomado um peixe | peguei um camarão | perdi a hora |

Nenhum modelo resolve bem expressões idiomáticas raras (*prendere un granchio*) nem todos os
falsos cognatos. O Index-Translate respondeu em espanhol ("temprano") num item francês.

## Qualidade — frases (20 itens, 0–2 cada)

| Hy-MT2 1.8B | Index 2B | TranslateGemma 4B |
|---|---|---|
| 26/40 | 24/40 | 25/40 |

Perfis de erro diferentes:

- **Hy-MT2:** literal e geralmente fiel; erra falsos cognatos ("passou no exame", "o chão dos meus
  tios"), traduz idiomas palavra a palavra ("peguei um caranguejo"), deixa termos sem traduzir
  ("hidalgo", "siesta").
- **TranslateGemma:** português mais natural e acerta falsos cognatos ("fez o exame",
  "delicioso… casa"), mas **alucina** ocasionalmente: "antes da badalada das 17 horas" (Woolf),
  "Que alívio que eu não esteja aqui!" (Goethe), "Comprei um caranguejo".
- **Index 2B:** semelhante ao Hy-MT2, com erros gramaticais ("os pedras", "criado mim"),
  modalidade errada em Kafka ("precisava difamar") e saída fora do idioma-alvo.

Por idioma (problemas observados): **EN** — idiomas de época (*in want of*, *by hand*);
**FR** — falsos cognatos (*passer un examen*) e vocabulário figurado (*lambeaux*);
**DE** — Konjunktiv/modalidade epistêmica (*mußte … haben*) e substantivos raros
(*Ungeziefer*); **IT** — arcaísmos geográficos (*mezzogiorno* = sul) e idiomatismos;
**ES** — arcaísmos de Cervantes (*lanza en astillero*, *adarga*) e falsos amigos (*piso*,
*exquisita*).

## Conclusão

A diferença decisiva está na **consulta de palavras em contexto** — a interação central do
Virgil: TranslateGemma 4B acertou ~73% contra ~50% do Hy-MT2 1.8B e ~36% do Index 2B. Em frases
os três empatam tecnicamente, com perfis de erro distintos. O custo do TranslateGemma é real:
2,2× o tamanho, 2,1× a memória e ~2,3× a latência.

Recomendação (detalhada no ADR-0001): **TranslateGemma 4B Q4_K_M como modelo único** em aparelhos
com ≥ 6 GB de RAM; **Hy-MT2 1.8B Q4_K_M como alternativa compacta** (instalada *no lugar*, nunca
junto) para aparelhos com 4 GB. O motor é o mesmo; muda apenas o "perfil de prompt".

## Pendências (precisam de aparelho físico)

1. Tempo de carregamento, latência por operação e tokens/s em ao menos dois celulares
   (intermediário com 6 GB e básico com 4 GB).
2. RSS real no Android com `mmap` + repack desativado, e comportamento sob `onTrimMemory`.
3. Consumo de bateria por consulta (Battery Historian) e temperatura em sessões longas.
4. Ganho do reaproveitamento de prefixo do KV cache (instruções fixas pré-avaliadas).
5. Ampliar o conjunto de avaliação (≥ 100 palavras por idioma) antes de congelar o prompt.

## Adendo — motor do app (mesma VM, 4 threads)

Medido pelo `EngineHostTest` (o `LocalTranslationEngine` do app, ponte JNI própria, sem repack):

| Consulta | Resultado | Tempo |
|---|---|---|
| *scarcely* em "She could scarcely believe…" (inclui carregar o modelo) | mal | 2,5 s |
| *passé* em "Il a passé l'examen hier…" (modelo já carregado) | realizado | 2,1 s |
| Frase alemã "Sie schlug das Buch auf…" (10 atualizações parciais) | Ela abriu o livro e começou a ler. | 3,3 s |
| Palavra pedida durante a tradução de um parágrafo (o parágrafo é cancelado) | promissor | 2,3 s |
| Consulta repetida | do cache | ~0 s |

Formato final do prompt de palavra (parte fixa → frase → instrução com a palavra) manteve 16/22 no
conjunto de palavras e aumenta o reaproveitamento do KV cache entre consultas
(`results/tgemma4b.word-final.json`).
