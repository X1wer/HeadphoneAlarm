# FülesÉbresztő

Natív Android-ébresztőalkalmazás, amelynek hangja **kizárólag csatlakoztatott és engedélyezett fejhallgatón** szólal meg. Ha nincs megfelelő fejhallgató, vagy a hangkimenet nem igazolható, a telefon **csak rezeg** – a beépített hangszórón soha nem indít ébresztőhangot.

> **Build típusa:** a GitHub Actions és a helyi fordítás **debug APK-t** készít (debug kulccsal aláírva). Release-aláírókulcs nincs a projektben, ezért release APK nem készül.

---

## 1. Az alkalmazás célja és fő funkciói

- Korlátlan számú ébresztés: név, óra/perc, ismétlődő napok vagy egyszeri dátum, be/ki kapcsoló, saját hang, hangerő (0–100%, 5%-os lépésekben), rezgés fejhallgató mellett, szundi időtartama, maximális csörgési idő.
- Első indításkor egy **kikapcsolt** minta-ébresztés: 06:30, hétfő–péntek, 60%, 10 perc szundi, 10 perc maximális idő, rezgés fejhallgató mellett kikapcsolva.
- **Fejhallgatós hangkimenet:** vezetékes (jack), vezetékes headset, USB-C fejhallgató automatikusan; Bluetooth A2DP, Bluetooth LE Audio, hallókészülék és egyéb USB-audioeszköz csak a felhasználó kifejezett engedélyezése után.
- **Fail-closed hangkezelés:** a lejátszó némán indul, és csak akkor lesz hallható, ha a tényleges kimenet igazoltan engedélyezett fejhallgató. Kétség esetén rezgés.
- **Lecsatlakozás kezelése:** azonnali némítás, rezgésre váltás, kijelzés a képernyőn, újracsatlakozáskor újraellenőrzés.
- **Teljes képernyős ébresztés** lezárt képernyőn is; felfelé húzás = leállítás, lefelé húzás = szundi; külön gombok és képernyőolvasó-műveletek; értesítési műveletek.
- **Beépített hangok** (4 db, saját szintetizálású, jogtiszta) és **saját hangfájlok** (MP3, WAV, OGG, M4A … a rendszer fájlválasztójával, tartós olvasási joggal).
- Pontos időzítés `AlarmManager.setAlarmClock()`-kal; helyreállítás újraindítás (a feloldás előtt is), frissítés, kézi időállítás és időzóna-váltás után.
- Sötét (alapértelmezett), világos és rendszertéma; 24 órás formátum kapcsolható; nagy betűméretre igazodó felület.
- Teljesen offline: nincs `INTERNET` jogosultság, reklám, analitika vagy fiók.

## 2. Támogatott Android-verziók

| | |
|---|---|
| Minimum | Android 10 (API 29) |
| Cél (targetSdk) | Android 16 (API 36) – megfelel a Google Play 2026-os célverzió-követelményének |
| Fordítás (compileSdk) | Android 17 (API 37) |
| Tesztelve | Android 17 (API 37) emulátoron – lásd a *Tesztelés* részt |

A targetSdk szándékosan 36: az API 37-re célzó alkalmazásokra vonatkozó új háttér-audió korlátozásokat még nem lehetett valódi készüléken ellenőrizni.

## 3. Telepítés

1. Töltsd le az APK-t: GitHub → *Actions* → a legutóbbi sikeres „Android – tesztek és debug APK” futás → *Artifacts* → `FulesEbreszto-debug-apk` (ZIP, benne `FulesEbreszto-v1.0-debug.apk`).
2. Másold a telefonra, és nyisd meg. Az Android kérni fogja az „ismeretlen forrásból történő telepítés” engedélyezését annál az alkalmazásnál, amellyel megnyitod (pl. Fájlok).
3. Telepítés után indítsd el a **FülesÉbresztő**-t.

Fejlesztői gépről: `adb install -r FulesEbreszto-v1.0-debug.apk`.

## 4. Első indítás és jogosultságok

Első indításkor az alkalmazás kéri az **értesítések** engedélyezését (Android 13+). A **Beállítások → Rendszerjogosultságok** szakasz mutatja az állapotot, és hiány esetén megnyitja a megfelelő Android-beállítást:

| Jogosultság | Miért kell | Megjegyzés |
|---|---|---|
| Pontos ébresztés | pontos időben szóljon | Android 13+: `USE_EXACT_ALARM` (telepítéskor megadva); Android 12: felhasználó adhatja meg |
| Értesítések | ébresztés-értesítés, Leállítás/Szundi gomb | Android 13+ futásidejű engedély |
| Teljes képernyős ébresztés | lezárt képernyőn megjelenő ébresztőképernyő | Android 14+: ha a rendszer nem adja meg automatikusan, kézzel kell engedélyezni |
| Rezgés | tartalékjelzés | telepítéskor megadott |
| Akkumulátor-optimalizálás | csak tájékoztatás | a `setAlarmClock()` megkerüli a Doze-t; agresszív gyártói energiatakarékosság esetén érdemes kivenni az alkalmazást |

Ha valamelyik kritikus engedély hiányzik, a főképernyőn sárga figyelmeztetés jelenik meg. Bluetooth-, mikrofon- vagy internetjogosultságot az alkalmazás **nem** kér.

## 5. Új ébresztés létrehozása

1. Főképernyő → **+ Új ébresztés**.
2. **Időpont:** görgesd az óra- és perckereket (24 órás), vagy válaszd a „Beírás billentyűzettel” módot.
3. **Ébresztés neve** (alapértelmezés: „Ébresztés”).
4. **Ismétlés:** H K Sze Cs P Szo V gombok, vagy gyorsgombok (Hétköznap / Hétvége / Minden nap / Egyszeri). Ha egy nap sincs kijelölve, az ébresztés egyszeri, és dátum választható.
5. **Ébresztőhang**, **Hangerő**, **Rezgés fejhallgató mellett is**, **Szundi** (5/10/15 perc vagy egyéni), **maximális időtartam**.
6. **Ébresztés mentése** – a mentés bekapcsolja és ütemezi az ébresztést; egy rövid üzenet jelzi, mennyi idő múlva szól.

Kártyára koppintás: szerkesztés. Hosszú érintés: **Másolás** (kikapcsolt másolat) vagy **Törlés** (megerősítéssel). A főképernyő tetején: „Következő ébresztés: hétfő 06:30”.

## 6. Saját ébresztőhang kiválasztása

Szerkesztő → **Ébresztőhang kiválasztása** → **Hangfájl hozzáadása**. A rendszer fájlválasztója nyílik meg; a kiválasztott fájlhoz az alkalmazás **tartós olvasási jogot** kér (`takePersistableUriPermission`), így újraindítás után is elérhető marad. A fájl a telefonon marad, sehová nem töltődik fel.

- Minden hang mellett van **tesztlejátszás** – ez is kizárólag ellenőrzött fejhallgatón szól.
- Saját hang eltávolításakor az azt használó ébresztések a „Klasszikus ébresztés” hangra váltanak.
- Ha a fájl később nem érhető el (törölték, visszavonták a jogot), az ébresztés a **beépített tartalékhangot** játssza – szintén csak fejhallgatón.

## 7. Fejhallgatók kezelése

- **Vezetékes és USB-C fejhallgató/headset:** automatikusan engedélyezett.
- **Bluetooth (A2DP, LE Audio), hallókészülék, egyéb USB-audioeszköz:** csatlakoztatás után megjelenik a **Beállítások → Engedélyezett Bluetooth- és USB-eszközök** listában; csak a bekapcsolt eszközön szólhat ébresztés. Bluetooth-hangszórót vagy autós kihangosítót **ne** engedélyezz.
- **Bluetooth LE hangszóró** típusú eszköz soha nem engedélyezhető.
- A **Beállítások → Hangkimenet** mutatja az aktuális médiakimenetet, a csatlakoztatott kimeneteket, a médiahangerőt és a kimenetváltozás-figyelés állapotát. A **Fejhallgatóhang tesztelése** gomb 8 másodperces teszthangot játszik – csak igazolt fejhallgatón.
- Ha a fejhallgató **médiahangereje 0**, a főképernyő figyelmeztet, ébresztéskor pedig a rezgés is bekapcsol.

## 8. Szundi és kikapcsolás gesztusai

Az ébresztőképernyőn a képernyő **bármely pontjáról** indítható:

- **Felfelé húzás (≥ 110 dp, túlnyomóan függőleges) = leállítás.** A hang és a rezgés leáll, az ismétlődő ébresztés következő alkalma ütemezve marad.
- **Lefelé húzás = szundi** („Szundi – 10 perc” visszajelzés). A szundi lejártakor az ébresztés újra megszólal.
- Rövid vagy ferde mozdulat nem vált ki semmit; egy gesztus legfeljebb egy műveletet indít; az ismételt események hatástalanok.
- Kisegítő lehetőségek: **Leállítás** és **Szundi** gomb, TalkBack egyéni műveletek, valamint az értesítés Leállítás/Szundi gombjai.
- A Vissza gomb nem némítja el az ébresztést.

## 9. Az ébresztési rendszer működése

```
AlarmManager.setAlarmClock ──▶ AlarmReceiver ──▶ AlarmRingService (foreground, systemExempted)
                                                     │
                                       AlarmStateMachine (egyetlen döntéshozó)
                                         │ effektek
             ┌───────────────────────────┼──────────────────────────────┐
     SafeHeadphonePlayer           VibrationController           AlarmEngine (Room)
   (néma indítás, útvonal-        (USAGE_ALARM rezgés)        (szundi / leállítás / következő
    ellenőrzés, azonnali némítás)                               ismétlés ütemezése)
                                         │
                         AlarmActivity (teljes képernyő, gesztusok)
```

- **Állapotgép:** `IDLE → SCHEDULED → TRIGGERED → CHECKING_AUDIO → PLAYING_HEADPHONES / VIBRATING_ONLY → SNOOZED / DISMISSED / TIMED_OUT`, hibánál `ERROR`. A hangot és a rezgést kizárólag az állapotgép által visszaadott effektek kapcsolják; az ismételt vagy érvénytelen események nem okoznak semmit (idempotens leállítás, nincs dupla szundi, leállítás után nem indulhat újra rezgés).
- **Időzítés:** minden ébresztéshez legfeljebb egy fő és egy szundi trigger tartozik (egyedi PendingIntent-azonosító). A következő időpontot a `NextTriggerCalculator` számolja a helyi faliórához (nyári időszámítás: a nem létező 02:30 03:30-kor szól, a kétszer előforduló 02:30 csak egyszer).
- **Elavult trigger elleni védelem:** a megszólaláskor az alkalmazás ellenőrzi, hogy az ébresztés létezik-e, be van-e kapcsolva, és ugyanarra az időpontra van-e ütemezve – törölt, módosított vagy már lejátszott ébresztés nem szólal meg.
- **Egy időben több ébresztés:** egyetlen csörgés lesz belőle; a leállítás/szundi mindegyikre vonatkozik. Szundi alatt esedékes másik ébresztés önállóan megszólal.
- **Újraindítás / időváltozás:** `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` után minden ébresztés újraütemeződik. Az adatbázis **eszközvédett tárhelyen** van, így a telefon a feloldás előtt is tud ébreszteni. A kikapcsolt állapot alatt legfeljebb a maximális csörgési időn belül elmulasztott ébresztés azonnal megszólal, a régebben elmulasztottakról értesítés jelenik meg. Boot után az alkalmazás nem indít foreground service-t (Android 15+ szabály).
- **Maximális csörgési idő** (alapértelmezés 10 perc) után az ébresztés leáll, és értesítés jelzi.

## 10. Ismert korlátozások

- **Valódi készüléken – különösen Bluetooth- és vezetékes fejhallgatóval – még nem volt tesztelve.** Az eszközszintű ellenőrzés Android 17 emulátoron történt, ahol nincs fejhallgató; így a hangszórómentes működést fizikailag nem lehetett mérni.
- A hangkimenet biztonságának korlátai: lásd a következő szakaszt.
- Android 14+ alatt a teljes képernyős ébresztés engedélyét a rendszer nem minden telepítési módnál adja meg automatikusan; enélkül csak felugró értesítés jelenik meg (a rezgés/hang ettől függetlenül működik).
- „Ne zavarjanak – teljes némítás” módban a rendszer a médiahangot és a rezgést is elnémíthatja.
- Telefonhívás vagy kommunikációs mód alatt az ébresztés csak rezeg.
- A feloldás előtti (direct boot) állapotban a saját hangfájlok nem olvashatók; ilyenkor a beépített tartalékhang szól.
- A Material 3 időbeviteli mező („Beírás billentyűzettel”) feliratait a rendszer nyelve határozza meg.

## Hangkimenet biztonsága – részletesen

Az Android nem ad olyan API-t, amellyel egy alkalmazás **garantálhatná**, hogy egyetlen hangminta sem kerül a hangszóróra. A FülesÉbresztő ezért több rétegű, „kétség esetén néma” stratégiát használ:

1. **Nem `USAGE_ALARM`:** a riasztás-hangfolyamot az Android fejhallgató mellett a hangszóróra is kiküldi. A lejátszás `USAGE_MEDIA`, amely fejhallgató csatlakoztatásakor normál esetben csak a fejhallgatóra megy.
2. **Néma indítás + `setPreferredDevice()`:** a lejátszó 0 hangerővel indul, a kiválasztott fejhallgatóhoz kötve. A `setPreferredDevice()` csak kérés, nem garancia – ezért:
3. **Tényleges útvonal ellenőrzése:** a hangerő csak akkor nő (5 s-os felfutással), ha a lejátszó által jelentett kimenet (`getRoutedDevice`, Android 16+ esetén `getRoutedDevices` – több kimenet is) kizárólag engedélyezett fejhallgató, **és** Android 13+ esetén a rendszer médiaútvonala (`getAudioDevicesForAttributes`) sem tartalmazza a beépített hangszórót/beszédhangszórót. Ha 1,5 s alatt nem igazolható, az ébresztés rezgésre vált.
4. **Eseményalapú figyelés:** `AudioDeviceCallback` (eltávolítás/hozzáadás), `ACTION_AUDIO_BECOMING_NOISY` (a rendszer még az átirányítás előtt küldi), útvonalváltás-figyelő és hangfókusz-vesztés. Ezek először **szinkron módon némítanak**, és csak utána értékelnek újra. Biztonsági tartalékként a lejátszás alatt 250 ms-onként is újraellenőrzés történik.
5. **Hívás vagy más audio mód**, illetve hangfókusz elvesztése esetén néma marad.

**Fennmaradó kockázatok (nem zárhatók ki minden készüléken):**

- Hirtelen lecsatlakozáskor a hangkártya pufferében már lévő néhány tíz ezredmásodpercnyi hang – gyártói implementációtól függően – a hangszóróra kerülhet, mielőtt a némítás érvényesül.
- Android 16 alatt a lejátszó csak egy kimenetet jelent; ha egy gyártói „kettős hangkimenet” funkció a hangszóróra is duplikál, ezt Android 13–15 között csak a rendszer-médiaútvonal ellenőrzése, Android 10–12 között semmi sem jelzi.
- A Bluetooth-eszközök azonosítása `BLUETOOTH_CONNECT` jogosultság nélkül, a név és a (részben anonimizált) cím alapján történik; egy fejhallgatóval azonos nevű és címvégű eszközt az alkalmazás nem tud megkülönböztetni. Ezért csak a ténylegesen fejhallgató eszközt engedélyezd.
- Ha a rendszer kimenetválasztójában a média a telefon hangszórójára van állítva Bluetooth-kapcsolat mellett, az alkalmazás ezt bizonytalannak tekinti, és csak rezeg.

**Tesztelt eszközök:** kizárólag Android 17 (API 37) x86_64 emulátor, fejhallgató nélkül. Fizikai Bluetooth/vezetékes/USB-C teszt nem történt – fontos ébresztésekhez a valódi készüléken végzett próbáig használj mellette másik ébresztőt is.

## 11. Hibaelhárítás

| Jelenség | Teendő |
|---|---|
| Nem jelenik meg a teljes képernyős ébresztés | Beállítások → Rendszerjogosultságok → Teljes képernyős ébresztés → Megnyitás, engedélyezd |
| Csak rezeg, pedig fejhallgató van | Bluetooth esetén engedélyezd az eszközt a listában; nézd meg a „Hangkimenet” szakaszt (aktuális médiakimenet, médiahangerő); próbáld a „Fejhallgatóhang tesztelése” gombot |
| Halk vagy néma a fejhallgató | Növeld a telefon médiahangerejét (fejhallgató csatlakoztatva) és az ébresztés hangerejét |
| Késve szól | Ellenőrizd a „Pontos ébresztés” engedélyt; gyártói energiatakarékos módban vedd ki az alkalmazást az optimalizálás alól |
| Saját hang helyett a beépített szól | A fájl nem érhető el: add hozzá újra a Hangfájl hozzáadása gombbal |
| Elmaradt ébresztés értesítés | A telefon ki volt kapcsolva vagy az órát előre állították az ébresztés idején |
| Részletek | Beállítások → Tesztelés → Diagnosztikai napló (csak helyben tárolt, személyes adatot nem tartalmaz) |

## 12. APK készítése

**Android Studio:** *File → Open* → a projekt gyökere → Gradle-szinkron → *Build → Build App Bundle(s)/APK(s) → Build APK(s)*. Eredmény: `app/build/outputs/apk/debug/app-debug.apk`.

**Parancssor** (JDK 17+ szükséges, a Gradle Wrapper letölti a Gradle 9.6.1-et, az Android Gradle Plugin a hiányzó SDK-csomagokat):

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Emulátoron/készüléken futó tesztek: `./gradlew connectedDebugAndroidTest`.

**GitHub Actions:** a `.github/workflows/android.yml` minden `main`-re történő pushkor, pull requestkor és kézi indításkor (*Actions → Android – tesztek és debug APK → Run workflow*) lefuttatja a unit teszteket és a lintet, lefordítja a debug APK-t, és `FulesEbreszto-debug-apk` néven letölthető artifactként feltölti. Bármely hiba sikertelen futást eredményez.

---

## Technikai összefoglaló

- Kotlin 2.4, Jetpack Compose + Material 3, MVVM, Room 2.8, DataStore, Navigation Compose, Android Gradle Plugin 9.4.1, Gradle 9.6.1.
- Csomagok: `domain` (tiszta Kotlin logika: időszámítás, állapotgép, gesztus, ütemezési terv), `audio` (útvonal-szabályok, biztonságos lejátszó, rezgés), `alarm` (motor, AlarmManager, service, értesítések, fogadók), `data` (Room, DataStore, napló), `ui` (képernyők).
- Tesztek: 95 JVM unit teszt (`app/src/test`) és 13 műszeres teszt (`app/src/androidTest`: Room, valódi AlarmManager, Compose gesztusok).
- Beépített hangok: `tools/generate_sounds.py` állítja elő őket (szintetizált hullámformák, harmadik fél jogaitól mentesek).

## Adatvédelem

Nincs internetkapcsolat, reklám, analitika, fiók vagy felhőmentés (`allowBackup=false`). Minden adat (ébresztések, beállítások, engedélyezett eszközök, diagnosztikai napló) kizárólag a telefonon tárolódik.

## Licenc

A forráskód MIT-licences (lásd `LICENSE`). Felhasznált könyvtárak: AndroidX/Jetpack és Kotlin – Apache License 2.0.
