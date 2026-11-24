package hu.voicetex.billentyuzet

/** Diktálási nyelvek: megjelenő név, Android nyelvkód, angol név a javító utasításához. */
data class Nyelv(val nev: String, val kod: String, val angol: String)

object Nyelvek {
    val lista = listOf(
        Nyelv("Magyar", "hu-HU", "Hungarian"),
        Nyelv("Svéd", "sv-SE", "Swedish"),
        Nyelv("Angol", "en-US", "English"),
        Nyelv("Német", "de-DE", "German"),
        Nyelv("Francia", "fr-FR", "French"),
        Nyelv("Spanyol", "es-ES", "Spanish"),
        Nyelv("Olasz", "it-IT", "Italian"),
        Nyelv("Portugál", "pt-PT", "Portuguese"),
        Nyelv("Holland", "nl-NL", "Dutch"),
        Nyelv("Lengyel", "pl-PL", "Polish"),
        Nyelv("Cseh", "cs-CZ", "Czech"),
        Nyelv("Szlovák", "sk-SK", "Slovak"),
        Nyelv("Román", "ro-RO", "Romanian"),
        Nyelv("Horvát", "hr-HR", "Croatian"),
        Nyelv("Szerb", "sr-RS", "Serbian"),
        Nyelv("Finn", "fi-FI", "Finnish"),
        Nyelv("Norvég", "nb-NO", "Norwegian"),
        Nyelv("Dán", "da-DK", "Danish"),
        Nyelv("Orosz", "ru-RU", "Russian"),
        Nyelv("Ukrán", "uk-UA", "Ukrainian"),
        Nyelv("Görög", "el-GR", "Greek"),
        Nyelv("Török", "tr-TR", "Turkish"),
    )

    fun keres(kod: String): Nyelv = lista.firstOrNull { it.kod == kod } ?: lista[0]
}
