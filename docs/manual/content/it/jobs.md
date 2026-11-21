---
title: "Annunci"
description: "Creare annunci, gli stati di un annuncio e perché un annuncio è pubblicato o no."
weight: 20
---

## Creare un annuncio {#create}

**Nuovo annuncio** apre il modulo in una finestra di dialogo, suddiviso in *Dati di base* (titolo, descrizione, URL, lingua,
ID di riferimento, categoria), *Classificazione* (tipo, modalità, livello, tag), *Grado di occupazione e
stipendio*, *Date*, *Sedi* e *Feed*. L’URL è la destinazione per chi si candida: la Sua pagina
carriere o il Suo sistema di gestione delle candidature.

Tag e sedi si scelgono tramite la ricerca. Se il tag desiderato non esiste ancora, ne digiti il nome e
scelga **Crea il tag «…»**: viene creato al salvataggio dell’annuncio, e non viene creato affatto se il
salvataggio non riesce.

Un nuovo annuncio viene salvato come **bozza**. **Salva bozza** lo lascia tale; **Salva e attiva**
prova anche ad attivarlo e, se manca qualcosa, salva la bozza ed elenca esattamente cosa manca.

**Un annuncio viene pubblicato solo nei feed in cui si trova.** Alla voce *Feed* spunti quali; un nuovo
annuncio parte nel feed *All jobs*. Anche un annuncio creato tramite l’API finisce lì.

## Stati {#states}

| Stato | Significato |
|---|---|
| **Bozza** | In preparazione. I dati incompleti vanno bene. Mai pubblicato. |
| **Attivo** | Destinato alla pubblicazione. |
| **Inattivo** | Ritirato, coperto o archiviato. Conservato per documentazione, mai pubblicato. |

Una bozza viene attivata, oppure eliminata se non serve; un annuncio attivo può tornare a bozza o
essere disattivato; uno inattivo può essere riattivato. La data della prima attivazione viene
conservata come data di pubblicazione e non cambia con le modifiche successive.

## Pronto per la pubblicazione {#readiness}

Un annuncio può essere attivato solo quando soddisfa tutti i requisiti di pubblicazione. La sua pagina
li mostra come lista di controllo, **Pronto per la pubblicazione**, e collega ogni requisito non
soddisfatto al campo che lo risolve:

- lo stato è attivo e la data di pubblicazione è impostata;
- titolo, URL, lingua e tipo sono presenti;
- almeno una sede;
- se è indicato uno stipendio, anche la valuta e la periodicità;
- rientra nel suo periodo di validità.

## Il periodo di validità {#date-window}

Un annuncio esce da solo dai suoi feed quando la data **Candidarsi entro** o la **Data di fine** è
passata, senza che nessuno debba modificarlo. Resta attivo, e ritorna da solo se Lei sposta la data. La
**Data di inizio** non rimuove mai un annuncio: una posizione che inizia tra tre mesi è un annuncio
normale.

## Come appare un annuncio {#shown-as}

L’elenco e la pagina dell’annuncio mostrano come l’annuncio si presenta in questo momento:

| Appare come | Perché |
|---|---|
| **Pubblicato** | Attivo, completo e nel suo periodo di validità: è nei suoi feed. |
| **Scaduto** | Attivo, ma la data «Candidarsi entro» o la data di fine è passata. |
| **Incompleto** | Attivo, ma un requisito non è più soddisfatto, ad esempio perché è stata rimossa la sua ultima sede. |
| **Bozza** | Una bozza. |
| **Inattivo** | Disattivato. |

Solo gli annunci **pubblicati** compaiono in un feed. La pagina dell’annuncio elenca anche i suoi feed,
con un campo per aggiungerlo a un altro, e avverte quando un annuncio pronto per la pubblicazione non è
in nessun feed. Mostra inoltre la sua **Cronologia dello stato**: chi ne ha cambiato lo stato, da cosa a
cosa e quando.

## Link degli annunci e clic {#clicks}

Nel feed pubblicato, il link di un annuncio punta a questa applicazione, che conta il clic e inoltra
il visitatore al Suo URL. I clic vengono contati solo per giorno e paese: non viene salvato alcun
indirizzo né alcun dato del browser. La dashboard li mostra. Quando un annuncio non è più pubblicato,
il suo link comunica al visitatore che l’annuncio non è più disponibile, invece di inoltrarlo.

## Eliminare un annuncio {#delete}

È possibile in qualsiasi stato. La conferma indica i feed da cui l’annuncio scomparirà.
