package hu.voicetex.billentyuzet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Beállítások és beüzemelési lépések egy képernyőn. */
class BeallitasokActivity : Activity() {

    private lateinit var beallitasok: Beallitasok
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var engedelyAllapot: TextView
    private lateinit var billentyuzetAllapot: TextView
    private lateinit var kulcsAllapot: TextView
    private lateinit var lebegoAllapot: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        beallitasok = Beallitasok(this)

        val tartalom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }
        val gorgeto = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1E2A3A"))
            addView(tartalom)
        }
        setContentView(gorgeto)

        tartalom.addView(szoveg("Voicetex Diktálás", 24f, felkover = true, szin = "#E8C46A"))
        tartalom.addView(szoveg(
            "Diktálj bármelyik appban: a telefon felismerője leírja, a Claude Haiku kijavítja, " +
                "és a szöveg a kurzor helyére kerül.", 14f
        ))

        // 1. Mikrofon
        tartalom.addView(cim("1. Mikrofon-engedély"))
        engedelyAllapot = szoveg("", 14f)
        tartalom.addView(engedelyAllapot)
        tartalom.addView(gomb("Engedély megadása") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })

        // 2. Billentyűzet bekapcsolása
        tartalom.addView(cim("2. Billentyűzet bekapcsolása"))
        billentyuzetAllapot = szoveg("", 14f)
        tartalom.addView(billentyuzetAllapot)
        tartalom.addView(gomb("Billentyűzet-beállítások megnyitása") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        // 3. API-kulcs
        tartalom.addView(cim("3. Claude API-kulcs"))
        kulcsAllapot = szoveg("", 14f)
        tartalom.addView(kulcsAllapot)
        val kulcsMezo = mezo("sk-ant-…", jelszo = true).apply { setText(beallitasok.apiKulcs) }
        tartalom.addView(kulcsMezo)
        tartalom.addView(gomb("Kulcs mentése") {
            beallitasok.apiKulcs = kulcsMezo.text.toString()
            frissit()
        })

        // 4. Modell
        tartalom.addView(cim("4. Modell"))
        tartalom.addView(szoveg("Üresen hagyva: ${ClaudeJavito.ALAP_MODELL}", 13f, szin = "#9AA8B8"))
        val modellMezo = mezo(ClaudeJavito.ALAP_MODELL).apply {
            if (beallitasok.modell != ClaudeJavito.ALAP_MODELL) setText(beallitasok.modell)
        }
        tartalom.addView(modellMezo)
        tartalom.addView(gomb("Modell mentése") {
            beallitasok.modell = modellMezo.text.toString()
            modellMezo.setText(if (beallitasok.modell == ClaudeJavito.ALAP_MODELL) "" else beallitasok.modell)
        })

        // 4a. Nyelv
        tartalom.addView(cim("Diktálási nyelv"))
        tartalom.addView(szoveg("A felismerő és a javító is ezen a nyelven dolgozik. A telefonon " +
            "a Google hangbevitelnél legyen letöltve az adott nyelv.", 13f, szin = "#9AA8B8"))
        val nyelvValaszto = android.widget.Spinner(this).apply {
            adapter = object : android.widget.ArrayAdapter<String>(
                this@BeallitasokActivity, android.R.layout.simple_spinner_dropdown_item,
                Nyelvek.lista.map { it.nev }
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                    (super.getView(position, convertView, parent) as TextView).apply {
                        setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    }
            }
            setSelection(Nyelvek.lista.indexOf(Nyelvek.keres(beallitasok.nyelv)))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    beallitasok.nyelv = Nyelvek.lista[pos].kod
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
        }
        tartalom.addView(nyelvValaszto)

        // 4b. Saját szabályok
        tartalom.addView(cim("Saját szabályok"))
        tartalom.addView(szoveg("Soronként egy szabály, a javító ezeket is betartja. " +
            "Példa: „Ha azt mondom »smiley«, tegyél 🙂-t.”", 13f, szin = "#9AA8B8"))
        val szabalyMezo = mezo("").apply {
            setText(beallitasok.sajatSzabalyok)
            minLines = 3
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        tartalom.addView(szabalyMezo)
        tartalom.addView(gomb("Szabályok mentése") {
            beallitasok.sajatSzabalyok = szabalyMezo.text.toString()
            android.widget.Toast.makeText(this, "Elmentve", android.widget.Toast.LENGTH_SHORT).show()
        })

        // 5. Próba
        tartalom.addView(cim("5. Kipróbálás"))
        val probaMezo = mezo("").apply {
            setText("szia holnap delutan haromkor talalkozunk a kavezoban hozol e magaddal egy ernyot nem tudom hol lesz pontosan de majd megirom eljossz")
            minLines = 2
        }
        tartalom.addView(probaMezo)
        val probaEredmeny = szoveg("", 15f, szin = "#BFE3C8")
        tartalom.addView(gomb("Javítás kipróbálása") {
            if (!beallitasok.vanKulcs) {
                probaEredmeny.text = "Előbb mentsd el az API-kulcsot."
                return@gomb
            }
            probaEredmeny.text = "Javítás…"
            val nyers = probaMezo.text.toString()
            scope.launch {
                probaEredmeny.text = try {
                    "✓ " + withContext(Dispatchers.IO) {
                        ClaudeJavito.javit(beallitasok.apiKulcs, beallitasok.modell, nyers, "", beallitasok.sajatSzabalyok, beallitasok.nyelv)
                    }
                } catch (e: Exception) {
                    "Hiba: ${e.message}"
                }
            }
        })
        tartalom.addView(probaEredmeny)

        // 6. Lebegő gomb (csak a teljes változatban)
        if (BuildConfig.LEBEGO_GOMB) {
            tartalom.addView(cim("6. Lebegő mikrofon (a megszokott billentyűzet fölött)"))
            lebegoAllapot = szoveg("", 14f)
            tartalom.addView(lebegoAllapot)
            tartalom.addView(szoveg(
                "Kisegítő szolgáltatásként működik. Bekapcsolás: Kisegítő lehetőségek → Telepített alkalmazások " +
                    "→ Voicetex lebegő mikrofon → Be.\n" +
                    "Ha szürke és nem kapcsolható: Beállítások → Alkalmazások → Voicetex Diktálás → jobb felül ⋮ → " +
                    "„Korlátozott beállítások engedélyezése”, utána próbáld újra.", 13f, szin = "#9AA8B8"
            ))
            tartalom.addView(gomb("Kisegítő lehetőségek megnyitása") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })

        }

        // 7. Használat
        tartalom.addView(cim("Használat"))
        tartalom.addView(gomb("Váltás a Voicetex billentyűzetre") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        tartalom.addView(szoveg(
            "• 🎤: diktálás indítása. Beszélhetsz hosszan, szünetekkel is; a végén nyomd meg a „Kész” gombot.\n" +
                "• ⌨: vissza a megszokott billentyűzetedre.\n" +
                "• Javítás BE/KI: a Claude-javítás ki-be kapcsolása.\n" +
                "• Nyers: az utolsó javított szöveget lecseréli az eredeti, javítatlanra (és vissza).\n" +
                "• Ha nincs internet, a javítatlan szöveg kerül be.\n" +
                (if (BuildConfig.LEBEGO_GOMB) "• Lebegő gomb: megnyomva diktálsz, újra megnyomva kész. Hosszan nyomva megnyílnak a beállítások. " +
                    "Jelszómezőknél nem jelenik meg.\n" else "") +
                "• A felismerést a telefon alapértelmezett hangbeviteli szolgáltatása végzi " +
                "(Beállítások → Általános kezelés → Hangbevitel).", 14f
        ))
    }

    override fun onResume() {
        super.onResume()
        frissit()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        frissit()
    }

    private fun frissit() {
        val mikrofon = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        engedelyAllapot.text = if (mikrofon) "✓ Megadva" else "✗ Még nincs megadva"

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val bekapcsolva = imm.enabledInputMethodList.any { it.packageName == packageName }
        billentyuzetAllapot.text = if (bekapcsolva) "✓ Bekapcsolva"
        else "✗ Kapcsold be a listában a „Voicetex diktáló” billentyűzetet"

        val lebegoBe = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').any { it.startsWith("$packageName/") }
        if (BuildConfig.LEBEGO_GOMB) lebegoAllapot.text = if (lebegoBe) "✓ Bekapcsolva" else "✗ Kikapcsolva"

        kulcsAllapot.text = when {
            beallitasok.vanKulcs -> "✓ Elmentve (…${beallitasok.apiKulcs.takeLast(4)})"
            beallitasok.apiKulcs.isNotEmpty() -> "✗ Ez nem tűnik Claude-kulcsnak (sk-ant- kezdetű kell)"
            else -> "✗ Még nincs megadva"
        }
    }

    // ------------------------------------------------------------------ segédek

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun szoveg(s: String, meret: Float, felkover: Boolean = false, szin: String = "#D0D8E0") =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, meret)
            setTextColor(Color.parseColor(szin))
            if (felkover) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun cim(s: String) = szoveg(s, 17f, felkover = true, szin = "#FFFFFF").apply {
        setPadding(0, dp(22), 0, dp(4))
    }

    private fun mezo(tipp: String, jelszo: Boolean = false) = EditText(this).apply {
        hint = tipp
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#7A8BA0"))
        inputType = if (jelszo) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        isSingleLine = jelszo
    }

    private fun gomb(felirat: String, katt: () -> Unit) = Button(this).apply {
        text = felirat
        isAllCaps = false
        setTextColor(Color.parseColor("#1E2A3A"))
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor("#E8C46A"))
        }
        setOnClickListener { katt() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) }
    }
}
