---
title: "API-Token"
description: "Ein anderes System Stellen und Feeds verwalten lassen, ohne dass sich jemand anmeldet."
weight: 60
---

Ein **API-Token** lässt ein anderes System — ein Bewerbermanagement, ein HR-Werkzeug, ein Skript — über
die Management-API an diesem Arbeitgeber arbeiten, ohne dass sich eine Person anmeldet. Nur
**Eigentümerinnen** sehen und verwalten Token.

## Ein Token erstellen {#create}

**Token erstellen** fragt nach Name, Rolle und **Berechtigungen**, die festlegen, was das Token darf,
etwa Stellen oder Feeds lesen oder schreiben. Ein Token erhält nie mehr, als Rolle und Berechtigungen
zulassen. Die Berechtigung für Personen erlaubt, Kolleginnen einzuladen und zu entfernen; vergeben Sie
sie nur, wenn genau das gewollt ist.

**Das Geheimnis wird einmal angezeigt**, direkt nach dem Erstellen. Kopieren Sie es dann ins andere
System; es lässt sich nicht erneut anzeigen. Geht es verloren, widerrufen Sie das Token und erstellen
ein neues.

## Ablauf, Verlängern und Widerrufen {#expiry}

Ein Token läuft nach der Frist ab, die die Installation festlegt, standardmässig nach zwölf Monaten.
Die Liste zeigt den Zustand jedes Tokens — **aktiv**, **läuft bald ab**, **abgelaufen** oder
**widerrufen** — und **wann es zuletzt verwendet wurde**: Ein Token, das seit Monaten niemand
verwendet, ist das, das Sie widerrufen sollten.

- **Verlängern** verschiebt das Ablaufdatum, ohne das Geheimnis zu ändern; nichts muss neu verteilt
  werden. Bei einem abgelaufenen Token heisst dieselbe Schaltfläche **Reaktivieren**.
- **Widerrufen** stoppt das Token sofort und endgültig: Ein widerrufenes Token lässt sich nicht
  verlängern.

Ein Token kann keine Token erstellen oder verlängern und den Arbeitgeber nicht löschen.

Die API ist durch ihr Schema beschrieben, unter `/graphql/schema` auf dieser Installation.
