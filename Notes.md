# Bermuda Reborn - Notes

## Bugfixes fuer Version 1.0.3

### 1. Touch Overlay bleibt nach Tod und Savegame-Load im Waffen-Modus haengen - erledigt
Stirbt der Spieler mit gezogener Waffe und laedt anschliessend ein Savegame, verbleibt das Touch Overlay faelschlicherweise im "Waffe gezogen"-Zustand. Es wird dann nur der Shoot Button statt des Run Buttons angezeigt. Erst nach manuellem Druecken des Waffe-ziehen Buttons springt das Layout wieder korrekt in den Normalmodus ohne gezogene Waffe zurueck.

### 2. Ausrichtung nach Schuss mit gezogener Waffe wird zurueckgesetzt - erledigt
Zielt der Spieler mit gezogener Waffe nach schraeg oben oder ganz oben und gibt einen Schuss ab, haelt die Spielfigur die anvisierte Richtung nicht. Stattdessen zielt sie nach dem Schuss wieder geradeaus, anstatt die zuvor eingenommene Zielrichtung beizubehalten.

### 3. Sprung nach vorn aus dem Stand funktioniert nicht mehr - erledigt
In der Originalsteuerung war ein Sprung nach vorn aus dem Stand ueber die Kombination Run Button + DPAD oben moeglich. Da der Run Button auf sofortiges Losrennen umgestellt wurde, war diese Eingabekombination nicht mehr verfuegbar.

## Features fuer Version 1.0.4

### 1. Savegame-Verzeichniswahl beim ersten Start - erledigt
Der Android-Launcher fragt nach dem Spieldatenimport ein Savegame-Verzeichnis per SAF ab. Die Engine nutzt weiter den internen Save-Cache, der beim Start aus dem gewaehlten Ordner importiert und bei Save-Aenderungen zurueck exportiert wird.

### 2. Crashlog mit Export ins Savegame-Verzeichnis - erledigt
Java/Kotlin-Crashes und native C++/Signal-Crashes schreiben Crashlog-TXT-Dateien mit Stacktrace, App-Version, Geraeteinformationen und Zeitstempel. Logs werden in das gewaehlte Savegame-Verzeichnis exportiert; native Crashlogs aus dem internen Cache werden beim naechsten App-Start ebenfalls exportiert.
