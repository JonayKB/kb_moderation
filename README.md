# KB Moderation

Mod de Forge para **Minecraft 1.20.1** (Forge 47) con herramientas de moderación para los servidores KB. El staff se reconoce por los **roles de Discord** que [KB Discord Auth](https://github.com/JonayKB/kb_discord_auth) pone en cada jugador como tags `discord_role.<id>`.

Va en **cliente y servidor**: el cliente dibuja el panel, los diálogos y los avisos. Todo lo decide el servidor: la interfaz solo lanza comandos que el servidor vuelve a comprobar.

## Qué hace

- **Panel de moderación** (`/mod`, o una tecla que cada uno asigna en *Controles → KB Moderación*; viene sin tecla). Lista de jugadores conectados y recientes con su cabeza y su estado, una ficha del jugador seleccionado y botones para cada acción.
- **Sanciones**: aviso, silencio, expulsión, baneo y nota interna, con motivo y duración que se eligen con un clic. Para banear hay que mantener pulsado el botón. Los silencios y los baneos se aplican **también a la cuenta de Discord**: si alguien está baneado, ninguna de sus otras cuentas puede entrar.
- **A la par con KB Discord Bot**: lo que se sanciona en el juego se aplica en Discord, y lo que se hace en el panel del bot (o a mano en Discord) llega al juego en unos segundos. Ver [Sincronización con el bot](#sincronización-con-kb-discord-bot).
- **Escalada automática**: a los N avisos activos, silencio o baneo automático (configurable).
- **Historial** de cada jugador (y de su Discord), con botones para levantar o quitar.
- **Inventario**: pantalla propia con inventario, armadura, mano secundaria, **Curios**, cofre de Ender y **armadura cosmética**, en vivo. Con shift + clic derecho en una **mochila** (Sophisticated Backpacks o cualquier objeto con inventario) o en un **shulker** se ve y se edita lo que lleva. Si ese objeto cambia de sitio mientras lo miras, la vista vuelve atrás sola. Quien no tiene `invsee.edit` solo puede mirar.
- **Vanish**: no se envía a los jugadores (ni como entidad ni en el tab), no salen los mensajes de entrar o salir, los mobs no te atacan y no se te oye en el chat de voz. El staff con `vanish.see` sigue viendo a los demás, con **◎** delante del nombre en la cabeza y en el tab. En vanish, el chat público se bloquea para que no te delates; empieza el mensaje con `!` si quieres hablar en público.
- **Ver invisibles** (`/verinvisibles`): el staff que lo activa ve a los jugadores con poción de invisibilidad y su nombre. Para los demás no cambia nada.
- **Congelar**: el jugador no puede moverse, romper, colocar, usar, atacar, tirar ni coger objetos, ni usar comandos (salvo los permitidos), y no recibe daño. Ve una pantalla de hielo. Sigue congelado si se desconecta, y el staff recibe un aviso.
- **Chat de staff**: pestaña **Chat staff** del panel, con los últimos mensajes y para escribir (marca los no leídos). Los mensajes también salen en el chat normal del staff, con un aviso sonoro. También con `/sc mensaje`, o `/sc` a secas para que todo lo que escribas vaya al staff hasta volver a usar `/sc`.
- **Reportes**: `/report` (o su tecla) abre una pantalla para elegir jugador, motivo y detalles. El staff recibe un aviso con sonido y botones (TP, inventario, info, cerrar). Hay pestaña de reportes en el panel. Con el bot, el reporte sale también en su panel y a quien reporta le llega un MD para seguir hablando con el staff.
- **Info** (`/modinfo`): Discord, otras cuentas del mismo Discord, cuentas que comparten IP (sin mostrar la IP), sanciones activas y estado.
- **Webhooks de Discord** para el registro de sanciones y para los reportes (con mención a un rol).
- **Simple Voice Chat**: los silenciados y los que están en vanish no se oyen.

## Comandos

| Comando | Permiso |
|---|---|
| `/mod` | cualquier permiso de staff |
| `/warn <jugador> <motivo>` · `/note <jugador> <texto>` · `/delwarn <id>` | `warn` |
| `/mute <jugador> <duración> [motivo]` · `/unmute <jugador>` | `mute` |
| `/kick <jugador> [motivo]` | `kick` |
| `/ban <jugador> <duración> [motivo]` · `/unban <jugador>` | `ban` |
| `/history <jugador>` | `history` |
| `/modinfo <jugador>` | `modinfo` |
| `/freeze <jugador>` · `/unfreeze <jugador>` | `freeze` |
| `/mtp <jugador>` · `/mtphere <jugador>` | `teleport` |
| `/vanish [jugador]` | `vanish` |
| `/verinvisibles` | `seeinvisible` |
| `/invsee <jugador>` · `/endersee <jugador>` | `invsee` (editar: `invsee.edit`) |
| `/sc [mensaje]` | `staffchat` |
| `/reports` · `/reports close <id>` | `reports` |
| `/report [<jugador> <motivo>]` | todos |

Duraciones: `30m`, `12h`, `7d`, `2w`, `1d12h`, `perm`. `/kick` y `/ban` sustituyen a los de vanilla (`replaceVanillaCommands = false` para usar `/kbkick` y `/kbban`).

## Configuración (`config/kb_moderation-common.toml`)

```toml
[permissions]
# "idDelRol|permiso1,permiso2"; "*" = todos
roles = [
  "111111111111111111|*",
  "222222222222222222|warn,mute,kick,history,freeze,staffchat,reports,vanish.see,invsee",
]
opLevelAll = 4   # los OP de este nivel tienen todo (0 = desactivado)
```

Permisos: `warn`, `mute`, `kick`, `ban`, `history`, `modinfo`, `freeze`, `teleport`, `vanish`, `vanish.see`, `seeinvisible`, `invsee`, `invsee.edit`, `staffchat`, `reports`, `exempt` (el resto del staff no puede sancionarlo).

Otras secciones: `[sanctions]` (comandos bloqueados a los silenciados, escalada, textos de la pantalla de baneo), `[vanish]`, `[freeze]`, `[reports]` y `[discord]` (webhooks).

Los datos (sanciones, jugadores vistos, congelados, vanish, reportes) se guardan en `world/kb_moderation/data.json`.

## Sincronización con KB Discord Bot

Sin configurar nada más: usa la misma URL y API key que las estadísticas de KB Discord Auth (`stats.endpoint` y `stats.apiKey`). Se puede cambiar en `[bot]` (`url`, `apiKey`, `pollSeconds`, `enabled`).

| En el juego | En Discord / el bot |
|---|---|
| Baneo (temporal o permanente) | Baneo; el bot lo levanta al vencer |
| Silencio | *Timeout* (Discord admite como mucho 28 días) |
| Aviso | Aviso con MD al usuario |
| Nota | Nota |
| Expulsión | Queda como nota (no echa de Discord) |
| Desbanear / quitar silencio / quitar aviso | Lo mismo en el bot |
| `/report` | Reporte en el panel del bot + MD a quien reporta |

Y al revés: lo que se hace en el panel del bot o directamente en Discord (baneos, *timeouts*, avisos, notas, expulsiones, desbaneos, casos borrados, reportes cerrados) se aplica en el juego a todas las cuentas de Minecraft vinculadas a ese Discord. Llega al momento: el servidor abre un WebSocket con el bot, lee lo pendiente al conectar y después solo cuando el bot avisa de algo nuevo. Mientras el WebSocket está caído (el bot reiniciándose, o una versión del bot sin él) pregunta cada `pollSeconds` (5 s por defecto) y vuelve a conectar solo. Lo que el juego no puede enviar porque el bot no responde se guarda en una cola en `data.json` y se reintenta en orden. El bot no duplica nada aunque reciba un reintento.

La primera vez, el juego importa todo el historial del bot (sin echar ni avisar a nadie por lo antiguo). Solo se comparten las sanciones de jugadores con el Discord vinculado.

## Probar en local

```bash
./gradlew runServer -PcompatMods=curios-forge:5.14.1+1.20.1,cosmeticarmorreworked:1.20.1-v1a,kb_load_test:1.0.0
./gradlew runClient -PmcUser=Staff -PmcServer=localhost -PcompatMods=curios-forge:5.14.1+1.20.1,cosmeticarmorreworked:1.20.1-v1a
```

Los jars de los mods opcionales van en `testenv/compat-mods/`. En `run/server.properties` hace falta `online-mode=false`. Los bots de KB Load Test sirven como segundo jugador.

Para probar la sincronización sin Discord real: `DEMO_MC_KEY=<key> npm run demo` en KB Discord Bot (panel en `http://127.0.0.1:3100` con un Discord simulado), y en `run/config/kb_discord_auth-common.toml` pon `enabled = false`, `stats.endpoint = "http://127.0.0.1:3100/api/integrations/minecraft/stats"` y `stats.apiKey = <key>`. Los vínculos de prueba van en `run/config/kb_discord_auth/links.json`.
