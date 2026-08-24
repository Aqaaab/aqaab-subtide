package com.aqaab.subtide

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reusable on-device translator. Models are prepared once per language and reused.
 * The caller may provide a source-language hint to avoid Language ID latency.
 */
class LocalTranslator {
    private val identifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val prepared = ConcurrentHashMap<String, AtomicBoolean>()

    fun prepare(sourceTag: String, onReady: () -> Unit, onError: (Throwable) -> Unit) {
        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: run {
            onError(IllegalArgumentException("Unsupported source language: $sourceTag"))
            return
        }
        val translator = getTranslator(source)
        if (prepared[source]!!.get()) {
            onReady()
            return
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                prepared[source]!!.set(true)
                onReady()
            }
            .addOnFailureListener(onError)
    }

    fun translateToArabic(
        text: String,
        sourceHint: String? = null,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (text.isBlank()) return
        val hint = sourceHint?.let { TranslateLanguage.fromLanguageTag(it) }
        if (hint == TranslateLanguage.ARABIC) {
            onResult(text)
            return
        }
        if (hint != null) {
            translateWith(hint, text, onResult, onError)
            return
        }
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                val source = TranslateLanguage.fromLanguageTag(tag)
                if (source == null || source == TranslateLanguage.ARABIC) {
                    onResult(text)
                } else {
                    translateWith(source, text, onResult, onError)
                }
            }
            .addOnFailureListener(onError)
    }

    private fun translateWith(
        source: String,
        text: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val translator = getTranslator(source)
        if (prepared[source]!!.get()) {
            translator.translate(text).addOnSuccessListener(onResult).addOnFailureListener(onError)
            return
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                prepared[source]!!.set(true)
                translator.translate(text).addOnSuccessListener(onResult).addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    private fun getTranslator(source: String): Translator {
        prepared.putIfAbsent(source, AtomicBoolean(false))
        return translators.getOrPut(source) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(TranslateLanguage.ARABIC)
                    .build()
            )
        }
    }

    fun close() {
        identifier.close()
        translators.values.forEach { it.close() }
        translators.clear()
        prepared.clear()
    }
}
