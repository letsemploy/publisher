---
title: "Tokens de API"
description: "Permitir que otro sistema gestione ofertas y feeds, sin que una persona inicie sesión."
weight: 60
---

Un **token de API** permite que otro sistema (un sistema de gestión de candidatos, una herramienta de
RR. HH., un script) trabaje con esta empresa a través de la API de gestión, sin que una persona inicie
sesión. Solo los **propietarios** ven y gestionan los tokens.

## Crear un token {#create}

**Crear un token** pide un nombre, un rol y un conjunto de **Permisos**, que indican lo que puede hacer
el token, como leer o escribir ofertas o feeds. Un token nunca obtiene más de lo que permiten su rol y
sus permisos. El permiso para personas permite invitar y quitar a colegas; concédalo solo cuando ese
sea el objetivo.

**El secreto se muestra una sola vez**, justo después de crear el token. Cópielo en ese momento en el
otro sistema; no se puede volver a mostrar. Si se pierde, revoque el token y cree otro.

## Vencimiento, renovación y revocación {#expiry}

Un token vence tras el plazo que fija la instalación, doce meses por defecto. La lista muestra el
estado de cada token (**activo**, **vence pronto**, **vencido** o **revocado**) y **cuándo se usó por
última vez**: un token que nadie ha usado en meses es el que conviene revocar.

- **Renovar** adelanta la fecha de vencimiento sin cambiar el secreto, de modo que no hay que volver a
  desplegar nada. En un token vencido, el mismo botón dice **Reactivar**.
- **Revocar** detiene el token de inmediato y para siempre: un token revocado no se puede renovar.

Un token no puede crear ni renovar tokens, ni eliminar la empresa.

La API está documentada por su esquema, en `/graphql/schema` en esta instalación.
