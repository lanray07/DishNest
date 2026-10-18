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
    private data class Phrases(
        val next: List<String>, val previous: List<String>, val repeat: List<String>,
        val ingredients: List<String>, val finish: List<String>, val timeLeft: List<String>,
        val pause: List<String>, val timer: String, val minutes: String, val seconds: String,
    )
    private fun phrases(vararg values: String) = Phrases(
        values[0].split("|"), values[1].split("|"), values[2].split("|"), values[3].split("|"),
        values[4].split("|"), values[5].split("|"), values[6].split("|"), values[7], values[8], values[9],
    )
    private val dictionaries = mapOf(
        "en" to phrases("next|next step|read the next step", "previous|previous step|go back|go back one step",
            "repeat|repeat that|start cooking", "ingredients|show ingredients", "finish|finish cooking",
            "how much time|how much time is left|time is left", "pause timer|pause the timer", "timer", "minutes?", "seconds?"),
        "es" to phrases("siguiente|siguiente paso", "anterior|paso anterior", "repite|repetir|empezar|empezar a cocinar",
            "ingredientes|mostrar ingredientes", "terminar|terminar de cocinar", "cuánto tiempo queda", "pausar temporizador", "temporizador", "minutos?", "segundos?"),
        "fr" to phrases("suivante|étape suivante", "précédente|étape précédente", "répéter|répète|commencer|commencer à cuisiner",
            "ingrédients|afficher les ingrédients", "terminer|terminer la cuisson", "combien de temps reste", "pause du minuteur", "minuteur", "minutes?", "secondes?"),
        "de" to phrases("weiter|nächste|nächster schritt", "zurück|vorheriger schritt", "wiederholen|kochen starten",
            "zutaten|zutaten anzeigen", "beenden|kochen beenden", "wie viel zeit bleibt", "timer pausieren", "timer", "minuten?", "sekunden?"),
        "it" to phrases("prossimo|prossimo passaggio", "precedente|passaggio precedente", "ripeti|inizia a cucinare",
            "ingredienti|mostra ingredienti", "finisci|termina la cottura", "quanto tempo rimane", "pausa timer", "timer", "minut[oi]", "second[oi]"),
        "pt" to phrases("próximo|próximo passo", "anterior|passo anterior", "repetir|repete|começar a cozinhar",
            "ingredientes|mostrar ingredientes", "terminar|terminar de cozinhar", "quanto tempo falta", "pausar temporizador", "temporizador", "minutos?", "segundos?"),
        "nl" to phrases("volgende|volgende stap", "vorige|vorige stap", "herhaal|begin met koken",
            "ingrediënten|toon ingrediënten", "klaar|stop met koken", "hoeveel tijd is er over", "pauzeer timer", "timer", "minuut|minuten", "seconde|seconden"),
        "pl" to phrases("następny|następny krok", "poprzedni|poprzedni krok", "powtórz|zacznij gotować",
            "składniki|pokaż składniki", "zakończ|zakończ gotowanie", "ile czasu zostało", "wstrzymaj minutnik", "minutnik", "minuta|minuty|minut", "sekunda|sekundy|sekund"),
        "ar" to phrases("التالي|الخطوة التالية", "السابق|الخطوة السابقة", "كرر|ابدأ الطهي",
            "المكونات|اعرض المكونات", "إنهاء|إنهاء الطهي", "كم تبقى من الوقت", "أوقف المؤقت مؤقتا", "مؤقت", "دقيقة|دقائق", "ثانية|ثواني|ثوان"),
        "hi" to phrases("अगला|अगला चरण", "पिछला|पिछला चरण", "दोहराएँ|खाना पकाना शुरू करें",
            "सामग्री|सामग्री दिखाएँ", "समाप्त|खाना पकाना समाप्त करें", "कितना समय बचा है", "टाइमर रोकें", "टाइमर", "मिनट", "सेकंड"),
    )

    fun parse(text: String, language: String = "en"): VoiceCommand {
        // Match complete commands rather than fragments inside unrelated words or compound requests.
        val family = language.lowercase(java.util.Locale.ROOT).substringBefore('-').substringBefore('_')
        val dictionary = dictionaries[family] ?: dictionaries.getValue("en")
        val t = text.lowercase(java.util.Locale.ROOT).trim().trimEnd('.', '!', '?', '؟', '।').trim()
        when (t) {
            in dictionary.next -> return VoiceCommand.Next
            in dictionary.previous -> return VoiceCommand.Previous
            in dictionary.repeat -> return VoiceCommand.Repeat
            in dictionary.ingredients -> return VoiceCommand.Ingredients
            in dictionary.finish -> return VoiceCommand.Finish
            in dictionary.timeLeft -> return VoiceCommand.TimeLeft
            in dictionary.pause -> return VoiceCommand.Pause
        }
        if (family != "en" && family in dictionaries) {
            val localized = t.map { character ->
                Character.digit(character, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: character
            }.joinToString("")
            val duration = Regex("^${Regex.escape(dictionary.timer)} ([0-9]+) (${dictionary.minutes}|${dictionary.seconds})$").matchEntire(localized)
                ?: return VoiceCommand.Unknown
            val amount = duration.groupValues[1].toLongOrNull() ?: return VoiceCommand.Unknown
            val multiplier = if (Regex(dictionary.seconds).matches(duration.groupValues[2])) 1L else 60L
            return if (amount in 1..(86400 / multiplier)) VoiceCommand.Timer(amount * multiplier) else VoiceCommand.Unknown
        }
        if (t.startsWith("timer ") || t.startsWith("set a timer ")) {
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
            val phrase = t.removePrefix("set a ").removePrefix("timer ").removePrefix("for ").removeSuffix(" please").trim()
            val duration = Regex("^([a-z0-9 -]+)\\s+(minutes?|seconds?)$").matchEntire(phrase)
                ?: return VoiceCommand.Unknown
            val number = duration.groupValues[1].trim()
            val tokens = number.replace('-', ' ').split(Regex("\\s+"))
            val n = number.toLongOrNull() ?: when (tokens.size) {
                1 -> words[tokens[0]]
                2 -> {
                    val tens = words[tokens[0]]; val ones = words[tokens[1]]
                    if (tens != null && tens >= 20 && tens % 10 == 0L && ones != null && ones in 1..9) tens + ones else null
                }
                else -> null
            }
            val multiplier = if (duration.groupValues[2].startsWith("second")) 1L else 60L
            if (n != null && n in 1..(86400 / multiplier)) return VoiceCommand.Timer(n * multiplier)
            return VoiceCommand.Unknown
        }
        val quantity = Regex("^how much (.+?)(?: do i need)?$").matchEntire(t)
        if (quantity != null) return VoiceCommand.Quantity(quantity.groupValues[1].trim())
        return VoiceCommand.Unknown
    }
}
