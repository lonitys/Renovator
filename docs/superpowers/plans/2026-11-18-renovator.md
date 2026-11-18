# Renovator (Umbenennung, Bau-Projektion, CurseForge) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stilwerk heißt Renovator; jede Aktion kann als Bau-Projektion stehen bleiben (Geister, Schichten, Rechtsklick-Setzen, Auto-Bauen); ein fertiges CurseForge-Paket liegt bereit.

**Architecture:** Projektionen leben auf dem Server (`projection/` Paket: `Cell`, `CellState`, `Projection`, `ProjectionStore`, `ProjectionOps`), werden einmal an den Besitzer synchronisiert und auf dem Client (`client/projection/`) gegen die Welt ausgewertet, gerendert und bedient. Alle Aktionen laufen weiter über `ChangeSet` → `ServerOps.submit`, das einen `BuildMode` bekommt.

**Tech Stack:** NeoForge 21.1.219 (gepinnt), ModDevGradle, GameTests (`tools/gt.sh`), DevScene-Screenshots, Python-Hilfsskripte in `tools/`.

**Spec:** `docs/superpowers/specs/2026-11-18-renovator-projection-design.md`

---

### Task 1: Umbenennung Stilwerk → Renovator

**Files:** alles unter `src/`, `tools/`, `build.gradle`, `gradle.properties`, `src/main/templates/META-INF/neoforge.mods.toml`; neu `config/Folders.java`-Erweiterung.

- [ ] `git mv src/main/java/com/stilwerk src/main/java/com/renovator`, `git mv src/main/resources/assets/stilwerk …/assets/renovator`, `git mv src/main/resources/data/stilwerk …/data/renovator`; Klassen `Stilwerk.java`, `StilwerkTags.java`, `StilwerkScreen.java`, `StilwerkCommand.java`, `StilwerkGameTests.java` → `Renovator*`.
- [ ] Textersetzung in `src/ tools/ build.gradle gradle.properties src/main/templates`: `com.stilwerk`→`com.renovator`, `Stilwerk`→`Renovator`, `stilwerk`→`renovator` (Achtung: Legacy-Ordnernamen in `Folders`-Aufrufen bewusst als `stilwerk/...` wieder eintragen).
- [ ] `Folders.resolve(Path base, String name, Path... legacy)`: neuer Ordner `base/name`; fehlt er, ersten existierenden Legacy-Ordner verschieben. Aufrufe:
  - Baupläne: `resolve(game/"renovator", "blueprints", game/"stilwerk/blueprints", game/"stilwerk/bauplaene")`
  - Mischungen: `resolve(cfg/"renovator", "mixes", cfg/"stilwerk/mixes", cfg/"stilwerk/mischungen")`
  - Vorlagen: `resolve(cfg/"renovator", "templates", cfg/"stilwerk/templates", cfg/"stilwerk/vorlagen")`
- [ ] `CoreFilterTests.legacyFoldersAreMoved` auf die neue Signatur (alter Ordner in anderem Basisordner) anpassen.
- [ ] `python tools/make_lang.py`, `python tools/make_interior.py`, `python tools/make_empty_structure.py` laufen lassen (erzeugen unter `renovator`).
- [ ] `bash tools/gt.sh` → alle grün; `grep -ri stilwerk src tools build.gradle gradle.properties` zeigt nur Legacy-Pfade.
- [ ] Commit `refactor: rename Stilwerk to Renovator`.

### Task 2: Zellzustand und Vergleich

**Files:** Create `projection/Cell.java`, `projection/CellState.java`; Test `test/ProjectionTests.java`.

- [ ] Test zuerst: `cellStates` – Luft-Zelle mit Ziel Eichentreppe(facing=north) → OPEN; gesetzte Treppe facing=north, shape=outer_left → DONE; facing=east → BLOCKED; Tausch-Zelle (expectedOld Eichenbretter) mit Eichenbrettern → OPEN, mit Fichte-Brettern (Ziel) → DONE, mit Stein → BLOCKED; Zaun mit anderen Verbindungen → DONE.
- [ ] `record Cell(BlockPos pos, BlockState target, BlockState expectedOld, @Nullable CompoundTag blockEntity)`.
- [ ] `CellState { DONE, OPEN, BLOCKED }` mit `static CellState of(BlockState world, Cell c)` und `static boolean matches(BlockState world, BlockState target)` (gleicher Block, gleiche Werte für Eigenschaften mit Namen in `ORIENTATION = Set.of("facing","axis","half","type","part","hinge","face","rotation","attachment","orientation","hanging")`). OPEN: `world.isAir() || world.canBeReplaced()` (und Ziel ist nicht schon da) oder `world.is(expectedOld.getBlock()) && !expectedOld.isAir()`.
- [ ] gt.sh grün, Commit.

### Task 3: Projektion und Speicher

**Files:** Create `projection/Projection.java`, `projection/ProjectionStore.java`; Test in `ProjectionTests`.

- [ ] Test zuerst: `storeRoundTrip` (2 Projektionen mit Tür-Gruppe speichern, Speicher leeren, laden → gleiche Zellen/Gruppen/Label), `storeKeepsEight` (9 anlegen → älteste weg).
- [ ] `Projection(UUID id, String labelKey, String labelArg, ResourceKey<Level> dimension, List<List<Cell>> groups, long created)` mit `static Projection of(ChangeSet set, String labelKey, String labelArg, ResourceKey<Level> dim)` (Gruppen aus `set.groups()`, `expectedOld = change.oldState()`), `toTag/fromTag` (Zustände via `NbtUtils.writeBlockState/readBlockState`), `int cellCount()`, `BoundingBox bounds()`.
- [ ] `ProjectionStore`: `add(server, player, projection)`, `list`, `get(id)`, `remove(id)`, Datei `<welt>/renovator/projections/<uuid>.nbt`, max 8, synchronized, Test-Override für den Pfad wie `UndoHistory`.
- [ ] gt.sh grün, Commit.

### Task 4: BuildMode, Bau-Dialog, Projektion anlegen

**Files:** Create `net/BuildMode.java` (enum ASK, PARTIAL, PROJECTION + StreamCodec); Modify `SwapRequest`, `PlaceRequest`, `DetailRequest`, `InteriorRequest` (`boolean partialOk` → `BuildMode mode`), `SmallPayloads.ConfirmRequest(int id, ConfirmAction action)` (CANCEL, BUILD, PROJECT), `SmallPayloads.MaterialReport` (+`complete` bleibt), `ServerOps.submit/confirm`, Client-Sender, Tests.

- [ ] Tests zuerst (`ProjectionOpsTests`): Kreativ + `PROJECTION` → nichts gebaut, eine Projektion mit allen Zellen; Survival + `ASK` mit vollem Material → Pending + Bericht `complete=true`, nichts gebaut; `confirm(PROJECT)` → Projektion; `confirm(BUILD)` → gebaut; bestehende ServerOps-/DetailOps-/Interior-Tests auf neue Signaturen.
- [ ] `submit(p, level, set, id, mode, centre, labelKey, labelArg)`: `PROJECTION` → `ProjectionOps.create`; Kreativ+ASK/PARTIAL → bauen; Survival: `PARTIAL` → bauen; `ASK` → immer Pending + Bericht.
- [ ] Labels: Tausch `renovator.projection.label.swap`, Bauplan `…label.blueprint` + Name, Details `…label.details`, Innen `…label.interior` + Raumtyp.
- [ ] Client: Enter sendet `ASK`, Shift+Enter `PROJECTION` (Preview, Placement, Details, Interior); `MaterialReportScreen`: Knöpfe „Automatisch bauen“/„So weit es geht“, „Als Projektion“, „Abbrechen“.
- [ ] gt.sh grün, Commit.

### Task 5: Server-Operationen und Sync

**Files:** Create `projection/ProjectionOps.java`, `net/ProjectionPayloads.java` (`ProjectionSync`, `ProjectionRemoved`, `ProjectionPlace`, `ProjectionBuild`, `ProjectionAction`); Modify `Net`, `ServerEvents` (Login/Dimensionswechsel → sync).

- [ ] Tests zuerst: `placeByHand` (Survival, Treppe im Inventar → exakter Zustand gesetzt, Item verbraucht; falsches Item → nichts; zu weit → nichts), `placeDoorGroup` (beide Hälften), `swapByHand` (alter Block kommt ins Inventar), `autoBuildLayer` (nur y-Schicht, fehlendes Material bleibt offen), `autoBuildAll`, `removedWhenDone`, `deleteAction`.
- [ ] `ProjectionOps.create(p, level, set, labelKey, labelArg)` → Store + Sync + Meldung.
- [ ] `place(p, ProjectionPlace)`: Gruppe der Zelle; Dimension, Reichweite `p.canInteractWithBlock(pos, 1.0)`; frische Changes (`oldState` = Welt) für Zellen ≠ DONE, alle OPEN sonst Abbruch; Gate Survival `new MaterialLedger(p, pos, (EmcBank) null)`, Kreativ `CreativeGate`; `Applier.applyNow`; Setz-Geräusch; danach `checkDone`.
- [ ] `build(p, ProjectionBuild)`: offene Gruppen im Umfang (`LAYER` y, `UP_TO` ≤ y, `ALL`) → frisches ChangeSet → `Applier.submit` mit `MaterialLedger(p, centre, useProjectE)` bzw. Kreativ; onDone → Undo + `checkDone`.
- [ ] `action`: `DELETE` → entfernen + `ProjectionRemoved`; `REMOVE_IF_DONE` → `checkDone`.
- [ ] Sync: Zellen in Stücken ≤ 50 000 (`ProjectionSync(id, label…, dim, part, parts, cells)`; Zustände per `Block.getId`), Gruppen als Gruppengrößen.
- [ ] gt.sh grün, Commit.

### Task 6: Client-Speicher, Schichten, Fortschritt

**Files:** Create `client/projection/ClientProjections.java` (Projektionen, Fokus, sichtbar, Schicht, Ansicht, Stil, Zustands-Scan ≈5 000 Zellen/Tick, Auto-Weiter bei fertiger Schicht, `REMOVE_IF_DONE` bei 100 %).
- [ ] Sync-Handler setzt Teile zusammen; Dimensionswechsel/Logout leert.
- [ ] Ansicht-Filter: ALL / SINGLE / UP_TO bezogen auf die Fokus-Projektion; andere Projektionen immer ALL.
- [ ] `ClientConfig`: `projectionStyle` (blue/real), `projectionView`.

### Task 7: Darstellung

**Files:** Modify `client/GhostMesh.java` (Tönung r,g,b; `capture`-Variante für Puls); Create `client/projection/ProjectionRenderer.java`, `client/projection/ProjectionHud.java`; Modify `ClientEvents` (Render-Hook, GUI-Layer).
- [ ] Blau: Tönung (0.55,0.75,1.0), Alpha 0.55; blockiert: (1.0,0.35,0.35); Echt: Alpha 0.4, Puls 0.85–1.0 über 24 Ticks, Eckpunkte einmal erfassen, pro Tick skaliert neu hochladen (DYNAMIC), > 400 000 Eckpunkte statisch.
- [ ] Infozeile (Schicht, Fortschritt, fehlende Items der Schicht gegen Client-Inventar, Top 3).

### Task 8: Bedienung

**Files:** Modify `Keys` (LAYER_UP PgUp, LAYER_DOWN PgDn, VIEW End, STYLE Home), `ClientEvents` (Rechtsklick/Mittelklick über `InputEvent.InteractionKeyMappingTriggered`), `WandMode` (+PROJECTIONS, Icon `Items.LIGHT_BLUE_STAINED_GLASS`), `WandClient`; Create `client/projection/ProjectionPicker.java` (Voxel-Strahl), `client/screen/ProjectionScreen.java`.
- [ ] Rechtsklick mit BlockItem, nicht schleichend: Strahl (DDA bis `player.blockInteractionRange()`), erste sichtbare OPEN-Zelle vor einem echten Hindernis → passendes Item: `ProjectionPlace` senden, Arm schwingen, Klick abbrechen; sonst „Hier gehört: X“, abbrechen.
- [ ] Mittelklick auf Geist: Hotbar-Slot wählen / `handlePickItem(slot)` / Kreativ `handleCreativeModeItemAdd`.
- [ ] Liste: Zeilen mit Name, %, Anzeigen, Fokus, Schicht bauen, Bis hier, Alles, Löschen; Stil/Ansicht-Knöpfe.
- [ ] Lang-Einträge (de/en) in `tools/make_lang.py`.

### Task 9: DevScene und Sichtprüfung

- [ ] DevScene: Bauplan als Projektion (Kreativ, Shift-Weg), Screenshots Stil Blau, Echt, Schicht-Ansicht, Rechtsklick-Setzen einer Treppe (CHECK richtige Ausrichtung), Auto-Bauen Schicht (CHECK), Liste.
- [ ] Lauf mit Möbel-Mods, Screenshots ansehen.

### Task 10: CurseForge-Paket

- [ ] `gradle.properties`/`build.gradle`: Jar `renovator-neoforge-1.21.1-1.0.0.jar`.
- [ ] `neoforge.mods.toml`: `license="All Rights Reserved"`, `logoFile="renovator_logo.png"`, Beschreibung, optionale Abhängigkeiten (`projecte`, `mcwfurnitures`, `refurbished_furniture`, type="optional").
- [ ] `tools/make_logo.py`: Logo 400×400 + `pack.png`/Mod-Logo aus der Stab-Textur auf Blaupausen-Raster.
- [ ] `release/curseforge/`: `description.md`, `summary.txt`, `changelog.md`, `categories.txt`, `UPLOAD.md`, Screenshots (englisch).

### Task 11: Abschluss

- [ ] gt.sh ohne und mit Mods grün; Release-Jar bauen; isolierter Server-Start (`C:\tmp\stilwerk_server`, altes Stilwerk-Jar entfernen).
- [ ] Memory-Notiz aktualisieren (Name, Pfade, Projektion, Release-Ordner).
- [ ] Commit.
