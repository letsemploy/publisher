---
title: "Feeds et domaines"
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
autres, donnez plutôt un permalien. Seuls les membres de votre employeur, connectés, peuvent l’ouvrir ; pour tous les autres, elle n’existe pas.

Renommer un feed ou changer son slug est sans risque : les anciens liens continuent de fonctionner et
sont redirigés. Supprimer un feed fait répondre son URL *introuvable* ; les offres ne sont pas
supprimées.

## Domaines {#permalinks}

Les portails d’emploi cherchent vos offres sur **votre propre domaine**, à l’adresse
`https://example.com/.well-known/ojobpub.json`. C’est sous **Domaines**, dans la barre latérale, que
vous le configurez. Chaque domaine a un **permalien** : une URL qui reste la même pendant que vous
changez le feed derrière elle. Votre site la sert à cette adresse ; ensuite, vous changez vous-même ce
qu’elle publie.

Chaque employeur commence avec un permalien qui publie son feed `all`, encore sans domaine. Après la
connexion, la première page demande ce domaine tant qu’il n’est pas saisi, et le tableau de bord vous
le rappelle jusqu’à ce qu’une vérification trouve votre site en train de le servir.

La liste indique pour chaque domaine ce qu’a trouvé la dernière vérification, avec un bouton **Vérifier
l’installation**, une liste de vos feeds avec un bouton **Changer**, et l’URL du permalien. Choisir
*Aucun feed* garde l’URL active mais ne publie aucune offre — la façon de faire une pause sans perdre
l’URL. Les consommateurs voient un changement dès que leur copie en cache expire.

**Nouveau domaine** demande d’abord le domaine — le domaine lui-même, par exemple `example.com`, sans
`www.` ni autre sous-domaine ; celui du site de votre employeur est proposé —, puis le feed, `all` pour
commencer, et au besoin un nom et une description. Un nom laissé vide est le domaine. Chaque domaine ne
sert qu’une fois par employeur.

Une fois enregistrée, la page de modification numérote les étapes : le domaine, une **configuration de
serveur web** prête à l’emploi pour Apache, nginx et Caddy, et la vérification.

- une **redirection** est plus simple à mettre en place, mais un consommateur qui ne suit pas les
  redirections échouera ;
- un **proxy inverse** sert le document directement depuis votre domaine.

**Vérifier l’installation** demande à l’adresse sur votre site ce qu’elle sert réellement : ce
permalien, une redirection vers lui, un autre feed, une copie du fichier, la page de votre site ou
rien. Chaque réponse indique quoi faire ensuite.

Supprimer un domaine fait répondre l’URL de son permalien *introuvable*. Pour cesser de publier tout en
gardant l’URL, passez plutôt à *Aucun feed*.
