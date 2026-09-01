# Plán X4 — Android projekt (koncept → prvý kód)

Toto je prvá verzia kódu k appke z konceptu "Plán X4" (kreslenie pôdorysov s Leica DISTO X4
cez Bluetooth). Nadväzuje na rozhodnutia z artefaktu, ktorý sme si prešli spolu — kombinovaný
spôsob kreslenia, hrúbka steny per stena s výberom líca, okná a dvere s domeraním.

## Dôležité: toto tu NEBOLO skompilované ani odskúšané

Sandbox, v ktorom tento kód vznikol, nemá sieťový prístup na Maven Central / Google Maven
(firemná/organizačná politika siete), takže Gradle si nevedel stiahnuť Android Gradle Plugin
ani žiadnu inú závislosť — `./gradlew build` som tu nemohol spustiť ani raz. Kód som písal
ručne s maximálnou opatrnosťou a s dobrou znalosťou použitých Android/Compose/Kotlin API, ale
**prvé reálne skompilovanie prebehne až u teba v Android Studiu**. Buď pripravený, že narazíš
na drobné chyby (preklep, chýbajúci import, verzia knižnice) — pošli mi chybovú hlášku z
Android Studia a opravím to.

Jediná časť, ktorú som si mohol overiť aspoň manuálne prepočítaním (nie spustením): matematika
plochy miestnosti v `core/geometry/FloorPlanMath.kt` — v `core/src/test/.../FloorPlanMathTest.kt`
sú testy s ručne dopočítanými očakávanými hodnotami. Spusti ich ako prvé (`./gradlew :core:test`),
sú nezávislé od Androidu a najľahšie sa opravia, ak by bola chyba.

## Zostavenie APK bez inštalácie čohokoľvek (GitHub Actions)

Ak nechceš inštalovať Android Studio (zaberá rádovo GB), appku ti vie zostaviť GitHub úplne
vo svojom cloude — `.github/workflows/build.yml` je na to už pripravený.

1. Vytvor si účet na [github.com](https://github.com) (zadarmo), ak ho ešte nemáš.
2. Vytvor nový repozitár (**New repository**) — pokojne súkromný ("Private"), README ani nič
   iné netreba zaškrtávať.
3. V prázdnom repozitári klikni na odkaz **"uploading an existing file"** a natiahni ("drag &
   drop") do prehliadača celý obsah rozbaleného priečinka `plan-x4-android` (aj skryté
   priečinky ako `.github` — vo väčšine prehliadačov stačí označiť všetko v priečinku a
   pretiahnuť naraz). Potvrď **Commit changes**.
4. Prepni sa na záložku **Actions** hore v repozitári — automaticky sa spustil build ("Build
   debug APK"). Počkaj 2–5 minút, kým dobehne (zelený znak ✓).
5. Klikni na dobehnutý beh, dole v sekcii **Artifacts** stiahni `plan-x4-debug-apk` (je to
   zip, v ňom je `app-debug.apk`).
6. Ten APK presuň do telefónu a nainštaluj presne tak, ako pri sideloadingu opísanom nižšie.

## Ako otvoriť (v Android Studiu)

1. Android Studio (aktuálna verzia) → **Open** → vyber priečinok `plan-x4-android`.
2. Studio nemá `gradlew` (wrapper som tu nevedel vygenerovať bez siete) — pri otvorení ponúkne
   vygenerovanie wrapperu, alebo choď **File → Sync Project with Gradle Files** a nechaj Studio
   nastaviť Gradle samo. Ak sa spýta na verziu Gradle, funguje 8.7+.
3. Po synchronizácii skús najprv `:core:test` (Kotlin/JVM, žiadny emulátor netreba), potom
   spusti appku na telefóne alebo emulátore s Bluetooth (emulátor BLE nevie, treba reálny telefón).

## Čo appka vie (a čo ešte nie)

| Oblasť | Stav |
|---|---|
| Kreslenie pôdorysu (kombinovaný režim) | Funkčné: DRAW mód pridáva steny ťahaním (smer + živá vzdialenosť z X4, alebo manuálne), EDIT mód ťahá rohy |
| Hrúbka steny per stena, výber líca (Dnu/Von/Stred) | Hotové, vrátane výpočtu čistej/hrubej plochy (`FloorPlanMath`) |
| Okná a dvere | Hotové: pridanie na vybranú stenu, domeranie odsadenia aj šírky cez X4, kreslenie v náryse |
| Viac miestností v projekte | Hotové (zoznam miestností, súčet plôch) |
| Bluetooth pripojenie k X4 | Scan + GATT pripojenie na komunitný protokol (distancia). **Sklon zatiaľ nie je zapojený** — pozri nižšie |
| Export PDF | Základný (obrys miestnosti + plochy), zdieľanie cez Android share sheet |
| Export DXF | Zámerne mimo MVP (koncept doc, časť 04) |
| Perzistencia | Jeden JSON súbor v `filesDir` — v poriadku pre pár menších projektov |

## Ďalší krok, ktorý potrebujem od teba: sklon (inklinometer)

`app/ble/DistoUuids.kt` a `DistoBleManager` sú napísané tak, aby sa dala charakteristika pre
sklon dopísať bez väčších zásahov — manager sa už teraz prihlási na odber (`notify`) **všetkých**
characteristics na známej DISTO službe, aj tých, ktoré ešte nevieme dekódovať (prídu ako
`DistoMeasurement.RawNotification` s UUID + surové bajty). Keď cez nRF Connect nájdeš, ktorá
characteristic sa mení pri naklonení X4 (postup je v artefakte "Plán X4", časť 03):

1. Pridaj jej UUID do `DistoUuids.kt` (napr. `CHAR_INCLINATION`).
2. V `DistoBleManager.onCharacteristicChanged` pridaj `when`-vetvu, ktorá ju dekóduje
   (pravdepodobne tiež float32 alebo int16, little-endian — uvidíme z odchytených bajtov) a
   pošle `DistoMeasurement.Inclination(...)`.
3. Zavolaj mi/napíš mi odchytené hodnoty, nech to spolu doladíme.

## Známe zjednodušenia (vedomé, nie chyby)

- `FloorPlanMath.offsetPolygon` robí jednoduchý mitre-join po hranách — pri bežných izbách
  (pravé/tupé uhly) je presný, pri veľmi ostrých rohoch alebo malých výklenkoch by mohol dať
  divný výsledok. Zatiaľ nie je dôvod to riešiť, kým sa appka neotestuje na reálnych pôdorysoch.
- Dvere sa v náryse kreslia ako štvrťkruhový výsek (jednoduchý, nie presne "architektonický"
  symbol) — vizuálne zjednodušenie, dá sa vylepšiť keď bude jasné, či to takto stačí.
- Perzistencia je jeden spoločný JSON súbor — ak by projektov pribudlo veľa, prejsť na
  jeden súbor na projekt alebo na SQLite (Room).
- BLE scan filtruje podľa mena "DISTO" v inzerovanom názve alebo podľa service UUID v
  advertise pakete — nie je isté, ktoré z toho X4 reálne vysiela, kým to neuvidíme naživo.

## Štruktúra projektu

```
plan-x4-android/
├── core/                    # čistý Kotlin/JVM modul — model + geometria, testovateľné bez Androidu
│   └── src/main/kotlin/sk/planx4/core/
│       ├── geometry/        # Point, FloorPlanMath (plocha, obvod, offset podľa hrúbky steny)
│       └── model/           # Project, RoomPlan, Wall, Opening
└── app/                     # Android appka (Kotlin + Jetpack Compose)
    └── src/main/java/sk/planx4/app/
        ├── ble/             # DistoBleManager — scan/connect/GATT/parsovanie
        ├── data/            # ProjectRepository — JSON perzistencia
        ├── export/          # PdfExporter, ExportHelper (zdieľanie)
        └── ui/
            ├── editor/       # Plátno na kreslenie, panely stena/otvor, FloorPlanEditorViewModel
            ├── device/       # Obrazovka párovania s X4
            ├── projectlist/  # Zoznam projektov a miestností
            └── theme/        # Farby/typografia rovnaké ako v koncept artefakte
```

## Odkaz na koncept

Funkčný návrh a wireframy, z ktorých toto vychádza: artefakt "Plán X4" (poslaný skôr v
konverzácii). Rozhodnutia, ktoré tento kód implementuje: kombinovaný spôsob kreslenia (časť 05),
líce steny s výberom strany (časť 06), okná/dvere s domeraním (časť 07), viac miestností v MVP
(časť 04).
