# Stilwerk – Design (Stufe 1 + 2)

Stand: 2026-11-18 · Status: vom Nutzer freigegeben („alles passt, umsetzen")

## Ziel

Eine NeoForge-Mod für Minecraft 1.21.1, mit der man Gebäude markiert, ihren Stil per
Block-Familien tauscht, monotone Flächen mit Mischpaletten detailreicher macht und Gebäude als
Baupläne kopiert, speichert, importiert und (auch in anderem Stil) wieder einsetzt.
Funktioniert im Kreativ (frei) und im Survival (kostet Material, optional EMC von ProjectE).

Gesamtvision in vier Stufen; **dieses Dokument spezifiziert Stufe 1 und 2**:

1. **Basis** – Werkzeug, Markieren, Vorschau, Anwenden, Rückgängig, Survival-Kosten
2. **Stil-Tausch, Mischpaletten, Bauplan-Bibliothek mit Import**
3. *(später, eigene Spec)* **Detail-Assistent** – Vorschläge für Treppen-Geländer, Laternen,
   Dachüberstände, Fensterrahmen …; Anzeige als „Rechtschreibprüfung" (alle Stellen
   durchgehen) **und** gezielt per Werkzeug (3 Varianten für eine Stelle)
4. *(später, eigene Spec)* **Innenausstatter** – Räume erkennen, Einrichtungen vorschlagen,
   nutzt Macaw's Furniture / Refurbished Furniture wenn installiert

Stufe 3 und 4 bauen auf den Bausteinen dieser Stufe auf (Auswahl, Vorschau, ChangeSet,
Applier, Undo, Bibliothek). Die Schnittstellen hier sind so geschnitten, dass sie das tragen.

## Umgebung

- Minecraft 1.21.1, NeoForge **21.1.219** (Version der Nutzer-Instanz „NeoForge (1)"; nicht höher
  bauen, sonst lädt die Jar dort nicht). ModDevGradle wie bei Slab Mix.
- Mod-ID `stilwerk`, Paket `com.stilwerk`, Anzeigename „Stilwerk".
- Läuft auf Client **und** Server (Freunde-Server bei WiseHosting).
- Optional: **ProjectE** (Instanz hat PE1.1.0), Anbindung per Reflection wie in Celestial Ascent
  (`compat/ProjectECompat`), keine Compile-Abhängigkeit.
- Im Pack vorhanden und berücksichtigt: Rechiseled (Varianten für Mischpaletten),
  Building Gadgets 2 (kein Konflikt, eigenes Item), keine Claim-Mod.

## Abschnitt 1: Basis

### Werkzeug
- Ein Item: **Architektenstab** (`stilwerk:architect_wand`), günstiges Rezept
  (Amethystscherbe + Goldbarren + Stock).
- Modus-Rad per Taste (einstellbar; Standard so gewählt, dass er im Pack frei ist):
  *Markieren · Stil-Tausch · Bauplan* (in Stufe 3/4 kommen *Details* und *Innen* dazu).
- Alle Tasten des Stabs sind nur aktiv, solange er in der Hand ist.

### Markieren
- **Quader:** Rechtsklick setzt abwechselnd Ecke 1 / Ecke 2. Strg + Mausrad schiebt die
  angeschaute Seite raus/rein. Anzeige: Rahmen, Maße, Blockanzahl. Schleichen + Rechtsklick in
  die Luft löscht die Auswahl.
- **Automatisch:** Schleichen + Rechtsklick auf einen Block → Flood-Fill über zusammenhängende
  *gebaute* Blöcke (26er-Nachbarschaft), Radius einstellbar (Standard 48), Obergrenze =
  Blocklimit. Ergebnis ist eine Blockmenge (mit Bounding-Box-Rahmen und hervorgehobenen Blöcken).
- **Natur vs. gebaut:**
  - Natur = Block-Tag `stilwerk:natural` (Erde, Gras, Sand, Kies, Ton, Grundgestein-Tags von
    Oberwelt/Nether/End, Erze `#c:ores`, Pflanzen, Flüssigkeiten, Schnee, Eis …).
  - Blätter mit `persistent=false` sind Natur; mit `persistent=true` (selbst gesetzt) gebaut.
  - Stämme: ein zusammenhängender Stamm-Cluster, der an Blätter mit `persistent=false` grenzt,
    ist ein Baum → Natur.
  - Im Quader-Modus wird Natur standardmäßig ausgelassen; Schalter „Natur einschließen"
    (nur Kreativ, siehe Survival-Regeln).

### Geschützte Blöcke (bestehende werden nie verändert, in keinem Modus)
Gilt für Blöcke, die schon in der Welt stehen (Tausch, Überschreiben). Ein Bauplan darf solche
Blöcke neu *setzen* (im Survival leer, siehe Abschnitt 3).
- Unzerstörbare Blöcke (`destroySpeed < 0`: Grundgestein, Barrieren, Befehlsblöcke …).
- Alle Blöcke mit Block-Entity **außer** einer Whitelist (Schilder, Hängeschilder).
  Damit sind Kisten, Öfen, Spawner, Betten usw. ausgeschlossen.
- Block-Tag `stilwerk:protected` für Zusätze per Datapack.

### Vorschau
- Geplante Änderungen werden als halbdurchsichtige Blöcke gerendert; Taste wechselt
  Vorher/Nachher. **Enter** übernimmt, **Esc** bricht ab.
- Client und Server rechnen **denselben Planer-Code** mit demselben Seed (deterministisch).
  Der Client rechnet die Vorschau aus seiner Welt, der Server rechnet beim Anwenden neu aus der
  Server-Welt. Es werden nur Auswahl + Zuordnung + Seed übertragen, keine Block-Listen.

### Anwenden
- Ergebnis jedes Planers ist ein **ChangeSet**: Liste von (Position, alter Zustand, neuer
  Zustand, optional Block-Entity-Daten), gruppiert: mehrteilige Blöcke (Tür oben/unten) bilden
  eine Gruppe und werden immer im selben Tick gesetzt.
- Der **Applier** setzt pro Server-Tick ein Budget (Standard 4000 Blöcke, einstellbar), zeigt
  dem Spieler einen Fortschrittsbalken. Setzen ohne Nachbar-Formupdates; danach ein zweiter Durchgang,
  der Verbindungsformen (Zäune, Mauern, Scheiben) der geänderten Blöcke und ihrer direkten
  Nachbarn neu berechnet.
- Erhalten bleiben: Ausrichtung, Hälfte, Treppenform, Achse, Wasser (`waterlogged`), Tür-Scharnier
  und -Offen-Zustand, Schildertexte.

### Survival-Kosten
- Pro gesetztem Block wird sein Item benötigt (Doppelstufe = 2 Stufen; obere Tür-/Bett-Hälfte = 0).
- Quelle: **zuerst Inventar, dann ProjectE-EMC** (nur wenn ProjectE installiert ist und der
  Spieler das Item im Transmutationstisch gelernt hat; Kosten = EMC-Wert des Items).
- Ausgebaute Blöcke: zurück ins Inventar; wenn voll → mit ProjectE in EMC umwandeln (nur Items
  mit EMC-Wert), sonst vor dem Spieler droppen.
- Vor dem Anwenden: **Materialliste** (braucht / hast / fehlt). Fehlt etwas: Wahl
  „Abbrechen" oder „So weit es reicht" (setzt nur Gruppen, die bezahlt werden können).
- **Survival-Regeln gegen Missbrauch:** Natur wird nie verändert (kein Behutsamkeit-Ersatz für Erze);
  geschützte Blöcke nie; Bauplan-Einsetzen nur in Luft/ersetzbare Blöcke (`canBeReplaced`);
  Block-Entity-Daten werden außer Schildertexten nicht übernommen.

### Rückgängig
- Pro Spieler die letzten **20 Aktionen** (einstellbar), gespeichert im Weltordner
  `stilwerk/undo/<uuid>.nbt`, überlebt Neustarts. Obergrenze Gesamtgröße pro Spieler (Standard
  1 000 000 Blöcke), älteste fallen raus.
- Rückgängig = Tausch in umgekehrter Richtung, im Survival mit denselben Kostenregeln.
- Position wird nur zurückgesetzt, wenn dort noch exakt der von uns gesetzte Zustand steht.
- Befehl/Taste „Wiederholen" ist **nicht** Teil dieser Stufe.

### Grenzen (Server-Config)
- Max. Blöcke pro Aktion: Survival 20 000, Kreativ 200 000.
- Max. Bauplan-Upload: 4 MB komprimiert.

## Abschnitt 2: Stil-Tausch und Mischpaletten

### Familien
- **Familie** = Material mit seinen Formen. Formen (`Shape`): FULL, STAIRS, SLAB, WALL, FENCE,
  FENCE_GATE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE, LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD,
  SIGN, WALL_SIGN, HANGING_SIGN, WALL_HANGING_SIGN, PANE.
- Erkennung (auch Modblöcke), in dieser Reihenfolge:
  1. **Overrides** per Datapack `data/<ns>/stilwerk/families/*.json` (feste Zuordnung Block → Familie/Form).
  2. **Holzarten**: registrierte `WoodType`s / `BlockSetType`s + Namensschema
     (`<holz>_planks`, `_log`, `_wood`, `stripped_<holz>_log` …).
  3. **Form aus der Blockklasse** (`StairBlock` → STAIRS, `SlabBlock` → SLAB, `WallBlock` → WALL …),
     **Material aus dem Namen** (Suffix entfernen; Plural-Regel `brick_stairs` ↔ `bricks`).
- Familien werden beim Laden der Welt/Datapacks auf Server und Client gleich aufgebaut.

### Form-Ersatz
- Hat die Zielfamilie eine Form nicht, gilt eine feste Ersatz-Tabelle:
  FENCE ↔ WALL, LOG/WOOD/STRIPPED_* → FULL, sonst **bleibt der Block unverändert**.
- Die Vorschau listet Ersatz und Ausnahmen („12 Türen bleiben Akazie – Steinziegel hat keine Türen").

### Zustände übertragen
- Alle Eigenschaften, die Quelle und Ziel gleichnamig und mit gültigem Wert haben, werden kopiert.
- Verbindungsformen (Zäune/Mauern/Scheiben) werden nach dem Setzen neu berechnet (s. Applier).

### Menü (Variante A)
- Links: gefundene Familien im Bereich (Symbol, Name, Anzahl). Daneben: Zielwahl per Suchfeld über
  alle Familien inkl. Modblöcke, oder *Mischung …*, oder *nicht tauschen*.
- Familie aufklappbar: einzelne Formen getrennt zuordnen.
- Unten: *Vorschau · Anwenden · Als Vorlage speichern*; im Survival die Materialliste.

### Vorlagen (Variante C)
- Vorlage = Liste von Zuordnungen (Familie → Ziel-Familie | Mischung | nicht tauschen).
- Eingebaut (~10): u. a. *Japanisch → Nordisch*, *Holz abdunkeln*, *Holz aufhellen*,
  *Stein → Tiefenschiefer*, *Wüste → Schnee*. Holz-Helligkeitsreihe für Abdunkeln/Aufhellen
  (je ein Schritt): Birke → Bambus → Eiche → Dschungel → Akazie → Kirsche → Fichte → Mangrove →
  Schwarzeiche. Karmesin/Wirr (Nether) bleiben unverändert.
- Eigene Vorlagen: `config/stilwerk/vorlagen/*.json` (teilbar). Server-weite per Datapack
  `data/<ns>/stilwerk/templates/*.json`.
- Eine Vorlage tauscht nur Familien, die im Bereich vorkommen.

### Mischpaletten
- Mischung = Liste (Block-Familie/Variante, Gewicht) + **Muster**:
  - *Gestreut* – gewichteter Zufall ohne Klumpen
  - *Verlauf* – Gewichte ändern sich entlang Höhe (Standard) oder einer Achse
  - *Flecken* – Rauschen (Simplex), natürlich wirkende Flecken
  - *Kanten* – Blöcke an Kanten/Ecken der Fläche bekommen einen eigenen Eintrag
- Formen werden mitgemischt, wo die Variante die Form hat (bemooste Steinziegeltreppe ja,
  rissige nicht → bleibt Basis).
- Auswahl pro Block ist eine reine Funktion aus (Seed, Position) → deterministisch auf Client/Server.
- Eingebaut: *Alt & bemoost*, *Burgmauer*, *Verwittertes Holz*, *Tiefenschiefer rau* u. a.;
  mit Rechiseled zusätzliche Mischungen mit dessen Varianten (nur geladen, wenn installiert).
- Editor: Blöcke wählen, Anteile per Schieberegler, Muster wählen, Live-Vorschau an der Auswahl.
  Speichern als `config/stilwerk/mischungen/*.json`.

## Abschnitt 3: Bauplan-Bibliothek, Kopieren, Import

### Kopieren
- Modus *Bauplan* mit aktiver Auswahl → „Kopieren": erfasst alle Nicht-Luft-Blöcke der Auswahl
  (Natur optional weglassen). Block-Entity-Daten: im Kreativ vollständig, im Survival nur
  Schildertexte (Behälter werden leer gesetzt). Entities (Rahmen, Rüstungsständer, Bilder) werden in
  dieser Stufe **nicht** kopiert.
- Kopie liegt im persönlichen Zwischenspeicher und kann sofort eingesetzt oder benannt
  gespeichert werden.

### Bibliothek
- Ordner auf dem Client: `<Spielordner>/stilwerk/bauplaene/` – funktioniert über alle Welten
  und Server hinweg, Dateien sind direkt teilbar.
- Eigenes Format = **Vanilla-Strukturdatei (`.nbt`)** plus Zusatz-Compound `stilwerk`
  (Name, Autor, Erstellt, Format-Version). Damit auch mit Strukturblöcken kompatibel.
- Bildschirm: Liste mit Name, Maße, Blockanzahl, **Draufsicht-Vorschaubild** (aus den
  `MapColor`s der obersten Blöcke berechnet), Suchfeld, Umbenennen, Löschen, „Ordner öffnen".

### Import
- Dateien einfach in den Ordner legen oder auf den Bibliotheks-Bildschirm ziehen (Drag & Drop).
- Unterstützt: **`.nbt`** (Vanilla-Struktur), **`.schem`** (Sponge Schematic v2 und v3 /
  WorldEdit), **`.litematic`** (Litematica; mehrere Regionen werden zu einem Bauplan vereint).
- Ältere Dateien werden über Minecrafts DataFixer auf 1.21.1 gehoben (anhand ihrer DataVersion).
- Unbekannte Blöcke (Mod nicht installiert) → Luft, mit Warnung „unbekannt: modid:block ×123".

### Einsetzen
- Bauplan wählen → Geister-Vorschau folgt dem Fadenkreuz (Unterseite auf die angeschaute
  Blockfläche). Tasten: Drehen, Spiegeln, Höhe (Strg + Mausrad). Enter setzt, Esc bricht ab.
- „Im Stil anpassen": öffnet das Stil-Tausch-Menü mit den Familien des Bauplans; Zuordnung und
  Mischungen werden vor dem Einsetzen auf den Bauplan angewendet.
- Kreativ: Schalter „Bereich überschreiben" (auch Luft des Bauplans wird gesetzt). Survival: nur
  in Luft/ersetzbare Blöcke; blockierte Stellen werden gelistet und übersprungen. Ersetzbare
  Blöcke (Gras, Schneeschicht) verschwinden dabei ohne Rückgabe wie beim normalen Bauen;
  Rückgängig stellt dort Luft her.
- Der Client lädt den Bauplan in Teilen (32 KB) zum Server hoch; der Server prüft Größe und
  Limits und merkt ihn sich pro Sitzung anhand eines Hashs.

## Architektur

Pakete unter `com.stilwerk` (jede Einheit eine klare Aufgabe):

| Paket | Inhalt |
|---|---|
| `core` | `Selection` (Quader/Blockmenge), `NatureFilter`, `Protection`, `ChangeSet`, `Applier` (Tick-Budget, zwei Durchgänge), `UndoHistory` (SavedData-Dateien), `Costs` (Kostenregel pro Zustand), `MaterialLedger` (Bilanz + Bezahlen aus Inventar/EMC) |
| `family` | `Shape`, `Family`, `FamilyIndex` (Aufbau + Lookup), `FamilyOverrides` (JSON-Loader), `StateTransfer`, `ShapeFallback` |
| `swap` | `SwapMapping` (Familie/Form → Ziel), `SwapPlanner` (Auswahl/Bauplan + Mapping + Seed → ChangeSet), `Template` + `Templates` (eingebaut/Config/Datapack) |
| `mix` | `MixPalette`, `MixPattern`, `Noise`, `Mixes` (eingebaut/Config) |
| `blueprint` | `Blueprint` (Größe, Palette, Blöcke, BE-Daten, Meta), `Transform` (Drehen/Spiegeln), `BlueprintIO` (eigenes `.nbt`), `importer/` (`VanillaNbtImporter`, `SpongeSchemImporter`, `LitematicImporter`, `BlockStateUpgrader`), `BlueprintPlanner` |
| `compat` | `ProjectECompat` (Reflection: EMC lesen/setzen, Wissen prüfen, Wert eines Items) |
| `net` | Payloads: `SelectionSync`, `SwapRequest`, `PlaceRequest`, `BlueprintChunk`, `ApplyProgress`, `MaterialReport`, `UndoRequest` |
| `item` | `ArchitectWandItem`, `WandMode` |
| `client` | Tasten, `ModeWheelScreen`, `SelectionRenderer`, `GhostRenderer`, `SwapScreen`, `MixEditorScreen`, `LibraryScreen`, `PlacementController`, `ClientLibrary`, HUD |
| `config` | `ServerConfig`, `ClientConfig` |
| `test` | GameTests, Fixtures, Dev-Szene (nicht in der Release-Jar) |

### Datenfluss Stil-Tausch
1. Client: Auswahl + Mapping + Seed → `SwapPlanner` (Client-Welt) → ChangeSet → `GhostRenderer`.
2. Enter → `SwapRequest(auswahl, mapping, seed, optionen)` an Server.
3. Server: Rechte/Limits prüfen → `SwapPlanner` (Server-Welt) → ChangeSet → `MaterialLedger`
   (Survival) → bei Fehlbestand `MaterialReport` zurück, Spieler entscheidet → `Applier` →
   `UndoHistory`.

### Datenfluss Bauplan
1. Client: Bauplan (Bibliothek/Import) → `Transform` → optional `SwapPlanner` auf Bauplan →
   `BlueprintPlanner` (Ziel-Position) → Vorschau.
2. Enter → Bauplan-Hash bekannt? sonst `BlueprintChunk`-Upload → `PlaceRequest(hash, position,
   drehung, spiegel, mapping, seed, optionen)`.
3. Server: gleiche Kette wie oben.

## Fehlerbehandlung
- Server rechnet immer selbst; der Client ist nur Vorschau. Abweichungen (Welt hat sich geändert)
  sind erlaubt – es gilt das Server-Ergebnis.
- Ungültige Anfragen (Limit überschritten, Auswahl in ungeladenen Chunks, Bauplan zu groß,
  unbekannter Hash) → Fehlermeldung im Chat (übersetzbar), keine Änderung.
- Defekte/unbekannte Import-Dateien → Eintrag in der Bibliothek als „defekt" mit Grund; Spiel läuft weiter.
- ProjectE-Reflection fehlgeschlagen → EMC-Quelle aus, Warnung im Log, Rest funktioniert.
- Spieler loggt während des Anwendens aus → Applier läuft zu Ende (Kosten waren schon bezahlt),
  Undo-Eintrag wird gespeichert.

## Tests
- **GameTests** (`./gradlew runGameTestServer`), u. a.:
  Familien-Erkennung (alle Vanilla-Hölzer/Steine), Form-Ersatz, Zustandsübertragung (Treppen,
  Doppelstufen, Türen, `waterlogged`), Natur-Filter (natürlicher Baum vs. gebaute Stämme),
  geschützte Blöcke, Tausch + Undo, Undo überspringt veränderte Blöcke, Survival-Kosten
  (abziehen, zurückgeben, Teilanwendung), Mischungs-Determinismus und Verteilung, Kopieren →
  Einsetzen mit Drehen/Spiegeln, Survival-Einsetzen nur in Luft, BE-Whitelist,
  Import von `.nbt`/`.schem`/`.litematic`-Fixtures (aus `tools/make_fixtures.py`, unabhängig vom
  Java-Code nach Formatbeschreibung erzeugt).
- **ProjectE-Tests** laufen, wenn die ProjectE-Jar per `-PwithProjectE=<jar>` im Dev-Runtime liegt.
- **Client-Check** wie bei Slab Mix: Dev-Szene per quickPlay, automatisiert Auswahl, Menüs,
  Vorschau, Bibliothek; Screenshots in `run/screenshots/stilwerk_*.png`, „CHECK … OK"-Logzeilen.
- Abschluss: Release-Jar in einer echten Instanz mit dem Pack des Nutzers starten (Konflikte prüfen).

## Nicht in dieser Stufe
- Detail-Assistent (Stufe 3), Innenausstatter (Stufe 4).
- Entities in Bauplänen, Wiederholen (Redo), Claim-Mod-Integration, Stil-Übernahme von einem
  zweiten Gebäude (Variante D).
