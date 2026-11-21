---
title: "Feeds e links permanentes"
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
Para outras pessoas, passe um link permanente.

Renomear um feed ou alterar o slug dele é seguro: os links antigos continuam funcionando e são
redirecionados. Excluir um feed faz a URL dele responder *não encontrado*; as vagas não são excluídas.

## Links permanentes {#permalinks}

Um **link permanente** é uma URL que continua a mesma enquanto você troca o feed por trás dela.
Configure com ela, uma única vez, o seu site ou a importação de um parceiro; depois, você mesmo troca o
que ela publica.

Os links permanentes são listados na página **Feeds**, abaixo dos feeds. Cada linha tem uma seleção dos
seus feeds e um botão **Trocar**. Escolher *Nenhum feed* mantém a URL ativa, mas não publica nenhuma
vaga — a forma de pausar sem perder a URL. Os consumidores veem a troca assim que a cópia que guardaram
em cache expira.

**Novo link permanente** pede um nome, uma descrição e o feed. Depois de criado, a página de edição dele
oferece uma **Configuração do servidor web** pronta para Apache, nginx e Caddy, para que o feed também
possa ser encontrado em `/.well-known/ojobpub.json` no seu próprio domínio:

- um **redirecionamento** é a configuração mais simples, mas um consumidor que não segue
  redirecionamentos vai falhar;
- um **proxy reverso** serve o documento diretamente a partir do seu domínio.

Informe no link permanente o domínio de **O seu site** — o próprio domínio, como `example.com`, sem
`www.` nem outro subdomínio. Ao criar um, é sugerido o domínio do site do seu empregador. A página de
edição mostra então o endereço completo no seu site e **Verificar configuração**, que pergunta a esse
endereço o que ele realmente serve: este link permanente, um redirecionamento para ele, outro feed, uma
cópia do arquivo, a própria página do seu site, ou nada. Cada resposta diz o que fazer em seguida. A
página Feeds mostra, para cada link permanente, se ele está configurado no seu site.

Excluir um link permanente faz a URL dele responder *não encontrado*. Para parar de publicar, mas manter
a URL, troque-o para *Nenhum feed*.
