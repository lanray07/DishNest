package com.dishnest.app.domain

sealed interface VoiceCommand {
    data object Next : VoiceCommand

    data object Previous : VoiceCommand

    data object Repeat : VoiceCommand

    data object Ingredients : VoiceCommand

    data object Finish : VoiceCommand

    data object TimeLeft : VoiceCommand

    data object Pause : VoiceCommand

    data class Timer(val seconds: Long) : VoiceCommand

    data class Quantity(val ingredient: String) : VoiceCommand

    data object Unknown : VoiceCommand
}

object VoiceParser {
    fun parse(text: String, language: String = "en"): VoiceCommand {
        if (text.contains("timer", true) && Regex("\\d[.,]\\d").containsMatchIn(text))
            return VoiceCommand.Unknown
        val t = text.lowercase().replace(Regex("[.!?]"), "").trim()
        if (t == "start cooking") return VoiceCommand.Repeat
        val next =
            mapOf(
                "en" to listOf("next"),
                "es" to listOf("siguiente", "empezar"),
                "fr" to listOf("suivante", "commencer"),
                "de" to listOf("weiter", "nächste"),
                "it" to listOf("prossimo"),
                "pt" to listOf("próximo"),
                "nl" to listOf("volgende"),
                "pl" to listOf("następny"),
                "ar" to listOf("التالي"),
                "hi" to listOf("अगला"),
            )
        if ((next[language] ?: next.getValue("en")).any { it in t }) return VoiceCommand.Next
        if (listOf("go back", "previous", "anterior", "précédente", "zurück").any { it in t })
            return VoiceCommand.Previous
        if (listOf("repeat", "repeat that", "répéter", "repite", "wiederholen").any { it in t })
            return VoiceCommand.Repeat
        if ("finish" in t || "terminer" in t) return VoiceCommand.Finish
        if ("how much time" in t || "time is left" in t) return VoiceCommand.TimeLeft
        if ("pause" in t && "timer" in t) return VoiceCommand.Pause
        if ("timer" in t) {
            val words =
                mapOf(
                    "one" to 1L,
                    "two" to 2L,
                    "three" to 3L,
                    "four" to 4L,
                    "five" to 5L,
                    "six" to 6L,
                    "seven" to 7L,
                    "eight" to 8L,
                    "nine" to 9L,
                    "ten" to 10L,
                    "eleven" to 11L,
                    "twelve" to 12L,
                    "thirteen" to 13L,
                    "fourteen" to 14L,
                    "fifteen" to 15L,
                    "sixteen" to 16L,
                    "seventeen" to 17L,
                    "eighteen" to 18L,
                    "nineteen" to 19L,
                    "twenty" to 20L,
                    "thirty" to 30L,
                    "forty" to 40L,
                    "fifty" to 50L,
                    "sixty" to 60L,
                    "seventy" to 70L,
                    "eighty" to 80L,
                    "ninety" to 90L,
                )
            val phrase =
                t.substringAfter("timer").trim().removePrefix("for ").removeSuffix(" please").trim()
            val duration =
                Regex("^([a-z0-9 -]+)\\s+(minutes?|seconds?)$").matchEntire(phrase)
                    ?: return VoiceCommand.Unknown
            val number = duration.groupValues[1].trim()
            val tokens = number.replace('-', ' ').split(Regex("\\s+"))
            val n =
                number.toLongOrNull()
                    ?: when (tokens.size) {
                        1 -> words[tokens[0]]
                        2 -> {
                            val tens = words[tokens[0]]
                            val ones = words[tokens[1]]
                            if (
                                tens != null &&
                                    tens >= 20 &&
                                    tens % 10 == 0L &&
                                    ones != null &&
                                    ones in 1..9
                            )
                                tens + ones
                            else null
                        }
                        else -> null
                    }
            val seconds =
                n?.takeIf { it in 1..86400 }
                    ?.let { it * if (duration.groupValues[2].startsWith("second")) 1 else 60 }
            if (seconds != null && seconds in 1..86400) return VoiceCommand.Timer(seconds)
            return VoiceCommand.Unknown
        }
        if ("how much" in t)
            return VoiceCommand.Quantity(
                t.substringAfter("how much ").substringBefore(" do i").trim()
            )
        if ("ingredient" in t) return VoiceCommand.Ingredients
        return VoiceCommand.Unknown
    }
}
