package com.growlink.trivia.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.trivia.adapter.persistence.SalaPreguntaRepository;
import com.growlink.trivia.application.JuegoService;
import com.growlink.trivia.application.SalaService;
import com.growlink.trivia.application.UsuariosClient;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.SalaTrivia;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.growlink.trivia.RespuestasDePrueba.correcta;
import static com.growlink.trivia.RespuestasDePrueba.incorrecta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El historial de competencias (ganadores, mis partidas, salon de la fama) y los retos "te reto a una trivia".
// Las pruebas comparten base, por eso usan usuarios con ids propios y no cuentan totales globales.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartidasYRetosTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SalaService salaService;
    @Autowired
    private JuegoService juegoService;
    @Autowired
    private SalaPreguntaRepository preguntas;

    @MockBean
    private UsuariosClient usuariosClient;

    @Value("${growlink.jwt.secret}")
    private String secret;

    // ------------------------------------------------------------- historial

    @Test
    void sinSesionNoSeVeElHistorial() throws Exception {
        mockMvc.perform(get("/api/partidas/ganadores")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/partidas/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/partidas/salon-de-la-fama")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/partidas/ABC123")).andExpect(status().isUnauthorized());
    }

    @Test
    void unaPartidaTerminadaQuedaRegistradaConGanadorYTablaCompleta() throws Exception {
        SalaTrivia sala = jugar(501L, "Valeria", 502L, "Bruno", 503L, "Camilo");

        JsonNode detalle = leer("/api/partidas/" + sala.getCodigo(), 501L);
        JsonNode partida = detalle.get("partida");
        assertThat(partida.get("codigo").asText()).isEqualTo(sala.getCodigo());
        assertThat(partida.get("categoria").asText()).isEqualTo("INGENIERIA_SISTEMAS");
        assertThat(partida.get("numPreguntas").asInt()).isEqualTo(5);
        assertThat(partida.get("totalJugadores").asInt()).isEqualTo(3);
        assertThat(partida.get("ganadorUsuarioId").asLong()).isEqualTo(501L);
        assertThat(partida.get("ganadorNombre").asText()).isEqualTo("Valeria");
        assertThat(partida.get("puntosGanador").asInt()).isGreaterThan(0);
        assertThat(partida.get("empate").asBoolean()).isFalse();
        assertThat(partida.get("finalizadaEn").asText()).isNotBlank();

        JsonNode ranking = detalle.get("ranking");
        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).get("usuarioId").asLong()).isEqualTo(501L);
        assertThat(ranking.get(0).get("posicion").asInt()).isEqualTo(1);
        assertThat(ranking.get(0).get("aciertos").asInt()).isEqualTo(5);
        assertThat(ranking.get(1).get("usuarioId").asLong()).isEqualTo(502L);
        assertThat(ranking.get(1).get("aciertos").asInt()).isEqualTo(3);
        assertThat(ranking.get(2).get("usuarioId").asLong()).isEqualTo(503L);
        assertThat(ranking.get(2).get("puntos").asInt()).isZero();
        assertThat(ranking.get(2).get("aciertos").asInt()).isZero();

        // aparece en la lista de ultimos ganadores, con su podio
        JsonNode ganadores = leer("/api/partidas/ganadores?limite=50", 999L);
        JsonNode esta = encontrar(ganadores, sala.getCodigo());
        assertThat(esta).isNotNull();
        assertThat(esta.get("podio")).hasSize(3);
        assertThat(esta.get("partida").get("ganadorNombre").asText()).isEqualTo("Valeria");

        // el ganador suma su victoria una sola vez
        verify(usuariosClient, times(1)).registrarVictoria(501L);
    }

    @Test
    void cadaQuienVeSusPartidasYSuPosicion() throws Exception {
        SalaTrivia sala = jugar(511L, "Elena", 512L, "Felipe", 513L, "Gloria");

        JsonNode deFelipe = leer("/api/partidas/mias", 512L);
        assertThat(deFelipe).hasSize(1);
        assertThat(deFelipe.get(0).get("partida").get("codigo").asText()).isEqualTo(sala.getCodigo());
        assertThat(deFelipe.get(0).get("miPosicion").asInt()).isEqualTo(2);
        assertThat(deFelipe.get(0).get("misAciertos").asInt()).isEqualTo(3);

        // quien no jugo no ve nada
        assertThat(leer("/api/partidas/mias", 9999L)).isEmpty();
    }

    @Test
    void elSalonDeLaFamaCuentaVictoriasPorPersona() throws Exception {
        jugar(521L, "Hugo", 522L, "Irene", 523L, "Jorge");
        jugar(521L, "Hugo", 522L, "Irene", 523L, "Jorge");
        jugar(522L, "Irene", 521L, "Hugo", 523L, "Jorge");

        JsonNode salon = leer("/api/partidas/salon-de-la-fama?limite=50", 999L);
        JsonNode hugo = porUsuario(salon, 521L);
        JsonNode irene = porUsuario(salon, 522L);
        assertThat(hugo).isNotNull();
        assertThat(hugo.get("victorias").asLong()).isEqualTo(2);
        assertThat(hugo.get("nombre").asText()).isEqualTo("Hugo");
        assertThat(hugo.get("mejorPuntaje").asInt()).isGreaterThan(0);
        assertThat(irene.get("victorias").asLong()).isEqualTo(1);
        // Jorge nunca gano, no sale
        assertThat(porUsuario(salon, 523L)).isNull();
        // y Hugo (2 victorias) va antes que Irene (1)
        assertThat(indiceDe(salon, 521L)).isLessThan(indiceDe(salon, 522L));
    }

    @Test
    void siNadieSumaPuntosLaPartidaQuedaSinGanador() throws Exception {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, 531L, "Karen", 5, 10);
        salaService.unirse(sala.getCodigo(), 532L, "Luis");
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), 531L, i, incorrecta(preguntas, sala, i));
            juegoService.responder(sala.getCodigo(), 532L, i, incorrecta(preguntas, sala, i));
        }
        JsonNode partida = leer("/api/partidas/" + sala.getCodigo(), 531L).get("partida");
        assertThat(partida.has("ganadorUsuarioId")).isFalse();
        assertThat(partida.get("puntosGanador").asInt()).isZero();
        assertThat(partida.get("empate").asBoolean()).isFalse();
    }

    @Test
    void unaPartidaQueNoExisteDa404() throws Exception {
        mockMvc.perform(get("/api/partidas/NOEXISTE").header("Authorization", token(1L)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- retos

    @Test
    void retarCreaLaSalaYLaOtraPersonaVeElReto() throws Exception {
        JsonNode reto = retar(601L, "Marta", 602L, "Nicolas", "Vamos con todo", 201);
        assertThat(reto.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(reto.get("mensaje").asText()).isEqualTo("Vamos con todo");
        assertThat(reto.get("retadorUsuarioId").asLong()).isEqualTo(601L);

        // la sala ya existe, esperando, con la retadora adentro
        JsonNode sala = leer("/api/salas/" + reto.get("codigoSala").asText(), 601L);
        assertThat(sala.get("estado").asText()).isEqualTo("ESPERANDO");

        JsonNode bandeja = leer("/api/retos/recibidos", 602L);
        assertThat(encontrarReto(bandeja, reto.get("id").asLong())).isNotNull();
        // ni la retadora ni un tercero lo ven en su bandeja de recibidos
        assertThat(encontrarReto(leer("/api/retos/recibidos", 601L), reto.get("id").asLong())).isNull();
        assertThat(encontrarReto(leer("/api/retos/recibidos", 603L), reto.get("id").asLong())).isNull();
        // pero la retadora lo ve entre los enviados
        assertThat(encontrarReto(leer("/api/retos/enviados", 601L), reto.get("id").asLong())).isNotNull();
    }

    @Test
    void elMensajeVacioUsaUnoPorDefectoYElMuyLargoSeRechaza() throws Exception {
        JsonNode reto = retar(611L, "Olga", 612L, "Pablo", "", 201);
        assertThat(reto.get("mensaje").asText()).isNotBlank();
        retar(611L, "Olga", 613L, "Quique", "x".repeat(141), 400);
    }

    @Test
    void nadieSePuedeRetarASiMismoNiMandarDosRetosPendientesALaMismaPersona() throws Exception {
        retar(621L, "Rosa", 621L, "Rosa", "yo contra mi", 400);
        retar(621L, "Rosa", 622L, "Saul", "uno", 201);
        retar(621L, "Rosa", 622L, "Saul", "dos", 409);
    }

    @Test
    void soloLaPersonaRetadaPuedeAceptarYSoloUnaVez() throws Exception {
        JsonNode reto = retar(631L, "Tania", 632L, "Ulises", "va", 201);
        long id = reto.get("id").asLong();

        // un tercero y la propia retadora reciben 404: para ellos el reto no existe
        responder(id, "aceptar", 633L, 404);
        responder(id, "aceptar", 631L, 404);

        JsonNode aceptado = objectMapper.readTree(responder(id, "aceptar", 632L, 200));
        assertThat(aceptado.get("estado").asText()).isEqualTo("ACEPTADO");
        assertThat(aceptado.get("codigoSala").asText()).isEqualTo(reto.get("codigoSala").asText());
        // ya no esta en la bandeja
        assertThat(encontrarReto(leer("/api/retos/recibidos", 632L), id)).isNull();
        // y no se puede aceptar ni rechazar otra vez
        responder(id, "aceptar", 632L, 409);
        responder(id, "rechazar", 632L, 409);
        // la retadora ve que fue aceptado
        assertThat(encontrarReto(leer("/api/retos/enviados", 631L), id).get("estado").asText()).isEqualTo("ACEPTADO");
    }

    @Test
    void rechazarDejaElRetoRechazadoYLaRetadoraPuedeRetarDeNuevo() throws Exception {
        JsonNode reto = retar(641L, "Vera", 642L, "Walter", "va", 201);
        long id = reto.get("id").asLong();
        JsonNode rechazado = objectMapper.readTree(responder(id, "rechazar", 642L, 200));
        assertThat(rechazado.get("estado").asText()).isEqualTo("RECHAZADO");
        retar(641L, "Vera", 642L, "Walter", "otra vez", 201);
    }

    @Test
    void aceptarYRechazarAlMismoTiempoSoloUnoGana() throws Exception {
        JsonNode reto = retar(651L, "Xenia", 652L, "Yago", "va", 201);
        long id = reto.get("id").asLong();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<Integer>> intentos = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String accion = i % 2 == 0 ? "aceptar" : "rechazar";
            intentos.add(pool.submit(() -> {
                arrancar.await();
                MvcResult r = mockMvc.perform(post("/api/retos/" + id + "/" + accion)
                        .header("Authorization", token(652L))).andReturn();
                return r.getResponse().getStatus();
            }));
        }
        arrancar.countDown();
        int exitos = 0;
        for (Future<Integer> f : intentos) {
            int estado = f.get(15, TimeUnit.SECONDS);
            assertThat(estado).isIn(200, 409);
            if (estado == 200) {
                exitos++;
            }
        }
        pool.shutdown();
        assertThat(exitos).isEqualTo(1);
    }

    @Test
    void siLaSalaYaEmpezoElRetoNoSePuedeAceptar() throws Exception {
        JsonNode reto = retar(661L, "Zoe", 662L, "Abel", "va", 201);
        String codigo = reto.get("codigoSala").asText();
        salaService.unirse(codigo, 663L, "Beatriz");
        salaService.iniciar(codigo);

        responder(reto.get("id").asLong(), "aceptar", 662L, 409);
    }

    @Test
    void sinSesionNoSePuedeRetarNiVerRetos() throws Exception {
        mockMvc.perform(get("/api/retos/recibidos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/retos/enviados")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/retos").contentType("application/json").content(objectMapper.writeValueAsString(Map.of("retadoUsuarioId", 2, "retadoNombre", "B", "retadorNombre", "A", "categoria", "MATEMATICAS", "numPreguntas", 5, "duracionSegundos", 10))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/retos/1/aceptar")).andExpect(status().isUnauthorized());
    }

    @Test
    void quienReta_salePorElTokenYNoPorElCuerpo() throws Exception {
        // aunque el cuerpo diga otra cosa, el retador es quien tiene la sesion
        JsonNode reto = retar(671L, "Carlos", 672L, "Diana", "va", 201);
        assertThat(reto.get("retadorUsuarioId").asLong()).isEqualTo(671L);
    }

    // ------------------------------------------------------------- ayudas

    // Juega una partida de 5 preguntas: el primero acierta las 5, el segundo 3 y el tercero ninguna
    private SalaTrivia jugar(long id1, String n1, long id2, String n2, long id3, String n3) {
        SalaTrivia sala = salaService.crear(Categoria.INGENIERIA_SISTEMAS, id1, n1, 5, 10);
        salaService.unirse(sala.getCodigo(), id2, n2);
        salaService.unirse(sala.getCodigo(), id3, n3);
        salaService.iniciar(sala.getCodigo());
        juegoService.empezarJuego(salaService.obtenerPorCodigo(sala.getCodigo()));
        for (int i = 0; i < 5; i++) {
            juegoService.responder(sala.getCodigo(), id1, i, correcta(preguntas, sala, i));
            juegoService.responder(sala.getCodigo(), id2, i, i < 3 ? correcta(preguntas, sala, i) : incorrecta(preguntas, sala, i));
            juegoService.responder(sala.getCodigo(), id3, i, incorrecta(preguntas, sala, i));
        }
        return sala;
    }

    private JsonNode retar(long retadorId, String retadorNombre, long retadoId, String retadoNombre, String mensaje,
                           int estadoEsperado) throws Exception {
        Map<String, Object> cuerpo = Map.of("retadoUsuarioId", retadoId, "retadoNombre", retadoNombre,
                "retadorNombre", retadorNombre, "categoria", "INGENIERIA_SISTEMAS", "numPreguntas", 5,
                "duracionSegundos", 10, "mensaje", mensaje);
        String respuesta = mockMvc.perform(post("/api/retos").header("Authorization", token(retadorId))
                        .contentType("application/json").content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().is(estadoEsperado)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(respuesta);
    }

    private String responder(long retoId, String accion, long usuarioId, int estadoEsperado) throws Exception {
        return mockMvc.perform(post("/api/retos/" + retoId + "/" + accion).header("Authorization", token(usuarioId)))
                .andExpect(status().is(estadoEsperado)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode leer(String url, long usuarioId) throws Exception {
        String cuerpo = mockMvc.perform(get(url).header("Authorization", token(usuarioId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(cuerpo);
    }

    private JsonNode encontrar(JsonNode lista, String codigo) {
        for (JsonNode e : lista) {
            if (e.get("partida").get("codigo").asText().equals(codigo)) {
                return e;
            }
        }
        return null;
    }

    private JsonNode encontrarReto(JsonNode lista, long id) {
        for (JsonNode e : lista) {
            if (e.get("id").asLong() == id) {
                return e;
            }
        }
        return null;
    }

    private JsonNode porUsuario(JsonNode salon, long usuarioId) {
        int i = indiceDe(salon, usuarioId);
        return i < 0 ? null : salon.get(i);
    }

    private int indiceDe(JsonNode salon, long usuarioId) {
        for (int i = 0; i < salon.size(); i++) {
            if (salon.get(i).get("usuarioId").asLong() == usuarioId) {
                return i;
            }
        }
        return -1;
    }

    private String token(long usuarioId) {
        return "Bearer " + Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", "USUARIO")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
