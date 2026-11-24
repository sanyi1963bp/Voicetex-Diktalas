package hu.voicetex.billentyuzet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Diktáló billentyűzet.
 *
 * Menet: mikrofon gomb → a telefon telepített beszédfelismerője leírja a szöveget →
 * a Claude Haiku kijavítja → a javított szöveg a kurzor helyére kerül.
 * Ha a javítás nem sikerül (nincs net, nincs kulcs), a nyers szöveg kerül be.
 */
class DiktaloBillentyuzet : InputMethodService() {

    private enum class Allapot { KESZ, HALLGAT, JAVIT }

    private lateinit var beallitasok: Beallitasok
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var felismero: SpeechRecognizer? = null
    private var allapot = Allapot.KESZ
    private var aktiv = false          // a felhasználó még diktál (nem nyomta meg a stopot)
    private var lezarva = true         // az aktuális diktálás már feldolgozva
    private var csendSzamlalo = 0      // egymás utáni "nem hallottam semmit" hibák
    private val gyujto = StringBuilder()
    private var munkamenet = 0         // minden diktálás új sorszámot kap

    // A "Nyers" gombhoz: az utolsó beszúrás javított és nyers változata
    private var utolsoBeszurt: String? = null
    private var utolsoMasik: String? = null

    private lateinit var allapotSor: TextView
    private lateinit var mikrofonGomb: Button
    private lateinit var javitasGomb: Button
    private lateinit var nyersGomb: Button

    override fun onCreate() {
        super.onCreate()
        beallitasok = Beallitasok(this)
    }

    override fun onDestroy() {
        leallitFelismerot()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ felület

    override fun onCreateInputView(): View {
        val gyoker = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E2A3A"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        allapotSor = TextView(this).apply {
            setTextColor(Color.parseColor("#D0D8E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.START
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        gyoker.addView(allapotSor, LinearLayout.LayoutParams(-1, -2))

        mikrofonGomb = gomb("🎤", 28f, "#E8C46A", "#1E2A3A").apply {
            setOnClickListener { mikrofonNyomva() }
        }
        gyoker.addView(mikrofonGomb, LinearLayout.LayoutParams(-1, dp(84)).apply { bottomMargin = dp(8) })

        val sor = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val valtGomb = gomb("⌨", 22f).apply {
            setOnClickListener { valtasMasikBillentyuzetre() }
        }
        javitasGomb = gomb("", 13f).apply {
            setOnClickListener {
                beallitasok.javitasBe = !beallitasok.javitasBe
                frissitJavitasGomb()
            }
        }
        nyersGomb = gomb("Nyers", 13f).apply {
            setOnClickListener { nyersCsere() }
            isEnabled = false
        }
        val szokozGomb = gomb("szóköz", 14f).apply {
            setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        }
        val torlesGomb = gomb("⌫", 22f).apply { ismetlodoTorles(this) }
        val enterGomb = gomb("↵", 22f).apply { setOnClickListener { enter() } }

        sor.addView(valtGomb, sulyos(1f))
        sor.addView(javitasGomb, sulyos(1.4f))
        sor.addView(nyersGomb, sulyos(1.2f))
        sor.addView(szokozGomb, sulyos(1.8f))
        sor.addView(torlesGomb, sulyos(1f))
        sor.addView(enterGomb, sulyos(1f))
        gyoker.addView(sor, LinearLayout.LayoutParams(-1, dp(52)))

        frissitJavitasGomb()
        frissitFelulet()
        return gyoker
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) {
            utolsoBeszurt = null
            utolsoMasik = null
        }
        frissitJavitasGomb()
        frissitFelulet()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Ha elrejtik a billentyűzetet diktálás közben, eldobjuk a felvételt.
        if (allapot == Allapot.HALLGAT) {
            aktiv = false
            lezarva = true
            leallitFelismerot()
            gyujto.clear()
            allapot = Allapot.KESZ
        }
        super.onFinishInputView(finishingInput)
    }

    private fun frissitFelulet(uzenet: String? = null) {
        if (!::mikrofonGomb.isInitialized) return
        when (allapot) {
            Allapot.KESZ -> {
                mikrofonGomb.text = "🎤"
                hatter(mikrofonGomb, "#E8C46A")
                mikrofonGomb.isEnabled = true
                allapotSor.text = uzenet ?: alapUzenet()
            }
            Allapot.HALLGAT -> {
                mikrofonGomb.text = "⏹  Kész"
                hatter(mikrofonGomb, "#E07A5F")
                mikrofonGomb.isEnabled = true
                if (uzenet != null) allapotSor.text = uzenet
            }
            Allapot.JAVIT -> {
                mikrofonGomb.text = "✨ Javítás…"
                hatter(mikrofonGomb, "#7A8BA0")
                mikrofonGomb.isEnabled = false
                allapotSor.text = uzenet ?: gyujto.toString()
            }
        }
        nyersGomb.isEnabled = utolsoBeszurt != null && allapot == Allapot.KESZ
    }

    private fun alapUzenet(): String = when {
        !vanMikrofonEngedely() -> "Nincs mikrofon-engedély — nyisd meg a Voicetex Diktálás appot"
        beallitasok.javitasBe && !beallitasok.vanKulcs -> "Nincs API-kulcs — javítás nélkül írok (Beállítások)"
        else -> "Nyomd meg a mikrofont, és beszélj"
    }

    private fun frissitJavitasGomb() {
        if (!::javitasGomb.isInitialized) return
        val be = beallitasok.javitasBe
        javitasGomb.text = if (be) "Javítás: BE" else "Javítás: KI"
        hatter(javitasGomb, if (be) "#3D6B4F" else "#3A4656")
    }

    // ------------------------------------------------------------------ diktálás

    private fun mikrofonNyomva() {
        when (allapot) {
            Allapot.KESZ -> inditDiktalas()
            Allapot.HALLGAT -> {
                // Stop: a felismerő még leadja az utolsó darabot az onResults-ban.
                aktiv = false
                felismero?.stopListening()
                val ez = munkamenet
                handler.postDelayed({ if (ez == munkamenet && !lezarva) befejez() }, 3_000)
            }
            Allapot.JAVIT -> Unit
        }
    }

    private fun inditDiktalas() {
        if (!vanMikrofonEngedely()) {
            frissitFelulet("Nincs mikrofon-engedély — nyisd meg a Voicetex Diktálás appot")
            startActivity(Intent(this, BeallitasokActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            frissitFelulet("Nincs beszédfelismerő a telefonon")
            return
        }
        gyujto.clear()
        munkamenet++
        csendSzamlalo = 0
        aktiv = true
        lezarva = false
        allapot = Allapot.HALLGAT
        frissitFelulet("Indul…")
        hallgat()
    }

    private fun hallgat() {
        leallitFelismerot()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, beallitasok.nyelv)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Ne csillagozza ki a csúnya szavakat (Android 13+)
            putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        felismero = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(figyelo)
            startListening(intent)
        }
    }

    private fun leallitFelismerot() {
        felismero?.let {
            try { it.cancel() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        felismero = null
    }

    private val figyelo = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (allapot == Allapot.HALLGAT) frissitFelulet(if (gyujto.isEmpty()) "Beszélj…" else gyujto.toString())
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val resz = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            if (resz.isNotBlank()) frissitFelulet(osszefuz(gyujto.toString(), resz))
        }

        override fun onResults(results: Bundle?) {
            val szoveg = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!szoveg.isNullOrBlank()) {
                val uj = osszefuz(gyujto.toString(), szoveg)
                gyujto.setLength(0)
                gyujto.append(uj)
                csendSzamlalo = 0
            }
            if (aktiv) {
                frissitFelulet(gyujto.toString().ifEmpty { "Beszélj…" })
                handler.post { if (aktiv) hallgat() } // folytatjuk, amíg a felhasználó nem nyom stopot
            } else {
                befejez()
            }
        }

        override fun onError(error: Int) {
            if (!aktiv) { befejez(); return }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    csendSzamlalo++
                    if (csendSzamlalo >= 3) {
                        // Hosszú csend: magától lezárjuk
                        aktiv = false
                        befejez()
                    } else handler.post { if (aktiv) hallgat() }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> handler.postDelayed({ if (aktiv) hallgat() }, 400)
                else -> {
                    aktiv = false
                    val ok = when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hálózati hiba a felismerőben"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Nincs mikrofon-engedély"
                        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon hiba"
                        else -> "Felismerési hiba ($error)"
                    }
                    if (gyujto.isNotBlank()) befejez() else {
                        lezarva = true
                        allapot = Allapot.KESZ
                        leallitFelismerot()
                        frissitFelulet(ok)
                    }
                }
            }
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** A diktálás vége: javítás, majd beszúrás. */
    private fun befejez() {
        if (lezarva) return
        lezarva = true
        aktiv = false
        leallitFelismerot()

        val nyers = gyujto.toString().trim()
        if (nyers.isEmpty()) {
            allapot = Allapot.KESZ
            frissitFelulet("Nem hallottam semmit")
            return
        }
        if (!beallitasok.javitasBe || !beallitasok.vanKulcs) {
            beszur(nyers, nyers)
            allapot = Allapot.KESZ
            frissitFelulet(if (beallitasok.javitasBe) "Nincs API-kulcs — javítás nélkül" else "Beírva (javítás kikapcsolva)")
            return
        }

        allapot = Allapot.JAVIT
        frissitFelulet(nyers)
        val elozo = currentInputConnection?.getTextBeforeCursor(300, 0)?.toString() ?: ""
        val kulcs = beallitasok.apiKulcs
        val modell = beallitasok.modell
        val szabalyok = beallitasok.sajatSzabalyok
        val nyelvKod = beallitasok.nyelv
        scope.launch {
            val eredmeny = try {
                Result.success(withContext(Dispatchers.IO) { ClaudeJavito.javit(kulcs, modell, nyers, elozo, szabalyok, nyelvKod) })
            } catch (e: Exception) {
                Result.failure(e)
            }
            eredmeny.onSuccess { javitott ->
                beszur(javitott, nyers)
                allapot = Allapot.KESZ
                frissitFelulet("✓ Javítva  (a „Nyers” gomb visszaállítja)")
            }.onFailure { e ->
                beszur(nyers, nyers)
                allapot = Allapot.KESZ
                frissitFelulet("Javítás sikertelen: ${e.message ?: "ismeretlen hiba"} — nyers szöveg került be")
            }
        }
    }

    /** Beszúrás a kurzorhoz, szükség esetén szóközzel elválasztva. */
    private fun beszur(szoveg: String, nyers: String) {
        val ic = currentInputConnection ?: return
        val elotte = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val kellSzokoz = elotte.isNotEmpty() && !elotte.last().isWhitespace() &&
            szoveg.firstOrNull()?.let { it !in ".,!?;:)…" } == true
        val elo = if (kellSzokoz) " " else ""
        ic.commitText(elo + szoveg, 1)
        if (szoveg != nyers) {
            utolsoBeszurt = elo + szoveg
            utolsoMasik = elo + nyers
        } else {
            utolsoBeszurt = null
            utolsoMasik = null
        }
    }

    /** Az utolsó javított beszúrást lecseréli a nyers változatra (és vissza). */
    private fun nyersCsere() {
        val ic = currentInputConnection ?: return
        val most = utolsoBeszurt ?: return
        val masik = utolsoMasik ?: return
        val elotte = ic.getTextBeforeCursor(most.length, 0)?.toString()
        if (elotte != most) {
            utolsoBeszurt = null
            utolsoMasik = null
            frissitFelulet("A szöveg azóta megváltozott, nem cserélem")
            return
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(most.length, 0)
        ic.commitText(masik, 1)
        ic.endBatchEdit()
        utolsoBeszurt = masik
        utolsoMasik = most
        frissitFelulet("Csere kész (újra megnyomva visszacseréli)")
    }

    // ------------------------------------------------------------------ gombok

    private fun enter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val akcio = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val nincsAkcio = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val tobbsoros = ((info?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!tobbsoros && !nincsAkcio && akcio != EditorInfo.IME_ACTION_NONE && akcio != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(akcio)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun torolEgyet() {
        val ic = currentInputConnection ?: return
        val kijelolt = ic.getSelectedText(0)
        if (!kijelolt.isNullOrEmpty()) ic.commitText("", 1)
        else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
    }

    /** Nyomva tartva ismétlődik, mint egy rendes billentyűzeten. */
    private fun ismetlodoTorles(g: Button) {
        val ismetlo = object : Runnable {
            override fun run() {
                torolEgyet()
                handler.postDelayed(this, 60)
            }
        }
        g.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    torolEgyet()
                    handler.postDelayed(ismetlo, 450)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(ismetlo)
                    if (e.action == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    private fun valtasMasikBillentyuzetre() {
        if (allapot == Allapot.HALLGAT) return
        // Az előzőleg használt (megszokott) billentyűzetre vált vissza
        if (!switchToPreviousInputMethod()) switchToNextInputMethod(false)
    }

    // ------------------------------------------------------------------ segédek

    private fun vanMikrofonEngedely() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun osszefuz(a: String, b: String): String =
        if (a.isBlank()) b.trim() else a.trimEnd() + " " + b.trim()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun sulyos(suly: Float) = LinearLayout.LayoutParams(0, -1, suly).apply {
        marginStart = dp(3); marginEnd = dp(3)
    }

    private fun gomb(szoveg: String, meret: Float, hatterSzin: String = "#3A4656", betuSzin: String = "#FFFFFF") =
        Button(this).apply {
            text = szoveg
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, meret)
            setTextColor(Color.parseColor(betuSzin))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            stateListAnimator = null
            hatter(this, hatterSzin)
        }

    private fun hatter(v: View, szin: String) {
        v.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.parseColor(szin))
        }
    }
}
