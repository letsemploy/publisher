---
title: "Feeds y enlaces permanentes"
description: "Elegir qué se publica, y la URL que entrega a otros."
weight: 30
---

## Feeds {#feeds}

Un **feed** es una selección de sus ofertas, publicada como un único documento `ojobpub.json`. Cada
empresa tiene un feed llamado `all`; cree más con **Nuevo feed** para publicar distintas selecciones
para distintos socios.

La página de un feed muestra:

- primero, **los enlaces permanentes que lo publican**, con sus URL para copiar;
- las ofertas del feed y las demás ofertas de la empresa una junto a otra, cada lista con su búsqueda,
  para añadir o quitar ofertas;
- para cada oferta del feed, si está **publicada** y, si no, por qué: borrador, inactiva, vencida o
  incompleta. Una oferta que ha añadido solo está en el documento mientras esté publicada;
- una **vista previa** del documento exactamente como se sirve, con una insignia que indica si es
  válido según el esquema de oJobPub.

La URL propia del feed se ofrece discretamente como **Probar**: sirve para probar el feed. A otros,
entrégueles en su lugar un enlace permanente.

Cambiar el nombre de un feed o su slug no causa problemas: los enlaces antiguos siguen funcionando y se
redirigen. Al eliminar un feed, su URL responde *no encontrado*; las ofertas no se eliminan.

## Enlaces permanentes {#permalinks}

Un **enlace permanente** es una URL que no cambia mientras usted cambia el feed que hay detrás.
Configure con ella una vez su sitio web o la importación de un socio; a partir de ahí, usted mismo
cambia lo que publica.

Los enlaces permanentes se listan en la página **Feeds**, debajo de los feeds. Cada fila tiene una
lista desplegable con sus feeds y un botón **Cambiar**. Elegir *Ningún feed* mantiene viva la URL pero
no publica ninguna oferta: así se hace una pausa sin perder la URL. Los consumidores ven un cambio en
cuanto caduca su copia en caché.

**Nuevo enlace permanente** pide un nombre, una descripción y el feed. Una vez creado, su página de
edición ofrece una **Configuración del servidor web** lista para usar para Apache, nginx y Caddy, de
modo que el feed también se encuentre en `/.well-known/ojobpub.json` en su propio dominio:

- una **Redirección** es la configuración más sencilla, pero un consumidor que no siga redirecciones fallará;
- un **Proxy inverso** sirve el documento directamente desde su dominio.

Introduzca en el enlace permanente el dominio de **Su sitio web**: el dominio en sí, como
`example.com`, sin `www.` ni otro subdominio. Al crear uno, se le propone el dominio del sitio web de su
empresa. La página de edición muestra entonces la dirección completa en su sitio y **Comprobar
configuración**, que consulta a esa dirección qué sirve realmente: este enlace permanente, una
redirección hacia él, otro feed, una copia del archivo, la propia página de su sitio, o nada. Cada
respuesta indica qué hacer a continuación. La página Feeds muestra, para cada enlace permanente, si
está configurado en su sitio web.

Al eliminar un enlace permanente, su URL responde *no encontrado*. Para dejar de publicar pero
conservar la URL, cámbielo a *Ningún feed*.
