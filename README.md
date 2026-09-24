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

Todo lo demas es por WebSocket, en `ws://localhost:8085/ws`, escuchando
`/topic/salas/{codigo}`:

| Mandas a | Con que | Que responde la sala |
|---|---|---|
| /app/salas/{codigo}/unirse | {usuarioId, nombre} | SALA_UPDATE |
| /app/salas/{codigo}/iniciar | nada | SALA_UPDATE y luego la primera PREGUNTA |
| /app/salas/{codigo}/responder | {usuarioId, indice, opcionElegida} | nada hasta que todos respondan, y ahi manda LEADERBOARD y la siguiente PREGUNTA (o RESULTADOS_FINALES si era la ultima) |

## Los dos lugares donde hay condiciones de carrera de verdad

**Quien inicia la partida.** Mismo truco que en los cupos de las
oportunidades del proyecto viejo: un solo UPDATE con el estado esperado en
el WHERE. Si dos le dan a iniciar casi al mismo tiempo, solo una peticion
de verdad arranca.

**Quien responde primero una pregunta, y cuando avanzar de pregunta.** Aqui
hay dos problemas al mismo tiempo: saber quien contesto primero, y saber
cuando ya respondieron todos para avanzar. Para lo primero usamos el mismo
truco del UPDATE con condicion en el WHERE. Para lo segundo no alcanza con
eso solo, porque hay que contar cuantos respondieron y esa cuenta se puede
leer mal si dos respuestas llegan casi juntas. Por eso ahi se usa un lock
de fila (SELECT FOR UPDATE) que obliga a que las respuestas de la misma
pregunta se procesen una por una, sin que dos peticiones alcancen a avanzar
la pregunta al mismo tiempo.

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
