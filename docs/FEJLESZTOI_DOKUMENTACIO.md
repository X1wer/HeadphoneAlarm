# FülesÉbresztő – fejlesztői dokumentáció

Dátum: 2026. november 8. · Verzió: 1.0 (versionCode 1) · Build: debug

## 1. A korábbi projekt átvizsgálása

Átvizsgált csomag: `FulesEbreszto_Automatikus_APK_forditas(1).zip` (a `FulesEbreszto_Android_Projekt.zip` nem volt elérhető; a mellékelt `FulesEbreszto_telefonos_forditas.yml` ugyanezt a ZIP-et csomagolta ki és fordította).

| Terület | Állapot a régi projektben | Döntés |
|---|---|---|
| Build | Nincs `gradlew`/`gradle-wrapper.jar`; AGP 8.7.3 + Kotlin 1.9.25; nem volt igazolva, hogy fordul | Új Gradle-projekt: AGP 9.4.1, Gradle 9.6.1 wrapperrel, version catalog |
| UI | View-alapú, kódból épített `LinearLayout`-ok, nincs Compose / Material 3 / navigáció | Teljesen újraírva Compose + Material 3 + Navigation |
| Adattárolás | `SharedPreferences` + kézi JSON | Room (eszközvédett tárhelyen) + DataStore |
| Időzítés | `setAlarmClock` (jó), de nem volt elavult-trigger védelem, nem volt szundi-perzisztencia, újraindításkor a lejárt egyszeri ébresztés elveszett | Tiszta `NextTriggerCalculator` + `ReschedulePlanner` + `TriggerValidator`, késői/elmaradt ébresztés kezelése |
| Hangkezelés | Jó alapötlet: `USAGE_MEDIA`, néma indítás, `setPreferredDevice`, `getRoutedDevice`-ellenőrzés, rezgéses tartalék, eszköz-callback | Az ötletet megtartottam, de újraírtam: tiszta, tesztelt szabályréteg (`OutputPolicy`, `AudioPathEvaluator`), `BECOMING_NOISY`, hangfókusz, hívás-mód, Android 13+ médiaútvonal-ellenőrzés, Android 16+ több-kimenetes útvonal, ellenőrzési türelmi idő, fade-in |
| Állapotkezelés | Statikus `@Volatile` mezők a service-ben, 400 ms-os UI-pollozás | Központi `AlarmStateMachine`, `StateFlow` a UI felé |
| Foreground service | `systemExempted` (helyes választás) | Megtartva, `mediaPlayback` tartaléktípussal |
| Gesztusok | `dispatchTouchEvent`, 120 dp, 1,3× dominancia – nem volt egyszeri-művelet garancia | Tiszta `SwipeGestureDetector` + `OneShotAction`, animált visszajelzés, akadálymentes műveletek |
| Tesztek | Nem voltak | 95 unit + 13 műszeres teszt |
| Hang | `soft_alarm.wav` ismeretlen eredettel | Eltávolítva; 4 saját szintetizálású hang (`tools/generate_sounds.py`) |
| Direct boot | Nem volt | Eszközvédett tárhely, `LOCKED_BOOT_COMPLETED`, `directBootAware` komponensek |

## 2. Architektúra

```
ui/            Compose képernyők + ViewModel-ek (MVVM)
  list/        főképernyő (AlarmListScreen/ViewModel)
  edit/        szerkesztő, görgetős időválasztó
  sounds/      hangválasztó, biztonságos tesztlejátszás (SoundPreview)
  settings/    beállítások
  ring/        AlarmActivity + AlarmRingScreen (teljes képernyő, gesztusok)
alarm/         AlarmEngine (ütemezési koordinátor), AndroidAlarmClock (AlarmManager),
               AlarmRingService (foreground), AlarmNotifications, Receivers, RingSessionState
audio/         OutputPolicy + AudioPathEvaluator (tiszta szabályok), AudioDevices (platform-leképezés),
               SafeHeadphonePlayer (végrehajtó), VibrationController, HeadphoneMonitor (UI)
data/          Room (alarms, custom_sounds, known_devices), DataStore, DiagnosticLog
domain/        Alarm, WeekDays, NextTriggerCalculator, ReschedulePlanner, TriggerValidator,
               AlarmStateMachine, SwipeGestureDetector, Sounds, HuFormat, VolumeCurve
```

Elv: minden döntés tiszta Kotlinban (`domain`, `audio/OutputPolicy`), JVM-en tesztelve; az Android-osztályok csak végrehajtanak.

### Csörgés folyamata

1. `AlarmManager.setAlarmClock` → `AlarmReceiver` (indító wake lock) → `startForegroundService(AlarmRingService)` – az alarm-clock broadcastból engedélyezett háttérindítás.
2. A service azonnal `startForeground` (típus: `systemExempted`; ha elutasítják: `mediaPlayback`), majd az `AlarmEngine.onTrigger` ellenőrzi és könyveli a triggert, és **azonnal ütemezi a következő ismétlést**.
3. `RingSession` → `AlarmStateMachine.handle(Trigger)` → effektek: rezgés indul, UI, időkorlát, hangellenőrzés.
4. `SafeHeadphonePlayer`: néma MediaPlayer a kiválasztott fejhallgatóra, útvonal-ellenőrzés, majd fade-in – vagy `Silent` állapot → rezgés.
5. Leállítás/szundi (gesztus, gomb, értesítés) → állapotgép → effektek → `AlarmEngine.dismiss/snooze` → a service csak a perzisztálás után áll le.

### Fontos tervezési döntések

- **`USAGE_MEDIA` a `USAGE_ALARM` helyett**: a riasztás-hangfolyamot az Android fejhallgató mellett a hangszóróra is kiküldi.
- **Rezgés már a hangellenőrzés alatt**: a felhasználó soha nem marad jelzés nélkül, ha az ellenőrzés elakad.
- **Egyetlen döntéshozó** (`AlarmStateMachine`): nem lehet két lejátszó, leállítás utáni rezgés, dupla szundi.
- **Ütemezési napló az adatbázisban** (`nextTriggerAt`, `lastTriggeredAt`, `snoozeUntil`): ebből állítható helyre minden boot / időváltás után; a `lastTriggeredAt` véd a visszaállított óra miatti újracsörgés ellen.
- **Azonos időpontú ébresztések egy munkamenetben**: nincs két egymást követő csörgés.
- **Boot után nincs foreground service**: a kis késéssel elmaradt ébresztést az AlarmManager 3 másodperc múlva újra kiváltja (Android 15+ szabálynak megfelelően).
- **Bluetooth-jogosultság nélkül**: az eszközazonosítás `AudioDeviceInfo` alapján történik; ennek ára a gyengébb azonosítás (dokumentált kockázat).
- **targetSdk 36 / compileSdk 37**: az API 37-re célzás háttér-audió szigorításait valódi eszközön kell majd ellenőrizni.

## 2b. Többnyelvűség

- Feliratok: `res/values/strings.xml` (angol, alapértelmezett – minden nem magyar nyelvű telefon ezt kapja) és `res/values-hu/strings.xml` (magyar). `localeFilters = en, hu`, így a könyvtárak (pl. Material 3 időválasztó) is csak ezen a két nyelven jelennek meg.
- A `domain` és `audio` réteg nem tartalmaz feliratot: enumokat ad vissza (`SilentReason`, `UnverifiedReason`, `OutputKind`, `StatusIssue`), ezeket a `ui/Labels.kt` fordítja. A dátum-/időformázás (`TimeFormat`) tiszta Kotlin, mindkét nyelvre unit tesztelve.
- Nyelvválasztás (`i18n/AppLocale`): Android 13+ esetén natív alkalmazásszintű nyelv (`LocaleManager`, `locales_config.xml`) – ez a háttérszolgáltatásra és az értesítésekre is érvényes. Android 10–12 esetén a választás eszközvédett SharedPreferences-ben tárolódik, az activityk és a service `attachBaseContext`-ben kapják meg, az értesítések és a ViewModel-üzenetek pedig `AppLocale.localized()`-on keresztül.
- A motor által adott nevek (alapértelmezett „Ébresztés”/„Alarm”, „(másolat)”, „Próbaébresztés”) a mentés pillanatában aktuális nyelven jönnek létre; a beépített hangok neve mindig az aktuális nyelven jelenik meg (kulcs alapján).
- App Bundle esetén a nyelvi felosztás ki van kapcsolva, hogy a nyelvváltáskor mindkét nyelv elérhető legyen.

## 3. Hibák, amelyeket a tesztelés tárt fel és javítottam

| # | Hiba | Hogyan derült ki | Javítás |
|---|---|---|---|
| 1 | Az `AlarmEngine` csak akkor mentett, ha a tervező módosított valamit, így a szundi időpontja és az egyszeri ébresztés kikapcsolása nem került az adatbázisba (a szundi nem csörgött volna újra) | 2 unit teszt hibája (`AlarmEngineTest`) | `applyPlan` mindig perzisztál |
| 2 | A teljes képernyős ébresztés nem jelent meg lezárt képernyőn: a `NotificationCompat.setSilent(true)` „csak összefoglaló riaszt” csoportba teszi az értesítést, ezért a rendszer elnyomta a full-screen intentet | Emulátoros teszt: a service futott és rezgett, de a képernyő sötét maradt | `setSilent` eltávolítva (a csatorna amúgy is hang és rezgés nélküli) |
| 3 | Versenyhelyzet: a full-screen intent a munkamenet közzététele előtt nyithatja meg az ébresztőképernyőt, amely ekkor azonnal bezárult | Kódáttekintés a 2. hiba vizsgálatakor | A képernyő 5 s-ig vár a munkamenetre; a service triggerkor törli az előző, lezárt állapotot |
| 4 | A Compose tesztkönyvtár által behúzott régi Espresso API 35+ alatt összeomlik | Műszeres tesztek | `espresso-core` 3.7.0 explicit függőség |
| 5 | „Egyéb kimenet” fejhallgató-ikonnal jelent meg a beállításokban | Emulátoros képernyőkép | Csak valódi fejhallgató-típus kap fejhallgató-ikont |
| 6 | A `state_on`/`state_off` erőforrásnév ütközött a Compose privát erőforrásaival, és felülírta volna minden kapcsoló akadálymentes „be/ki” feliratát | Lint (`PrivateResource`) | Átnevezve `alarm_state_on`/`alarm_state_off`-ra |

## 4. Függőségek (mind stabil kiadás, 2026. novemberi állapot)

| Komponens | Verzió |
|---|---|
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.6.1 |
| Kotlin (+ Compose compiler plugin) | 2.4.21 |
| KSP | 2.3.13 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| DataStore Preferences | 1.2.1 |
| Navigation Compose | 2.10.2 |
| Lifecycle | 2.11.0 |
| Activity Compose | 1.13.0 |
| Core KTX | 1.19.1 |
| kotlinx.coroutines | 1.11.0 |
| material-icons-core | 1.7.8 |

Licencek: Apache License 2.0 (AndroidX, Kotlin, coroutines). Tesztfüggőségek: JUnit 4.13.2 (EPL 1.0), AndroidX Test, Espresso 3.7.0.

## 5. Hátralévő / javasolt munka

1. **Fizikai teszt** valódi telefonon: Bluetooth A2DP és LE Audio fejhallgató, Bluetooth-hangszóró (elutasítás), vezetékes jack, USB-C fejhallgató; kihúzás és BT-megszakadás csörgés közben, hangszórós kiszivárgás mérése (pl. a telefon mellé tett hangmérővel).
2. Release-aláírókulcs létrehozása és biztonságos tárolása (GitHub Secrets), release build R8-cal.
3. targetSdk 37-re emelés valódi Android 17 készüléken végzett teszt után.
4. Opcionális: `BLUETOOTH_CONNECT` kérése a pontosabb Bluetooth-eszközosztály (fejhallgató vs. hangszóró) megjelenítéséhez.
5. Az Android 10–12-es nyelvváltási út kipróbálása valódi vagy emulált régebbi készüléken.


## 1.1.0 – színek, hangos ébresztés, Zengő-listák (2026-11-19)

- **Modell:** `Alarm.output` (`HEADPHONES` / `SPEAKER`), `Alarm.color` (ARGB vagy null = a kimenet alapszíne:
  türkiz / piros), `Alarm.playlist` (`PlaylistOptions`: sorrend, lista vége, kezdő dal). Új hangforrás:
  `SoundRef.Zengo(authority, playlistId)` (`zengo:<hatóság>/<id>`; a Kedvencek azonosítója 0).
- **Adatbázis:** 2-es séma, `AutoMigration(1 → 2)` alapértékekkel (`output` = HEADPHONES, `playlist_order` /
  `playlist_end` = FOLLOW_APP, a többi null); `MigrationTest` igazolja, hogy az 1.0.x ébresztései megmaradnak.
- **Lejátszási terv (tiszta Kotlin):** `PlaylistPlanner` a Zengő mentett módjából és az ébresztés beállításaiból
  `PlaylistPlan`-t készít (Zengő ALL → újrakezdés, ONE → az első dal ismétlése, OFF → a lista után csak rezgés,
  STOP_AFTER_CURRENT → az első dal után csak rezgés); `PlaylistSequence` adja a sorrendet (kezdő dal, keverés
  ismétlés nélkül a körök határán, a hibás dalok kihagyása és megjegyzése).
- **Hang:** `AlarmSoundSource` (egy hang / `PlaylistSoundSource`), `AlarmAudio` közös felület. A fejhallgatós út
  (`SafeHeadphonePlayer`) minden dalhoz új, néma lejátszót hoz létre és újra igazolja az útvonalat; a dalok közti
  rövid szünetet nem jelenti útvonalhibának. A hangos út (`SpeakerAlarmPlayer`) a riasztási hangfolyamon
  (`USAGE_ALARM`) szól, útvonal-korlátozás nélkül – szándékosan.
- **Zengő-kapcsolat:** `integration/ZengoClient` a Zengő 1.7.0 csak olvasható szolgáltatóját kérdezi
  (`<csomag>.alarmplaylists`, engedély `<csomag>.permission.ALARM_PLAYLISTS`, kiadás és debug változat is).
  A lista csengéskor, a háttérszálon töltődik (`AlarmSounds.sourceFor`), egy újrapróbálással; ha nem érhető el,
  a beépített hang szól. A manifest `<queries>` része teszi láthatóvá a szolgáltatót.
- **Felület:** a kártya keretét és színét az `alarmFrame()` adja: hangos → 4 dp piros keret; Zengő-lista →
  `ZigZagShape` (cikk-cakkos alak és keret); egyébként vékony keret a választott színben, a kártya enyhén
  színezve. Szerkesztő: Hangkimenet, Szín (10 szín + Automatikus), Zengő-beállítások (sorrend, lista vége, kezdő
  dal a Zengőből olvasott dalok közül). Hangválasztó: „Zengő lejátszási listák” rész engedélykéréssel.
  Ébresztőképernyő: a szín árnyalja, listás ébresztésnél „♪ cím – előadó”.

### Tesztelés közben talált és javított hibák (1.1.0)

| # | Hiba | Hogyan derült ki | Javítás |
|---|---|---|---|
| 7 | Hidegen (futó Zengő nélkül) a Zengő szolgáltatója a saját `Application.onCreate`-je előtt kapta a kérést, és hibát adott → a listás ébresztés a beépített hangra esett vissza. A valóságban reggel ez a szokásos helyzet. | Emulátoros ébresztés a Zengő újratelepítése után | Zengő: lusta `AppContainer`; ébresztő: egy újrapróbálás 0,7 s után |
| 8 | A hibás dalt minden körben újra megpróbálta | Emulátoron a napló két körben két hibát mutatott | `PlaylistSequence.next()` kihagyja a megjegyzett hibás dalokat |
| 9 | A szóközzel kezdődő feliratokból (pl. „ (fejlesztői változat)”, „ · hangszóró, nem engedélyezhető”) az Android levágta a szóközt – az 1.0.x-ben is | Képernyőkép | Idézőjelek a `strings.xml`-ben |
| 10 | A room-testing a navigáció által behozott régebbi kotlinx-serializationnel összeomlott | `MigrationTest` | 1.8.1-es verziókényszer (mint a Zengőben) |

## 1.2.0 – a lista a Zengőben szól, oldalra húzás (2026-11-20)

- **Kérés:** a Zengő-listás ébresztés magában a Zengőben induljon el; felfelé húzás = ébresztés és zene leáll,
  lefelé = szundi, oldalra = az ébresztés eltűnik, a lista szól tovább. A főképernyőről lekerült a
  „Nincs fejhallgató – ébresztéskor csak rezgés” sor (a hangos ébresztés mellett félrevezető volt).
- **Zengő-oldal (1.8.0):** a Zengő MediaSession-je az `ALARM_PLAYLISTS` engedéllyel rendelkező appnak három saját
  parancsot ad (`integration/ZengoSession`, a Zengőben `AlarmBridge` 2-es változat): `ALARM_PLAY` (lista, kezdő dal,
  keverés, ismétlés, „leállás az első dal után”, „utolsó dal ismétlése”, riasztási hangfolyam, előnyben részesített
  kimenet, kulcs + szundi utáni folytatás – **némán indul**), `ALARM_VOLUME` (cél-hangerő áttűnéssel), `ALARM_FINISH`
  (szól tovább / szünet; a Zengő visszaállítja a felhasználó ismétlési módját, a hangerőt, a médiahangfolyamot és a
  kimenetet).
- **Ébresztő-oldal:** `audio/ZengoAlarmPlayer` (`AlarmAudio`) Media3 `MediaController`-rel vezérli a Zengőt.
  Fejhallgatós módban a döntés ugyanaz az `OutputPolicy`, mint eddig, de „útvonalként” a rendszer médiaútvonala
  (`getAudioDevicesForAttributes`, API 33+) szolgál, mert egy másik app lejátszójának útvonala nyilvános API-n nem
  kérdezhető le (`AudioPlaybackConfiguration`-ből csak az attribútumok és az eszköz látszik, a munkamenet nem). Ezért
  API 33 alatt fejhallgatós módban a régi, saját lejátszó szól. Kihúzás / BECOMING_NOISY / kétség → azonnali
  némítás + szünet a Zengőben, rezgés; visszadugáskor ugyanott folytatja. Hangos módban nincs útvonal-ellenőrzés.
  Ha a Zengő nem érhető el, nem ad ébresztési parancsot (régi verzió), nem indul el 6 mp alatt, tartósan hibára fut
  vagy megszakad a kapcsolat, a régi, saját lejátszó szól ugyanazzal a listával (`RingSound.local`).
- **Állapotgép:** új `RingEvent.Continue` → `DISMISSED` + `HandOverAudio` (a zene nem áll le); a szolgáltatás csak
  akkor küldi, ha a lista hallhatóan szól a Zengőben (`ZengoAlarmPlayer.canHandOver`). `FinishKind.CONTINUED`.
- **Felület:** `SwipeGestureDetector` vízszintes iránya (`SwipeAction.CONTINUE`, csak amíg engedélyezett);
  ébresztőképernyő: oldalnyilak, „Húzd oldalra…” sor, „A Zengőben szól”, kisegítő művelet; értesítés: „Zene tovább”
  gomb, és az elhúzott értesítés (`deleteIntent`) is ezt jelenti, más ébresztésnél újra megjelenik.
- **Függőség:** `androidx.media3:media3-session` 1.11.1 (a Zengővel azonos); az általa hozott
  `ACCESS_NETWORK_STATE` engedélyt a manifest eltávolítja.

### Tesztelés közben talált és javított hibák (1.2.0)

| # | Hiba | Hogyan derült ki | Javítás |
|---|---|---|---|
| 11 | A második csengetésnél az ébresztő azonnal „a lista véget ért”-et jelzett: a Zengő még az előző csengetés befejezett állapotát mutatta, mielőtt az új parancs hatott volna | Emulátoros csengetés, napló | A lista vége csak azután számít, hogy a Zengő ebben a körben már szólt (`seenPlaying`) |
| 12 | Szundi utáni folytatáskor a Zengő lejátszója hibára futott: a Media3 1.11 automatikus hangfókusz-kezelése csak média/játék hangfolyamra engedélyezett, a riasztásira kivételt dob (az első csengetésnél a még üres lejátszón ez nem jött elő) | Emulátor, a Zengő naplója (`IllegalArgumentException`) | Zengő: riasztási hangfolyamon saját fókuszkérés, a Media3 fókuszkezelése kikapcsolva; a tartalék (az ébresztő saját lejátszója) közben helyesen átvette a listát |
| 13 | Elutasított `ALARM_PLAY` (pl. üres lista) is elnémította a Zengőt | Új műszeres teszt írása közben | A lista a lejátszó módosítása előtt készül el |
| 14 | A Zengő rövid hiba utáni átugrását (hibás dal) az ébresztő tartós hibának vehette volna | Átnézés | 4 mp-es türelmi idő a tartalékra váltás előtt |
