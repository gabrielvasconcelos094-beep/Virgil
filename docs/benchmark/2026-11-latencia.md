# Diagnóstico de latência da tradução (nov/2026)

Medições reais feitas na JVM do computador de desenvolvimento (CPU x86-64, 4 threads, llama.cpp
8d60873, pesos por mmap sem repack), com `LatencyProfileHostTest` e `EngineHostTest`
(`engine/translation/src/test`). **Não há celular neste ambiente**: os tempos absolutos num ARM
serão outros. O que se transfere é a *decomposição* (onde o tempo vai) e o efeito relativo de cada
mudança. Medições pendentes no aparelho estão listadas no fim.

## Onde o tempo ia

| Etapa | TranslateGemma 4B | Hy-MT2 1.8B | Observação |
|---|---|---|---|
| Carga do modelo, disco frio | **83 040 ms** | 37 341 ms | leitura dos 2,5 GB/1,1 GB do arquivo |
| Carga do modelo, arquivo já no cache de páginas | 379 ms | — | inicialização do llama.cpp em si é barata |
| Palavra: avaliação do prompt (60–80 tokens novos) | 1 200–1 700 ms | 600–740 ms | **domina** a consulta de palavra |
| Palavra: geração (1–4 tokens) | 120–380 ms | 120–250 ms | |
| Palavra, média de 22 consultas (modelo carregado) | 1 780 ms | 971 ms | |
| 2ª palavra da mesma frase (KV reaproveitado: 56 de 102 tokens) | 1 200 ms | 615 ms | |
| Frase de 20 palavras: primeiro texto / total | 1 622 / 5 309 ms | 563 / 2 698 ms | streaming: o texto aparece aos poucos |
| Parágrafo de 60 palavras: primeiro texto / total | 1 829 / 12 824 ms | 800 / 8 121 ms | 7,7–8,1 vs 15,4 tokens/s |

Diagnóstico:

1. **Causa principal da lentidão percebida no primeiro toque:** o modelo só era carregado na
   primeira consulta (`warmUp()` existia, mas não era chamado). Com o arquivo fora do cache de
   páginas, a carga custou 83 s neste ambiente. No celular, a leitura de 2,5 GB de memória flash
   (UFS 3.x, ~1–2 GB/s em leitura sequencial) deve levar alguns segundos, e isso recai sobre o
   primeiro toque.
2. **Depois de carregado, o custo é a avaliação do prompt**, não a geração. O prompt validado no
   benchmark de qualidade põe a frase antes da instrução, e só ~26 tokens do início ficam no KV
   cache entre palavras de frases diferentes.
3. A interface não bloqueia: a inferência roda num executor dedicado, há uma consulta por vez
   (mutex), e a última consulta cancela a anterior (já existia e foi verificada:
   `ultimaConsultaVence`). A RAM ocupada vem dos pesos mapeados (páginas de arquivo que o kernel
   pode descartar) mais o KV de 2 048 tokens.

## O que foi mudado

| Mudança | Efeito medido |
|---|---|
| **Aquecimento ao abrir o leitor**: carrega o modelo e avalia o início fixo do prompt de palavra do idioma do livro (`Translator.warmUp`). Um toque durante o aquecimento espera o mesmo lock e encontra o modelo pronto | primeiro toque deixa de pagar a carga: carga + prefixo em 559 ms (cache quente) feitos antes do toque; primeiro toque em seguida 2 132 ms, igual a um toque comum |
| Aviso honesto no cartão quando o modelo ainda está carregando ("preparando o modelo de tradução…") | — |
| **Chave de cache normalizada** (espaços, apóstrofos e aspas tipográficas); maiúsculas preservadas | a mesma frase vinda de EPUB (’) e de TXT (') reaproveita a tradução (teste `chaveDeCacheNormalizada`) |
| **Janela de contexto** de 30 palavras de cada lado do termo em frases com mais de 61 palavras | o prompt deixa de crescer com frases enormes; cada token a menos economiza ~20 ms no host |
| Mantidos: modelo carregado enquanto o leitor está aberto (e 3 min depois), cancelamento, uma inferência por vez, cache em Room | — |

## O que foi testado e **não** adotado

Prompt de palavra com a instrução antes da frase (a parte fixa inteira fica no KV cache):

| | TranslateGemma 4B | Hy-MT2 1.8B |
|---|---|---|
| Tempo médio por palavra: atual → variante | 1 780 → 1 357 ms (−24%) | 971 → 676 ms (−30%) |
| 2ª palavra da mesma frase: atual → variante | 1 200 → 715 ms | 615 → 250 ms |
| Acertos no conjunto do benchmark (22 palavras) | **15 → 12** | 11 → 10 |

A variante é mais rápida, mas erra mais ("estudante brilhante" para *bright*, "de manhã" para
*de bonne heure*, "de repente" para *smise*). Como a qualidade da tradução em contexto é a função
central, o prompt validado foi mantido. A comparação completa, palavra a palavra, está abaixo.

## Pendente no aparelho (não medido aqui)

- Carga a frio e tempo de aquecimento em celulares de 4, 6 e 8 GB de RAM (leitura da flash).
- Tokens/s de avaliação e geração em núcleos ARM (o benchmark de jun–nov/2026 em `2026-11-traducao.md`
  traz estimativas, não medições em aparelho).
- Memória residente (RSS) com o leitor aberto e efeito do `onTrimMemory`.
- Consumo de bateria por 100 consultas e temperatura em sessões longas.

Roteiro para medir: abrir um livro com o modelo instalado, esperar o aquecimento, tocar 20 palavras
e traduzir 5 frases; o histórico de consultas (`lookup_history.latency_ms`) registra cada tempo, e
`adb shell dumpsys meminfo app.virgil` dá a memória.

---

### Saída completa — TranslateGemma 4B (disco frio)

## Perfil de latência — translategemma-4b-it-Q4_K_M.gguf, 4 threads, CPU do host (x86-64)

Carga do modelo (mmap, sem repack): **83040 ms**

| consulta | tokens do prompt | reaproveitados (KV) | avaliados | ms prompt | tokens gerados | ms total | saída |
|---|---|---|---|---|---|---|---|
| atual · bright | 86 | 0 | 86 | 1789 | 4 | 2190 | brilhante |
| atual · bright | 85 | 26 | 59 | 1168 | 3 | 1436 | promissor |
| atual · scarcely | 93 | 26 | 67 | 1458 | 1 | 1597 | mal |
| atual · want | 106 | 26 | 80 | 1374 | 3 | 1765 | desejo |
| atual · 2ª palavra da mesma frase | 102 | 56 | 46 | 915 | 2 | 1148 |  |
| instrução antes · 2ª palavra da mesma frase | 96 | 87 | 9 | 452 | 2 | 693 |  |
| frase (20 palavras) | 105 | 23 | 82 | 1679 | 30 | 5391 | primeiro texto em 1684 ms; 8.1 tokens/s |
| parágrafo (60 palavras) | 155 | 74 | 81 | 1617 | 85 | 12111 | primeiro texto em 1622 ms; 8.1 tokens/s |

Média por palavra (22 palavras do benchmark, frases diferentes, modelo já carregado): atual **1804 ms**, instrução antes **1342 ms**
Acertos (tradução contém uma das referências): atual **15/22**, instrução antes **12/22**

| palavra | frase | atual | instrução antes |
|---|---|---|---|
| bright | She is a bright student. | brilhante | estudante brilhante |
| bright | His future looks bright. | promissor | promissor |
| scarcely | She could scarcely believe what she had witnessed. | mal | quase |
| want | It is a truth universally acknowledged, that a single man in | desejo | desejo |
| post | He put the kettle on and waited for the post. | correio | correio |
| gave up | He gave up the struggle at last. | desistiu | abandonou |
| passé | Il a passé l'examen hier, mais il ne connaît pas encore le r | realizado | realizou |
| de bonne heure | Longtemps, je me suis couché de bonne heure. | cedo | de manhã |
| lambeaux | Pendant plusieurs jours de suite des lambeaux d'armée en dér | restos | fragmentos |
| nouveau | Nous étions à l'Étude, quand le Proviseur entra, suivi d'un  | novo | novo |
| schlug | Sie schlug das Buch auf und fing an zu lesen. | abriu | abriu |
| Bank | Die Bank am Fluss war leer, nur ein Hund lag darunter. | banco | banco |
| verleumdet | Jemand mußte Josef K. verleumdet haben, denn ohne daß er etw | denunciado | difamar |
| Ungeziefer | Als Gregor Samsa eines Morgens aus unruhigen Träumen erwacht | inseto | inseto |
| mezzogiorno | Quel ramo del lago di Como, che volge a mezzogiorno, tra due | meio-dia | meio-dia |
| preso un granchio | Ho preso un granchio: il treno partiva alle sette, non alle  | peguei um caranguejo | peguei um caranguejo |
| smise | Verso sera smise di piovere e uscimmo nel giardino. | parou | de repente |
| sassi | Un tempo i Malavoglia erano stati numerosi come i sassi dell | pedras | pedras |
| lugar | En un lugar de la Mancha, de cuyo nombre no quiero acordarme | lugar | em algum lugar |
| exquisita | La cena estaba exquisita, aunque el piso de mis tíos es bast | delicioso | delicioso |
| piso | La cena estaba exquisita, aunque el piso de mis tíos es bast | apartamento | apartamento |
| embarazada | Mi hermana está embarazada de su segundo hijo. | grávida | grávida |

### Saída completa — Hy-MT2 1.8B

## Perfil de latência — Hy-MT2-1.8B-Q4_K_M.gguf, 4 threads, CPU do host (x86-64)

Carga do modelo (mmap, sem repack): **37341 ms**

| consulta | tokens do prompt | reaproveitados (KV) | avaliados | ms prompt | tokens gerados | ms total | saída |
|---|---|---|---|---|---|---|---|
| atual · bright | 61 | 0 | 61 | 627 | 3 | 822 | brillante |
| atual · bright | 60 | 4 | 56 | 672 | 4 | 921 | luzirante |
| atual · scarcely | 66 | 4 | 62 | 597 | 2 | 720 | quase |
| atual · want | 79 | 4 | 75 | 738 | 2 | 872 | querer |
| atual · 2ª palavra da mesma frase | 75 | 33 | 42 | 489 | 2 | 615 |  |
| instrução antes · 2ª palavra da mesma frase | 69 | 64 | 5 | 123 | 2 | 250 |  |
| frase (20 palavras) | 51 | 2 | 49 | 555 | 33 | 2698 | primeiro texto em 563 ms; 15.4 tokens/s |
| parágrafo (60 palavras) | 104 | 24 | 80 | 797 | 113 | 8121 | primeiro texto em 800 ms; 15.4 tokens/s |

Média por palavra (22 palavras do benchmark, frases diferentes, modelo já carregado): atual **971 ms**, instrução antes **676 ms**
Acertos (tradução contém uma das referências): atual **11/22**, instrução antes **10/22**

| palavra | frase | atual | instrução antes |
|---|---|---|---|
| bright | She is a bright student. | brillante | brillante |
| bright | His future looks bright. | luzirante | luceiro |
| scarcely | She could scarcely believe what she had witnessed. | quase | quase |
| want | It is a truth universally acknowledged, that a single man in | querer | buscar esposa |
| post | He put the kettle on and waited for the post. | correio | correio |
| gave up | He gave up the struggle at last. | desistir | desistir |
| passé | Il a passé l'examen hier, mais il ne connaît pas encore le r | aprovado | passou |
| de bonne heure | Longtemps, je me suis couché de bonne heure. | cedo | cedo |
| lambeaux | Pendant plusieurs jours de suite des lambeaux d'armée en dér | pedaços de guerra | pedaços de guerra |
| nouveau | Nous étions à l'Étude, quand le Proviseur entra, suivi d'un  | novo burguês | novo burgueses |
| schlug | Sie schlug das Buch auf und fing an zu lesen. | abriu | abriu |
| Bank | Die Bank am Fluss war leer, nur ein Hund lag darunter. | banco | banco |
| verleumdet | Jemand mußte Josef K. verleumdet haben, denn ohne daß er etw | caluniado | calunhar |
| Ungeziefer | Als Gregor Samsa eines Morgens aus unruhigen Träumen erwacht | vermeado | vermehamento |
| mezzogiorno | Quel ramo del lago di Como, che volge a mezzogiorno, tra due | sul meio-dia | sul sul |
| preso un granchio | Ho preso un granchio: il treno partiva alle sette, non alle  | pegou um perigo | às sete |
| smise | Verso sera smise di piovere e uscimmo nel giardino. | parou de chover | parou de chover |
| sassi | Un tempo i Malavoglia erano stati numerosi come i sassi dell | pedras da estrada | pedras |
| lugar | En un lugar de la Mancha, de cuyo nombre no quiero acordarme | localidade | local |
| exquisita | La cena estaba exquisita, aunque el piso de mis tíos es bast | deliciosa | deliciosa |
| piso | La cena estaba exquisita, aunque el piso de mis tíos es bast | quarto | chão |
| embarazada | Mi hermana está embarazada de su segundo hijo. | grávida | grávida |
