---
title: "Feed e permalink"
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
fornisca invece un permalink.

Rinominare un feed o cambiarne lo slug non comporta rischi: i vecchi link continuano a funzionare e
vengono inoltrati. Eliminando un feed, il suo URL risponde *non trovato*; gli annunci non vengono
eliminati.

## Permalink {#permalinks}

Un **permalink** è un URL che resta lo stesso mentre Lei cambia il feed che c’è dietro. Configuri una
volta con esso il Suo sito web o l’importazione di un partner; in seguito sarà Lei a cambiare ciò che
pubblica.

I permalink sono elencati nella pagina **Feed**, sotto i feed. Ogni riga ha un elenco di selezione dei
Suoi feed e un pulsante **Cambia**. Scegliendo *Nessun feed* l’URL resta attivo ma non pubblica alcun
annuncio: è il modo per fare una pausa senza perdere l’URL. Chi legge il feed vede un cambio non appena
la sua copia in cache scade.

**Nuovo permalink** chiede un nome, una descrizione e il feed. Una volta creato, la sua pagina di
modifica offre una **Configurazione del server web** già pronta per Apache, nginx e Caddy, in modo che
il feed si trovi anche su `/.well-known/ojobpub.json` del Suo dominio:

- un **reindirizzamento** è la configurazione più semplice, ma un lettore che non segue i
  reindirizzamenti fallirà;
- un **reverse proxy** serve il documento direttamente dal Suo dominio.

Nel campo **Il Suo sito web** del permalink inserisca il dominio stesso, ad esempio `example.com`,
senza `www.` o un altro sottodominio. Quando ne crea uno, viene proposto il dominio del sito web del Suo datore di lavoro. La pagina di modifica mostra poi l’indirizzo completo sul Suo
sito web e **Verifica configurazione**, che chiede a quell’indirizzo cosa serve davvero: questo
permalink, un reindirizzamento verso di esso, un altro feed, una copia del file, una pagina del Suo
sito o niente. Ogni risposta indica cosa fare dopo. La pagina Feed mostra, per ogni permalink, se è
configurato sul Suo sito web.

Eliminando un permalink, il suo URL risponde *non trovato*. Per smettere di pubblicare mantenendo
l’URL, lo imposti invece su *Nessun feed*.
