# trivia-service

Salas de trivia en tiempo real. Por ahora tiene crear sala, unirse e iniciar
partida (HU-16, HU-17, HU-18). Lo de preguntas, respuestas y leaderboard
queda para la proxima.

## Como correrlo

```bash
docker compose up -d   # Postgres en localhost:5436
mvn spring-boot:run    # arranca en localhost:8085
```

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

## Por qué el UPDATE atomico para iniciar

Es el mismo truco que usamos para los cupos de las oportunidades en el
proyecto viejo. Si dos personas le dan a iniciar casi al mismo tiempo, el
UPDATE con el estado esperado en el WHERE hace que solo una de las dos
peticiones de verdad arranque la partida. No hace falta ningun lock a mano.

## Pruebas

```bash
mvn test
```

Conecta un cliente STOMP real (no mock) y hace todo el flujo: crear sala,
unirse, iniciar, y confirma que alguien que llega tarde ya no se puede
unir.
