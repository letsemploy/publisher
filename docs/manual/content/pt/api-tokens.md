---
title: "Tokens de API"
description: "Deixar outro sistema gerenciar vagas e feeds, sem que uma pessoa precise entrar."
weight: 60
---

Um **token de API** permite que outro sistema — um sistema de recrutamento, uma ferramenta de RH, um
script — trabalhe neste empregador pela API de gerenciamento, sem que uma pessoa precise entrar. Só os
**proprietários** veem e gerenciam tokens.

## Criar um token {#create}

**Criar um token** pede um nome, uma função e um conjunto de **escopos**, que dizem o que o token pode
fazer, como ler ou alterar vagas ou feeds. Um token nunca recebe mais do que a função e os escopos
permitem. O escopo de pessoas permite convidar e remover colegas; conceda-o só quando esse for o
objetivo.

**O segredo é exibido uma única vez**, logo depois de criar o token. Copie-o nesse momento para o outro
sistema; ele não pode ser exibido novamente. Se for perdido, revogue o token e crie outro.

## Expiração, renovação e revogação {#expiry}

Um token expira depois do período definido pela instalação, doze meses por padrão. A lista mostra o
estado de cada token — **ativo**, **expira em breve**, **expirado** ou **revogado** — e o **último
uso**: um token que ninguém usa há meses é o que deve ser revogado.

- **Renovar** adia a data de expiração sem mudar o segredo, então nada precisa ser reimplantado. Em um
  token expirado, o mesmo botão diz **Reativar**.
- **Revogar** interrompe o token imediatamente e de forma definitiva: um token revogado não pode ser
  renovado.

Um token não pode criar nem renovar tokens, nem excluir o empregador.

A API é documentada pelo seu schema, em `/graphql/schema` nesta instalação.
