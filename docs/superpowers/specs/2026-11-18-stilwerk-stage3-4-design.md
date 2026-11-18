# Stilwerk – Design Stufe 3 (Detail-Assistent) und Stufe 4 (Innenausstatter)

Stand: 2026-11-18 · Status: vom Nutzer freigegeben („alles perfekt", „perfekt")
Baut auf Stufe 1+2 auf (`2026-11-18-stilwerk-design.md`): Auswahl, ChangeSet, Applier, Gate/Ledger,
Undo, Familien, StateTransfer, Mischpaletten, Vorschau-Ghosts, ServerOps-Muster.

## Gemeinsame Grundsätze

- Neue Stab-Modi im Modus-Rad: **Details** und **Innen** (zusammen fünf Modi).
- Alles rechnet deterministisch aus (Welt, Eingabe, Seed). Der Client zeigt die Vorschau, der Server
  rechnet beim Anwenden selbst neu. Übertragen werden nur Auswahl/Klickposition, Seed und Wahl.
- Setzen nur in Luft oder ersetzbare Blöcke (`canBeReplaced`), außer bei Regeln, die ausdrücklich
  bestehende Wandblöcke tauschen (Säulen, Sockel, Mischfläche). Dort gelten Natur- und Schutzregeln
  aus Stufe 1 und die Familien-Logik aus Stufe 2.
- Survival: Kosten über den bestehenden `MaterialLedger` (Inventar, dann EMC), Materialliste bei
  Fehlbestand, „so weit es reicht". Block-Entities (Truhen, Schränke) werden leer gesetzt.
- Jede Anwendung ist ein Undo-Schritt (U / `/stilwerk undo`).

## Stufe 3: Detail-Assistent

### Gebäudeanalyse
`BuildingView` über eine aufgelöste Auswahl: gebaute Blöcke (ohne Natur), Außenluft vs. Innenluft
(Flood-Fill der Luft von der Auswahlgrenze = außen), dominante Holzfamilie und Steinfamilie
(meistgezählte Familien mit `wood=true` bzw. ohne), Hilfsabfragen (ist Außenseite, Wandnormale).

### Regeln (je 3 Varianten, Material aus dem Gebäude)
| Regel | Erkennung | Varianten |
|---|---|---|
| `stairs` | Treppenlauf: ≥ 2 Treppen, je eine Stufe höher in Laufrichtung | Zaun-Geländer · Mauer-Geländer · offene Falltür-Blende; je + Laterne am unteren Ende |
| `eaves` | Dachkante: Treppe mit Luft darunter an der Außenseite | umgedrehte Treppe unter der Traufe · Falltür-Blende · Stufen-Überstand |
| `window` | Glas/Scheibe in Außenwand, außen Luft | Fensterläden (offene Falltüren links/rechts) · Treppen-Rahmen (oben umgedreht, unten Sims) · Blumenkasten (Falltür-Sims + Blumentöpfe/Blätter) |
| `pillars` | gerade Außenwand ≥ 5 lang, ≥ 3 hoch | Stammsäulen alle 4 Blöcke · entrindete Säulen · Steinziegel-Säulen (ersetzt Wandblöcke) |
| `plinth` | unterste Außenwandreihe | Material wechseln (Steinfamilie / Bruchstein) · Treppen-Schürze außen · bemooste Mischung |
| `corner` | Außenecke einer Wand, oberes Drittel | Laterne an Zaun-Arm · Seelenlaterne · Wandfackel-Paar |
| `entrance` | Tür in Außenwand | Laternen links/rechts · Vordach (Stufen/Treppen) · Stufen vor der Tür (wenn erhöht) |
| `plain` | ≥ 12 gleiche Blöcke einer Familie in einer Ebene | passende Mischpalette (Stein → *Alt & bemoost*, Holz → *Verwittertes Holz*, Tiefenschiefer → *Tiefenschiefer rau*, Sandstein → *Sandstein verwittert*, sonst *Burgmauer*) |

Ein Vorschlag = (Regel, Ankerposition, Seiten-/Richtungsinfo) mit Varianten; jede Variante liefert
ein ChangeSet. Überschneidende Vorschläge derselben Regel werden zusammengefasst (ein Fenster = ein
Vorschlag, eine Wand = ein Säulen-Vorschlag).

### Bedienung
- **Prüfen (Rechtschreibprüfung):** Auswahl vorhanden → Rechtsklick im Modus *Details* öffnet die
  Vorschlagsliste („14 Vorschläge", gruppiert nach Regel, „Alle übernehmen", „Durchgehen").
  Durchgehen in der Welt: aktueller Vorschlag umrandet + Ghost; **←/→** vorheriger/nächster,
  **↑** Variante, **Enter** übernehmen (springt zum nächsten), **Esc** beenden.
- **Gezielt:** Rechtsklick auf einen Block ohne Auswahl (oder Schleichen + Rechtsklick) → Vorschläge,
  deren Anker in der Nähe (Radius 3) liegt, aus einer 17×17×17-Analyse um den Block; **↑** Variante,
  **Enter** setzen.
- Server-Anfrage `DetailRequest(selection, seed, Liste von (Regel, Anker, Variante))`. Der Server
  rechnet die Vorschläge neu, wendet die gewählten an (unbekannte/inzwischen ungültige werden
  übersprungen).

## Stufe 4: Innenausstatter

### Raumerkennung
Rechtsklick (Modus *Innen*) auf einen Block → Start = Luftblock darüber (bzw. angeklickte Seite).
Flood-Fill über Luft/ersetzbare Blöcke (6er), Grenze 24×12×24 und 4000 Zellen. Ergebnis `Room`:
Luftzellen, Bodenzellen (Luft über festem Block), Höhe pro Bodenzelle, Wandzellen mit Normale,
Ecken, Türen (Türblöcke an der Hülle) mit Freihaltezone (2 Zellen tief), Fenster (Glas an der Hülle),
belegte Zellen (vorhandene Nicht-Luft-Blöcke innen). Läuft der Fill über die Grenze → „Raum nicht
geschlossen" + Position der letzten Randzelle.

### Möbel-Gruppen (Module)
- Datendateien im Jar: `data/stilwerk/stilwerk_interior/modules/*.json` (vom Klassenpfad geladen,
  auf Client und Server identisch). Erzeugt von `tools/make_interior.py` aus kompakten Layouts.
- Format: `{id, category, size:[x,y,z], placement: wall|corner|center|window|free, requires:[modids],
  blocks:[{p:[x,y,z], s:"ns:block[props]"}]}`. Ausrichtung: Vorderseite nach Süden (+z), Rückseite
  bei z=0 (an die Wand). Drehung über `Transform`.
- Jede Kategorie hat mindestens ein Vanilla-Modul; Mod-Module (`requires`) nur, wenn die Mods
  geladen sind. Mod-Varianten werden bevorzugt, wenn vorhanden.
- Holzanpassung: Module sind in Eiche geschrieben. Vanilla-Blöcke per Familien-Mapping
  (`oak` → Hausholz), Mod-Möbel per Namensersatz (`ns:oak_x` → `ns:<holz>_x`, falls registriert).
- Kategorien: bed, nightstand, wardrobe, sofa, coffee_table, dining, kitchen_counter, kitchen_sink,
  stove, fridge, bookshelf, lectern, desk, workshop, forge, storage, bath, toilet, basin, plant,
  lamp, rug (generiert), ceiling_light (generiert).

### Raumtypen (Rezepte, ebenfalls Daten)
`bedroom, living, kitchen, dining, library, bath, workshop, storage` + *Automatisch*
(Größe/Inhalt: Ofen/Amboss → Werkstatt, Bett → Schlafzimmer, Truhen → Lager, sonst nach Fläche).
Rezept: Liste `{category, min, max, priority, prefer: far_from_door|near_window|corner|centre|near_wall}`
plus `rug`, `ceiling_light`.

### Generator
1. Nach Priorität: für jede Kategorie Kandidaten (Bodenzelle × 4 Drehungen × passende Module), die
   passen (Fußabdruck frei, Höhe ≤ Raumhöhe, Rückseite an Wand bei wall/corner/window, nicht in
   Tür-Freihaltezone), Bewertung nach Vorliebe + Zufall (Seed).
2. Nach jedem gesetzten Modul: Wegeprüfung – alle freien Bodenzellen bleiben von den Türen aus
   erreichbar (BFS), sonst Kandidat verwerfen.
3. Teppich: größtes freies Rechteck in der Mitte (≥ 2×2), Farbe aus Seed passend zum Holz.
4. Deckenlicht: Raumhöhe ≥ 3 → Laterne an Kette in der Mitte, sonst Kerzen auf einem Tisch-Modul.
5. Ausgabe: Liste (Position, Zustand) → ChangeSet (nur Luft/ersetzbar).

### Bedienung
- Rechtsklick → `InteriorScreen`: Raummaße, Türen/Fenster, gefundene Möbel-Mods, Raumtypen.
- Typ wählen → Vorschau im Raum; **↑** neu würfeln, **←/→** Raumtyp wechseln, **Enter** einsetzen,
  **Esc** abbrechen.
- Server-Anfrage `InteriorRequest(klickPos, seite, typ, seed)`. Server erkennt den Raum neu,
  generiert mit gleichem Seed und wendet an.

## Tests
- GameTests: jede Detail-Regel an kleinen Testbauten (erkennt, Varianten setzen nur erlaubte Blöcke,
  Holz passt), Prüfen/Anwenden über ServerOps; Raumerkennung (geschlossen, offen → Fehler, Türen,
  Fenster), Generator (Pflichtmodule gesetzt, Tür frei, Weg erhalten, deterministisch, nur Vanilla
  ohne Mods), Holzanpassung, Survival-Kosten.
- Mit Mods: GameTests und DevScene mit `-PwithMods=<jar;jar>` (Macaw's, Refurbished + Abhängigkeiten
  aus der Instanz, nur in der Testumgebung) – Mod-Module werden verwendet, Namensersatz funktioniert.
- DevScene: Details prüfen/durchgehen/gezielt, Innen für mehrere Raumtypen, Screenshots.

## Nicht in diesen Stufen
- Entities (Bilderrahmen, Rüstungsständer, Gemälde) als Deko.
- Mehrstöckige Raumerkennung (Treppenhäuser), Außengärten.
