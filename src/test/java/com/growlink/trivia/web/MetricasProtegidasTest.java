package com.growlink.trivia.web;

import com.growlink.trivia.config.TokenDeMetricasFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// ATRIBUTO seguridad: /actuator/prometheus solo con el token del scrape (ver TokenDeMetricasFilter). Antes era
// publico, y el nginx del front reenvia /api-trivia/** a este servicio. Mismo contexto que MetricasTest, con el
// registro de Prometheus de verdad. La regla del token caso por caso esta en TokenDeMetricasFilterTest.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class MetricasProtegidasTest {

    // el de application-test.yml
    private static final String TOKEN = "token-de-metricas-solo-para-pruebas";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void conElTokenDelScrapeSeVen() throws Exception {
        assertThat(respuesta(get("/actuator/prometheus").header(TokenDeMetricasFilter.CABECERA, TOKEN)).getStatus())
                .isEqualTo(200);
    }

    @Test
    void sinTokenOConUnoEquivocado401ConSusCabecerasDeRastreo() throws Exception {
        MockHttpServletResponse sinToken = respuesta(get("/actuator/prometheus"));

        assertThat(sinToken.getStatus()).isEqualTo(401);
        assertThat(sinToken.getContentAsString()).contains("message");
        // el rechazo tambien se puede seguir: trae el id de la peticion y la replica que lo dio
        assertThat(sinToken.getHeader("X-Request-Id")).isNotBlank();
        assertThat(sinToken.getHeader("X-Instancia")).isNotBlank();

        assertThat(respuesta(get("/actuator/prometheus").header(TokenDeMetricasFilter.CABECERA, "otro-token-cualquiera-largo"))
                .getStatus()).isEqualTo(401);
        // el token de una persona (Authorization) no sirve para esto
        assertThat(respuesta(get("/actuator/prometheus").header("Authorization", "Bearer " + TOKEN)).getStatus())
                .isEqualTo(401);
    }

    @Test
    void lasVariantesDeLaRutaNoSonOtraPuerta() throws Exception {
        // Spring MVC reconoce estas como el mismo endpoint (o no lo sirve): sin token, ninguna da 200
        for (RequestBuilder variante : new RequestBuilder[]{
                get("/actuator/prometheus/"),
                get("/actuator/prometheus;x=1"),
                get(URI.create("/actuator/prometh%65us")),
                get("/actuator/Prometheus")}) {
            assertThat(respuesta(variante).getStatus()).isNotEqualTo(200);
        }
    }

    @Test
    void elRestoDelServicioNoPideElToken() throws Exception {
        // la salud la pregunta el balanceador sin credenciales, y las demas rutas siguen como estaban
        assertThat(respuesta(get("/actuator/health")).getStatus()).isEqualTo(200);
        assertThat(respuesta(get("/api/salas/NOEXISTE")).getStatus()).isEqualTo(404);
    }

    private MockHttpServletResponse respuesta(RequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion).andReturn().getResponse();
    }
}
