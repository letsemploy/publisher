---
title: "Feeds und Permalinks"
description: "Festlegen, was veröffentlicht wird, und die URL, die Sie weitergeben."
weight: 30
---

## Feeds {#feeds}

Ein **Feed** ist eine Auswahl Ihrer Stellen, veröffentlicht als ein `ojobpub.json`-Dokument. Jeder
Arbeitgeber hat einen Feed namens `all`; mit **Neuer Feed** erstellen Sie weitere, um verschiedenen
Partnern verschiedene Auswahlen zu veröffentlichen.

Die Seite eines Feeds zeigt:

- zuerst **die Permalinks, die ihn veröffentlichen**, mit ihren URLs zum Kopieren;
- nebeneinander die Stellen im Feed und die übrigen Stellen des Arbeitgebers, je mit einer Suche, um
  Stellen hinzuzufügen oder zu entfernen;
- für jede Stelle im Feed, ob sie **veröffentlicht** ist, und wenn nicht, warum: Entwurf, inaktiv,
  abgelaufen oder unvollständig. Eine hinzugefügte Stelle steht nur im Dokument, solange sie
  veröffentlicht ist;
- eine **Vorschau** des Dokuments genau so, wie es ausgeliefert wird, mit einem Hinweis, ob es dem
  oJobPub-Schema entspricht.

Die eigene URL des Feeds wird nur unauffällig als **Testen** angeboten: Sie dient zum Ausprobieren.
Anderen geben Sie stattdessen einen Permalink.

Einen Feed umzubenennen oder seinen Slug zu ändern ist unbedenklich: Alte Links funktionieren weiter
und werden umgeleitet. Wird ein Feed gelöscht, antwortet seine URL mit *nicht gefunden*; die Stellen
bleiben erhalten.

## Permalinks {#permalinks}

Ein **Permalink** ist eine URL, die gleich bleibt, während Sie den Feed dahinter wechseln. Richten Sie
Ihre Website oder den Import eines Partners einmal damit ein; danach wechseln Sie selbst, was sie
veröffentlicht.

Permalinks stehen auf der Seite **Feeds**, unter den Feeds. Jede Zeile hat eine Auswahl Ihrer Feeds und
eine Schaltfläche **Wechseln**. *Kein Feed* hält die URL am Leben, veröffentlicht aber keine Stellen —
so pausieren Sie, ohne die URL zu verlieren. Leser sehen einen Wechsel, sobald ihre zwischengespeicherte
Kopie abläuft.

**Neuer Permalink** fragt nach Name, Beschreibung und Feed. Sobald er besteht, bietet seine
Bearbeitungsseite eine fertige **Webserver-Konfiguration** für Apache, nginx und Caddy an, damit der
Feed auch unter `/.well-known/ojobpub.json` auf Ihrer eigenen Domain zu finden ist:

- eine **Weiterleitung** ist einfacher einzurichten, aber ein Leser, der Weiterleitungen nicht folgt,
  scheitert daran;
- ein **Reverse Proxy** liefert das Dokument direkt von Ihrer Domain aus.

Wird ein Permalink gelöscht, antwortet seine URL mit *nicht gefunden*. Um nicht mehr zu veröffentlichen,
die URL aber zu behalten, wechseln Sie stattdessen auf *Kein Feed*.
