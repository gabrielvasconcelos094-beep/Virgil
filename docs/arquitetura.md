# Virgil — arquitetura e análise técnica (Fase 0)

## Princípios

1. **A leitura vem primeiro.** Nenhuma operação de IA bloqueia a rolagem, o virar de página ou a
   navegação. Inferência roda fora da thread principal, é cancelável e nunca é disparada sem pedido
   explícito do usuário.
2. **Offline por construção.** O app só usa rede para baixar o modelo (e, no futuro, backups opcionais
   escolhidos pelo usuário). Não há servidores do Virgil, contas, telemetria ou anúncios.
3. **Um modelo neural.** Tradução de palavra, expressão, frase e parágrafo usam o mesmo modelo
   (ADR-0001). Análise de dificuldade e repetição espaçada são determinísticas (ADR-0004, ADR-0005).
4. **Honestidade.** Nada simulado: sem modelo instalado, a interface diz isso; estimativas aparecem
   como estimativas, com confiança.

## Visão geral

```
┌──────────────────────── app (Compose, navegação, AppContainer) ───────────────────────┐
│ feature:library  feature:reader(F2)  feature:vocabulary  feature:review  feature:progress │
│ feature:settings                                         widget (Glance, F5)              │
└───────────────┬───────────────────────────────┬──────────────────────────────┬──────────┘
                │                               │                              │
        core:data (repositórios)        engine:translation (F3)        engine:analysis (F4)
                │                        ├ PromptProfile por modelo     ├ idioma e variante
     ┌──────────┼──────────┐             ├ fila com cancelamento        ├ métricas por capítulo
 core:database core:datastore            ├ cache (Room)                 └ faixa CEFR + confiança
   (Room)      (preferências)            └ engine:llama (JNI/NDK)
                                                └ llama.cpp (submódulo)
        engine:books (F2): EPUB/TXT → modelo de blocos → arquivos privados
        engine:models (F3): catálogo, download retomável, SHA-256, remoção
        engine:srs (F5): FSRS
```

## Fluxo de uma consulta de palavra (Fase 3)

1. Toque no texto → `getOffsetForPosition` → `BreakIterator` (palavra) e frase ao redor.
2. Consulta ao cache (`translation_cache`, chave = modelo + operação + idioma + texto + contexto).
   Achou → mostra imediatamente.
3. Não achou → mostra o pop-up com a palavra e um indicador discreto; a requisição entra numa fila
   de prioridade (palavras à frente de parágrafos). Um toque novo **cancela** a consulta de palavra
   anterior ainda pendente.
4. O motor monta o prompt com a parte fixa primeiro (KV reaproveitado) e gera no máximo ~8 tokens.
5. Resultado → pop-up; cache; histórico; vocabulário (salvamento automático, desativável).

Frase e parágrafo seguem o mesmo caminho, com saída em *streaming* (o texto aparece aos poucos) e
limite de tokens proporcional à entrada.

## Política de memória do modelo

- Carregado sob demanda na primeira consulta (ou ao abrir o leitor, se o usuário optar).
- Mantido enquanto o leitor está aberto; descarregado **3 minutos** depois de sair do leitor,
  imediatamente em `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL/COMPLETE)` e quando o app vai para
  segundo plano por mais de 1 minuto.
- Pesos por `mmap` sem repack: páginas de arquivo que o kernel pode descartar sob pressão, em vez de
  matar o app.

## Riscos antecipados e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Latência de palavra > 3 s em intermediários | Leitura interrompida | Cache, KV de prefixo, cancelamento, limite de tokens, perfil compacto (Hy-MT2) |
| Modelo de 2,5 GB não cabe/assusta | Instalação abandonada | Tamanho exibido antes; checagem de espaço livre; download retomável em Wi-Fi; perfil compacto |
| Alucinação em frases | Tradução enganosa | Greedy, limites de tokens, detecção de idioma da saída e de repetição, original sempre visível |
| EPUBs malformados ou enormes | Falha na importação | jsoup tolerante, streaming do ZIP, capítulos em arquivos, importação em WorkManager com progresso |
| Arquivos > 1 000 capítulos ou > 5 MB por capítulo | Memória | Divisão de capítulos longos em segmentos; `LazyColumn` virtualizada |
| CEFR impreciso | Expectativa errada | Faixa + confiança + explicação; calibração com textos de nível conhecido |
| Origem do modelo (repositório comunitário) some | Download quebra | Revisão fixada + SHA-256; importação manual de `.gguf`; possibilidade de espelho próprio |
| Licença Gemma | Obrigações de repasse | Termos e política exibidos e aceitos na instalação; NOTICE no app |
| Superaquecimento/bateria | Experiência ruim | Nada de inferência automática; threads limitadas aos núcleos rápidos; descarregamento por inatividade |
| Perda de dados em migração | Vocabulário perdido | Esquemas exportados, migrações testadas, nunca destrutivas; backup local exportável (Fase 6) |

## Fases

| Fase | Conteúdo | Estado |
|---|---|---|
| 0 | Diagnóstico, benchmark de modelos, ADRs | concluída |
| 1 | Estrutura, tema, tipografia, navegação, Room, DataStore | concluída (ver README) |
| 2 | Importação EPUB/TXT, biblioteca, leitor, posição, seleção | próxima |
| 3 | llama.cpp via JNI, gerenciamento do modelo, tradução, cache, medições no aparelho | — |
| 4 | Idioma/variante, análise CEFR por capítulo, perfil do leitor | — |
| 5 | Vocabulário a partir do leitor, FSRS, widget, estatísticas | — |
| 6 | Polimento, desempenho, testes de ponta a ponta, APK de release | — |
