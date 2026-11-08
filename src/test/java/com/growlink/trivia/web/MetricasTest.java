package com.growlink.trivia.web;

import com.growlink.trivia.config.MetricsConfig;
import com.growlink.trivia.config.WebSocketMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sin la etiqueta "instancia" en TODAS las metricas, Prometheus suma lo de todas las replicas y
// no se puede ver que una sola esta mal (ni como esta repartiendo el balanceador). Y sin los
// contadores de negocio el tablero solo muestra CPU y memoria, que no dicen nada de la trivia.
// @AutoConfigureObservability porque en las pruebas Spring Boot apaga los exportadores de
// metricas por defecto (deja solo el registro simple en memoria): sin esto no existiria el
// registro de Prometheus y /actuator/prometheus daria 404 aunque en produccion si funcione
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class MetricasTest {

    @Autowired
    private MeterRegistry registry;
    @Autowired
    private MetricsConfig metricsConfig;
    @Autowired
    private WebSocketMetrics webSocketMetrics;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void todasLasMetricasLlevanLaEtiquetaDeInstancia() {
        assertThat(registry.getMeters()).isNotEmpty();

        var sinEtiqueta = registry.getMeters().stream()
                .filter(meter -> meter.getId().getTag("instancia") == null)
                .map(meter -> meter.getId().getName())
                .toList();

        assertThat(sinEtiqueta).as("estas metricas se quedaron sin la etiqueta instancia").isEmpty();
    }

    // el valor sale de la variable de entorno (WEBSITE_INSTANCE_ID) y, cuando no viene, del
    // nombre del host; nunca puede quedar vacio, porque una etiqueta vacia no distingue nada
    @Test
    void laEtiquetaDeInstanciaTraeUnValor() {
        Meter cualquiera = registry.getMeters().get(0);

        assertThat(cualquiera.getId().getTag("instancia"))
                .isNotBlank()
                .isEqualTo(metricsConfig.instancia());
    }

    @Test
    void estanLosContadoresDeNegocio() {
        assertThat(registry.find("trivia.salas.creadas").counter()).isNotNull();
        assertThat(registry.find("trivia.jugadores.unidos").counter()).isNotNull();
        assertThat(registry.find("trivia.partidas.terminadas").counter()).isNotNull();
    }

    @Test
    void estaElGaugeDeConexionesDeWebSocket() {
        Gauge gauge = registry.find("trivia.websocket.conexiones.activas").gauge();

        assertThat(gauge).isNotNull();
        // en una prueba sin WebSocket abiertos el valor es 0, igual que lo que reporta el componente
        assertThat(gauge.value()).isEqualTo(webSocketMetrics.activas());
    }

    @Test
    void crearUnaSalaMueveElContadorDeSalasCreadas() throws Exception {
        Counter contador = registry.find("trivia.salas.creadas").counter();
        assertThat(contador).isNotNull();
        double antes = contador.count();

        mockMvc.perform(post("/api/salas")
                        .contentType("application/json")
                        .content("""
                                {"categoria":"INGENIERIA_SISTEMAS","hostUsuarioId":1,"hostNombre":"Ana",
                                 "numPreguntas":5,"duracionSegundos":10}
                                """))
                .andExpect(status().isCreated());

        assertThat(contador.count()).isEqualTo(antes + 1);
    }

    // el scrape de Prometheus tiene que ver la etiqueta y los contadores, no solo el registro en memoria
    @Test
    void elScrapeDePrometheusTraeLaEtiquetaYLosContadores() throws Exception {
        String cuerpo = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("instancia=\"" + metricsConfig.instancia() + "\"");
        // Prometheus cambia los puntos por guiones bajos y a los contadores les pone el sufijo _total
        assertThat(cuerpo).contains("trivia_salas_creadas_total");
        assertThat(cuerpo).contains("trivia_jugadores_unidos_total");
        assertThat(cuerpo).contains("trivia_partidas_terminadas_total");
        assertThat(cuerpo).contains("trivia_websocket_conexiones_activas");
    }
}
