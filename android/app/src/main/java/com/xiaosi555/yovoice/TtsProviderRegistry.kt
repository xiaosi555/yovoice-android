package com.xiaosi555.yovoice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import java.util.Locale

data class TtsProviderEntry(
    val packageName: String,
    val label: String
)

/** Discovers only services that advertise Android's public TTS service action. */
object TtsProviderRegistry {
    fun discover(context: Context): List<TtsProviderEntry> {
        return runCatching {
            context.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.GET_META_DATA
            ).mapNotNull { resolved ->
                val service = resolved.serviceInfo ?: return@mapNotNull null
                val packageName = service.packageName?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val label = runCatching {
                    resolved.loadLabel(context.packageManager).toString()
                }.getOrDefault(packageName).ifBlank { packageName }
                TtsProviderEntry(packageName, label)
            }.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }.getOrDefault(emptyList())
    }
}
