package com.dishnest.app.domain

/** Typed/pasted amounts have no reliable source locale. Never guess a grouping separator. */
object QuantityNumbers {
    private fun digits(raw: String) = raw.map { character ->
        when (character) {
            '٫', '．' -> '.'
            '，' -> ','
            else -> Character.digit(character, 10).takeIf { it >= 0 }
                ?.let { ('0'.code + it).toChar() } ?: character
        }
    }.joinToString("")

    fun decimal(raw: String): Double? {
        val value = digits(raw.trim())
        if (!Regex("^(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)$").matches(value)) return null
        // A comma with three fractional digits could instead be a thousands separator.
        if (Regex("^[0-9]+,[0-9]{3}$").matches(value)) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    }

    fun ingredient(raw: String): Double? {
        val parts = digits(raw.trim()).split(Regex("[\\s\\u00a0\\u202f]+"))
        if (parts.size !in 1..2) return null
        if (parts.size == 2 && (!Regex("^[0-9]+$").matches(parts[0]) || '/' !in parts[1])) return null
        val amount = parts.sumOf { part ->
            if ('/' in part) {
                val fraction = part.split('/')
                if (fraction.size != 2 || fraction.any { !Regex("^[0-9]+$").matches(it) }) return null
                val numerator = fraction[0].toDoubleOrNull() ?: return null
                val denominator = fraction[1].toDoubleOrNull() ?: return null
                if (!numerator.isFinite() || !denominator.isFinite() || denominator <= 0) return null
                numerator / denominator
            } else decimal(part) ?: return null
        }
        return amount.takeIf { it.isFinite() && it > 0 }
    }
}
