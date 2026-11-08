package com.growlink.trivia.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Las tres puertas que mira la infraestructura: /health para el humano y el balanceador,
// /health/readiness para saber si esta replica ya puede recibir trafico, y /health/liveness para
// saber si hay que reiniciarla. Las dos probes no se prenden solas fuera de Kubernetes, hay que
// pedirlas en application.yml, asi que esta prueba es la que avisa si alguien las apaga.
// @AutoConfigureObservability porque en las pruebas Spring Boot apaga los exportadores de
// metricas por defecto (deja solo el registro simple en memoria): sin esto no existiria el
// registro de Prometheus y /actuator/prometheus daria 404 aunque en produccion si funcione
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class ActuatorHealthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elHealthRespondeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void laProbeDeReadinessEstaHabilitada() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void laProbeDeLivenessEstaHabilitada() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // el health no exige autenticacion: si algun dia se mete una cadena de filtros de seguridad,
    // esta prueba falla y obliga a dejar /actuator/health/** en permitAll, porque el balanceador
    // y el orquestador preguntan sin credenciales
    @Test
    void elHealthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // show-details: never -> el health dice si la instancia esta viva y nada mas. El detalle
    // (estado de la base de datos, espacio en disco, de que piezas depende el servicio) es
    // informacion util para quien lo quiera atacar, y el balanceador no la necesita
    @Test
    void elHealthNoCuentaElDetalleDeLosComponentes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    // las metricas se exponen en el mismo formato que ya raspa el resto del proyecto
    @Test
    void lasMetricasSalenEnFormatoPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    // Solo health (con sus probes) y prometheus estan expuestos por web. Todo lo demas que trae
    // actuator queda cerrado: /env y /configprops sueltan la configuracion entera (con las urls y
    // los nombres de las variables de entorno), /beans y /mappings el mapa interno del servicio,
    // /info y /metrics no los usa nadie aqui. Si alguien agrega uno a
    // management.endpoints.web.exposure.include sin pensarlo, esta prueba lo frena
    @Test
    void losDemasEndpointsDeActuatorNoEstanExpuestos() throws Exception {
        for (String cerrado : List.of("info", "metrics", "env", "configprops", "beans", "mappings",
                "loggers", "threaddump", "heapdump", "scheduledtasks", "caches")) {
            mockMvc.perform(get("/actuator/" + cerrado))
                    .andExpect(status().isNotFound());
        }
    }
}
