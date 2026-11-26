---
title: "Feeds und Domains"
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
Anderen geben Sie stattdessen einen Permalink. Öffnen können sie nur die Mitglieder Ihres Arbeitgebers, angemeldet; für alle anderen gibt es sie nicht.

Ein Feed, den kein Permalink mit Domain veröffentlicht, ist auf keiner Website: Jobbörsen, die auf
Ihrer Website nach Ihren Stellen suchen, finden sie nicht. Seine Seite beginnt dann mit einer Warnung,
und die Feed-Liste und das Dashboard kennzeichnen ihn mit **keine Domain**. Lassen Sie unter
**Domains** eine Ihrer Domains auf ihn zeigen.

Einen Feed umzubenennen oder seinen Slug zu ändern ist unbedenklich: Alte Links funktionieren weiter
und werden umgeleitet. Wird ein Feed gelöscht, antwortet seine URL mit *nicht gefunden*; die Stellen
bleiben erhalten.

## Domains {#permalinks}

Jobbörsen suchen Ihre Stellen unter **Ihrer eigenen Domain**, unter
`https://example.com/.well-known/ojobpub.json`. Unter **Domains** in der Seitenleiste richten Sie das
ein. Jede Domain hat einen **Permalink**: eine URL, die gleich bleibt, während Sie den Feed dahinter
wechseln. Ihre Website stellt ihn unter dieser Adresse bereit; danach wechseln Sie selbst, was er
veröffentlicht.

Jeder Arbeitgeber beginnt mit einem Permalink, der seinen Feed `all` veröffentlicht, noch ohne Domain.
Nach der Anmeldung fragt die erste Seite nach dieser Domain, bis sie eingetragen ist, und das Dashboard
erinnert daran, bis eine Prüfung findet, dass Ihre Website ihn bereitstellt.

Die Liste zeigt für jede Domain, was die letzte Prüfung ergeben hat, mit der Schaltfläche **Einrichtung
prüfen**, eine Auswahl Ihrer Feeds mit der Schaltfläche **Wechseln** und die URL des Permalinks. *Kein
Feed* hält die URL am Leben, veröffentlicht aber keine Stellen — so pausieren Sie, ohne die URL zu
verlieren. Abnehmer sehen einen Wechsel, sobald ihre zwischengespeicherte Kopie abläuft.

**Neue Domain** fragt zuerst nach der Domain — der Domain selbst, etwa `example.com`, ohne `www.` oder
eine andere Subdomain; angeboten wird die Domain der Website Ihres Arbeitgebers —, dann nach dem Feed,
zu Beginn `all`, und wahlweise nach einem Namen und einer Beschreibung. Ein leerer Name ist die Domain.
Jede Domain gibt es pro Arbeitgeber einmal.

Nach dem Speichern nummeriert die Bearbeitungsseite die Schritte: die Domain, eine fertige
**Webserver-Konfiguration** für Apache, nginx und Caddy, und die Prüfung.

- eine **Weiterleitung** ist einfacher einzurichten, aber ein Abnehmer, der Weiterleitungen nicht
  folgt, scheitert;
- ein **Reverse Proxy** liefert das Dokument direkt von Ihrer Domain aus.

**Einrichtung prüfen** fragt die Adresse auf Ihrer Website, was sie tatsächlich liefert: diesen
Permalink, eine Weiterleitung dorthin, einen anderen Feed, eine Kopie der Datei, die eigene Seite Ihrer
Website oder nichts. Jede Antwort sagt, was als Nächstes zu tun ist.

Wird eine Domain gelöscht, antwortet die URL ihres Permalinks mit *nicht gefunden*. Wer nicht mehr
veröffentlichen, die URL aber behalten will, wechselt stattdessen auf *Kein Feed*.
