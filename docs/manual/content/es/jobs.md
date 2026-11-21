---
title: "Ofertas"
description: "Crear ofertas, los estados de una oferta y por qué se publica o no."
weight: 20
---

## Crear una oferta {#create}

**Nueva oferta** abre el formulario en un cuadro de diálogo, agrupado en *Datos básicos* (título,
descripción, URL, idioma, ID de referencia, categoría), *Clasificación* (tipo, modalidad, nivel,
etiquetas), *Jornada y salario*, *Fechas*, *Ubicaciones* y *Feeds*. La URL es adonde deben dirigirse
los candidatos: su página de empleo o su sistema de gestión de candidatos.

Las etiquetas y ubicaciones se eligen buscándolas. Si la etiqueta que busca aún no existe, escriba su
nombre y elija **Crear la etiqueta «…»**: se crea al guardar la oferta, y no se crea en absoluto si el
guardado falla.

Una oferta nueva se guarda como **borrador**. **Guardar borrador** la mantiene así; **Guardar y
activar** además intenta activarla y, si falta algo, guarda el borrador e indica exactamente qué falta.

**Una oferta solo se publica en los feeds en los que está.** En *Feeds* marca cuáles; una oferta nueva
empieza en el feed *All jobs*. Una oferta creada a través de la API también va allí.

## Estados {#states}

| Estado | Significado |
|---|---|
| **Borrador** | En preparación. Los datos incompletos no son un problema. Nunca se publica. |
| **Activa** | Destinada a publicarse. |
| **Inactiva** | Retirada, cubierta o archivada. Se conserva como registro, nunca se publica. |

Un borrador se activa, o se elimina si no se necesita; una oferta activa puede volver a borrador o
desactivarse; una inactiva puede activarse de nuevo. La fecha en que una oferta se activó por primera
vez se conserva como su fecha de publicación y no cambia cuando la edita más adelante.

## Requisitos de publicación {#readiness}

Una oferta solo puede activarse cuando cumple todos los requisitos de publicación. Su página los
muestra como una lista de comprobación, **Requisitos de publicación**, con cada requisito pendiente
enlazado al campo que lo resuelve:

- el estado es activo y la fecha de publicación está definida;
- título, URL, idioma y tipo están presentes;
- al menos una ubicación;
- si se indica un salario, también su moneda y su periodicidad;
- está dentro de su periodo de vigencia.

## El periodo de vigencia {#date-window}

Una oferta sale sola de sus feeds cuando ha pasado su fecha de **Postular antes del** o su **Fecha de
fin**, sin que nadie tenga que editarla. Sigue activa y vuelve sola si mueve la fecha. La **Fecha de
inicio** nunca retira una oferta: un puesto que empieza dentro de tres meses es una oferta normal.

## Cómo se muestra una oferta {#shown-as}

La lista y la página de la oferta muestran cómo se presenta la oferta en ese momento:

| Se muestra como | Por qué |
|---|---|
| **Publicada** | Activa, completa y dentro de su periodo de vigencia: está en sus feeds. |
| **Vencida** | Activa, pero su fecha de postulación o de fin ha pasado. |
| **Incompleta** | Activa, pero ya no cumple un requisito, por ejemplo porque se quitó su última ubicación. |
| **Borrador** | Un borrador. |
| **Inactiva** | Desactivada. |

Solo las ofertas **publicadas** aparecen en un feed. La página de la oferta también lista sus feeds,
con un campo para añadirla a otro, y avisa cuando una oferta lista para publicarse no está en ningún
feed. También muestra su **Historial de estados**: quién cambió su estado, de cuál a cuál y cuándo.

## Enlaces de ofertas y clics {#clicks}

En el feed publicado, el enlace de una oferta apunta a esta aplicación, que cuenta el clic y redirige
al visitante a su URL. Los clics se cuentan solo por día y país: no se guarda ninguna dirección ni
ningún dato del navegador. El panel los muestra. Cuando una oferta deja de estar publicada, su enlace
indica al visitante que la oferta ya no existe en lugar de redirigirlo.

## Eliminar una oferta {#delete}

Es posible en cualquier estado. La confirmación indica los feeds de los que desaparecerá la oferta.
