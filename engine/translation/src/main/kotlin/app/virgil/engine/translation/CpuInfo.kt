package app.virgil.engine.translation

import android.os.Build
import java.io.File

internal object CpuInfo {
    fun isSupported(): Boolean = Build.SUPPORTED_ABIS.orEmpty().contains("arm64-v8a")

    /**
     * Núcleos de alto desempenho (frequência máxima ≥ 80% da maior). Em SoCs big.LITTLE, usar os
     * núcleos eficientes na inferência deixa tudo mais lento (sincronização) e esquenta o aparelho.
     */
    fun performanceCores(): Int {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { i ->
            runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        if (freqs.isEmpty()) return (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
        val max = freqs.max()
        return freqs.count { it >= max * 0.8 }.coerceIn(2, 4)
    }
}
