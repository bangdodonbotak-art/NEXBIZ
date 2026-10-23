package com.example.core.util

import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

object PosPrimitives {
    private val idrLocale = Locale.forLanguageTag("id-ID")

    fun generateId(): String = UUID.randomUUID().toString()

    fun currentUtcEpochMillis(): Long = System.currentTimeMillis()

    fun formatIdr(amountRp: Long): String {
        val formatter = NumberFormat.getNumberInstance(idrLocale).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        val prefix = if (amountRp < 0L) "-Rp " else "Rp "
        return prefix + formatter.format(kotlin.math.abs(amountRp))
    }
}
