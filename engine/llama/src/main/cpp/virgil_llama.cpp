// Ponte JNI mínima entre o Virgil e o llama.cpp (ADR-0002).
//
// Uma instância = um modelo + um contexto. Não é thread-safe: o lado Kotlin serializa as chamadas
// num executor dedicado. Recursos:
//  - reaproveitamento de prefixo do KV cache entre prompts (só o sufixo novo é avaliado);
//  - cancelamento cooperativo (durante a avaliação do prompt e a cada token gerado);
//  - saída em UTF-8 entregue apenas em sequências completas (tokens podem cortar caracteres);
//  - pesos mapeados (mmap) sem repack, para que o kernel possa descartar páginas sob pressão.

#include <jni.h>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>
#include <chrono>

#include "llama.h"
#include "ggml-backend.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VirgilLlama", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VirgilLlama", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { fprintf(stderr, __VA_ARGS__); fputc('\n', stderr); } while (0)
#define LOGE(...) do { fprintf(stderr, __VA_ARGS__); fputc('\n', stderr); } while (0)
#endif

namespace {

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::vector<llama_token> cached;   // tokens cujo estado está no KV (sequência 0)
    std::atomic<bool> cancel{false};
    bool partial_rm_ok = true;         // modelos recorrentes/híbridos não aceitam remoção parcial
};

bool abort_cb(void * data) {
    return static_cast<Engine *>(data)->cancel.load();
}

void log_cb(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) LOGE("%s", text);
}

// Quantos bytes do início de [s] formam sequências UTF-8 completas.
size_t complete_utf8_prefix(const std::string & s) {
    size_t i = 0, last_ok = 0;
    while (i < s.size()) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        size_t len = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 1;
        if (i + len > s.size()) break;
        i += len;
        last_ok = i;
    }
    return last_ok;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

// Avalia tokens[from..] em lotes de n_batch, a partir da posição [from]; logits só no último.
int eval_tokens(Engine * e, const std::vector<llama_token> & tokens, size_t from) {
    const int n_batch = static_cast<int>(llama_n_batch(e->ctx));
    llama_batch_ext * batch = llama_batch_ext_init(e->ctx);
    int rc = 0;
    for (size_t start = from; start < tokens.size() && rc == 0; start += n_batch) {
        const size_t end = std::min(tokens.size(), start + static_cast<size_t>(n_batch));
        llama_batch_ext_clear(batch);
        for (size_t i = start; i < end; ++i) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
            const llama_pos pos = static_cast<llama_pos>(i);
            llama_batch_ext_set_pos(batch, idx, &pos);
        }
        if (end == tokens.size()) llama_batch_ext_set_output_logits(batch, static_cast<int32_t>(end - start - 1), true);
        rc = llama_process(e->ctx, LLAMA_PROCESS_TYPE_DECODE, batch);
    }
    llama_batch_ext_free(batch);
    return rc;
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_virgil_engine_llama_LlamaNative_initBackends(JNIEnv * env, jclass, jstring jdir) {
    llama_log_set(log_cb, nullptr);
    if (jdir != nullptr) {
        const char * dir = env->GetStringUTFChars(jdir, nullptr);
        ggml_backend_load_all_from_path(dir);   // escolhe a variante de CPU adequada (dotprod, i8mm, SVE…)
        env->ReleaseStringUTFChars(jdir, dir);
    } else {
        ggml_backend_load_all();
    }
    llama_backend_init();
}

JNIEXPORT jlong JNICALL
Java_app_virgil_engine_llama_LlamaNative_load(JNIEnv * env, jclass, jstring jpath, jint n_ctx, jint n_threads, jboolean repack) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;
    mp.use_extra_bufts = repack == JNI_TRUE;
    llama_model * model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (model == nullptr) { LOGE("falha ao carregar o modelo"); return 0; }

    auto * e = new Engine();
    e->model = model;
    e->vocab = llama_model_get_vocab(model);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(n_ctx);
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    cp.n_seq_max = 1;
    cp.n_threads = n_threads;
    cp.n_threads_batch = n_threads;
    cp.abort_callback = abort_cb;
    cp.abort_callback_data = e;
    cp.no_perf = true;
    e->ctx = llama_init_from_model(model, cp);
    if (e->ctx == nullptr) {
        LOGE("falha ao criar o contexto");
        llama_model_free(model);
        delete e;
        return 0;
    }
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT void JNICALL
Java_app_virgil_engine_llama_LlamaNative_free(JNIEnv *, jclass, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e == nullptr) return;
    if (e->ctx) llama_free(e->ctx);
    if (e->model) llama_model_free(e->model);
    delete e;
}

JNIEXPORT void JNICALL
Java_app_virgil_engine_llama_LlamaNative_cancel(JNIEnv *, jclass, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (e != nullptr) e->cancel.store(true);
}

JNIEXPORT jint JNICALL
Java_app_virgil_engine_llama_LlamaNative_countTokens(JNIEnv * env, jclass, jlong handle, jstring jtext) {
    auto * e = reinterpret_cast<Engine *>(handle);
    const char * text = env->GetStringUTFChars(jtext, nullptr);
    const int n = -llama_tokenize(e->vocab, text, static_cast<int32_t>(strlen(text)), nullptr, 0, true, true);
    env->ReleaseStringUTFChars(jtext, text);
    return n;
}

// Gera a continuação de [jprompt]. A cada trecho UTF-8 completo chama callback.onPiece(byte[]):
// se devolver false, a geração para. Devolve long[5]:
//   [0] status (0 ok, 1 cancelado, 2 prompt longo demais, 3 erro de avaliação, 4 fim do contexto)
//   [1] tokens do prompt, [2] tokens reaproveitados do KV, [3] tokens gerados,
//   [4] tempo de avaliação do prompt em ms
JNIEXPORT jlongArray JNICALL
Java_app_virgil_engine_llama_LlamaNative_generate(JNIEnv * env, jclass, jlong handle, jstring jprompt,
                                                  jint max_tokens, jfloat repeat_penalty, jobject callback) {
    auto * e = reinterpret_cast<Engine *>(handle);
    e->cancel.store(false);
    jlong out[5] = {0, 0, 0, 0, 0};
    auto finish = [&](jlong status) {
        out[0] = status;
        jlongArray arr = env->NewLongArray(5);
        env->SetLongArrayRegion(arr, 0, 5, out);
        return arr;
    };

    // Tokenização (tokens especiais do template de chat são reconhecidos).
    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    const int32_t plen = static_cast<int32_t>(strlen(prompt));
    int n = -llama_tokenize(e->vocab, prompt, plen, nullptr, 0, true, true);
    std::vector<llama_token> tokens(std::max(n, 0));
    llama_tokenize(e->vocab, prompt, plen, tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    env->ReleaseStringUTFChars(jprompt, prompt);
    // Evita BOS duplicado quando o template já o contém.
    if (tokens.size() >= 2 && tokens[0] == llama_vocab_bos(e->vocab) && tokens[1] == tokens[0]) tokens.erase(tokens.begin());
    out[1] = static_cast<jlong>(tokens.size());

    const int n_ctx = static_cast<int>(llama_n_ctx(e->ctx));
    if (tokens.empty() || static_cast<int>(tokens.size()) >= n_ctx - 8) return finish(2);

    // Prefixo comum com o que já está no KV.
    size_t common = 0;
    while (common < e->cached.size() && common < tokens.size() && e->cached[common] == tokens[common]) common++;
    if (common == tokens.size()) common--;  // é preciso avaliar ao menos um token para obter logits
    llama_memory_t mem = llama_get_memory(e->ctx);
    if (!e->partial_rm_ok || !llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(common), -1)) {
        e->partial_rm_ok = false;
        llama_memory_clear(mem, true);
        common = 0;
    }
    e->cached.assign(tokens.begin(), tokens.begin() + static_cast<long>(common));
    out[2] = static_cast<jlong>(common);

    const auto t0 = std::chrono::steady_clock::now();
    int rc = eval_tokens(e, tokens, common);
    out[4] = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - t0).count();
    if (rc != 0) {
        // Avaliação interrompida: ressincroniza o que de fato ficou no KV.
        const llama_pos last = llama_memory_seq_pos_max(mem, 0);
        e->cached.assign(tokens.begin(), tokens.begin() + std::max<long>(0, std::min<long>(last + 1, static_cast<long>(tokens.size()))));
        return finish(rc == 2 || e->cancel.load() ? 1 : 3);
    }
    e->cached = tokens;

    // Amostragem gulosa (determinística e cacheável), com penalidade de repetição opcional.
    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (repeat_penalty > 1.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(e->vocab), 64, repeat_penalty, 0.0f, 0.0f));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onPiece = env->GetMethodID(cbClass, "onPiece", "([B)Z");

    std::string pending;
    jlong status = 0;
    llama_batch_ext * batch = llama_batch_ext_init(e->ctx);
    int generated = 0;
    const int budget = std::min<int>(max_tokens, n_ctx - static_cast<int>(e->cached.size()) - 1);
    if (budget < max_tokens) status = 4;
    for (; generated < budget; ++generated) {
        if (e->cancel.load()) { status = 1; break; }
        const llama_token tok = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, tok)) { status = status == 4 ? 0 : status; break; }
        char buf[256];
        const int len = llama_token_to_piece(e->vocab, tok, buf, sizeof(buf), 0, false);
        if (len > 0) pending.append(buf, static_cast<size_t>(len));
        const size_t ok = complete_utf8_prefix(pending);
        if (ok > 0) {
            jbyteArray bytes = to_bytes(env, pending.substr(0, ok));
            const jboolean keep = env->CallBooleanMethod(callback, onPiece, bytes);
            env->DeleteLocalRef(bytes);
            pending.erase(0, ok);
            // O token foi entregue mas não avaliado: o KV continua coerente com e->cached.
            if (!keep) { generated++; break; }
        }
        // Avalia o token gerado para a próxima etapa.
        llama_batch_ext_clear(batch);
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tok);
        const llama_pos pos = static_cast<llama_pos>(e->cached.size());
        llama_batch_ext_set_pos(batch, idx, &pos);
        llama_batch_ext_set_output_logits(batch, idx, true);
        if (llama_process(e->ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) { status = e->cancel.load() ? 1 : 3; break; }
        e->cached.push_back(tok);
    }
    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    out[3] = generated;
    return finish(status);
}

} // extern "C"
