
$
$  Copyright 2008 Search Solution Corporation
$  Copyright 2016 CUBRID Corporation
$ 
$   Licensed under the Apache License, Version 2.0 (the "License");
$   you may not use this file except in compliance with the License.
$   You may obtain a copy of the License at
$ 
$       http://www.apache.org/licenses/LICENSE-2.0
$ 
$   Unless required by applicable law or agreed to in writing, software
$   distributed under the License is distributed on an "AS IS" BASIS,
$   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
$   See the License for the specific language governing permissions and
$   limitations under the License.
$ 
$ 
$

$
$ $Id$
$
$ csql.msg
$

$set 1 MSGCAT_CSQL_SET_CSQL
40 \
interaktives SQL Programm, Version %1$s\n\
Verwendung: %2$s [OPTION] datenbanknamen[@host]\n\
\n\
gültigen Optionen:\n\
  -S, --SA-mode                Ausführung in selbstständigem Modus\n\
  -C, --CS-mode                Ausführung in Client-Server Modus\n\
  -u, --user=ARG               alternativer Benutzername\n\
  -p, --password=ARG           Kennwort Zeichenfolge, "" für keines eingeben\n\
  -e, --error-continue         auf Anweisung Fehler nicht beenden\n\
  -i, --input-file=ARG         Eingabedateinamen\n\
  -o, --output-file=ARG        Ausgabedateinamen\n\
  -s, --single-line            Ausführung ausgerichtet auf einer Zeile\n\
  -c, --command=ARG            CSQL-Befehle\n\
  -l, --line-output            jeden Wert in einer Zeile anzeigen\n\
  -r, --read-only              Nur-Lese-Modus\n\
  -t, --plain-output           Darstellungsarten ergeben in einem Drucker-freundlichen Format angezeigt (funktioniert nur mit -c and -i)\n\
  -q, --query-output           Darstellungsarten ergeben in einem abfrage-freundlichen Format angezeigt (funktioniert nur mit -c und -i)\n\
  -d, --loaddb-output          Darstellungsarten ergeben in einem loaddb-freundlichen Format angezeigt (funktioniert nur mit -c und -i)\n\
  -N, --skip-column-names      Spaltendarstellung in Ergebnisse nicht anzuzeigen (funktioniert nur mit -c and -i)\n\
      --string-width           jede Spalte der Art String in dieser Breite anzeigen\n\
      --no-auto-commit         Auto-Commit-Modus deaktivieren\n\
      --no-pager               Pager nicht verwenden\n\
      --no-single-line         Ausführung ausgerichtet auf einer Zeile deaktivieren\n\
      --no-trigger-action      deaktivieren Trigger Aktion\n\
      --delimiter=ARG          Trennzeichen zwischen Spalte (funktioniert nur mit -q)\n\
      --enclosure=ARG          Gehäuse für eine Ergebniszeichenfolge (funktioniert nur mit -q)\n\
\n\
Für weitere Informationen, siehe http://www.cubrid.org\n
41 %1$s: der Umgebungswert von %2$s_MODE ist inkorrekt. \nEr sollte entweder %3$s oder %4$s sein.\n
42 %1$s: zu viele Argumente gegeben. Maximum ist %2$d.\n
43 %1$s: Das Root-Verzeichnis Umgebungsvariable ($CUBRID) soll gesetzt werden.\n
44 %1$s: der Versuch "%2$s" auszuführen ist fehlgeschlagen.\n
45 %1$s: -sa und -cs Optionen können nicht gleichzeitig benutzt werden.\n
46 %1$d Befehl(e) erfolgreich verarbeitet.
47 In Befehl auf Zeile %1$d,
48 Auf Zeile %1$d, Spalte %2$d,
49 Für hilfe, `;help' eingeben.
50 FEHLER: 
51 CUBRID SQL Interpreter
52 Es gibt Updates. Wollen Sie diese verpflichten? (`Y' or `N') 
53 `Y' für Verpflichtung, `N' für Abbruch eingeben. 
54 Verpflichtet.
55 Wiederherstellt.
56 Befehlspuffer wurde gespeichert.
57 Die Datei wurde in den Befehlspuffer gelesen.
58 Der Befehlspuffer wurde an den Drucker gesendet.
59 Aktuelles Verzeichnis ersetzt durch
61 Kennwort eingeben: 
62 <Ergebnis des %1$s Befehls in Zeile %2$d>
63 Es gibt keine Ergebnisse.
64 Der Checkpoint wurde ausgestellt.
65 Die Datenbank wurde neugestartet.
66 Tran index            Benutzername      Hostname      Prozess-ID      Programmname\n
67 %1$4d%2$-12s %3$14s %4$14s %5$15d %6$17s\n 
68 Die angegebene Transaktion wurde beendet.
69 Beendung der angegebenen Transaktion ist fehlgeschlagen.
70 %1$d Reihen %2$s.
71 %1$d Reihe %2$s.
$ Diese 3 sollten leer sein
75 Automatisierter API-Tester
76 Ungültiger automatisierten API-Tester 
79 csql>
80 csql
81 sysadm>
82 %1$d transactions %2$s.
83 %1$d transaction %2$s.
84 Zu wenig Argumente angegeben.

145 <Hilfe: Schema einer Klasse>
146 nichts
147 <Hilfe: Alle Anstöße>
148 <Hilfe: Keine definierte Anstöße>
150 <Hilfe: Anstoßdefinition >
151 <Hilfe: Befehlssyntax>
152 <Hilfe: Session-Befehl Zusammenfassung>
178 Ein Dateiname wird benötigt.
179 Der Pager kann nicht ausgeführt werden.
180 Es ist nicht genügend Speicher zugewiesen.
184 Die eingetragene Zeile ist zu lang.
185 Zu viele Zeilen wurden eingegeben.
188 Zu viele Dateinamen wurden eingegeben.
190 Kein solcher Session-Befehl. `;help' für Hilfe eingeben.
191 Mehr als einen Session-Befehl gefunden. Bitte mehr Details eingeben.
193 Mehr als einen Befehl gefunden. Bitte mehr Details eingeben.
194 Ungültige Kombination von Befehlszeilenargumente (Optionen).
196 Ein unbekannter Fehler ist aufgetreten.
197 ';edit' ist nicht zulässig, wenn die Ausgabe umgeleitet wurde.
198 Formatieren fehlgeschlagen.

203 <%1$s> 
204 <Oberklassen> 
205 <Unterklassen> 
206 <Attribute> 
207 <Klassenattribute> 
208 <Methoden> 
209 <Klassenmethoden> 
210 <Resolutionen> 
211 <Methodendateien> 
212 <Abfragespezifikationen> 
213 <Auslöser> 
214 <Name> 
215 <Ereignis> 
216 <Zeit der Bedingung> 
217 <Bedingung> 
218 <Zeit der Aktion> 
219 <Aktion> 
220 <Status> 
221 <Priorität> 
222 <Name> 
223 <Beschreibung> 
224 <Syntax> 
225 <Beispiel> 

$ csql help messages
231 \
\n\
   Alle Session-Befehle sollten durch `;' vorangestellt werden und nur\n\
   Leerzeichen/Tabs können das Präfix voranstellen. Großgeschriebene\n\
   Zeichen sind die minimale Abkürzung, die Sie eingeben müssen, um den\n\
   angegebenen Befehl auszuführen.\n\
\n\
   ;REAd   [<file-name>]        - Datei in Befehlspuffer lesen.\n\
   ;Write  [<file-name>]        - (über)schreiben des Befehlspuffers in eine Datei.\n\
   ;APpend [<file-name>]        - Befehlspuffer in eine Datei anfügen.\n\
   ;PRINT                       - Befehlspuffer drucken.\n\
   ;SHELL                       - Shell rufen.\n\
   ;CD                          - aktuelles Verzeichnis ändern.\n\
   ;EXit (or Ctrl+d)            - Programm beenden.\n\
\n\
   ;CLear                       - Befehlspuffer leeren.\n\
   ;EDIT   [format/fmt]         - System-Editor [nach dem Formatierer] mit dem Befehlspuffer.\n\
   ;LISt                        - Inhalt eines Befehlspuffer anzeigen.\n\
\n\
   ;RUn                         - SQL in Befehlspuffer ausführen.\n\
   ;Xrun                        - SQL in Befehlspuffer ausführen,\n\
                                  und Befehlspuffer leeren.\n\
   ;COMmit                      - aktuelle Transaktion verpflichten.\n\
   ;ROllback                    - aktuelle Transaktion rückgängig machen.\n\
   ;AUtocommit [ON|OFF]         - aktivieren/deaktivieren des Auto-Commit-Modus.\n\
   ;REStart                     - verbinden Sie sich erneut mit der aktuellen Datenbank in einer CSQL-Sitzung.\n\
   ;CHeckpoint                  - führen Sie den Prüfpunkt aus (nur CSQL mit --sysadm).\n\
   ;Killtran                    - informationen zum Transaktionsstatus prüfen oder eine bestimmte Transaktion beenden. (nur CSQL mit --sysadm).\n\
\n\
   ;SHELL_Cmd     [shell-cmd]   - Standard-Shell, -Editor, -Printer, -Pager und -Formatierer setzen\n\
   ;EDITOR_Cmd    [editor-cmd]    Befehl für eine neue, oder die aktuelle Anzeigen\n\
   ;PRINT_Cmd     [print-cmd]     einzeln, jeweils.\n\
   ;PAger_cmd     [pager-cmd]\n\
   ;FOrmatter_cmd [formatter-cmd]\n\
\n\
   ;DATE                        - lokale Uhrzeit und Datum anzeigen.\n\
   ;DATAbase                    - den Namen der zugegriffenen Datenbank anzeigen.\n\
   ;SChema class-name           - Schema-Informationen einer Klasse anzeigen.\n\
   ;TRIgger [`*'|trigger-name]  - Trigger-definition anzeigen.\n\
   ;Get system_parameter        - Wert eines System-Parameter erhalten.\n\
   ;SEt system_parameter=value  - Wert eines System-Parameter setzen.\n\
   ;STring-width [width]        - Breite setzen, in der jeder Spalte der Art String angezeigt wird.\n\
   ;COLumn-width [name]=[width] - Breite einer bestimmten Spalte setzen.\n\
   ;PLan [simple/detail/off]    - Abfrageausführungsplan anzeigen.\n\
   ;Info <command>              - interne Information anzeigen.\n\
   ;TIme [ON/OFF]               - aktivieren/deaktivieren, um die Ausführungszeit\n\
					der Abfrage anzuzeigen.\n\
   ;LINe-output [ON/OFF]        - Anzeigen in einer Zeile aktivieren/deaktivieren.\n\
   ;HISTORYList                 - Liste der ausgeführten Abfragen anzeigen.\n\
   ;HISTORYRead <history_num>   - Eintrag über die Verlaufszahl in den Befehlspuffer schreiben.\n\
   ;TRAce [ON/OFF] [text/json]  - enable/disable sql auto trace.\n\
   ;SIngleline [ON|OFF]         - Single-Line-Modus aktivieren/deaktivieren.\n\
   ;CONnect username [dbname | dbname@hostname]\n\
                                - sich als Benutzername mit der aktuellen oder anderen Datenbanken verbinden..\n\
   ;.Hist [ON/OFF]              - sammeln von Statistikinformationen in CSQL starten/stoppen (nur verfügbarer DBA).\n\
   ;.Clear_hist                 - löschen Sie die SQL Statistikinformationen im Puffer.\n\
   ;.Dump_hist                  - anzeige der CSQL-Statistikinformationen in CSQL.\n\
   ;.X_hist                     - anzeige der CSQL-Statistikinformationen in CSQL mit initialisierten Statistikdaten.\n\
   ;HElp                        - Hilfe anzeigen.\n\n

232 <Beschränkungen> 
233 Info {schema|trigger|deferred|workspace|lock|stats}\n\
\tschema [class-name]    - Schemainformation\n\
\ttrigger [trigger-name] - Trigger Information\n\
\tdeferred               - verzögerte Trigger Information\n\
\tworkspace              - Informationen zum Arbeitsumgebung\n\
\tlock                   - Sperre Information (Client-Server-Modus)\n\
\tstats class-name       - Klassenstatistikinformation\n\
\ttrantable              - Transaktionsdaten\n\
\tlogstat                - Log-Informationen\n\
\tcsstat                 - kritischen Abschnitt Informationen\n\
\tplan                   - Plan Cache Information\n\
\tqcache                 - Abfrage Cache Information\n
235 <Partitionen>
236 Ein Klassennamen ist erforderlich.
237 <Comment>
