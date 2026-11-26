---
title: "Feeds y dominios"
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
entrégueles en su lugar un enlace permanente. Solo los miembros de su empleador, con la sesión iniciada, pueden abrirla; para los demás no existe.

Cambiar el nombre de un feed o su slug no causa problemas: los enlaces antiguos siguen funcionando y se
redirigen. Al eliminar un feed, su URL responde *no encontrado*; las ofertas no se eliminan.

## Dominios {#permalinks}

Los portales de empleo buscan sus ofertas en **su propio dominio**, en
`https://example.com/.well-known/ojobpub.json`. Lo configura en **Dominios**, en la barra lateral. Cada
dominio tiene un **enlace permanente**: una URL que no cambia mientras usted cambia el feed que hay
detrás. Su sitio la sirve en esa dirección; a partir de ahí, usted mismo cambia lo que publica.

Cada empleador empieza con un enlace permanente que publica su feed `all`, todavía sin dominio. Tras
iniciar sesión, la primera página pide ese dominio hasta que esté introducido, y el panel se lo
recuerda hasta que una comprobación encuentra que su sitio lo sirve.

La lista muestra, para cada dominio, lo que encontró la última comprobación, con un botón **Comprobar
configuración**, una lista de sus feeds con un botón **Cambiar** y la URL del enlace permanente. Elegir
*Ningún feed* mantiene viva la URL pero no publica ninguna oferta: la forma de hacer una pausa sin
perder la URL. Los consumidores ven un cambio cuando caduca su copia en caché.

**Nuevo dominio** pide primero el dominio —el dominio en sí, por ejemplo `example.com`, sin `www.` ni
otro subdominio; se ofrece el del sitio de su empleador—, luego el feed, `all` para empezar, y
opcionalmente un nombre y una descripción. Un nombre vacío es el dominio. Cada dominio se usa una sola
vez por empleador.

Una vez guardado, la página de edición numera los pasos: el dominio, una **configuración del servidor
web** lista para Apache, nginx y Caddy, y la comprobación.

- una **redirección** es más sencilla de configurar, pero un consumidor que no sigue redirecciones
  fallará;
- un **proxy inverso** sirve el documento directamente desde su dominio.

**Comprobar configuración** pregunta a la dirección de su sitio qué sirve realmente: este enlace
permanente, una redirección a él, otro feed, una copia del archivo, la propia página de su sitio o
nada. Cada respuesta indica qué hacer a continuación.

Al eliminar un dominio, la URL de su enlace permanente responde *no encontrado*. Para dejar de publicar
pero conservar la URL, cámbielo a *Ningún feed*.
