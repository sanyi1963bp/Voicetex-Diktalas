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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.hypot
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
        menuBezar()
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

    private var gombMeret = 52                 // dp
    private var imeTeteje = -1                 // a billentyűzet teteje (képernyő-koordináta)
    private var menu: View? = null

    // Érintéskezelés: koppintás = diktálás, 1 mp = húzás, 2 mp (húzás nélkül) = menü
    private var huzasMod = false
    private var elmozdult = false
    private var kezdoX = 0f
    private var kezdoY = 0f

    private fun meretDp() = when (beallitasok.lebegoMeret) { 0 -> 42; 2 -> 64; else -> 52 }

    private fun epitPanelt() {
        buborek = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            maxWidth = dp(220)
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
            setOnTouchListener(erintesFigyelo)
        }
        hatter("#E8C46A")
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(buborek, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
            addView(gomb, LinearLayout.LayoutParams(dp(52), dp(52)))
        }
        alkalmazMeret()
    }

    private fun alkalmazMeret() {
        gombMeret = meretDp()
        gomb.setTextSize(TypedValue.COMPLEX_UNIT_SP, gombMeret * 0.42f)
        gomb.layoutParams = (gomb.layoutParams as LinearLayout.LayoutParams).apply {
            width = dp(gombMeret); height = dp(gombMeret)
        }
    }

    private fun kepernyo(): Rect = wm.currentWindowMetrics.bounds

    /** A gomb középpontja: a mentett hely, vagy alaphelyzetben a billentyűzet fölött jobbra. */
    private fun kozeppont(): Pair<Int, Int> {
        val k = kepernyo()
        val fel = dp(gombMeret) / 2
        val x: Int
        var y: Int
        if (beallitasok.lebegoX >= 0f) {
            x = (beallitasok.lebegoX * k.width()).toInt()
            y = (beallitasok.lebegoY * k.height()).toInt()
        } else {
            x = k.width() - dp(12) - fel
            y = (if (imeTeteje > 0) imeTeteje else k.height() * 2 / 3) - dp(10) - fel
        }
        // A billentyűzetre ne kerüljön rá
        if (imeTeteje > 0) y = y.coerceAtMost(imeTeteje - dp(6) - fel)
        return Pair(x.coerceIn(fel, k.width() - fel), y.coerceIn(fel, k.height() - fel))
    }

    /** Ablakparaméterek a középpont alapján; a buborék a gomb fölé, a képernyő belseje felé nyílik. */
    private fun parameterek(cx: Int, cy: Int): WindowManager.LayoutParams {
        val k = kepernyo()
        val fel = dp(gombMeret) / 2
        val bal = cx < k.width() / 2
        panel?.gravity = if (bal) Gravity.START else Gravity.END
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or (if (bal) Gravity.START else Gravity.END)
            x = if (bal) cx - fel else k.width() - cx - fel
            y = k.height() - cy - fel
        }
    }

    private fun mutat(billentyuzetTeteje: Int) {
        imeTeteje = billentyuzetTeteje
        frissitHelyzet()
    }

    private fun frissitHelyzet() {
        val p = panel ?: return
        if (huzasMod) return
        val (cx, cy) = kozeppont()
        try {
            if (!lathato) {
                wm.addView(p, parameterek(cx, cy))
                lathato = true
            } else {
                wm.updateViewLayout(p, parameterek(cx, cy))
            }
        } catch (e: Exception) {
            lathato = false
        }
    }

    private fun elrejt() {
        val p = panel ?: return
        if (lathato) {
            try { wm.removeView(p) } catch (_: Exception) {}
            lathato = false
        }
    }

    private val huzasIndito = Runnable {
        huzasMod = true
        gomb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        gomb.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).start()
        frissitGombot("Húzd bárhová – tartsd tovább a menühöz")
    }

    private val menuIndito = Runnable {
        if (huzasMod && !elmozdult) {
            gomb.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            huzasVege()
            menuMutat()
        }
    }

    private fun huzasVege() {
        huzasMod = false
        gomb.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        frissitGombot(null)
    }

    private val erintesFigyelo = View.OnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                kezdoX = e.rawX; kezdoY = e.rawY
                huzasMod = false; elmozdult = false
                if (allapot == Allapot.KESZ) {
                    handler.postDelayed(huzasIndito, 1_000)
                    handler.postDelayed(menuIndito, 2_000)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val tav = hypot(e.rawX - kezdoX, e.rawY - kezdoY)
                if (huzasMod) {
                    if (tav > dp(10)) {
                        elmozdult = true
                        handler.removeCallbacks(menuIndito)
                    }
                    if (elmozdult) {
                        try { wm.updateViewLayout(panel, parameterek(e.rawX.toInt(), e.rawY.toInt())) } catch (_: Exception) {}
                    }
                } else if (tav > dp(12)) {
                    handler.removeCallbacks(huzasIndito)
                    handler.removeCallbacks(menuIndito)
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(huzasIndito)
                handler.removeCallbacks(menuIndito)
                if (huzasMod) {
                    if (elmozdult) {
                        val k = kepernyo()
                        beallitasok.lebegoX = (e.rawX / k.width()).coerceIn(0f, 1f)
                        beallitasok.lebegoY = (e.rawY / k.height()).coerceIn(0f, 1f)
                    }
                    huzasVege()
                    frissitHelyzet()
                } else if (menu == null && hypot(e.rawX - kezdoX, e.rawY - kezdoY) < dp(12)) {
                    gombNyomva()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(huzasIndito)
                handler.removeCallbacks(menuIndito)
                if (huzasMod) { huzasVege(); frissitHelyzet() }
            }
        }
        true
    }

    // ------------------------------------------------------------------ gyorsmenü

    private fun javitasFelirat() = "✨  Javítás: " + if (beallitasok.javitasBe) "BE" else "KI"
    private fun kuldesFelirat() = "📤  Küldés a 2. érintésre: " + if (beallitasok.autoKuldes) "BE" else "KI"
    private fun nyelvFelirat() = "🌍  Nyelv: " + Nyelvek.keres(beallitasok.nyelv).nev
    private fun meretFelirat() = "📏  Méret: " + when (beallitasok.lebegoMeret) { 0 -> "kicsi"; 2 -> "nagy"; else -> "közepes" }

    private fun menuMutat() {
        if (menu != null) return
        val lap = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            setOnClickListener { menuBezar() }
        }
        val kartya = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#1E2A3A"))
            }
        }
        menuTartalom(kartya)
        lap.addView(kartya, FrameLayout.LayoutParams(dp(310), -2, Gravity.CENTER))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(lap, p)
            menu = lap
        } catch (_: Exception) {}
    }

    private fun menuBezar() {
        menu?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        menu = null
    }

    private fun menuSor(kartya: LinearLayout, szoveg: String, szin: String = "#FFFFFF", katt: (TextView) -> Unit) {
        kartya.addView(TextView(this).apply {
            text = szoveg
            setTextColor(Color.parseColor(szin))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setOnClickListener { katt(this) }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun menuCim(kartya: LinearLayout, szoveg: String) {
        kartya.addView(TextView(this).apply {
            text = szoveg
            setTextColor(Color.parseColor("#E8C46A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(6), dp(16), dp(8))
        })
    }

    private fun menuTartalom(kartya: LinearLayout) {
        kartya.removeAllViews()
        menuCim(kartya, "Voicetex lebegő mikrofon")
        menuSor(kartya, javitasFelirat()) {
            beallitasok.javitasBe = !beallitasok.javitasBe
            it.text = javitasFelirat()
        }
        menuSor(kartya, kuldesFelirat()) {
            beallitasok.autoKuldes = !beallitasok.autoKuldes
            it.text = kuldesFelirat()
        }
        menuSor(kartya, nyelvFelirat()) { nyelvLista(kartya) }
        menuSor(kartya, meretFelirat()) {
            beallitasok.lebegoMeret = (beallitasok.lebegoMeret + 1) % 3
            it.text = meretFelirat()
            alkalmazMeret()
            frissitHelyzet()
        }
        menuSor(kartya, "↺  Gomb vissza a billentyűzet fölé") {
            beallitasok.lebegoX = -1f
            beallitasok.lebegoY = -1f
            menuBezar()
            frissitHelyzet()
        }
        menuSor(kartya, "⚙  Összes beállítás") {
            menuBezar()
            startActivity(Intent(this, BeallitasokActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        menuSor(kartya, "✕  Bezárás", szin = "#9AA8B8") { menuBezar() }
    }

    private fun nyelvLista(kartya: LinearLayout) {
        kartya.removeAllViews()
        menuCim(kartya, "Diktálási nyelv")
        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val aktualis = beallitasok.nyelv
        for (ny in Nyelvek.lista) {
            menuSor(lista, (if (ny.kod == aktualis) "●  " else "○  ") + ny.nev,
                szin = if (ny.kod == aktualis) "#E8C46A" else "#FFFFFF") {
                beallitasok.nyelv = ny.kod
                menuTartalom(kartya)
            }
        }
        kartya.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(-1, dp(360)))
        menuSor(kartya, "←  Vissza", szin = "#9AA8B8") { menuTartalom(kartya) }
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
            if (ez == munkamenet && allapot == Allapot.KESZ && !huzasMod) {
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
            if (beszur(nyers)) beirvaEsKuldes("Beírva") else villant("Nem találom a szövegmezőt")
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
            if (beszur(javitott)) beirvaEsKuldes("✓ Javítva")
            else villant("Nem találom a mezőt — a szöveg a vágólapon van")
        }
    }

    /** Beírás után, ha be van kapcsolva, a küldés gombot is megnyomja. */
    private fun beirvaEsKuldes(uzenet: String) {
        if (!beallitasok.autoKuldes) {
            villant(uzenet)
            return
        }
        frissitGombot("$uzenet – küldés…")
        // Kis szünet: sok appban a küldés gomb csak akkor jelenik meg, ha már van szöveg
        handler.postDelayed({
            val ok = try { kuld() } catch (e: Exception) { false }
            villant(if (ok) "$uzenet és elküldve" else "$uzenet – küldés gombot nem találtam")
        }, 450)
    }

    private val KULDES_SZAVAK = Regex(
        "(?i)^(küldés|elküld|küld|üzenet küldése|send|send message|senden|envoyer|enviar|invia|skicka|send melding|wyślij|odeslat|odoslať|trimite|pošalji|lähetä|send besked|отправить|надіслати)"
    )

    /**
     * Elküldi a beírt szöveget: először a mező közelében lévő "Küldés" gombot
     * keresi meg és nyomja meg, ha nincs ilyen, a billentyűzet Enter/Küldés műveletét kéri.
     */
    private fun kuld(): Boolean {
        val mezo = fokuszaltMezo()
        val mezoHatar = Rect()
        mezo?.getBoundsInScreen(mezoHatar)
        rootInActiveWindow?.let { gyoker ->
            val jelolt = keresKuldoGomb(gyoker, mezoHatar)
            var n: AccessibilityNodeInfo? = jelolt
            while (n != null && !n.isClickable) n = n.parent
            if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return mezo?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) == true
    }

    private fun keresKuldoGomb(gyoker: AccessibilityNodeInfo, mezoHatar: Rect): AccessibilityNodeInfo? {
        val sor = ArrayDeque<AccessibilityNodeInfo>()
        sor.add(gyoker)
        var szamlalo = 0
        val savFelett = dp(140)
        while (sor.isNotEmpty() && szamlalo < 800) {
            val n = sor.removeFirst()
            szamlalo++
            if (!n.isEditable && n.isVisibleToUser) {
                val leiras = n.contentDescription?.toString()?.trim()
                val gombSzoveg = if ((n.className?.toString() ?: "").contains("Button")) n.text?.toString()?.trim() else null
                val cimke = leiras ?: gombSzoveg
                if (cimke != null && cimke.length <= 25 && KULDES_SZAVAK.containsMatchIn(cimke)) {
                    val r = Rect()
                    n.getBoundsInScreen(r)
                    if (mezoHatar.isEmpty || r.centerY() in (mezoHatar.top - savFelett)..(mezoHatar.bottom + savFelett)) return n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { sor.add(it) }
        }
        return null
    }

    // ------------------------------------------------------------------ beszúrás

    /** A kurzor előtti szöveg (környezet a javításhoz). */
    private fun elotteLevoSzoveg(): String {
        val mezo = fokuszaltMezo() ?: return ""
        val szoveg = mezoSzovege(mezo)
        val vege = mezo.textSelectionStart.takeIf { it in 0..szoveg.length } ?: szoveg.length
        return szoveg.substring((vege - 300).coerceAtLeast(0), vege)
    }

    /** A mező valódi szövege; a halvány tippszöveget ("Írj hozzászólást…") üresnek veszi. */
    private fun mezoSzovege(mezo: AccessibilityNodeInfo): String {
        if (mezo.isShowingHintText) return ""
        val szoveg = mezo.text?.toString() ?: return ""
        val tipp = mezo.hintText?.toString()
        return if (tipp != null && szoveg == tipp) "" else szoveg
    }

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
        // 1. Beillesztés a kurzor helyére – a mező többi része (megemlítés, név,
        //    formázás) érintetlen marad. Ez a megbízható út a Facebookhoz és társaihoz.
        vagolapra(betet)
        if (mezo.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true

        // 2. Tartalék: a teljes szöveg visszaírása – csak egyszerű mezőknél jó
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
        return false
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
