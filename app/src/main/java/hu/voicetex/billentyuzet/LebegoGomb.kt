package hu.voicetex.billentyuzet

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lebegő mikrofon gomb a megszokott billentyűzet fölött.
 *
 * Kisegítő szolgáltatás: figyeli, mikor jelenik meg a billentyűzet, és fölé
 * tesz egy kerek gombot. Megnyomva diktálni lehet; a (Claude-dal javított)
 * szöveg a fókuszban lévő szövegmezőbe, a kurzor helyére kerül.
 */
class LebegoGomb : AccessibilityService() {

    private enum class Allapot { KESZ, HALLGAT, JAVIT }

    private lateinit var beallitasok: Beallitasok
    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var panel: LinearLayout? = null
    private lateinit var gomb: TextView
    private lateinit var buborek: TextView
    private var lathato = false
    private var utolsoY = -1

    private var felismero: SpeechRecognizer? = null
    private var allapot = Allapot.KESZ
    private var aktiv = false
    private var lezarva = true
    private var munkamenet = 0
    private var csendSzamlalo = 0
    private val gyujto = StringBuilder()

    // ------------------------------------------------------------------ életciklus

    override fun onServiceConnected() {
        super.onServiceConnected()
        beallitasok = Beallitasok(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        epitPanelt()
    }

    override fun onDestroy() {
        leallitFelismerot()
        elrejt()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Sok esemény jön egymás után — kicsit várunk, és egyszer frissítünk.
        handler.removeCallbacks(frissito)
        handler.postDelayed(frissito, 120)
    }

    private val frissito = Runnable { frissitLathatosag() }

    /** Megnézi, látszik-e billentyűzet, és ahhoz igazítja a gombot. */
    private fun frissitLathatosag() {
        if (allapot != Allapot.KESZ) return // diktálás közben ne tűnjön el
        val ime = try {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: Exception) { null }

        if (ime == null || sajatBillentyuzet(ime) || jelszoMezo()) {
            elrejt()
            return
        }
        val hatar = Rect()
        ime.getBoundsInScreen(hatar)
        if (hatar.height() < dp(80)) { elrejt(); return }
        mutat(hatar.top)
    }

    private fun sajatBillentyuzet(ime: AccessibilityWindowInfo): Boolean =
        try { ime.root?.packageName == packageName } catch (e: Exception) { false }

    private fun jelszoMezo(): Boolean =
        try { fokuszaltMezo()?.isPassword == true } catch (e: Exception) { false }

    private fun fokuszaltMezo(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    // ------------------------------------------------------------------ lebegő panel

    private fun epitPanelt() {
        buborek = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            maxWidth = dp(240)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#E61E2A3A"))
            }
            visibility = View.GONE
        }
        gomb = TextView(this).apply {
            text = "🎤"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setOnClickListener { gombNyomva() }
            setOnLongClickListener {
                // Hosszú nyomás: a beállítások megnyitása
                startActivity(Intent(this@LebegoGomb, BeallitasokActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }
        hatter("#E8C46A")
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(buborek, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            addView(gomb, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
    }

    private fun parameterek(y: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(12)
        this.y = y
    }

    private fun mutat(billentyuzetTeteje: Int) {
        val p = panel ?: return
        val y = (billentyuzetTeteje - dp(52) - dp(10)).coerceAtLeast(0)
        try {
            if (!lathato) {
                wm.addView(p, parameterek(y))
                lathato = true
            } else if (y != utolsoY) {
                wm.updateViewLayout(p, parameterek(y))
            }
            utolsoY = y
        } catch (e: Exception) {
            lathato = false
        }
    }

    private fun elrejt() {
        val p = panel ?: return
        if (lathato) {
            try { wm.removeView(p) } catch (_: Exception) {}
            lathato = false
            utolsoY = -1
        }
    }

    private fun frissitGombot(uzenet: String? = null) {
        when (allapot) {
            Allapot.KESZ -> { gomb.text = "🎤"; hatter("#E8C46A") }
            Allapot.HALLGAT -> { gomb.text = "⏹"; hatter("#E07A5F") }
            Allapot.JAVIT -> { gomb.text = "✨"; hatter("#7A8BA0") }
        }
        if (uzenet.isNullOrBlank()) {
            buborek.visibility = View.GONE
        } else {
            buborek.text = uzenet
            buborek.visibility = View.VISIBLE
        }
    }

    /** Rövid ideig kiírt üzenet, utána eltűnik. */
    private fun villant(uzenet: String) {
        frissitGombot(uzenet)
        val ez = munkamenet
        handler.postDelayed({
            if (ez == munkamenet && allapot == Allapot.KESZ) {
                buborek.visibility = View.GONE
                frissitLathatosag()
            }
        }, 3_500)
    }

    private fun hatter(szin: String) {
        gomb.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(szin))
            setStroke(dp(2), Color.parseColor("#1E2A3A"))
        }
    }

    // ------------------------------------------------------------------ diktálás

    private fun gombNyomva() {
        when (allapot) {
            Allapot.KESZ -> indit()
            Allapot.HALLGAT -> {
                aktiv = false
                felismero?.stopListening()
                val ez = munkamenet
                handler.postDelayed({ if (ez == munkamenet && !lezarva) befejez() }, 3_000)
            }
            Allapot.JAVIT -> Unit
        }
    }

    private fun indit() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            villant("Nincs mikrofon-engedély — nyisd meg a Voicetex appot")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            villant("Nincs beszédfelismerő a telefonon")
            return
        }
        gyujto.clear()
        munkamenet++
        csendSzamlalo = 0
        aktiv = true
        lezarva = false
        allapot = Allapot.HALLGAT
        frissitGombot("Beszélj…")
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
        override fun onPartialResults(partialResults: Bundle?) {
            val resz = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            if (resz.isNotBlank()) frissitGombot(osszefuz(gyujto.toString(), resz))
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
                frissitGombot(gyujto.toString().ifEmpty { "Beszélj…" })
                handler.post { if (aktiv) hallgat() }
            } else befejez()
        }

        override fun onError(error: Int) {
            if (!aktiv) { befejez(); return }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    csendSzamlalo++
                    if (csendSzamlalo >= 3) { aktiv = false; befejez() }
                    else handler.post { if (aktiv) hallgat() }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> handler.postDelayed({ if (aktiv) hallgat() }, 400)
                else -> {
                    aktiv = false
                    if (gyujto.isNotBlank()) befejez() else {
                        lezarva = true
                        allapot = Allapot.KESZ
                        leallitFelismerot()
                        villant(when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Nincs mikrofon-engedély"
                            SpeechRecognizer.ERROR_AUDIO -> "A mikrofon most nem érhető el"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hálózati hiba a felismerőben"
                            else -> "Felismerési hiba ($error)"
                        })
                    }
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun befejez() {
        if (lezarva) return
        lezarva = true
        aktiv = false
        leallitFelismerot()

        val nyers = gyujto.toString().trim()
        if (nyers.isEmpty()) {
            allapot = Allapot.KESZ
            villant("Nem hallottam semmit")
            return
        }
        if (!beallitasok.javitasBe || !beallitasok.vanKulcs) {
            allapot = Allapot.KESZ
            villant(if (beszur(nyers)) "Beírva" else "Nem találom a szövegmezőt")
            return
        }

        allapot = Allapot.JAVIT
        frissitGombot("Javítás…")
        val elozo = elotteLevoSzoveg()
        val kulcs = beallitasok.apiKulcs
        val modell = beallitasok.modell
        val szabalyok = beallitasok.sajatSzabalyok
        val nyelvKod = beallitasok.nyelv
        scope.launch {
            val javitott = try {
                withContext(Dispatchers.IO) { ClaudeJavito.javit(kulcs, modell, nyers, elozo, szabalyok, nyelvKod) }
            } catch (e: Exception) {
                allapot = Allapot.KESZ
                val ok = if (beszur(nyers)) "nyers szöveg került be" else "a szöveg a vágólapon van"
                villant("Javítás sikertelen: ${e.message} — $ok")
                return@launch
            }
            allapot = Allapot.KESZ
            villant(if (beszur(javitott)) "✓ Javítva" else "Nem találom a mezőt — a szöveg a vágólapon van")
        }
    }

    // ------------------------------------------------------------------ beszúrás

    /** A kurzor előtti szöveg (környezet a javításhoz). */
    private fun elotteLevoSzoveg(): String {
        val mezo = fokuszaltMezo() ?: return ""
        val szoveg = mezoSzovege(mezo)
        val vege = mezo.textSelectionStart.takeIf { it in 0..szoveg.length } ?: szoveg.length
        return szoveg.substring((vege - 300).coerceAtLeast(0), vege)
    }

    private fun mezoSzovege(mezo: AccessibilityNodeInfo): String =
        if (mezo.isShowingHintText) "" else mezo.text?.toString() ?: ""

    /**
     * Beszúrás a fókuszban lévő mezőbe, a kurzor helyére.
     * Ha a mező nem engedi a közvetlen írást, vágólapon át illeszt be.
     * Igazzal tér vissza, ha sikerült.
     */
    private fun beszur(beszurando: String): Boolean {
        val mezo = fokuszaltMezo()
        if (mezo == null || !mezo.isEditable) {
            vagolapra(beszurando)
            return false
        }
        val szoveg = mezoSzovege(mezo)
        var eleje = mezo.textSelectionStart
        var vege = mezo.textSelectionEnd
        if (eleje !in 0..szoveg.length) eleje = szoveg.length
        if (vege !in eleje..szoveg.length) vege = eleje

        val elotte = szoveg.substring(0, eleje)
        val kellSzokoz = elotte.isNotEmpty() && !elotte.last().isWhitespace() &&
            beszurando.firstOrNull()?.let { it !in ".,!?;:)…" } == true
        val betet = (if (kellSzokoz) " " else "") + beszurando
        val uj = elotte + betet + szoveg.substring(vege)

        val ok = mezo.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, uj)
            }
        )
        if (ok) {
            val kurzor = elotte.length + betet.length
            mezo.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, kurzor)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, kurzor)
                }
            )
            return true
        }
        // Tartalék: vágólap + beillesztés
        vagolapra(betet)
        return mezo.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun vagolapra(s: String) {
        try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Voicetex", s))
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ segédek

    private fun osszefuz(a: String, b: String): String =
        if (a.isBlank()) b.trim() else a.trimEnd() + " " + b.trim()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
