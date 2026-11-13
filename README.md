# trivia-service

Salas de trivia en tiempo real. Ya tiene el flujo completo de una partida:
crear sala, unirse, iniciar, preguntas con tiempo, respuestas con puntaje
por velocidad, leaderboard y resultados finales (HU-16 a HU-22), y el
publicador ya puede agregar sus propias preguntas (HU-23).

## Como correrlo

```bash
docker compose up -d   # Postgres en localhost:5436
SPRING_DATASOURCE_PASSWORD=growlink mvn spring-boot:run   # arranca en localhost:8085
```

La contrasena de la base ya no esta escrita en el codigo (SonarCloud la marcaba como secreto expuesto): se pasa con la variable
`SPRING_DATASOURCE_PASSWORD`. La del Postgres de este `docker-compose.yml` es `growlink`.

Para HU-23 tambien tiene que estar corriendo cursos-service (localhost:8086,
se cambia con `cursos.base-url`) y hay que usar el mismo `GROWLINK_JWT_SECRET`
que usuarios-service (si no se pone, se usa la misma clave de desarrollo local).

Al arrancar se siembran preguntas de prueba: 5 de INGENIERIA_SISTEMAS, 4 de IDIOMAS y 4 de DERECHO.
Las demas categorias se llenan con las que agreguen los publicadores.

## Endpoints

| Metodo | Ruta | Que hace |
|---|---|---|
| POST | /api/salas | Crea la sala, el host ya queda adentro |
| GET | /api/salas/{codigo} | Ver el estado de una sala |
| POST | /api/preguntas | HU-23: el publicador agrega una pregunta al banco (necesita token) |
| GET | /api/partidas/ganadores?limite=N | Ultimas partidas terminadas, cada una con su podio (necesita token) |
| GET | /api/partidas/{codigo} | El acta completa de una partida: jugadores, puestos, puntos y aciertos, duracion |
| GET | /api/partidas/mias | Las partidas en las que jugo quien pregunta, con su puesto |
| GET | /api/partidas/salon-de-la-fama | Quien ha ganado mas partidas |
| POST | /api/retos | "Te reto a una trivia": crea la sala (quien reta queda esperando) y una invitacion pendiente |
| GET | /api/retos/recibidos | Los retos pendientes que todavia no vencen |
| GET | /api/retos/enviados | Los ultimos retos que mande, con su estado |
| POST | /api/retos/{id}/aceptar o /rechazar | Solo la persona retada; es un UPDATE atomico, asi que aceptar y rechazar a la vez no se pisan |

Unirse e iniciar es por WebSocket, no por REST:

- te conectas a `ws://localhost:8085/ws`
- te suscribes a `/topic/salas.{codigo}` (con punto, no barra, ver `Destinos`) para escuchar lo que pasa
- mandas a `/app/salas/{codigo}/unirse` con `{usuarioId, nombre}`
- mandas a `/app/salas/{codigo}/iniciar` cuando ya hay 2 o mas

## HU-23: preguntas del publicador

`POST /api/preguntas` con `Authorization: Bearer <token de usuarios-service>`:

```json
{
  "categoria": "INGENIERIA_SISTEMAS",
  "texto": "Que hace un indice en una base de datos",
  "opciones": ["Acelera busquedas", "Borra filas", "Cifra datos", "Nada"],
  "respuestaCorrecta": 0
}
```

- El publicador es el dueño del token (el `sub`), no viene en el body. Si
  viniera en el body cualquiera con sesion podria crear preguntas a nombre de
  otro publicador. trivia-service solo valida la firma del JWT, no usa Spring
  Security, asi que el WebSocket y /api/salas quedan igual que antes.
- Se le pregunta a cursos-service (`GET /api/cursos?publicadorUsuarioId=X`,
  reenviando el mismo token) en que categorias tiene cursos activos. Si la
  categoria no esta ahi, 403.
- Exactamente 4 opciones no vacias y `respuestaCorrecta` entre 0 y 3, si no 400.
- Sin token o con token invalido 401. Si cursos-service no responde 503.
- La pregunta queda guardada con su `publicadorUsuarioId` (las sembradas lo
  tienen en null) y desde ese momento sale en las salas de esa categoria.

## Historial de competencias y retos

- Al terminar una partida se guarda su **acta** (`partida_resultado` + `partida_puesto`) en la misma transaccion que la cierra: ganador, jugadores, puntos, aciertos y duracion. Si varias personas empatan en el puntaje mas alto, todas cuentan como ganadoras.
- Un **reto** vence a los 5 minutos (`trivia.retos.vigencia-minutos`). No se puede retar a uno mismo ni mandar dos retos pendientes a la misma persona. Quien reta sale del token, nunca del cuerpo.
- La prueba de la clase: `PartidaCompletaConcurrenteTest` juega una partida entera con 6 jugadores contestando a la vez, y `infra/scripts/humo` lo repite por WebSocket real.

## Cierre por tiempo de una pregunta

Si se acaba el tiempo de una pregunta y alguien no respondio (o no respondio nadie), la
partida ya no se queda trabada: el servidor la cierra igual que si todos hubieran
contestado (quien no respondio queda con 0 puntos, sale el LEADERBOARD, y se activa la
siguiente pregunta o se finaliza la partida si era la ultima, con las mismas garantias de
siempre: acta de la partida, aviso a usuarios-service, avisos despues de confirmar el
guardado).

- Una pregunta vence cuando `ahora > enviadaEn + duracionSegundos de la sala + margen`. El
  margen es una tolerancia para no castigar una respuesta que llego a tiempo pero con algo
  de latencia de red (`trivia.cierre-pregunta.margen-ms`, 1000 ms por defecto).
- `CierrePreguntaScheduler` es un barredor `@Scheduled` (`trivia.cierre-pregunta.intervalo-barredor-ms`,
  1000 ms por defecto) que corre en **todas las replicas**, no un timer en memoria: si una
  replica cae, cualquier otra sigue cerrando las preguntas vencidas, porque todo el estado
  (incluido que una pregunta ya vencio) vive en la base de datos.
- Cada `SalaPregunta` tiene una columna `cerrada` (default `false`, funciona sobre las filas
  que ya existian). Se cierra con el mismo truco de siempre, un UPDATE atomico condicionado
  (`UPDATE ... SET cerrada = true WHERE id = ? AND cerrada = false`), asi que si el barredor y
  la ultima respuesta llegan casi a la vez, solo uno de los dos de verdad avanza la partida.
  Responder tambien usa el lock de fila que ya tenia (`buscarConLockPorId`) para que las dos
  cosas nunca se crucen a medio camino.
- Una respuesta que llega a una pregunta ya cerrada se rechaza (mensaje de error "Se acabó
  el tiempo"), igual que cualquier otro error del juego.
- Si al finalizar nadie sumo puntos, no hay ganador: no se avisa a usuarios-service y no
  cuenta como trivia ganada. El historial y el salon de la fama ya manejaban ese caso.

## Por que el UPDATE atomico para iniciar

Es el mismo truco que usamos para los cupos de las oportunidades en el
proyecto viejo. Si dos personas le dan a iniciar casi al mismo tiempo, el
UPDATE con el estado esperado en el WHERE hace que solo una de las dos
peticiones de verdad arranque la partida. No hace falta ningun lock a mano.

## Pruebas

```bash
mvn test     # solo las pruebas
mvn verify   # las pruebas y la cobertura: target/site/jacoco/index.html
```

### Cobertura y SonarCloud en el CI

`ci.yml` corre `mvn -B verify`, que con JaCoCo deja la cobertura en `target/site/jacoco/` (se puede bajar como
artefacto `cobertura-trivia-service` desde el resumen de la corrida). Despues manda el analisis a SonarCloud, que lee
ese mismo reporte (`jacoco.xml`) sin volver a correr las pruebas. El build no se cae por un porcentaje de cobertura:
eso lo decide la puerta de calidad de SonarCloud.

Mientras el repo no tenga el secreto `SONAR_TOKEN`, el paso de SonarCloud se **omite** con un aviso y el workflow queda
en verde. Para prenderlo:

1. En SonarCloud, importar el repo desde la organizacion de GitHub y, en *Administration > Analysis Method*, apagar
   *Automatic Analysis* (si queda prendido, el analisis desde el CI falla).
2. Crear un token en SonarCloud (*My Account > Security*) y guardarlo en GitHub como secreto del repo `SONAR_TOKEN`.
3. El CI usa la organizacion `grow-link` y la clave `Grow-Link_trivia-service`, que son las que pone SonarCloud al
   importar. Si quedaron distintas, poner las variables del repo `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`.

### Que prueba cada clase

- `SalaWebSocketIntegrationTest`: conecta clientes STOMP reales y juega una
  partida completa de principio a fin, con las 5 preguntas.
- `PreguntaControllerTest`: HU-23, con tokens firmados de verdad y
  cursos-service simulado. Prueba la regla de categorias, las validaciones,
  el 401 sin token y que no se pueda suplantar a otro publicador por el body.
- `JuegoConcurrencyTest`: hilos reales, no simulados. Una prueba con 15
  usuarios uniendose a la vez, y otra con 10 usuarios respondiendo la misma
  pregunta al mismo tiempo, confirmando que solo uno gana el orden y que la
  partida avanza exactamente una vez, no dos.
- `CierrePorTiempoTest`: el cierre por tiempo de una pregunta. Nadie responde y la partida
  avanza hasta terminar; solo responde una parte y avanza igual; una respuesta tardia se
  rechaza; una partida sin ganador no suma trivia ganada; y con hilos reales, la ultima
  respuesta compitiendo contra dos "replicas" barriendo al mismo tiempo, confirmando que la
  partida avanza una sola vez. `application-test.yml` deja el margen y el intervalo del
  barredor en milisegundos para que estas pruebas no esperen segundos de verdad.

## Trivias ganadas (HU-22)

Cuando termina una partida, el que quedo primero suma una trivia ganada en
su perfil. El perfil vive en usuarios-service, asi que trivia-service le
avisa con una llamada de servicio a servicio
(`POST /api/interno/trivias-ganadas/{usuarioId}`, ver `UsuariosRestClient`).

- Esa llamada no lleva el token de ningun usuario, se identifica con la
  llave interna `GROWLINK_INTERNAL_KEY`, que tiene que ser la misma en los
  dos servicios.
- El aviso se manda hasta que la partida ya quedo guardada, no antes.
- Si usuarios-service no contesta, la partida igual termina bien, solo
  queda un aviso en el log.
- Si nadie sumo puntos (todos fallaron todo), no hay ganador y no se suma nada.

## Eventos de la partida (base del dashboard de HU-24)

Cada cosa importante que pasa en una partida se guarda en la tabla
`metrica_evento` (`MetricaService`): sala creada, jugador unido, pregunta
enviada, primera respuesta de cada pregunta (con la latencia desde que se
envio), "empates" resueltos por el UPDATE atomico (cuando alguien acierta
pero otro ya habia acertado primero) y partida finalizada (con duracion y
numero de participantes). El dashboard del admin son consultas sobre esa tabla.

## Autenticacion (`auth.mode`)

El servicio no monta una cadena de filtros de Spring Security: la autenticacion
se exige endpoint por endpoint (`TokenService`) y en el CONNECT del WebSocket
(`StompAuthChannelInterceptor`). Cual token se acepta lo decide `AUTH_MODE`:

| `AUTH_MODE` | Que valida |
|---|---|
| `legacy` (por defecto) | El JWT HS256 que emite usuarios-service, con el `GROWLINK_JWT_SECRET` compartido. El CONNECT del WebSocket no pide token y la identidad del jugador viaja en el payload del mensaje. Es el comportamiento historico. |
| `oidc` | El JWT del proveedor: firma **RS256** contra su JWKS, `kid` y `exp` obligatorios, `iss`, `aud` y `sub`. El CONNECT exige token y el `sub` queda como `Principal` de la sesion, asi que los handlers **ignoran** cualquier `usuarioId` que venga en el mensaje. En REST, un token que no cuadra es 401 y el motivo queda en el log con el `X-Request-Id`. |

`auth.mode` se valida al arrancar: un valor que no sea `legacy` ni `oidc` tumba
el arranque, en vez de caer callado a `legacy` y quedar desplegado sin la
autenticacion que uno creia haber puesto.

### Un emisor fijo o multi-tenant

`AUTH_MULTI_TENANT` decide como se valida el `iss`:

- **`false` (por defecto)**: un solo emisor, el de `AUTH_ISSUER_URI`, comparado
  tal cual. Con esto el comportamiento no cambia en nada.
- **`true`**: la app esta registrada en Entra ID para **cuentas de cualquier
  directorio organizacional y cuentas personales de Microsoft**. Ahi el `iss`
  del token trae el tenant de *quien inicia sesion*, no uno fijo, asi que
  compararlo contra un valor unico rechazaria a casi todo el mundo.

En multi-tenant, `AUTH_ISSUER_URI` se ignora y en su lugar se exige que el
`iss` cumpla **exactamente** este patron (anclado, nada de coincidencias
parciales):

```
^https://login\.microsoftonline\.com/<GUID-del-tenant>/v2\.0$
```

y que el claim `tid` sea **igual al GUID que viene dentro del `iss`**. Ese
segundo chequeo es el que hace que "aceptar cualquier tenant" siga siendo
seguro: sin el, aceptar cualquier directorio seria creerle al `iss` a secas;
con el, el token tiene que ser internamente consistente, y como el `iss` va
firmado nadie le puede cambiar el tenant a un token ajeno.

Quedan rechazados los emisores con otro host (`login.microsoftonline.com.evil.com`),
otro esquema (`http://`), sin `/v2.0`, con `common` en vez del GUID, o con
cualquier cosa pegada al final. Las cuentas personales de Microsoft no son un
caso aparte: Entra las emite con su propio tenant
(`9188040d-6c67-4c5b-b112-36a304b66dad`), que cae por el mismo patron.

`AUTH_ALLOWED_TENANTS` cierra ademas la puerta a una lista corta de directorios.
Vacio acepta cualquier tenant que cumpla el patron; con valores, solo esos. Un
GUID mal escrito ahi tumba el arranque, para no descubrirlo cuando alguien de
ese directorio no pueda entrar.

### De donde sale el usuarioId (`auth.identity-source`)

Este servicio guarda un `usuarioId` **numerico** en sus tablas
(`SalaParticipante`, `RespuestaSala`, `PartidaPuesto`). El `sub` de Microsoft
Entra **no** es un numero: es un texto opaco tipo
`AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ`. Quien sabe traducir "este `sub`
de Microsoft" a "el usuario 42" es usuarios-service.

| `AUTH_IDENTITY_SOURCE` | De donde sale la identidad |
|---|---|
| `usuarios-service` (por defecto) | `GET {USUARIOS_SERVICE_URL}/api/usuarios/me`, reenviando el mismo token en el `Authorization`. De ahi salen el `usuarioId` y el **rol**. Es el unico modo que funciona con tokens reales de Entra |
| `claims` | El `usuarioId` sale del `sub` y el rol de un claim. Es el comportamiento anterior; se conserva para desarrollo y pruebas sin usuarios-service arriba, y solo sirve si el emisor comparte el padron de usuarios de GrowLink |

Un valor que no sea ninguno de los dos **tumba el arranque**: caer callado a
`claims` dejaria el servicio aceptando el `sub` del token mientras uno cree que
le esta preguntando a usuarios-service.

Con `usuarios-service`, el rol del dashboard de admin tambien sale de esa
consulta: `/api/metricas/dashboard` ya no depende del claim `rol`.

**Cache.** La consulta esta en el camino de *cada* peticion autenticada, asi que
se cachea `AUTH_IDENTITY_CACHE_SECONDS` segundos (60 por defecto). Tres detalles:

- La clave es el **SHA-256 del token**, no el token: guardar el token entero en
  memoria es guardar una credencial viva.
- La entrada **nunca** vive mas alla del `exp` del token, pase lo que pase con
  el TTL. Si no, un token vencido seguiria entrando por el cache.
- Hay tope de 10 000 entradas con descarte del menos usado, para que el trafico
  real (tokens que se renuevan) no llene la memoria.

**Cuando usuarios-service falla.** Importa la diferencia:

| Que pasa | Que responde trivia |
|---|---|
| usuarios-service devuelve 401/403 | **401**. El problema es del token |
| No contesta, timeout o 5xx | **503** en REST, y el CONNECT del WebSocket se rechaza. El motivo queda en el log con el `X-Request-Id` |
| Hay un valor vigente en cache | Se usa, aunque usuarios-service este caido |

Lo que **nunca** pasa es caer al modo `claims` por una falla: eso convertiria
una caida de usuarios-service en "aceptamos el token y le creemos al `sub`",
justo lo que este modo viene a quitar. El WebSocket consulta solo al conectar y
la identidad queda en el `Principal` de la sesion, asi que los handlers siguen
ignorando cualquier `usuarioId` que venga en el mensaje.

### Variables

| Variable | Para que sirve |
|---|---|
| `AUTH_MODE` | `legacy` (por defecto) u `oidc` |
| `AUTH_MULTI_TENANT` | `false` (por defecto) un emisor fijo, `true` cualquier tenant de Entra |
| `AUTH_ISSUER_URI` | El emisor, obligatorio **solo** con `AUTH_MULTI_TENANT=false`. Se ignora con `true` |
| `AUTH_AUDIENCE` | Obligatorio. **Lista separada por comas**: el token pasa si su `aud` coincide con alguna. Entra pone `api://<client-id>` o el `<client-id>` pelado segun como quede registrada la app, asi que conviene poner las dos |
| `AUTH_ALLOWED_TENANTS` | Opcional, solo con multi-tenant. GUIDs de tenant separados por comas. Vacio = cualquier tenant que cumpla el patron |
| `AUTH_JWKS_URI` | Opcional. Por defecto `<AUTH_ISSUER_URI>/.well-known/jwks.json` en single-tenant, y `https://login.microsoftonline.com/common/discovery/v2.0/keys` en multi-tenant (el "common" de Entra publica las llaves de todos los directorios) |
| `AUTH_IDENTITY_SOURCE` | `usuarios-service` (por defecto) o `claims`. Un valor invalido tumba el arranque |
| `USUARIOS_SERVICE_URL` | **Obligatoria** con `AUTH_IDENTITY_SOURCE=usuarios-service`; si falta, el arranque falla. Tiene que apuntar al mismo sitio que `USUARIOS_BASE_URL` |
| `AUTH_IDENTITY_CACHE_SECONDS` | Opcional, cuanto vale una identidad ya resuelta (60). Nunca pasa del `exp` del token |
| `AUTH_ROL_CLAIM` | Opcional, **solo con `AUTH_IDENTITY_SOURCE=claims`**, de que claim sale el rol que mira `exigirAdmin` para `/api/metricas/dashboard`. Por defecto `rol`, el mismo nombre que usa usuarios-service. Acepta texto suelto o lista |

Las llaves publicas se bajan del JWKS y se cachean; si llega un `kid` que no
esta en el cache se recarga, pero como maximo una vez por minuto, para que un
token con un `kid` inventado no se convierta en una peticion al emisor por cada
intento.

### Ejemplo para Entra ID multi-tenant

Registro de la app en Entra: *Supported account types* en **Accounts in any
organizational directory and personal Microsoft accounts**, y la API expuesta
como `api://<client-id>`.

```bash
AUTH_MODE=oidc
AUTH_MULTI_TENANT=true
AUTH_AUDIENCE=api://4c1a9f70-aaaa-bbbb-cccc-ddddeeeeffff,4c1a9f70-aaaa-bbbb-cccc-ddddeeeeffff

# la identidad numerica y el rol salen de usuarios-service, porque el sub de Entra es opaco
AUTH_IDENTITY_SOURCE=usuarios-service
USUARIOS_SERVICE_URL=https://growlink-usuarios.azurewebsites.net
AUTH_IDENTITY_CACHE_SECONDS=60

# AUTH_ISSUER_URI no hace falta: el emisor sale del token
# AUTH_JWKS_URI tampoco: por defecto usa el "common" de Entra
# opcional, para limitar a dos directorios:
# AUTH_ALLOWED_TENANTS=11111111-2222-3333-4444-555555555555,aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
```

Y el mismo servicio contra un solo tenant (o contra otro proveedor de OIDC,
Keycloak por ejemplo), que es el modo por defecto:

```bash
AUTH_MODE=oidc
AUTH_MULTI_TENANT=false
AUTH_ISSUER_URI=https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555/v2.0
AUTH_AUDIENCE=api://4c1a9f70-aaaa-bbbb-cccc-ddddeeeeffff
```

## Despliegue

El servicio se despliega en Azure App Service, el flujo de ramas y ambientes
esta explicado en el README del repo `infra`. En resumen:

- `ci.yml` corre las pruebas, mide la cobertura y (si esta configurado) manda el analisis a SonarCloud en cada push
  a `main`, `avance` o `final` y en cada pull request a `main`.
- `cd.yml` despliega la rama a su ambiente de GitHub (`main` -> `actual`,
  `avance` -> `avance`, `final` -> `final`).
- Hay un `Dockerfile` para correrlo como contenedor.

Variables de entorno de la App Service:

| Variable | Para que sirve |
|---|---|
| `PORT` y `WEBSITES_PORT` | Poner las dos con `8085` |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | La base Postgres |
| `GROWLINK_JWT_SECRET` | El mismo secreto que usa usuarios-service |
| `GROWLINK_INTERNAL_KEY` | La misma llave interna que usuarios-service |
| `AUTH_MODE` | `legacy` (por defecto) u `oidc`, ver [Autenticacion](#autenticacion-authmode) |
| `AUTH_MULTI_TENANT` | `true` si la app esta registrada en Entra como multi-tenant |
| `AUTH_ISSUER_URI` | El emisor, solo con `AUTH_MULTI_TENANT=false` |
| `AUTH_AUDIENCE` | Obligatorio en modo `oidc`, lista separada por comas |
| `AUTH_ALLOWED_TENANTS` | Opcional, GUIDs de los tenants que se aceptan |
| `AUTH_JWKS_URI` | Opcional, de donde se bajan las llaves publicas |
| `AUTH_IDENTITY_SOURCE` | `usuarios-service` (por defecto) o `claims` |
| `USUARIOS_SERVICE_URL` | Obligatoria en modo `oidc` con `identity-source=usuarios-service` |
| `AUTH_IDENTITY_CACHE_SECONDS` | Opcional, cache de identidades en segundos (60) |
| `USUARIOS_BASE_URL` | URL de usuarios-service |
| `CURSOS_BASE_URL` | URL de cursos-service |
| `TRIVIA_BROKER_RELAY_ENABLED` | `true` para usar RabbitMQ (obligatorio si hay mas de una instancia) |
| `TRIVIA_BROKER_RELAY_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | Donde esta el RabbitMQ con el plugin STOMP (puerto 61613) |
| `TRIVIA_SALA_ACTIVA_MINUTOS` | Opcional, pasado este tiempo una sala sin terminar ya no cuenta como activa en el dashboard (120) |
| `TRIVIA_CIERRE_MARGEN_MS` | Opcional, tolerancia en ms sobre la duracion de la sala antes de vencer una pregunta (1000) |
| `TRIVIA_CIERRE_INTERVALO_MS` | Opcional, cada cuanto corre en ms el barredor que cierra preguntas vencidas (1000) |
| `METRICAS_TOKEN` | El token con el que Prometheus lee `/actuator/prometheus` (el mismo en los tres servicios, minimo 16 caracteres). Sin el, las metricas quedan cerradas. Ver [Metricas para Prometheus](#metricas-para-prometheus) |
| `SPRING_PROFILES_ACTIVE` | Opcional. En Azure no se pone (los logs salen en JSON); `logs-texto` es solo para leerlos a ojo en desarrollo |

En la App Service hay que prender **Web sockets** (Configuration > General
settings), si no la trivia en vivo no conecta.

**Salud**: `GET /actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` responden sin
credenciales y sin detalles. `readiness` incluye la base de datos (una replica sin base responde 503 y el balanceador
deja de mandarle trafico) y `liveness` no, a proposito: reiniciar la replica no arregla la base. Es la ruta que va en el
health check de Azure y en el `docker-compose` (ver `ReadinessConLaBaseDeDatosTest`).

## Dashboard de metricas (HU-24)

`GET /api/metricas/dashboard`, solo para el rol ADMIN (el rol viene firmado
dentro del JWT, sin token responde 401 y con otro rol 403). Son consultas
sobre las tablas del juego y sobre `metrica_evento`, sin ninguna herramienta
externa de monitoreo:

| Campo | Que es |
|---|---|
| `salasActivas`, `salasEnEspera`, `salasEnCurso` | Salas que no han terminado |
| `participantesConectados` | Jugadores en esas salas |
| `latenciaPromedioMs` | Promedio entre que sale una pregunta y llega la primera respuesta |
| `empatesResueltos` | Veces que dos jugadores acertaron y el UPDATE atomico dejo pasar solo a uno |
| `partidasFinalizadas`, `duracionPromedioPartidaMs` | Partidas terminadas y cuanto duran |

Con `?ultimosMinutos=N` la latencia, los empates y las partidas se cuentan solo
sobre los ultimos N minutos. El front lo muestra en el panel del admin
(`ConcurrenciaEnVivo`) y se actualiza solo cada 5 segundos.

## Metricas para Prometheus

`/actuator/prometheus` publica las metricas del servicio para los tableros de Grafana (repo `infra`, ADR-0009):
`trivia.salas.creadas`, `trivia.jugadores.unidos` (sin contar a quien crea la sala), `trivia.partidas.terminadas`,
`trivia.websocket.conexiones.activas`, las de la JVM y la latencia de cada peticion HTTP en cubetas fijas (10 ms a
10 s), todas con la etiqueta `instancia` (`WEBSITE_INSTANCE_ID` en Azure, si no el nombre del host).

Solo contesta con la cabecera `X-Metricas-Token: <METRICAS_TOKEN>` (`TokenDeMetricasFilter`); sin ella, 401. Antes
quedaba abierto, y el nginx del front reenvia `/api-trivia/**` a este servicio, asi que cualquiera las podia leer desde
el navegador. El filtro reconoce la ruta con el mismo analizador que Spring MVC: `/actuator/prometheus;x=1` o
`/actuator/prometh%65us` tampoco entran sin el token. Sin `METRICAS_TOKEN` configurado queda cerrado para todos, y un
token de menos de 16 caracteres no deja arrancar.

```bash
curl -H "X-Metricas-Token: $METRICAS_TOKEN" http://localhost:8085/actuator/prometheus
```

## Logs en JSON y X-Request-Id

Cada linea de la salida es un objeto JSON (ver `logback-spring.xml`), con los mismos campos que usuarios-service y
cursos-service, para que el almacen de logs (Azure Log Analytics) los filtre igual en los tres:

| Campo | Que trae |
|---|---|
| `@timestamp` | Cuando paso, en UTC |
| `level`, `logger_name`, `thread_name`, `message` | Lo de siempre |
| `rid` | El `X-Request-Id` de la peticion o del mensaje del WebSocket (en el MDC se llama `requestId`) |
| `servicio` | `trivia-service` |
| `instancia` | La replica: el mismo valor que la cabecera `X-Instancia` |
| `stack_trace` | Si hubo excepcion, dentro de la misma linea, con la causa raiz primero |

Con el perfil `logs-texto` (`SPRING_PROFILES_ACTIVE=logs-texto`) sale el formato de texto de antes. El letrero de
Spring esta apagado, porque no es JSON.

**De donde sale el id**, que siempre hay uno mientras se atiende algo:

- una peticion HTTP: `RequestIdFilter` (respeta el que venga, si es valido);
- un mensaje del WebSocket (el CONNECT, cada "unirse", "responder"...): `RequestIdStompInterceptor`, uno por mensaje
  (respeta un `X-Request-Id` valido que venga como cabecera nativa del frame);
- el cierre por tiempo de una pregunta: `CierrePreguntaScheduler`, uno por cierre, con el prefijo `cierre-`.

**Se les reenvia a los otros servicios** (`ReenvioDeRequestId`, en todos los `RestClient` del servicio): la llamada a
usuarios-service que suma la trivia ganada, la consulta de la identidad en modo `oidc` (`/api/usuarios/me`) y la de
categorias a cursos-service. Asi, la ultima respuesta de una partida y la victoria que le suma al ganador se encuentran
en los logs de los dos servicios con un solo id.

**Que id se acepta**: el mismo que en usuarios-service y cursos-service (ADR-0006): de 8 a 64 letras, numeros, punto,
guion o guion bajo. Cualquier otra cosa se reemplaza por uno nuevo. Antes este servicio aceptaba hasta 120 caracteres
y los dos puntos, y a lo que traia caracteres raros se los quitaba; con eso, un id que se aceptaba aqui lo cambiaban por
otro al llegar a usuarios-service y la traza se partia justo entre servicios.

## Prueba de carga: donde esta el cuello de botella

Se busco con `npm run trivia` (repo `infra`, `scripts/carga`, ver el ADR-0013 de ese repo), que juega partidas
completas por WebSocket con cada vez mas jugadores y mide la **respuesta**: desde que el jugador contesta hasta que le
llega la confirmacion (`RESPUESTA_REGISTRADA`). Medido en local el 12-11-2026: una replica de trivia-service con H2 en
memoria y broker en memoria, usuarios-service levantado, la carga desde la misma maquina, partidas de 10 preguntas.

**1. Con el ritmo de una persona no hay cuello de botella.** Si cada jugador contesta entre 0 y 1,5 s despues de recibir
la pregunta (lo normal), la carga se reparte en el tiempo:

| Jugadores (salas de 5) | Respuesta p95, contestando en 0 a 1,5 s | Respuesta p95, todos contestando al instante |
|---|---|---|
| 100 | 22 ms | 74 ms |
| 200 | 20 ms | 148 ms |
| 400 | 25 ms | 356 ms |

**2. El limite aparece cuando muchas respuestas llegan a la vez**, y crece en linea recta con cuantas son: cada
respuesta se procesa entera por un camino de capacidad fija, y la ultima espera a que pasen todas las anteriores. Ese
camino es:

- un hilo del canal de entrada de STOMP, que Spring crea a razon de **2 por nucleo** (aqui 24; en una instancia de
  1 vCPU, solo 2);
- que toma una **conexion de la base** (10 en el pool de Hikari) durante toda la transaccion;
- y hace **7 a 9 consultas** por respuesta (la que cierra la pregunta, unas 5 mas: cerrarla, el ranking, la siguiente),
  de la cuarta en adelante **con el candado de la fila de la pregunta tomado**.

Lo confirmaron volcados de hilos tomados durante la carga (400 jugadores contestando al instante): los 24 hilos del canal
de entrada ocupados (23,1 en promedio), el **55 %** de ellos esperando una conexion de la base en `JuegoService.responder`
y solo el 2,7 % esperando el candado de la pregunta. Subir el pool a 24 conexiones casi no cambia (356 → 287 ms): la
espera se pasa al candado y a la CPU. El limite es el **trabajo por respuesta**, no un parametro.

En una instancia chica pega antes. Con la JVM viendo un solo nucleo (`-XX:ActiveProcessorCount=1`, como una instancia de
1 vCPU: 2 hilos de entrada):

| Jugadores (salas de 5) | Respuesta p95, contestando en 0 a 1,5 s | Respuesta p95, todos al instante |
|---|---|---|
| 50 | 18 ms | 121 ms |
| 100 | 19 ms | 271 ms |
| 200 | - | 599 ms |

**3. Las salas grandes tienen dos problemas mas.** Con 50 jugadores por sala, todos al instante:

| Jugadores | Respuesta p95 | Reparto de la pregunta p95 (salas de 5: 3 a 12 ms) |
|---|---|---|
| 100 | 330 ms | 13 ms |
| 200 | 613 ms | 319 ms |
| 400 | 1.182 ms | 441 ms |

- Las respuestas de una misma sala se ponen **en fila en el candado de su pregunta**, y mientras esperan tienen tomada
  una conexion: el 29,4 % de los hilos esperaba ese candado (contra 2,7 % con salas de 5), y el pool se agota antes.
- Cada `RESPUESTA_REGISTRADA` se manda **a toda la sala** aunque solo le sirve a quien respondio (el front filtra): con
  n jugadores son n × n mensajes por pregunta, 2.500 en una sala de 50. Como cada jugador recibe sus mensajes en orden
  (`setPreservePublishOrder`), la pregunta siguiente le llega detras de todos ellos: el reparto sube a 250 a 550 ms.

**Lo que no se midio** y se deduce del codigo:

- En Azure la base es Postgres por la red: cada consulta cuesta mas, asi que la conexion y el candado se tienen mas
  tiempo y la misma curva sube antes. Hay que repetir la prueba alla (ADR-0013 en `infra`).
- La llamada que suma la victoria a usuarios-service es HTTP sincrono (hasta 3 s para conectar y 5 s para leer) y corre
  en el mismo hilo del canal de entrada, al terminar la partida. Con 2 hilos, dos partidas que terminan contra un
  usuarios-service colgado frenarian toda la trivia de esa replica hasta 8 s.

**Que haria, en orden de valor** (nada de esto esta hecho; son cambios de diseno o de despliegue para decidir):

1. En Azure, trivia con al menos 2 vCPU por instancia, o fijar el pool del canal de entrada
   (`configureClientInboundChannel(...).taskExecutor()`), con el pool de Hikari al menos de ese tamano y sin pasarse del
   limite de conexiones de Postgres (instancias × pool).
2. Mandar `RESPUESTA_REGISTRADA` solo a quien respondio (un destino por jugador) y no a toda la sala: quita los n × n
   mensajes. Hay que cambiar tambien el front.
3. Abaratar cada respuesta: menos consultas antes y dentro del candado (por ejemplo, un contador de respuestas en la
   fila de la pregunta en vez de contarlas con `COUNT`, y no releer la sala y el participante en cada respuesta).
4. Sacar la llamada de la victoria del hilo del canal de entrada (asincrona, con su propio pool).

Para repetirlo: levantar el servicio, y en `infra/scripts/carga`
`ETAPAS=100,200,400 POR_SALA=5 PREGUNTAS=10 ESPERA_MAX_MS=0 npm run trivia` (o `POR_SALA=50` para las salas grandes).
Mientras corre, el tablero tecnico de Grafana muestra las conexiones a la base "esperando" y los hilos.

## Escalar a varias instancias

El estado de las salas y las partidas esta en la base de datos, asi que cualquier
instancia puede atender cualquier peticion. Lo unico que vivia en memoria era el
broker de mensajes del WebSocket, y por eso un mensaje que salia por una instancia
no le llegaba a los jugadores conectados a otra. Con
`TRIVIA_BROKER_RELAY_ENABLED=true` todas las instancias usan un RabbitMQ compartido
(relay STOMP, ver `WebSocketConfig`). Sin esa variable sigue el broker en memoria,
que sirve para desarrollo y para una sola instancia. Cada respuesta HTTP trae el
header `X-Instancia` con la instancia que la atendio. El `docker-compose` con
replicas, RabbitMQ y la prueba de fuego estan en el repo `infra`.

Una cosa a tener en cuenta: las preguntas de prueba (`PreguntaSeeder`) se
siembran al arrancar si el banco esta vacio, asi que la primera vez hay que dejar
una sola instancia hasta que arranque, si no se duplican. El script `levantar.sh`
del repo `infra` ya lo hace asi.

El relay se probo contra un RabbitMQ real con 3 replicas de trivia detras del nginx, jugando una
partida completa por WebSocket (ver el README de `infra`). Dos cosas que esa prueba obligo a hacer:

- El destino de cada sala es `/topic/salas.CODIGO` (con punto, ver `Destinos`), porque RabbitMQ rechaza
  `/topic/...` con una barra despues del nombre.
- Los avisos a los jugadores (pregunta, ranking, resultados) se mandan **despues** de confirmar el guardado
  en la base de datos (`JuegoService.difundir`), no antes: si no, un jugador podia contestar a una replica
  que todavia no veia la pregunta.
- Cada jugador recibe los mensajes **en el orden en que se publicaron** (`setPreservePublishOrder(true)` en
  `WebSocketConfig`). Sin eso, al terminar una ronda el ranking y la pregunta siguiente salen casi a la vez por
  un grupo de hilos y a veces llegaban al reves; fue lo que hizo fallar `SalaWebSocketIntegrationTest` en el CI
  de GitHub (fallaba 3 de cada 8 corridas, ahora 0 de 15).
