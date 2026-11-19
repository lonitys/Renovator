# Renovator auf CurseForge hochladen

Alles liegt in diesem Ordner bereit. Das Projekt kann nur mit deinem CurseForge-Konto angelegt werden.

## 1. Projekt anlegen

1. https://authors.curseforge.com → **Create Project** → Spiel **Minecraft**, Klasse **Mods**.
2. **Name:** `Renovator`
3. **Summary:** Inhalt von `summary.txt` einfügen.
4. **Description:** Editor auf **Markdown** stellen und `description.md` einfügen.
5. **Logo/Avatar:** `logo.png` (400×400).
6. **Main category:** *Utility & QoL*. Zusätzlich: *Cosmetic*, *Miscellaneous* (siehe `categories.txt`).
7. **License:** *All Rights Reserved*.
8. Speichern. CurseForge prüft neue Projekte; das kann einige Stunden dauern.

## 2. Datei hochladen

1. Im Projekt → **Files** → **Upload File**.
2. Datei: `renovator-neoforge-1.21.1-1.0.0.jar` (aus `build/libs/`).
3. **Display name:** `Renovator 1.0.0 (NeoForge 1.21.1)`
4. **Release type:** *Beta* für die erste öffentliche Version empfohlen (später *Release*).
5. **Game versions:** `1.21.1`, Loader **NeoForge**, Java **21**, Environment **Client** und **Server**.
6. **Changelog:** Inhalt von `changelog.md` (Markdown).
7. **Relations** (alle *Optional Dependency*): *ProjectE*, *Macaw's Furniture*, *Refurbished Furniture*.

## 3. Galerie

Bilder aus `screenshots/` hochladen; Titel und Beschreibung stehen in `captions.md`.

## 4. Vor dem Hochladen

- Das alte `stilwerk-1.0.0.jar` aus deinen Instanzen entfernen (z. B. „Greatness Upgrade 1.21.1“), sonst laufen beide Mods.
  Deine gespeicherten Baupläne werden beim ersten Start automatisch von `stilwerk/bauplaene` nach `renovator/blueprints` übernommen.
- Modpack-Erlaubnis: in den Projekteinstellungen „Allow in modpacks“ aktiviert lassen.
