package hu.voicetex.billentyuzet

import android.content.Context

/**
 * Az app beállításai. Az API-kulcs az app saját, privát tárhelyén van
 * (más app nem olvashatja, és a biztonsági mentésbe sem kerül bele).
 */
class Beallitasok(context: Context) {
    private val p = context.getSharedPreferences("voicetex", Context.MODE_PRIVATE)

    var apiKulcs: String
        get() = p.getString("api_kulcs", "") ?: ""
        set(v) = p.edit().putString("api_kulcs", v.trim()).apply()

    var modell: String
        get() = (p.getString("modell", "") ?: "").ifBlank { ClaudeJavito.ALAP_MODELL }
        set(v) = p.edit().putString("modell", v.trim()).apply()

    var javitasBe: Boolean
        get() = p.getBoolean("javitas_be", true)
        set(v) = p.edit().putBoolean("javitas_be", v).apply()

    var sajatSzabalyok: String
        get() = p.getString("sajat_szabalyok", "") ?: ""
        set(v) = p.edit().putString("sajat_szabalyok", v.trim()).apply()

    var nyelv: String
        get() = p.getString("nyelv", "hu-HU") ?: "hu-HU"
        set(v) = p.edit().putString("nyelv", v).apply()

    val vanKulcs: Boolean get() = apiKulcs.startsWith("sk-ant-")
}
