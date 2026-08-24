package com.aqaab.subtide

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap

/** Fast on-device language identification + Arabic translation with model reuse. */
class LocalTranslator {
    private val identifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()
    private val ready = ConcurrentHashMap.newKeySet<String>()

    fun prepare(sourceTag: String, onReady: () -> Unit = {}, onError: (Throwable) -> Unit = {}) {
        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: run {
            onError(IllegalArgumentException("Unsupported language: $sourceTag"))
            return
        }
        if (source == TranslateLanguage.ARABIC) {
            onReady()
            return
        }
        val translator = getTranslator(source)
        if (ready.contains(source)) {
            onReady()
            return
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                ready.add(source)
                onReady()
            }
            .addOnFailureListener(onError)
    }

    fun translateToArabic(
        text: String,
        hintedSourceTag: String? = null,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (text.isBlank()) return

        val hinted = hintedSourceTag?.let { TranslateLanguage.fromLanguageTag(it) }
        if (hinted == TranslateLanguage.ARABIC) {
            onResult(text)
            return
        }

        if (hinted != null) {
            translateWithSource(text, hinted, onResult, onError)
            return
        }

        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                val source = TranslateLanguage.fromLanguageTag(tag)
                if (source == null || source == TranslateLanguage.ARABIC) {
                    onResult(text)
                } else {
                    translateWithSource(text, source, onResult, onError)
                }
            }
            .addOnFailureListener(onError)
    }

    private fun translateWithSource(
        text: String,
        source: String,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val translator = getTranslator(source)
        if (ready.contains(source)) {
            translator.translate(text)
                .addOnSuccessListener(onResult)
                .addOnFailureListener(onError)
            return
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                ready.add(source)
                translator.translate(text)
                    .addOnSuccessListener(onResult)
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    private fun getTranslator(source: String): Translator = translators.getOrPut(source) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.ARABIC)
                .build()
        )
    }

    fun close() {
        identifier.close()
        translators.values.forEach { it.close() }
        translators.clear()
        ready.clear()
    }
}
