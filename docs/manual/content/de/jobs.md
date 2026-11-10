---
title: "Stellen"
description: "Stellen erfassen, ihre Status und warum eine Stelle veröffentlicht ist oder nicht."
weight: 20
---

## Eine Stelle erfassen {#create}

**Neue Stelle** öffnet das Formular in einem Dialog, gegliedert in *Grunddaten* (Titel, Beschreibung, URL, Sprache,
Referenznummer, Kategorie), *Einordnung* (Art, Modus, Erfahrungsstufe, Schlagwörter),
*Pensum und Lohn*, *Daten* und *Standorte*. Die URL ist das Ziel für Bewerbende — Ihre
Karriereseite oder Ihr Bewerbermanagement.

Schlagwörter und Standorte wählen Sie über die Suche. Gibt es das gewünschte Schlagwort noch nicht,
tippen Sie seinen Namen und wählen **Schlagwort «…» erstellen**: Es wird beim Speichern der Stelle
angelegt, und gar nicht, wenn das Speichern scheitert.

Eine neue Stelle wird als **Entwurf** gespeichert. **Entwurf speichern** belässt sie so; **Speichern und
aktivieren** versucht zusätzlich, sie zu aktivieren, und speichert bei fehlenden Angaben den Entwurf
und zählt genau auf, was fehlt.

## Status {#states}

| Status | Bedeutung |
|---|---|
| **Entwurf** | In Vorbereitung. Unvollständige Angaben sind in Ordnung. Nie veröffentlicht. |
| **Aktiv** | Zur Veröffentlichung bestimmt. |
| **Inaktiv** | Zurückgezogen, besetzt oder archiviert. Bleibt erhalten, wird nie veröffentlicht. |

Ein Entwurf wird aktiviert oder, wenn er nicht gebraucht wird, gelöscht; eine aktive Stelle kann
zurück zum Entwurf oder deaktiviert werden; eine inaktive kann wieder aktiviert werden. Das Datum der
ersten Aktivierung bleibt als Publikationsdatum stehen und ändert sich bei späteren Bearbeitungen nicht.

## Bereit zur Veröffentlichung {#readiness}

Eine Stelle lässt sich nur aktivieren, wenn sie jede Anforderung erfüllt. Ihre Seite zeigt diese als
Checkliste, **Bereit zur Veröffentlichung**, und verlinkt jede offene mit dem Feld, das sie behebt:

- der Status ist aktiv und das Publikationsdatum gesetzt;
- Titel, URL, Sprache und Art sind vorhanden;
- mindestens ein Standort;
- ist ein Lohn angegeben, dann auch Währung und Intervall;
- sie liegt innerhalb ihres gültigen Zeitraums.

## Der gültige Zeitraum {#date-window}

Eine Stelle verlässt ihre Feeds von selbst, sobald ihre **Bewerbungsfrist** oder ihr **Enddatum**
vorbei ist — niemand muss sie bearbeiten. Sie bleibt aktiv und kehrt von selbst zurück, wenn Sie das
Datum verschieben. Das **Startdatum** entfernt nie eine Stelle: Eine Stelle, die in drei Monaten
beginnt, ist ein ganz normales Inserat.

## Wie eine Stelle angezeigt wird {#shown-as}

Die Liste und die Seite einer Stelle zeigen, wie sie gerade dasteht:

| Angezeigt als | Warum |
|---|---|
| **Veröffentlicht** | Aktiv, vollständig und im gültigen Zeitraum: Sie ist in ihren Feeds. |
| **Abgelaufen** | Aktiv, aber Bewerbungsfrist oder Enddatum ist vorbei. |
| **Unvollständig** | Aktiv, aber eine Anforderung ist nicht mehr erfüllt, zum Beispiel weil ihr letzter Standort entfernt wurde. |
| **Entwurf** | Ein Entwurf. |
| **Inaktiv** | Deaktiviert. |

Nur **veröffentlichte** Stellen erscheinen in einem Feed. Die Seite einer Stelle zeigt auch ihre Feeds
und ihren **Statusverlauf**: wer den Status wann von was zu was geändert hat.

## Stellenlinks und Klicks {#clicks}

Im veröffentlichten Feed zeigt der Link einer Stelle auf diese Anwendung, die den Klick zählt und
die Besucherin an Ihre URL weiterleitet. Gezählt wird nur pro Tag und Land — keine Adresse und keine
Angaben zum Browser werden gespeichert. Die Übersicht zeigt die Klicks. Ist eine Stelle nicht mehr
veröffentlicht, sagt ihr Link, dass das Inserat nicht mehr besteht, statt weiterzuleiten.

## Eine Stelle löschen {#delete}

In jedem Status möglich. Die Bestätigung nennt die Feeds, aus denen die Stelle verschwindet.
