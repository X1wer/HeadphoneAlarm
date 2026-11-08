# FülesÉbresztő – tesztjegyzőkönyv

Dátum: 2026. november 8. · Tesztelt build: `FulesEbreszto-v1.0-debug.apk` (debug, versionName `1.0-debug`)
Fordítási környezet: Windows 11, JDK 21 (Android Studio OpenJDK), Gradle 9.6.1, AGP 9.4.1, compileSdk 37.
Eszköz: **Android 17 (API 37) x86_64 emulátor** (`Medium_Phone_API_37.0`, Google APIs Play Store image). **Valódi telefon nem állt rendelkezésre.**

## 1. Fordítás és statikus ellenőrzés

| Lépés | Eredmény |
|---|---|
| `./gradlew clean testDebugUnitTest lintDebug assembleDebug` | **Sikeres** |
| Lint (abortOnError = true) | 0 hiba; figyelmeztetések: régebbi targetSdk (szándékos), AGP/függőség-frissítés javaslat, `UseKtx` stílusjavaslatok |
| APK jogosultságai (`aapt2 dump badging`) | Nincs `INTERNET`, nincs Bluetooth-jogosultság; `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (≤ API 32), `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE(_SYSTEM_EXEMPTED/_MEDIA_PLAYBACK)`, `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` |

## 2. Automatikus unit tesztek (JVM) – 95/95 sikeres

| Tesztosztály | Db | Lefedett követelmény (9.1) |
|---|---|---|
| `NextTriggerCalculatorTest` | 16 | következő időpont, hétköznapok, hétvége kihagyása, hónap-/év-/szökőnap-váltás, nyári időszámítás (tavaszi rés, őszi átfedés), időzónaváltás, egyszeri ébresztés, visszaállított óra, több ébresztés közül a legközelebbi, rendezés |
| `ReschedulePlannerTest` | 12 | ütemezés, kikapcsolás, módosítás, törlés, újraindítás utáni késői/elmaradt ébresztés, szundi-helyreállítás, maximális idő mint türelmi ablak, elavult trigger elutasítása |
| `AlarmEngineTest` | 19 | első indítás alapértékei, mentés, módosítás (nincs dupla trigger), törlés + törölt ébresztés nem szól, kikapcsolás, ismétlés ütemezése csörgéskor, dupla trigger elutasítása, egyszeri ébresztés kikapcsolása, szundi és újracsörgés, szundi alatti másik ébresztés, két egyidejű ébresztés, újraindítás, időzóna- és kézi időváltás, értékhatárok, másolás, próbaébresztés |
| `AlarmStateMachineTest` | 15 | állapotátmenetek, rezgés/hang vezérlése, fejhallgató elvesztése és visszatérése, idempotens leállítás, nincs dupla szundi, leállítás utáni események hatástalansága, maximális idő, hiba → rezgés |
| `SwipeGestureDetectorTest` | 10 | irányfelismerés, kezdőponttól független gesztus, rövid/véletlen érintések kiszűrése, ferde mozdulat elutasítása, egy gesztus = egy művelet |
| `OutputPolicyTest` | 13 | engedélyezési szabályok, prioritás, anonimizált BT-cím, útvonal-igazolás (nincs adat / máshová irányított / duplikált hangszóró / médiaútvonal / hívás / fókusz / lecsatlakozás), audioútvonal-állapotváltás sorozat, főképernyő-állapotsor |
| `SoundResolverTest` | 5 | hiányzó / visszavont hangfájl tartalékkezelése, szerializáció |
| `FormattingTest` | 5 | magyar időformátum (24/12 órás), ismétlés-összegzés, visszaszámlálás, hangerőgörbe, hétnap-maszk |

A tesztelés során 2 unit teszt valódi hibát talált (a szundi és az egyszeri kikapcsolás nem perzisztálódott) – javítva, lásd a fejlesztői dokumentáció 3. pontját.

## 3. Műszeres tesztek (Android 17 emulátor)

| Tesztosztály | Db | Mit ellenőriz |
|---|---|---|
| `AlarmDaoTest` | 4 | Room: minden mező oda-vissza, rendezett lista, módosítás/törlés, eltávolított saját hang → beépített hang |
| `AlarmSchedulingDeviceTest` | 3 | valódi AlarmManager: pontos ébresztés engedélyezett; mentett ébresztés = rendszer `nextAlarmClock`; törlés után eltűnik |
| `AlarmRingScreenGestureTest` | 6 | Compose: felfelé húzás = leállítás (egyszer), lefelé = szundi, rövid/oldalirányú mozdulat nem vált ki semmit, a képernyő tetejéről indított gesztus is működik, gomb, TalkBack egyéni művelet |

Végleges futás (`./gradlew connectedDebugAndroidTest`, a commitolt forrásból): **13/13 sikeres**. Az első futáskor a 6 Compose-teszt egy könyvtári hiba miatt bukott (régi Espresso, API 35+), ezt az `espresso-core` 3.7.0 függőség javította.

## 4. Eszközszintű (end-to-end) tesztek – spec 9.2

| # | Teszt | Eredmény | Bizonyíték |
|---|---|---|---|
| 1 | Ébresztés aktív alkalmazás mellett | **Sikeres** (a próbaébresztés az alkalmazás futása közben is megszólalt, az ébresztőképernyő előtérbe került) | logcat, `dumpsys` |
| 2 | Ébresztés háttérbe küldött alkalmazás mellett | **Sikeres** (a képernyő kikapcsolása után az alkalmazás nem volt előtérben) | – |
| 3 | Ébresztés lezárt képernyőn | **Sikeres**: a képernyő felébredt (`mWakefulness=Awake`), az `AlarmActivity` a zárolt képernyő felett jelent meg | `05_ringing_locked.png` |
| 4 | Ébresztés újraindított telefon után | **Sikeres**: `LOCKED_BOOT_COMPLETED` és `BOOT_COMPLETED` után újraütemezve, 12:44:00-kor pontosan megszólalt | `15_after_reboot_ring.png`, logcat |
| 5 | Bluetooth-fejhallgatóval | **Nem végezhető el** – az emulátornak nincs Bluetooth-audiója | – |
| 6 | Vezetékes fejhallgatóval | **Nem végezhető el** – az emulátoron nem szimulálható fejhallgató-csatlakoztatás | – |
| 7 | Fejhallgató nélküli, csak rezgéses ébresztés | **Sikeres**: rezgés `USAGE_ALARM` attribútummal fut, **0 db** `USAGE_MEDIA` lejátszó jött létre (a hangszórón nem szólt semmi), kijelzés: „Csak rezgés – nincs fejhallgató” | `dumpsys vibrator_manager`, `dumpsys audio` |
| 8 | Fejhallgató kihúzása lejátszás közben | **Nem végezhető el** (fizikai eszköz kell); a logika unit tesztekkel lefedve | `OutputPolicyTest`, `AlarmStateMachineTest` |
| 9 | Bluetooth-kapcsolat megszakadása | **Nem végezhető el** (fizikai eszköz kell); unit tesztekkel lefedve | – |
| 10 | Fejhallgató újracsatlakoztatása | **Nem végezhető el** (fizikai eszköz kell); unit tesztekkel lefedve | – |
| 11 | Saját MP3 lejátszása | **Nem végezhető el teljes körűen** – a lejátszás fejhallgatót igényel; a tartalékkezelés unit tesztelve | – |
| 12 | Szundi, majd ismételt ébresztés | **Sikeres**: lefelé húzás → service leállt, rezgés leállt, szundi pontos ébresztésként ütemezve → 1 perc múlva (12:36:23) lezárt képernyőn újra megszólalt → „Leállítás” gombbal leállítva | `07_snooze_feedback.png`, `08_rering.png` |
| 13 | Felfelé húzással leállítás | **Sikeres** (valódi érintés-injektálás): visszajelzés, rezgés leállt, service leállt, képernyő bezárult | `06_dismiss_feedback.png` |
| 14 | Lefelé húzással szundi | **Sikeres** (lásd 12.) | – |
| 15 | Hiányzó / visszavont jogosultság | **Részben**: a beállítások jogosultsági szakasza és a főképernyő figyelmeztetése működik; a teljes képernyős engedély visszavonását nem teszteltem | – |

| + | Maximális csörgési idő (10 perc) | **Sikeres**: 12:44:00,14-kor indult, 12:54:00,27-kor `TIMED_OUT`; rezgés leállt, „Az ébresztés automatikusan leállt” értesítés megjelent, a következő napi ismétlés ütemezve maradt | logcat, `dumpsys notification` |

A teljes képernyős ébresztés első emulátoros próbája **hibát tárt fel** (a képernyő sötét maradt, az ébresztés csak rezgett) – javítva, a fenti 3., 4., 12. sor már a javított buildre vonatkozik.

További eszközszintű megfigyelések:

- Az ébresztés foreground service-e `systemExempted` típussal (0x400) fut, az értesítés csatornája hang és rezgés nélküli.
- Az AlarmManager-bejegyzés `setAlarmClock` (window=0, `exactAllowReason=policy_permission`, show-intenttel).
- Csörgés közben a következő napi ismétlés már ütemezve volt.
- A „Fejhallgatóhang tesztelése” fejhallgató nélkül nem indított lejátszást („Nincs engedélyezett fejhallgató – a teszthang nem indul el.”), 8 s után leállt.
- A beállítások a beépített hangszórót „ébresztéshez soha nem használt” kimenetként mutatták.
- Szerkesztő: görgetős választóval beállított 12:44, „Minden nap”, mentés → helyes rendszer-ébresztés.

## 4b. Kétnyelvűség (angol bővítés után)

| Teszt | Eredmény |
|---|---|
| Unit tesztek (a formázó magyar és angol kimenetével bővítve) | **95/95 sikeres** |
| Műszeres tesztek (a gesztusteszt a feliratokat erőforrásból olvassa) | **13/13 sikeres** angol rendszernyelvű emulátoron |
| Lint | 0 hiba, nincs hiányzó fordítás; egy névütközést talált (`state_on`/`state_off` felülírta volna a Compose saját kapcsoló-feliratait) – átnevezve |
| Angol felület (rendszernyelv: angol) | **Sikeres**: főképernyő, beállítások, első indításkori „Alarm” nevű minta-ébresztés |
| Váltás magyarra a Beállítások → Nyelv pontban | **Sikeres**: a rendszer az alkalmazásnyelvet `[hu]`-ra állította, a képernyő azonnal magyarra váltott |
| Ébresztőképernyő és értesítés magyarul (próbaébresztés lezárt képernyőn) | **Sikeres**: „ÉBRESZTÉS”, „Csak rezgés – nincs fejhallgató”, „Húzd felfelé a kikapcsoláshoz”, értesítés: „Próbaébresztés – Csak rezgés – nincs fejhallgató” |
| Visszaváltás „Rendszer nyelve” beállításra | **Sikeres**: alkalmazásnyelv `[]`, a felület angol |
| Android 10–12 nyelvváltási út | **Nem tesztelt** (nincs ilyen emulátor-image) |

## 5. Nem igazolt területek

- **Hangszórómentes működés fejhallgatóval**: fizikai teszt nélkül nem igazolt. A logika unit tesztekkel lefedett, de a gyártói audio-routing viselkedést csak valódi készüléken lehet mérni.
- Android 10–16 viselkedés (csak Android 17 emulátoron futott).
- Teljes képernyős engedély nélküli viselkedés, „Ne zavarjanak” módok, gyártói energiatakarékos módok.
