---
title: "Feeds e domínios"
description: "Escolher o que é publicado, e a URL que você passa a outros."
weight: 30
---

## Feeds {#feeds}

Um **feed** é uma seleção das suas vagas, publicada como um único documento `ojobpub.json`. Todo
empregador tem um feed chamado `all`; crie outros com **Novo feed** para publicar seleções diferentes
para parceiros diferentes.

A página de um feed mostra:

- primeiro, **os links permanentes que o publicam**, com as URLs deles para copiar;
- as vagas do feed e as demais vagas do empregador lado a lado, cada lista com uma pesquisa, para
  adicionar ou remover vagas;
- para cada vaga do feed, se ela está **publicada** e, se não estiver, por quê: rascunho, inativa,
  expirada ou incompleta. Uma vaga que você adicionou só está no documento enquanto estiver publicada;
- uma **pré-visualização** do documento exatamente como ele é servido, com um selo que diz se ele é
  válido segundo o schema oJobPub.

A URL própria do feed é oferecida discretamente como **Testar**: ela serve para experimentar o feed.
Para outras pessoas, passe um link permanente. Só os membros do seu empregador, com sessão iniciada, podem abri-la; para os demais, ela não existe.

Um feed que nenhum link permanente com domínio publica não está em nenhum site: os portais de emprego
que procuram suas vagas no seu site não as encontram. A página dele abre então com um aviso, e a lista
de feeds e o painel o marcam com **sem domínio**. Aponte um dos seus domínios para ele em **Domínios**.

Renomear um feed ou alterar o slug dele é seguro: os links antigos continuam funcionando e são
redirecionados. Excluir um feed faz a URL dele responder *não encontrado*; as vagas não são excluídas.

## Domínios {#permalinks}

Os portais de emprego procuram as suas vagas no **seu próprio domínio**, em
`https://example.com/.well-known/ojobpub.json`. Você configura isso em **Domínios**, na barra lateral.
Cada domínio tem um **link permanente**: uma URL que continua a mesma enquanto você troca o feed por
trás dela. O seu site a serve nesse endereço; depois, você mesmo troca o que ela publica.

Cada empregador começa com um link permanente que publica o seu feed `all`, ainda sem domínio. Depois
de entrar, a primeira página pede esse domínio até ele ser indicado, e o painel lembra você disso até
que uma verificação encontre o seu site servindo-o.

A lista mostra, para cada domínio, o que a última verificação encontrou, com um botão **Verificar
configuração**, uma seleção dos seus feeds com um botão **Trocar** e a URL do link permanente. Escolher
*Nenhum feed* mantém a URL ativa, mas não publica nenhuma vaga — a forma de pausar sem perder a URL. Os
consumidores veem uma troca quando a cópia em cache deles expira.

**Novo domínio** pede primeiro o domínio — o próprio domínio, por exemplo `example.com`, sem `www.` nem
outro subdomínio; é oferecido o do site do seu empregador —, depois o feed, `all` para começar, e, se
quiser, um nome e uma descrição. Um nome vazio é o domínio. Cada domínio é usado uma só vez por
empregador.

Depois de salvo, a página de edição numera os passos: o domínio, uma **configuração de servidor web**
pronta para Apache, nginx e Caddy, e a verificação.

- um **redirecionamento** é mais simples de configurar, mas um consumidor que não segue
  redirecionamentos vai falhar;
- um **proxy reverso** serve o documento diretamente do seu domínio.

**Verificar configuração** pergunta ao endereço no seu site o que ele realmente serve: este link
permanente, um redirecionamento para ele, outro feed, uma cópia do arquivo, a própria página do seu
site ou nada. Cada resposta diz o que fazer em seguida.

Excluir um domínio faz a URL do link permanente dele responder *não encontrado*. Para parar de publicar
mas manter a URL, troque-o para *Nenhum feed*.
