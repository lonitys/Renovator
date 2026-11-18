# Renovator – Umbenennung, Bau-Projektion, CurseForge-Paket

Stand 2026-11-18, vom Nutzer freigegeben („passt“). Baut auf `2026-11-18-stilwerk-design.md` und `…-stage3-4-design.md` auf.

## 1. Umbenennung Stilwerk → Renovator

- Mod-ID `renovator`, Anzeigename „Renovator“, Paket `com.renovator`, Hauptklasse `Renovator`, weitere `Stilwerk*`-Klassen → `Renovator*`.
- Alle Namensräume: `assets/renovator`, `data/renovator`, Übersetzungsschlüssel `renovator.*`, Tag `renovator:natural`, Mischungs-/Vorlagen-IDs `renovator:*`, Befehl `/renovator`, Konfigdateien `renovator-client.toml` / `renovator-server.toml`, Weltdaten `<welt>/renovator/…`, Systemeigenschaften `renovator.devScene|gallery|probe`.
- Der Stab bleibt „Architect's Wand“ (`renovator:architect_wand`).
- Ordner: `<spiel>/renovator/blueprints`, `config/renovator/mixes`, `config/renovator/templates`. **Migration:** existiert der neue Ordner nicht, wird der erste vorhandene alte verschoben – `stilwerk/bauplaene`, `stilwerk/blueprints` bzw. `config/stilwerk/mischungen|mixes`, `config/stilwerk/vorlagen|templates` (der Nutzer hat in „Greatness Upgrade 1.21.1“ bereits Baupläne und Schematics gespeichert). Alte Undo-Daten und alte `.toml` werden nicht übernommen.
- Das Projektverzeichnis `C:\modwork\stilwerk` bleibt (offener Editor-Arbeitsbereich).

## 2. Bau-Projektion

### Idee
Jede Aktion (Stil-Tausch, Bauplan, Details, Innenausstatter) endet in einer Änderungsliste (`ChangeSet`). Statt sie sofort anzuwenden, kann sie als **Projektion** stehen bleiben: Geisterblöcke zeigen, was wohin gehört; der Spieler baut von Hand (Rechtsklick mit dem passenden Block setzt ihn exakt), Schicht für Schicht, oder lässt Schichten / alles automatisch bauen (Inventar, dann ProjectE-EMC).

### Ansatz (A): Projektion auf dem Server
Der Server speichert die Projektionen pro Spieler; der Client bekommt sie einmal beim Anlegen/Login und berechnet den Fortschritt selbst aus der Welt. Vorteile: exakte Ausrichtung beim Setzen, Multiplayer, übersteht Neustarts. (Verworfen: nur Client wie Litematica – Vanilla rät die Ausrichtung.)

### Ablauf
- **Survival:** Beim Bestätigen erscheint immer der Bau-Dialog (bisheriger Material-Bericht) mit Bedarf/Inventar/EMC und den Knöpfen **Automatisch bauen** (alles da) bzw. **So weit es geht** (fehlt etwas), **Als Projektion**, **Abbrechen**.
- **Kreativ:** Enter baut sofort wie bisher; **Shift+Enter** legt eine Projektion an.
- Anfragen tragen statt `partialOk` einen `BuildMode` (`ASK`, `PARTIAL`, `PROJECTION`); `ConfirmRequest` trägt eine Aktion (`CANCEL`, `BUILD`, `PROJECT`).

### Datenmodell (Server)
- `Projection(UUID id, String labelKey, String labelArg, ResourceKey<Level> dimension, List<List<Cell>> groups, long created)`; Gruppen wie im `ChangeSet` (Tür, Bett: zusammen setzen).
- `Cell(BlockPos pos, BlockState target, BlockState expectedOld, @Nullable CompoundTag blockEntity)`.
- Zustand einer Zelle aus der aktuellen Welt:
  - **fertig**: gleicher Block wie `target` und gleiche Ausrichtungs-Eigenschaften (Namen `facing`, `axis`, `half`, `type`, `part`, `hinge`, `face`, `rotation`, `attachment`, `orientation`, `hanging`); Form/Verbindungen/`waterlogged`/`open`/`powered` usw. werden ignoriert.
  - **offen**: Zelle frei (Luft/ersetzbar) oder – beim Tausch – noch der erwartete alte Block (gleicher Block wie `expectedOld`).
  - **blockiert**: etwas anderes steht dort (rot markiert).
- `ProjectionStore`: pro Spieler bis zu 8 Projektionen (älteste fällt weg), gespeichert in `<welt>/renovator/projections/<uuid>.nbt`, Sync an den Besitzer bei Login/Dimensionswechsel/Änderung. Nur der Besitzer sieht sie.
- Ist eine Projektion komplett fertig, wird sie entfernt („Projektion fertig“). Der Client meldet 100 % (`REMOVE_IF_DONE`), der Server prüft nach und entfernt; ebenso nach jedem Setzen/Auto-Bauen.

### Netzwerk
- Server → Client: `ProjectionSync` (eine Projektion vollständig, Zellen als Position + Zustands-ID, bei Bedarf in Stücken ≤ 50 000 Zellen), `ProjectionRemoved(id)`.
- Client → Server: `ProjectionPlace(id, pos)`, `ProjectionBuild(id, scope, y)` mit `scope` ∈ `LAYER`, `UP_TO`, `ALL`, `ProjectionAction(id, DELETE | REMOVE_IF_DONE)`.

### Von Hand setzen
- Rechtsklick mit einem Block-Item: Strahl vom Auge bis zur Interaktionsreichweite durch die **sichtbaren offenen** Geisterzellen; trifft er eine, bevor ein echter Block (außer dem erwarteten alten Block der Zelle) im Weg ist, übernimmt Renovator den Klick.
  - Passendes Item (eines der Items, die die Gruppe kostet): `ProjectionPlace` → Server prüft Reichweite und Zustand, baut die Gruppe mit frischen alten Zuständen über `Applier.applyNow` mit einer Inventar-Kasse ohne EMC (Survival) bzw. Kreativ-Kasse; beim Tausch kommt der alte Block ins Inventar; Setz-Geräusch, Arm schwingen. Kein Undo-Eintrag (wie normales Bauen).
  - Falsches Item: Aktionsleiste „Hier gehört: <Block>“, Klick abgefangen. **Schleichen** = normales Vanilla-Verhalten.
- Mittelklick (Block auswählen) auf einen Geist: Survival – passenden Stapel aus der Hotbar wählen bzw. aus dem Inventar in die Hand holen (Vanilla-Pick-Paket); Kreativ – Item in die Hand.
- Normales Bauen zählt mit: passt der Block, ist die Zelle fertig.

### Automatisch bauen
`ProjectionBuild`: Server bildet aus den offenen Gruppen im Umfang (Schicht y / bis y / alles) ein frisches `ChangeSet` und lässt es über den `Applier` laufen – Survival mit `MaterialLedger` (Inventar, dann EMC), was nicht bezahlbar ist, wird übersprungen und bleibt Geist; Kreativ sofort. Jeder Lauf ist ein Undo-Schritt.

### Anzeige (Client)
- **Stil „Blau“** (Standard): Geister bläulich getönt (Farbe × (0.55, 0.75, 1.0)), Alpha 0.55.
- **Stil „Echt“**: echte Texturen, Alpha 0.4, pulsierend 85 %–100 % Größe (Zyklus ~1,2 s). Umsetzung ohne eigenen Shader (Iris-sicher): die Eckpunkte werden einmal erfasst, pro Puls-Schritt zur Blockmitte skaliert neu hochgeladen; über 400 000 Eckpunkten ohne Puls.
- **Blockierte** Zellen: Ziel-Geist rot getönt.
- **Schichten** = y-Ebenen der fokussierten Projektion. Ansicht: **alle**, **nur diese Schicht**, **bis hierher**. Ist die aktuelle Schicht fertig (Ansicht „nur diese“/„bis hierher“), springt sie zur nächsten offenen.
- **Tasten** (umbelegbar): Bild↑/Bild↓ Schicht, Ende Ansicht, Pos1 Stil. Wirken ohne Stab in der Hand, solange eine Projektion fokussiert ist.
- **Infozeile** unten: „Projektion: <Name> · Schicht 3/12 (y 64) · 45/120 · fehlt: 12× Eichenbretter, 4× Glas“ (fehlend = offene Zellen der Schicht minus Inventar).
- Fortschritt: der Client prüft die Zellen rundum (≈ 5 000 pro Tick) und baut die Geister bei Änderung neu.

### Stab-Modus „Projektionen“
Neuer Modus im Rad. Rechtsklick öffnet die Liste: Name, Fortschritt %, Knöpfe Anzeigen/Ausblenden, Fokus, Diese Schicht bauen, Bis hier bauen, Alles bauen, Löschen; dazu Stil und Ansicht.

### Tests
GameTests für: Projektion aus jeder Aktion anlegen; Zellzustände (fertig/offen/blockiert, Ausrichtungs-Vergleich); Von-Hand-Setzen (richtiges/falsches Item, Tausch mit Rückgabe, Tür als Gruppe, Reichweite); Auto-Bauen Schicht/bis/alles mit fehlendem Material (Rest bleibt); Speichern/Laden; Entfernen wenn fertig. Client: DevScene-Schritte mit Screenshots beider Stile, Schichten, Rechtsklick-Setzen.

## 3. CurseForge-Paket

- Jar `renovator-neoforge-1.21.1-1.0.0.jar`; `neoforge.mods.toml`: Lizenz „All Rights Reserved“, Logo, Beschreibung, optionale Abhängigkeiten ProjectE, Macaw's Furniture, Refurbished Furniture.
- Logo: Architect's Wand auf Blaupausen-Hintergrund (400×400 und Mod-Icon).
- `release/curseforge/`: `description.md` (englisch, kurze deutsche Zusammenfassung), `summary.txt`, `changelog.md`, `categories.txt`, Screenshots (englisch, inkl. Projektion), `UPLOAD.md` mit Schritten. Hochladen macht der Nutzer (Projekt nur mit seinem Konto anlegbar).
