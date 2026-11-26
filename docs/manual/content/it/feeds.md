---
title: "Feed e domini"
description: "Scegliere cosa viene pubblicato, e l’URL da fornire ad altri."
weight: 30
---

## Feed {#feeds}

Un **feed** è una selezione dei Suoi annunci, pubblicata come un unico documento `ojobpub.json`. Ogni
datore di lavoro ha un feed chiamato `all`; ne crei altri con **Nuovo feed** per pubblicare selezioni
diverse per partner diversi.

La pagina di un feed mostra:

- per primi **i permalink che lo pubblicano**, con i loro URL da copiare;
- gli annunci nel feed e gli altri annunci del datore di lavoro, affiancati e ciascuno con una ricerca,
  per aggiungere o rimuovere annunci;
- per ogni annuncio nel feed, se è **pubblicato** e, in caso contrario, perché: bozza, inattivo,
  scaduto o incompleto. Un annuncio aggiunto è nel documento solo finché è pubblicato;
- un’**anteprima** del documento esattamente come viene servito, con un badge che indica se è valido
  rispetto allo schema oJobPub.

L’URL proprio del feed è offerto con discrezione come **Prova**: serve per provare il feed. Ad altri
fornisca invece un permalink. Possono aprirlo solo i membri del Suo datore di lavoro, con l’accesso effettuato; per tutti gli altri non esiste.

Un feed che nessun permalink con un dominio pubblica non è su alcun sito: i portali di lavoro che
cercano i Suoi annunci sul Suo sito non li trovano. La sua pagina si apre allora con un avviso, e
l’elenco dei feed e la dashboard lo segnano con **nessun dominio**. Faccia puntare uno dei Suoi domini
a esso in **Domini**.

Rinominare un feed o cambiarne lo slug non comporta rischi: i vecchi link continuano a funzionare e
vengono inoltrati. Eliminando un feed, il suo URL risponde *non trovato*; gli annunci non vengono
eliminati.

## Domini {#permalinks}

I portali di lavoro cercano i Suoi annunci sul **Suo dominio**, all’indirizzo
`https://example.com/.well-known/ojobpub.json`. Lo imposta in **Domini**, nella barra laterale. Ogni
dominio ha un **permalink**: un URL che resta lo stesso mentre Lei cambia il feed che c’è dietro. Il
Suo sito lo serve a quell’indirizzo; in seguito è Lei a cambiare ciò che pubblica.

Ogni datore di lavoro inizia con un permalink che pubblica il suo feed `all`, ancora senza dominio.
Dopo l’accesso, la prima pagina chiede quel dominio finché non è inserito, e la dashboard lo ricorda
finché una verifica non trova il Suo sito che lo serve.

L’elenco mostra per ogni dominio che cosa ha trovato l’ultima verifica, con un pulsante **Verifica
configurazione**, un elenco dei Suoi feed con un pulsante **Cambia** e l’URL del permalink. Scegliendo
*Nessun feed* l’URL resta attivo ma non pubblica alcun annuncio: è il modo di fare una pausa senza
perdere l’URL. Chi lo utilizza vede un cambio quando scade la sua copia in cache.

**Nuovo dominio** chiede prima il dominio — il dominio stesso, ad esempio `example.com`, senza `www.`
né altri sottodomini; viene proposto quello del sito del Suo datore di lavoro —, poi il feed, `all`
all’inizio, e facoltativamente un nome e una descrizione. Un nome lasciato vuoto è il dominio. Ogni
dominio si usa una sola volta per datore di lavoro.

Dopo il salvataggio, la pagina di modifica numera i passi: il dominio, una **configurazione del server
web** pronta per Apache, nginx e Caddy, e la verifica.

- un **reindirizzamento** è più semplice da impostare, ma chi non segue i reindirizzamenti non riceverà
  nulla;
- un **reverse proxy** serve il documento direttamente dal Suo dominio.

**Verifica configurazione** chiede all’indirizzo sul Suo sito che cosa serve davvero: questo permalink,
un reindirizzamento verso di esso, un altro feed, una copia del file, la pagina del Suo sito o niente.
Ogni risposta dice che cosa fare dopo.

Eliminando un dominio, l’URL del suo permalink risponde *non trovato*. Per smettere di pubblicare
mantenendo l’URL, scelga invece *Nessun feed*.
