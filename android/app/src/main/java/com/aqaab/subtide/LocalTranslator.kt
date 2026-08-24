package com.aqaab.subtide

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.ConcurrentHashMap

/** On-device language identification + translation to Arabic. */
class LocalTranslator {
    private val identifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()

    fun translateToArabic(text: String, onResult: (String) -> Unit, onError: (Throwable) -> Unit) {
        if (text.isBlank()) return
        identifier.identifyLanguage(text)
            .addOnSuccessListener { tag ->
                val source = TranslateLanguage.fromLanguageTag(tag)
                if (source == null || source == TranslateLanguage.ARABIC) {
                    onResult(text)
                    return@addOnSuccessListener
                }
                val translator = translators.getOrPut(source) {
                    Translation.getClient(
                        TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(TranslateLanguage.ARABIC).build()
                    )
                }
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { translator.translate(text).addOnSuccessListener(onResult).addOnFailureListener(onError) }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    fun close() {
        identifier.close()
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
