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

    /** A lebegő gomb helye a képernyő arányában (-1 = alaphelyzet a billentyűzet fölött). */
    var lebegoX: Float
        get() = p.getFloat("lebego_x", -1f)
        set(v) = p.edit().putFloat("lebego_x", v).apply()

    var lebegoY: Float
        get() = p.getFloat("lebego_y", -1f)
        set(v) = p.edit().putFloat("lebego_y", v).apply()

    /** 0 = kicsi, 1 = közepes, 2 = nagy */
    var lebegoMeret: Int
        get() = p.getInt("lebego_meret", 1)
        set(v) = p.edit().putInt("lebego_meret", v).apply()

    /** A második érintés után el is küldi a szöveget. */
    var autoKuldes: Boolean
        get() = p.getBoolean("auto_kuldes", true)
        set(v) = p.edit().putBoolean("auto_kuldes", v).apply()

    val vanKulcs: Boolean get() = apiKulcs.startsWith("sk-ant-")
}
