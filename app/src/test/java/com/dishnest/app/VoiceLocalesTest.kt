package com.dishnest.app

import com.dishnest.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class VoiceLocalesTest {
    @Test fun allTargetLanguageFamiliesSupportCompleteCookingCommands() {
        val phrases = mapOf(
            "en-GB" to listOf("next step", "previous step", "start cooking", "ingredients", "finish cooking", "how much time is left", "pause timer", "timer 5 minutes"),
            "en-US" to listOf("next step", "previous step", "repeat", "ingredients", "finish cooking", "how much time is left", "pause timer", "timer 5 minutes"),
            "es" to listOf("siguiente paso", "paso anterior", "empezar a cocinar", "ingredientes", "terminar de cocinar", "cuánto tiempo queda", "pausar temporizador", "temporizador 5 minutos"),
            "fr" to listOf("étape suivante", "étape précédente", "commencer à cuisiner", "ingrédients", "terminer la cuisson", "combien de temps reste", "pause du minuteur", "minuteur 5 minutes"),
            "de" to listOf("nächster schritt", "vorheriger schritt", "kochen starten", "zutaten", "kochen beenden", "wie viel zeit bleibt", "timer pausieren", "timer 5 minuten"),
            "it" to listOf("prossimo passaggio", "passaggio precedente", "inizia a cucinare", "ingredienti", "termina la cottura", "quanto tempo rimane", "pausa timer", "timer 5 minuti"),
            "pt-BR" to listOf("próximo passo", "passo anterior", "começar a cozinhar", "ingredientes", "terminar de cozinhar", "quanto tempo falta", "pausar temporizador", "temporizador 5 minutos"),
            "pt-PT" to listOf("próximo passo", "passo anterior", "começar a cozinhar", "ingredientes", "terminar de cozinhar", "quanto tempo falta", "pausar temporizador", "temporizador 5 minutos"),
            "nl" to listOf("volgende stap", "vorige stap", "begin met koken", "ingrediënten", "stop met koken", "hoeveel tijd is er over", "pauzeer timer", "timer 5 minuten"),
            "pl" to listOf("następny krok", "poprzedni krok", "zacznij gotować", "składniki", "zakończ gotowanie", "ile czasu zostało", "wstrzymaj minutnik", "minutnik 5 minut"),
            "ar" to listOf("الخطوة التالية", "الخطوة السابقة", "ابدأ الطهي", "المكونات", "إنهاء الطهي", "كم تبقى من الوقت", "أوقف المؤقت مؤقتا", "مؤقت ٥ دقائق"),
            "hi" to listOf("अगला चरण", "पिछला चरण", "खाना पकाना शुरू करें", "सामग्री", "खाना पकाना समाप्त करें", "कितना समय बचा है", "टाइमर रोकें", "टाइमर ५ मिनट"),
        )
        val expected = listOf(VoiceCommand.Next, VoiceCommand.Previous, VoiceCommand.Repeat,
            VoiceCommand.Ingredients, VoiceCommand.Finish, VoiceCommand.TimeLeft, VoiceCommand.Pause, VoiceCommand.Timer(300))
        phrases.forEach { (locale, commands) -> commands.forEachIndexed { index, command ->
            assertEquals("$locale: $command", expected[index], VoiceParser.parse(command, locale))
        } }
    }

    @Test fun unrelatedWordsCompoundCommandsAndAmbiguousDurationsDoNotTriggerActions() {
        listOf("nextdoor", "do not finish cooking", "next then finish", "set a timer for 5.5 minutes",
            "timer for 1 minute and 30 seconds", "timer for 999999999999999999999 minutes",
            "timer for 1441 minutes").forEach { assertEquals(VoiceCommand.Unknown, VoiceParser.parse(it)) }
        assertEquals(VoiceCommand.Unknown, VoiceParser.parse("temporizador 1,5 minutos", "es"))
        assertEquals(VoiceCommand.Unknown, VoiceParser.parse("مؤقت ١ دقيقة و٣٠ ثانية", "ar"))
        assertEquals(VoiceCommand.Timer(30), VoiceParser.parse("टाइमर ३० सेकंड", "hi"))
        assertEquals(VoiceCommand.Repeat, VoiceParser.parse("Empezar.", "es"))
    }
}
