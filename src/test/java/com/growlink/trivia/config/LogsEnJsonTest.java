package com.growlink.trivia.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// ATRIBUTO observabilidad: los logs salen en JSON, una linea por evento (ver logback-spring.xml). Se prueba con la
// configuracion de verdad y la salida de verdad: lo que el servicio escribe en la consola es lo que lee el almacen de
// logs. Si alguien cambiara el appender o el encoder y dejara de salir JSON, esto falla.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LogsEnJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaLineaDeLogDeUnaPeticionEsJsonConSuIdentificadorServicioEInstancia(CapturedOutput salida)
            throws Exception {
        String rid = "prueba-logs-json-0001";

        // un scrape de metricas sin el token deja una linea WARN (ver TokenDeMetricasFilter)
        String instancia = mockMvc.perform(get("/actuator/prometheus").header("X-Request-Id", rid))
                .andReturn().getResponse().getHeader("X-Instancia");

        JsonNode linea = unicaLineaQueContiene(salida, rid);
        assertThat(linea.get("level").asText()).isEqualTo("WARN");
        assertThat(linea.get("message").asText()).contains("Scrape de metricas rechazado");
        assertThat(linea.get("logger_name").asText()).isEqualTo(TokenDeMetricasFilter.class.getName());
        assertThat(linea.get("@timestamp").asText()).endsWith("Z");
        // el id sale en el campo rid, como en usuarios-service y cursos-service (en el MDC se llama requestId)
        assertThat(linea.get("rid").asText()).isEqualTo(rid);
        assertThat(linea.has("requestId")).as("ya lo dice rid").isFalse();
        assertThat(linea.get("servicio").asText()).isEqualTo("trivia-service");
        assertThat(linea.get("instancia").asText()).isNotBlank().isEqualTo(instancia);
        // campos que se quitaron a proposito: no sirven para buscar, o repiten otro
        assertThat(linea.has("@version")).isFalse();
        assertThat(linea.has("level_value")).isFalse();
        assertThat(linea.has("HOSTNAME")).as("ya lo dice instancia").isFalse();
    }

    @Test
    void unaExcepcionVaDentroDeLaMismaLineaYNoEnLineasSueltas(CapturedOutput salida) throws Exception {
        String marca = "falla-de-prueba-para-los-logs-json";

        LoggerFactory.getLogger(LogsEnJsonTest.class)
                .error(marca, new IllegalStateException("envoltura", new IllegalArgumentException("causa raiz")));

        JsonNode linea = unicaLineaQueContiene(salida, marca);
        assertThat(linea.get("level").asText()).isEqualTo("ERROR");
        String traza = linea.get("stack_trace").asText();
        // la causa raiz va primero, para que el recorte de una traza larga nunca se la lleve
        assertThat(traza.indexOf("IllegalArgumentException: causa raiz"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(traza.indexOf("IllegalStateException: envoltura"));
        // y ninguna linea de la traza quedo suelta en la salida: el almacen la tomaria como otro evento
        assertThat(salida.getOut().lines().filter(l -> l.contains("causa raiz")).toList()).hasSize(1);
        // fuera de una peticion no hay identificador que poner
        assertThat(linea.has("rid")).isFalse();
    }

    // la linea de la salida que trae `texto`: tiene que haber exactamente una y tiene que ser un objeto JSON entero
    private static JsonNode unicaLineaQueContiene(CapturedOutput salida, String texto) throws Exception {
        List<String> lineas = salida.getOut().lines().filter(l -> l.contains(texto)).toList();
        assertThat(lineas).as("lineas con \"" + texto + "\"").hasSize(1);
        JsonNode linea = JSON.readTree(lineas.get(0));
        assertThat(linea.isObject()).isTrue();
        return linea;
    }
}
