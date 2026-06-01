# Manual E2E Core

Guía rápida para validar el flujo core descentralizado con tres instancias locales aisladas.

## Preparación

```bash
./build.sh
./scripts/dev-3-instances.sh
```

El script crea directorios aislados en `/tmp/qfolder-dev`:

- `home-a/qfolder`, `home-b/qfolder`, `home-c/qfolder`: raíces locales de qfolder. Cada workspace se guarda como `YYYY/MM/DD/HHmm-{workspace_id}-{workspace_slug}/`.
- `samples/`: archivos de prueba para arrastrar a Archivos o adjuntar en Chat.
- `home-a`, `home-b`, `home-c`: `user.home` separado para cada instancia local.
- `logs/`: salida stdout/stderr de cada instancia.

Para detener:

```bash
xargs -r kill < /tmp/qfolder-dev/pids
```

## Escenario 1: Workspace Y Sync Inicial

1. En `devA`, crear un workspace.
2. Copiar el ID/URL del workspace desde Configuración.
3. En `devB` y `devC`, unirse al workspace usando la invitación P2P directa.
4. Confirmar en `devA` las aprobaciones si aparecen.
5. Verificar que las tres instancias muestran miembros y archivos.

Resultado esperado:

- `Core event` viaja entre peers.
- `Core sync request/response` recupera eventos faltantes desde peers directos al entrar.
- La vista reconstruye chat, notas, pizarra y metadata de archivos desde core.

## Escenario 2: Estado Visual Core→UI

1. En `devA`, escribir un mensaje de chat.
2. En `devB`, editar notas compartidas.
3. En `devC`, dibujar en pizarra con color/grosor distinto.
4. Cerrar `devB`.
5. Volver a iniciar `devB` con el mismo comando del script o relanzando el script tras detener todo.
6. Unir `devB` al workspace.

Resultado esperado:

- `devB` recupera eventos faltantes por sync core.
- Chat, notas y pizarra aparecen reconstruidos.
- La pizarra conserva objetos y trazos con color/grosor.

## Escenario 3: Metadata De Archivos Y Descarga Distribuida

1. Arrastrar `samples/a.txt` a la solapa Archivos de `devA`, o adjuntarlo desde el chat.
2. Confirmar que el archivo aparece para los otros miembros.
3. En una instancia, descargar el archivo de otro miembro desde la tabla de archivos.

Resultado esperado:

- La tabla muestra metadata core aunque el listado legacy no llegue primero.
- La descarga intenta primero chunks distribuidos:
  - `Core chunk availability request`
  - `Core chunk availability response`
  - `Core chunk request`
  - `Core chunk response`
- El archivo reconstruido valida SHA-256 final.
- Si faltan chunks o falla el hash, la descarga falla explícitamente con error; la transferencia completa legacy ya no es fallback activo.

## Escenario 4: Cache De Indexación

1. Refrescar archivos en `devA`.
2. Revisar que exista cache en `/tmp/qfolder-dev/home-a/qfolder/systemdata/workspaces/<id>/index-cache.properties` y estado core en `/tmp/qfolder-dev/home-a/qfolder/systemdata/workspaces/<id>/events/`.
3. Refrescar nuevamente sin cambiar archivos.
4. Modificar el archivo dentro de `home-a/qfolder/userdata/YYYY/MM/DD/HHmm-.../files/` y refrescar otra vez.

Resultado esperado:

- Sin cambios, no se reemite `file.shared` para el mismo path/tamaño/mtime.
- Al modificar tamaño o mtime, se registra de nuevo en core.

## Logs Útiles

```bash
ls -lh /tmp/qfolder-dev/logs
```

Buscar mensajes relacionados:

- `Evento core recibido`
- `Sync core aplico`
- `Descargando chunks`
- `descargado desde chunks distribuidos`
- `Descarga P2P por chunks no completada`
