# ADR-0002 — Runtime de inferência no Android

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

O runtime precisa executar o modelo escolhido (ADR-0001) offline, em CPU ARM64 de celulares
comuns, com baixo uso de memória, cancelamento de requisições e reaproveitamento de KV cache.

## Alternativas

| Runtime | Avaliação |
|---|---|
| **llama.cpp** (MIT) | Suporta `gemma3`, `hunyuan-dense` e `qwen35` (os três candidatos), GGUF com quantizações k-quant, kernels NEON/dotprod/i8mm e KleidiAI, `mmap`, controle total de KV cache e amostragem via API C. Compila com o NDK via CMake. Muito ativo. |
| MediaPipe LLM Inference / LiteRT-LM | Otimizado (inclusive GPU) mas centrado em Gemma em formato `.task`/`.litertlm`; não roda Hy-MT2; formatos de conversão próprios; menos controle de KV. |
| ONNX Runtime GenAI | Exige exportação ONNX específica por arquitetura; sem suporte estável aos três candidatos; binário maior. |
| MLC-LLM | Compilação TVM por modelo/dispositivo; complexidade de build alta. |
| ExecuTorch | Bom para Llama/Qwen exportados; exige exportação por modelo; menos maduro para Gemma 3 quantizado em k-quants. |

## Decisão

**llama.cpp**, incorporado como submódulo git (`third_party/llama.cpp`, commit `8d60873`, o mesmo do
benchmark), compilado pelo Gradle (`externalNativeBuild` + CMake) para `arm64-v8a`, com uma camada JNI
mínima escrita para o Virgil (`engine/llama/src/main/cpp/virgil_llama.cpp`), sem bindings de terceiros:

- **Variantes de CPU em tempo de execução** (`GGML_CPU_ALL_VARIANTS` + `GGML_BACKEND_DL`, como no
  exemplo oficial do llama.cpp para Android): sete bibliotecas, de armv8.0 a armv9.2 (dotprod, i8mm,
  SVE, SME); o ggml carrega a melhor que o processador suporta. Um mesmo APK aproveita os núcleos
  novos e não trava por instrução ilegal nos antigos. Exige extrair as bibliotecas na instalação
  (`jniLibs.useLegacyPackaging = true`). Custo: ~12 MB de código nativo no APK.

- Carregamento com `mmap` ativo e **repack desativado** (pesos ficam como páginas de arquivo,
  descartáveis pelo kernel; ver benchmark).
- Uma única instância de modelo + contexto, acessada por um executor dedicado (llama.cpp não é
  thread-safe por contexto).
- Cancelamento cooperativo: a cada token gerado o laço nativo consulta uma flag atômica.
- Reaproveitamento de prefixo: o prompt é montado com a parte fixa primeiro; o KV correspondente é
  mantido e só o sufixo novo é avaliado.
- Threads = número de núcleos de alto desempenho (lido de `/sys/devices/system/cpu/*/cpufreq`),
  limitado a 4, para não aquecer o aparelho.

## Verificação

A mesma ponte JNI é compilada para Linux e testada na JVM com o modelo real
(`engine/llama/.../LlamaHostTest`, `engine/translation/.../EngineHostTest`): tradução com acentos
íntegros, reaproveitamento de prefixo (44 de 86 tokens numa segunda consulta na mesma frase, 2,1 s →
1,4 s), cancelamento em 0,4 s durante a avaliação do prompt, e cancelamento pela interface ("a última
consulta vence").

## Consequências

- Build nativo adiciona alguns minutos à primeira compilação e exige NDK r28+ e CMake 3.31.
- O tamanho do APK cresce ~3–6 MB por ABI com a biblioteca nativa.
- Atualizar o llama.cpp = atualizar o submódulo + rodar o benchmark novamente.
