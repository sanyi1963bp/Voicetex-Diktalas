# Voicetex Diktálás – Android

Diktálj bármelyik alkalmazásba a telefonodon. A telefon beépített
beszédfelismerője leírja, amit mondasz, a **Claude Haiku** pedig kijavítja:
kiteszi az írásjeleket és a kérdőjeleket, rendbe teszi az ékezeteket, és
kijavítja a félrehallott szavakat. A javított szöveg oda kerül, ahol a kurzor áll.

> 💻 **Számítógépes változat (Windows):** [Voicetex v3 – Duma2](https://github.com/sanyi1963bp/Duma2)

## Fő funkciók

- **Diktáló billentyűzet** – nagy mikrofon gomb. Hosszan is beszélhetsz,
  szünetekkel, a végén „Kész”.
- **Lebegő mikrofon** – kis sárga gomb a megszokott billentyűzeted fölött.
  Egy koppintás: diktálás, még egy: kész, javítás, beírás és **automatikus
  küldés** (kikapcsolható). 1 mp nyomva tartás: bárhová elhúzható.
  2 mp nyomva tartás: gyorsmenü (javítás, küldés, nyelv, méret, alaphelyzet).
- **AI-javítás** – Claude Haiku, a saját API-kulcsoddal. Ha nincs internet,
  a javítatlan szöveg kerül be.
- **„Nyers” gomb** – egy gombnyomással visszaállítja az eredeti, javítatlan szöveget.
- **22 nyelv** – magyar, angol, német, svéd és még sok más.
- **Saját szabályok** – például „Ha azt mondom »smiley«, tegyél 🙂-t.”
- **Nem cenzúráz** – a csúnya szavakat sem csillagozza ki.

## Mennyibe kerül?

Az app ingyenes. Az AI-javításhoz saját Claude API-kulcs kell (Claude-előfizetés
nem). Egy diktálás javítása nagyjából **0,0002 dollár** (kb. 0,1 Ft), így
napi 30 diktálásnál havonta kb. 0,20 dollár. A legkisebb feltöltés 5 dollár,
ami átlagos használatnál nagyon sokáig elég.

### Kulcs megszerzése

1. Regisztrálj a [platform.claude.com](https://platform.claude.com) oldalon.
2. Settings → **Billing** → **Buy credits** (legalább 5 dollár).
3. Settings → **API keys** → **Create key** (ha kérdezi: *Continue with an API key*).
4. Másold ki a kulcsot (csak egyszer látod!), és illeszd be az appban.

A kulcs csak a telefonodon, az app zárt tárhelyén van.

## Letöltés

| Változat | Mit tud | Telepítés |
| --- | --- | --- |
| **[Lite – letöltés](https://github.com/sanyi1963bp/Voicetex-Diktalas/raw/main/apk/voicetex-diktalo-lite.apk)** | diktáló billentyűzet, AI-javítás, 22 nyelv | telefonon a linkre koppintva |
| **[Teljes – letöltés](https://github.com/sanyi1963bp/Voicetex-Diktalas/raw/main/apk/voicetex-diktalo.apk)** | mint a Lite + lebegő mikrofon gomb | USB-kábellel a gépről (lásd lent) |

**Lite telepítése:** nyisd meg a linket a telefonon, töltsd le, majd koppints a
letöltött fájlra. A telefon megkérdezi, engedélyezed-e a telepítést ebből a
forrásból (Chrome vagy Fájlok) – engedélyezd, és telepítsd. A Play Protect
figyelmeztethet, hogy ismeretlen fejlesztőtől származik; ilyenkor válaszd a
„Telepítés mindenképp” lehetőséget.

A két változat két külön alkalmazás, egy telefonon akár mindkettő lehet.

## Fordítás és a teljes változat telepítése

A teljes változat a lebegő gomb miatt csak USB-n telepíthető (lásd lent),
ehhez a mellékelt APK-t vagy saját fordítást használhatsz (Android Studio
vagy a mellékelt parancsfájlok, Windowson).

1. Telepítsd az [Android Studiót](https://developer.android.com/studio)
   (ez hozza az Android SDK-t és a Javát).
2. Töltsd le ezt a repót, és nyisd meg Android Studióban egyszer, hogy
   létrejöjjön a `local.properties` (az SDK helyével).
3. Futtasd a **`forditas.bat`**-ot. Mindkét APK elkészül, az `apk` mappába és a repó melletti mappába is.
4. A telefonon kapcsold be a fejlesztői módot és az **USB-hibakeresést**,
   csatlakoztasd kábellel, majd futtasd a **`telepites_usb.bat`**-ot.

### Kábel nélkül: Wi-Fi

A **`telepites_wifi.bat`** ugyanezt Wi-Fi-n végzi: a telefonon a Fejlesztői
beállításokban kapcsold be a **Vezeték nélküli hibakeresést**, első
alkalommal párosítsd a gépet (*Eszköz párosítása párosítókóddal*), utána
elég a telefon képernyőjén látható „IP-cím és port”-ot beírni. A port
általában minden alkalommal változik.

### Miért USB-n vagy Wi-Fi-n?

A lebegő mikrofon kisegítő szolgáltatást (Accessibility) használ. A Google
Play Protect a máshonnan telepített ilyen appokat letiltja, az USB-n
telepítetteket nem. Telepítés után a lebegő gomb bekapcsolásához a telefon
„korlátozott beállítást” jelez: ezt a **`engedely_usb.bat`** oldja fel
(vagy: Beállítások → Alkalmazások → Voicetex Diktálás → ⋮ → *Korlátozott
beállítások engedélyezése*).

## Első beállítás

Nyisd meg a **Voicetex Diktálás** appot, és menj végig a számozott pontokon:
mikrofon-engedély, billentyűzet bekapcsolása, API-kulcs, nyelv, kipróbálás,
és ha kéred, a lebegő mikrofon bekapcsolása.

## Adatvédelem

A hangot a telefon saját beszédfelismerője dolgozza fel. A leírt szöveg a
javításhoz a Claude API-hoz kerül (Anthropic), majd a javított szöveg
visszajön. Az app semmit nem tárol és nem küld máshová. A lebegő mikrofon
csak azt figyeli, látszik-e billentyűzet, és csak a fókuszban lévő mezőbe ír;
jelszómezőknél nem jelenik meg.

## Licenc

MIT – lásd a [LICENSE](LICENSE) fájlt.
