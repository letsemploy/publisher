---
title: "Token API"
description: "Permettere a un altro sistema di gestire annunci e feed, senza che una persona acceda."
weight: 60
---

Un **token API** permette a un altro sistema (un sistema di gestione delle candidature, uno strumento
HR, uno script) di lavorare su questo datore di lavoro tramite l’API di gestione, senza che una persona
acceda. Solo i **proprietari** vedono e gestiscono i token.

## Creare un token {#create}

**Crea un token** chiede un nome, un ruolo e un insieme di **Ambiti**, che stabiliscono cosa può fare
il token, ad esempio leggere o scrivere annunci o feed. Un token non ottiene mai più di quanto
consentono il suo ruolo e i suoi ambiti. L’ambito per le persone consente di invitare e rimuovere
colleghi; lo conceda solo quando è proprio questo lo scopo.

**Il segreto viene mostrato una sola volta**, subito dopo la creazione del token. Lo copi in quel
momento nell’altro sistema; non potrà essere mostrato di nuovo. Se va perso, revochi il token e ne crei
un altro.

## Scadenza, rinnovo e revoca {#expiry}

Un token scade dopo il periodo stabilito dall’installazione, dodici mesi per impostazione predefinita.
L’elenco mostra lo stato di ogni token (**attivo**, **in scadenza**, **scaduto** o **revocato**) e il
suo **Ultimo utilizzo**: un token che nessuno usa da mesi è quello da revocare.

- **Rinnova** sposta in avanti la data di scadenza senza cambiare il segreto, quindi non serve
  ridistribuire nulla. Su un token scaduto lo stesso pulsante si chiama **Riattiva**.
- **Revoca** blocca il token subito e per sempre: un token revocato non può essere rinnovato.

Un token non può creare né rinnovare token, né eliminare il datore di lavoro.

L’API è documentata dal suo schema, su `/graphql/schema` di questa installazione.
