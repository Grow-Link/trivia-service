# trivia-service

Salas de trivia en tiempo real. Ya tiene el flujo completo de una partida:
crear sala, unirse, iniciar, preguntas con tiempo, respuestas con puntaje
por velocidad, leaderboard y resultados finales (HU-16 a HU-22). Falta
HU-23, que el publicador agregue sus propias preguntas.

## Como correrlo

```bash
docker compose up -d   # Postgres en localhost:5436
mvn spring-boot:run    # arranca en localhost:8085
```

Al arrancar se siembran preguntas de prueba, 5 de BACKEND y 5 de FRONTEND.
Las demas categorias todavia no tienen preguntas.

## Endpoints

| Metodo | Ruta | Que hace |
|---|---|---|
| POST | /api/salas | Crea la sala, el host ya queda adentro |
| GET | /api/salas/{codigo} | Ver el estado de una sala |

Unirse e iniciar es por WebSocket, no por REST:

- te conectas a `ws://localhost:8085/ws`
- te suscribes a `/topic/salas/{codigo}` para escuchar lo que pasa
- mandas a `/app/salas/{codigo}/unirse` con `{usuarioId, nombre}`
- mandas a `/app/salas/{codigo}/iniciar` cuando ya hay 2 o mas

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
- `JuegoConcurrencyTest`: hilos reales, no simulados. Una prueba con 15
  usuarios uniendose a la vez, y otra con 10 usuarios respondiendo la misma
  pregunta al mismo tiempo, confirmando que solo uno gana el orden y que la
  partida avanza exactamente una vez, no dos.

## Pendiente

- HU-23: que el publicador agregue sus propias preguntas, restringido a
  categorias donde tenga cursos publicados (necesita a cursos-service).
- El contador de "trivias ganadas" en el perfil del ganador vive en
  user-service, y todavia no hay una llamada de este servicio hacia alla
  para actualizarlo.
