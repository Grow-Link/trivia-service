package com.growlink.trivia.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// El X-Request-Id es lo que permite seguir una peticion completa en los logs: por eso tiene que
// aparecer siempre (generado si no viene) y, si el que llama ya trae uno, tiene que ser EL MISMO
// de ida y de vuelta, o la traza se parte en dos entre servicios.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void siLaPeticionNoTraeRequestIdSeGeneraUno() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String generado = resultado.getResponse().getHeader("X-Request-Id");
        assertThat(generado).isNotBlank();
        // tiene que ser un UUID de verdad, no una cadena cualquiera
        assertThat(UUID.fromString(generado)).isNotNull();
    }

    @Test
    void dosPeticionesSinRequestIdNoCompartenElMismoId() throws Exception {
        String primero = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        String segundo = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(primero).isNotEqualTo(segundo);
    }

    @Test
    void siLaPeticionTraeRequestIdSePropagaElMismo() throws Exception {
        String elQueManda = "traza-de-prueba-123";

        mockMvc.perform(get("/actuator/health").header("X-Request-Id", elQueManda))
                .andExpect(header().string("X-Request-Id", elQueManda));
    }

    // tambien sale en las respuestas de error, que son justo las que uno va a buscar en los logs
    @Test
    void elRequestIdTambienSaleCuandoLaPeticionFalla() throws Exception {
        mockMvc.perform(get("/api/salas/NOEXISTE").header("X-Request-Id", "traza-del-404"))
                .andExpect(header().string("X-Request-Id", "traza-del-404"));
    }

    // el valor va a terminar escrito en los logs: un header con saltos de linea podria inventar
    // lineas de log falsas, asi que lo que no sea un id no llega al log: se reemplaza por uno nuevo
    // (antes se le quitaban los caracteres raros; ahora es la misma regla de usuarios-service y
    // cursos-service, para que el id que se les reenvia no lo cambien por otro al llegar)
    @Test
    void unRequestIdConBasuraSeReemplazaAntesDeLlegarAlLog() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", "traza\nfalsa WARN algo exploto"))
                .andReturn();

        String devuelto = resultado.getResponse().getHeader("X-Request-Id");
        assertThat(devuelto).doesNotContain("traza").doesNotContain("WARN");
        assertThat(UUID.fromString(devuelto)).isNotNull();
    }

    // la regla de los tres servicios: de 8 a 64 letras, numeros, punto, guion o guion bajo
    @Test
    void seAceptaLoQueAceptanLosOtrosServiciosYNadaMas() throws Exception {
        String justo8 = "abcd-123";
        String justo64 = "a".repeat(64);
        for (String valido : new String[]{justo8, justo64, "traza.con_punto-y-guion"}) {
            assertThat(devueltoPara(valido)).as(valido).isEqualTo(valido);
        }
        for (String invalido : new String[]{"corto", "a".repeat(65), "con:dos:puntos", "con espacio"}) {
            String devuelto = devueltoPara(invalido);
            assertThat(devuelto).as(invalido).isNotEqualTo(invalido);
            assertThat(UUID.fromString(devuelto)).isNotNull();
        }
    }

    private String devueltoPara(String requestId) throws Exception {
        return mockMvc.perform(get("/actuator/health").header("X-Request-Id", requestId))
                .andReturn().getResponse().getHeader("X-Request-Id");
    }

    @Test
    void unRequestIdEnBlancoCuentaComoQueNoVino() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/actuator/health").header("X-Request-Id", "   "))
                .andReturn();

        String devuelto = resultado.getResponse().getHeader("X-Request-Id");
        assertThat(devuelto).isNotBlank();
        assertThat(UUID.fromString(devuelto)).isNotNull();
    }
}
