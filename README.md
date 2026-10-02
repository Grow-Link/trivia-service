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
- te suscribes a `/topic/salas/{codigo}` para escuchar lo que pasa
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

## Pendiente

- El contador de "trivias ganadas" en el perfil del ganador vive en
  user-service, y todavia no hay una llamada de este servicio hacia alla
  para actualizarlo.
