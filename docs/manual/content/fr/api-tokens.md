---
title: "Jetons d’API"
description: "Laisser un autre système gérer les offres et les feeds, sans qu’une personne se connecte."
weight: 60
---

Un **jeton d’API** permet à un autre système — un logiciel de suivi des candidatures, un outil RH, un
script — de travailler sur cet employeur via l’API de gestion, sans qu’une personne se connecte. Seuls
les **propriétaires** voient et gèrent les jetons.

## Créer un jeton {#create}

**Créer un jeton** demande un nom, un rôle et un ensemble de **portées**, qui définissent ce que le
jeton peut faire, comme lire ou modifier les offres ou les feeds. Un jeton n’obtient jamais plus que ce
que son rôle et ses portées permettent. La portée relative aux personnes permet d’inviter et de retirer
des collègues ; ne l’accordez que si c’est précisément le but.

**Le secret n’est affiché qu’une fois**, juste après la création du jeton. Copiez-le alors dans l’autre
système ; il ne peut plus être affiché. S’il est perdu, révoquez le jeton et créez-en un autre.

## Expiration, renouvellement et révocation {#expiry}

Un jeton expire après la durée fixée par l’installation, douze mois par défaut. La liste indique l’état
de chaque jeton — **actif**, **expire bientôt**, **expiré** ou **révoqué** — et sa **Dernière
utilisation** : un jeton que personne n’a utilisé depuis des mois est celui à révoquer.

- **Renouveler** repousse la date d’expiration sans changer le secret, si bien que rien n’est à
  redéployer. Sur un jeton expiré, le même bouton s’intitule **Réactiver**.
- **Révoquer** arrête le jeton immédiatement et définitivement : un jeton révoqué ne peut pas être
  renouvelé.

Un jeton ne peut ni créer ni renouveler de jetons, ni supprimer l’employeur.

L’API est documentée par son schéma, à `/graphql/schema` sur cette installation.
