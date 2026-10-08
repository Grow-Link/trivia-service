# trivia-service

Salas de trivia en tiempo real. Ya tiene el flujo completo de una partida:
crear sala, unirse, iniciar, preguntas con tiempo, respuestas con puntaje
por velocidad, leaderboard y resultados finales (HU-16 a HU-22), y el
publicador ya puede agregar sus propias preguntas (HU-23).

## Como correrlo

```bash
docker compose up -d   # Postgres en localhost:5436
mvn spring-boot:run    # arranca en localhost:8085
```

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

## Por que el UPDATE atomico para iniciar

Es el mismo truco que usamos para los cupos de las oportunidades en el
proyecto viejo. Si dos personas le dan a iniciar casi al mismo tiempo, el
UPDATE con el estado esperado en el WHERE hace que solo una de las dos
peticiones de verdad arranque la partida. No hace falta ningun lock a mano.

## Pruebas

```bash
mvn test
```

- `SalaWebSocketIntegrationTest`: conecta clientes STOMP reales y juega una
  partida completa de principio a fin, con las 5 preguntas.
- `PreguntaControllerTest`: HU-23, con tokens firmados de verdad y
  cursos-service simulado. Prueba la regla de categorias, las validaciones,
  el 401 sin token y que no se pueda suplantar a otro publicador por el body.
- `JuegoConcurrencyTest`: hilos reales, no simulados. Una prueba con 15
  usuarios uniendose a la vez, y otra con 10 usuarios respondiendo la misma
  pregunta al mismo tiempo, confirmando que solo uno gana el orden y que la
  partida avanza exactamente una vez, no dos.

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

## Despliegue

El servicio se despliega en Azure App Service, el flujo de ramas y ambientes
esta explicado en el README del repo `infra`. En resumen:

- `ci.yml` corre las pruebas en cada push a `main`, `avance` o `final`.
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
| `USUARIOS_BASE_URL` | URL de usuarios-service |
| `CURSOS_BASE_URL` | URL de cursos-service |
| `TRIVIA_BROKER_RELAY_ENABLED` | `true` para usar RabbitMQ (obligatorio si hay mas de una instancia) |
| `TRIVIA_BROKER_RELAY_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | Donde esta el RabbitMQ con el plugin STOMP (puerto 61613) |
| `TRIVIA_SALA_ACTIVA_MINUTOS` | Opcional, pasado este tiempo una sala sin terminar ya no cuenta como activa en el dashboard (120) |

En la App Service hay que prender **Web sockets** (Configuration > General
settings), si no la trivia en vivo no conecta.

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
