---
title: "Feeds et permaliens"
description: "Choisir ce qui est publié, et l’URL que vous transmettez."
weight: 30
---

## Feeds {#feeds}

Un **feed** est une sélection de vos offres, publiée sous forme d’un document `ojobpub.json`. Chaque
employeur a un feed appelé `all` ; créez-en d’autres avec **Nouveau feed** pour publier des sélections
différentes pour différents partenaires.

La page d’un feed présente :

- d’abord **les permaliens qui le publient**, avec leurs URL à copier ;
- côte à côte, les offres du feed et les autres offres de l’employeur, chacune avec une recherche, pour
  ajouter ou retirer des offres ;
- pour chaque offre du feed, si elle est **publiée** et, sinon, pourquoi : brouillon, inactive,
  expirée ou incomplète. Une offre ajoutée ne figure dans le document que tant qu’elle est publiée ;
- un **aperçu** du document exactement tel qu’il est servi, avec un badge indiquant s’il est valide
  au regard du schéma oJobPub.

L’URL propre du feed est proposée discrètement sous **Tester** : elle sert à essayer le feed. Aux
autres, donnez plutôt un permalien.

Renommer un feed ou changer son slug est sans risque : les anciens liens continuent de fonctionner et
sont redirigés. Supprimer un feed fait répondre son URL *introuvable* ; les offres ne sont pas
supprimées.

## Permaliens {#permalinks}

Un **permalien** est une URL qui reste la même pendant que vous changez le feed derrière elle.
Configurez-la une fois pour toutes sur votre site web ou dans l’import d’un partenaire ; ensuite, vous
changez vous-même ce qu’elle publie.

Les permaliens sont listés sur la page **Feeds**, sous les feeds. Chaque ligne comporte une liste de
vos feeds et un bouton **Changer**. Choisir *Aucun feed* garde l’URL active mais ne publie aucune
offre — la façon de faire une pause sans perdre l’URL. Les consommateurs voient le changement dès que
leur copie en cache expire.

**Nouveau permalien** demande un nom, une description et le feed. Une fois créé, sa page de
modification propose une **Configuration du serveur web** prête à l’emploi pour Apache, nginx et
Caddy, afin que le feed soit aussi accessible à `/.well-known/ojobpub.json` sur votre propre domaine :

- une **Redirection** est la configuration la plus simple, mais un consommateur qui ne suit pas les
  redirections échouera ;
- un **Reverse proxy** sert le document directement depuis votre domaine.

Saisissez sur le permalien le domaine de **Votre site web** — le domaine lui-même, par exemple
`example.com`, sans `www.` ni autre sous-domaine. À la création, le domaine du site web de votre employeur est proposé. La page de modification affiche alors l’adresse complète sur votre
site web et **Vérifier l’installation**, qui demande à cette adresse ce qu’elle sert réellement : ce
permalien, une redirection vers lui, un autre feed, une copie du fichier, une page de votre propre
site, ou rien. Chaque réponse indique la marche à suivre. La page Feeds indique, pour chaque permalien,
s’il est installé sur votre site web.

Supprimer un permalien fait répondre son URL *introuvable*. Pour cesser de publier tout en gardant
l’URL, passez-le plutôt sur *Aucun feed*.
