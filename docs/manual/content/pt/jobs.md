---
title: "Vagas"
description: "Criar vagas, os status das vagas e por que uma vaga é publicada ou não."
weight: 20
---

## Criar uma vaga {#create}

**Nova vaga** abre o formulário em uma caixa de diálogo, dividido em *Informações básicas* (título,
descrição, URL, idioma, ID de referência, categoria), *Classificação* (tipo, modalidade, nível, tags),
*Carga horária e salário*, *Datas*, *Locais* e *Feeds*. A URL é para onde os candidatos devem ir — a
sua página de carreiras ou o seu sistema de recrutamento.

Tags e locais são escolhidos por pesquisa. Se a tag que você quer ainda não existe, digite o nome dela e
escolha **Criar a tag “…”**: ela é criada quando a vaga é salva, e não é criada se o salvamento falhar.

Uma vaga nova é salva como **rascunho**. **Salvar rascunho** a mantém assim; **Salvar e ativar** também
tenta ativá-la e, se faltar algo, salva o rascunho e lista exatamente o que falta.

**Uma vaga só é publicada nos feeds em que está.** Em *Feeds* você marca quais; uma vaga nova começa no
feed *All jobs*. Uma vaga criada pela API também vai para ele.

## Status {#states}

| Status | Significado |
|---|---|
| **Rascunho** | Em preparação. Dados incompletos não são problema. Nunca é publicada. |
| **Ativa** | Destinada à publicação. |
| **Inativa** | Retirada, preenchida ou arquivada. Mantida para registro, nunca é publicada. |

Um rascunho é ativado, ou excluído se não for necessário; uma vaga ativa pode voltar para rascunho ou
ser desativada; uma inativa pode ser ativada novamente. A data em que uma vaga foi ativada pela primeira
vez fica guardada como a sua data de publicação e não muda quando você a edita depois.

## Pronta para publicação {#readiness}

Uma vaga só pode ser ativada quando atende a todos os requisitos de publicação. A página dela mostra
esses requisitos como uma lista de verificação, **Pronta para publicação**, com cada requisito não
atendido ligado ao campo que o resolve:

- o status é ativo e a data de publicação está definida;
- título, URL, idioma e tipo estão preenchidos;
- pelo menos um local;
- se um salário for informado, também a moeda e a periodicidade;
- ela está dentro do seu período de validade.

## O período de validade {#date-window}

Uma vaga sai dos seus feeds sozinha quando a data em **Candidatar-se até** ou a **Data de término** já
passou — ninguém precisa editá-la. Ela continua ativa e volta sozinha se você mudar a data. A **Data de
início** nunca remove uma vaga: uma posição que começa daqui a três meses é uma vaga normal.

## Como uma vaga aparece {#shown-as}

A lista e a página da vaga mostram como a vaga se apresenta no momento:

| Aparece como | Por quê |
|---|---|
| **Publicada** | Ativa, completa e dentro do seu período de validade: está nos seus feeds. |
| **Expirada** | Ativa, mas a data em Candidatar-se até ou a data de término já passou. |
| **Incompleta** | Ativa, mas um requisito deixou de ser atendido, por exemplo porque o seu último local foi removido. |
| **Rascunho** | Um rascunho. |
| **Inativa** | Desativada. |

Só vagas **publicadas** aparecem em um feed. A página da vaga também lista os feeds dela, com um campo
para adicioná-la a outro, e avisa quando uma vaga pronta para publicação não está em nenhum feed. Ela
também mostra o **Histórico de status**: quem alterou o status, de qual para qual, e quando.

## Links das vagas e cliques {#clicks}

No feed publicado, o link de uma vaga aponta para este aplicativo, que conta o clique e encaminha o
visitante para a sua URL. Os cliques são contados apenas por dia e por país — nenhum endereço e nenhum
dado do navegador é armazenado. O painel os mostra. Quando uma vaga deixa de ser publicada, o link dela
informa ao visitante que a vaga não existe mais, em vez de encaminhá-lo.

## Excluir uma vaga {#delete}

Possível em qualquer status. A confirmação mostra os feeds dos quais a vaga vai desaparecer.
