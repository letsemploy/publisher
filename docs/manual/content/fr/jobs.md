---
title: "Offres"
description: "Créer des offres, leurs statuts, et pourquoi une offre est publiée ou non."
weight: 20
---

## Créer une offre {#create}

**Nouvelle offre** ouvre le formulaire dans une fenêtre, organisé en *Informations de base* (titre,
description, URL, langue, référence, catégorie), *Classification* (type, mode, niveau, tags),
*Taux d’occupation et salaire*, *Dates*, *Sites* et *Feeds*. L’URL est l’adresse où les candidats
doivent se rendre — votre page carrières ou votre logiciel de suivi des candidatures.

Les tags et les sites se choisissent par recherche. Si le tag voulu n’existe pas encore, saisissez son
nom et choisissez **Créer le tag « … »** : il est créé à l’enregistrement de l’offre, et pas du tout si
l’enregistrement échoue.

Une nouvelle offre est enregistrée comme **Brouillon**. **Enregistrer le brouillon** la laisse ainsi ;
**Enregistrer et activer** tente en plus de l’activer et, s’il manque quelque chose, enregistre le
brouillon et indique précisément ce qui manque.

**Une offre n’est publiée que dans les feeds où elle figure.** Sous *Feeds*, vous cochez lesquels ;
une nouvelle offre commence dans le feed *All jobs*. Une offre créée via l’API y va aussi.

## Statuts {#states}

| Statut | Signification |
|---|---|
| **Brouillon** | En préparation. Des données incomplètes sont acceptées. Jamais publiée. |
| **Active** | Destinée à être publiée. |
| **Inactive** | Retirée, pourvue ou archivée. Conservée pour mémoire, jamais publiée. |

Un brouillon est activé, ou supprimé s’il n’est plus voulu ; une offre active peut repasser en
brouillon ou être désactivée ; une offre inactive peut être réactivée. La date de la première
activation est conservée comme date de publication et ne change pas lorsque vous modifiez l’offre par
la suite.

## Prête à publier {#readiness}

Une offre ne peut être activée que si elle remplit toutes les conditions de publication. Sa page les
présente sous forme de liste de contrôle, **Prête à publier**, chaque condition non remplie renvoyant
au champ qui la corrige :

- le statut est actif et la date de publication est définie ;
- le titre, l’URL, la langue et le type sont renseignés ;
- au moins un site ;
- si un salaire est indiqué, sa devise et sa période aussi ;
- elle est dans sa période de validité.

## La période de validité {#date-window}

Une offre quitte ses feeds d’elle-même lorsque sa date **Postuler avant le** ou sa **Date de fin** est
passée — personne n’a besoin de la modifier. Elle reste active, et revient d’elle-même si vous
déplacez la date. La **Date de début** ne retire jamais une offre : un poste qui commence dans trois
mois est une offre tout à fait normale.

## Comment une offre apparaît {#shown-as}

La liste et la page de l’offre montrent comment l’offre se présente actuellement :

| Affichée comme | Pourquoi |
|---|---|
| **Publiée** | Active, complète et dans sa période de validité : elle figure dans ses feeds. |
| **Expirée** | Active, mais sa date limite de candidature ou sa date de fin est passée. |
| **Incomplète** | Active, mais une condition n’est plus remplie, par exemple son dernier site a été retiré. |
| **Brouillon** | Un brouillon. |
| **Inactive** | Désactivée. |

Seules les offres **publiées** apparaissent dans un feed. La page de l’offre liste aussi ses feeds,
avec un champ pour l’ajouter à un autre, et avertit lorsqu’une offre prête à publier ne figure dans
aucun feed. Elle montre également son **Historique des statuts** : qui a changé son statut, de quoi à
quoi, et quand.

## Liens des offres et clics {#clicks}

Dans le feed publié, le lien d’une offre pointe vers cette application, qui compte le clic et renvoie
le visiteur vers votre URL. Les clics sont comptés uniquement par jour et par pays — aucune adresse ni
aucune information sur le navigateur n’est enregistrée. Le tableau de bord les affiche. Dès qu’une
offre n’est plus publiée, son lien indique au visiteur que l’offre n’existe plus au lieu de le
rediriger.

## Supprimer une offre {#delete}

Possible quel que soit le statut. La confirmation nomme les feeds dont l’offre disparaîtra.
