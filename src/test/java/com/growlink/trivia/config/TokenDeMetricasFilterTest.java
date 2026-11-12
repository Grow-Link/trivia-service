package com.growlink.trivia.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La regla del token del scrape y que rutas cubre, caso por caso (ver TokenDeMetricasFilter). Con el servicio
// levantado lo prueba MetricasProtegidasTest.
class TokenDeMetricasFilterTest {

    private static final String TOKEN = "un-token-de-scrape-largo";

    @Test
    void soloEntraConElTokenExacto() {
        TokenDeMetricasFilter filtro = new TokenDeMetricasFilter(TOKEN);

        assertThat(filtro.valido(conCabecera(TOKEN))).isTrue();
        // los espacios alrededor no cuentan (se cuelan al copiar el valor a una variable de entorno)
        assertThat(filtro.valido(conCabecera("  " + TOKEN + " "))).isTrue();
        assertThat(filtro.valido(conCabecera(TOKEN.toUpperCase()))).isFalse();
        assertThat(filtro.valido(conCabecera(TOKEN + "x"))).isFalse();
        assertThat(filtro.valido(conCabecera(""))).isFalse();
        assertThat(filtro.valido(new MockHttpServletRequest())).isFalse();
    }

    @Test
    void sinTokenConfiguradoNoEntraNadie() {
        // olvidar METRICAS_TOKEN deja las metricas cerradas, nunca abiertas
        for (String configurado : new String[]{"", "   ", null}) {
            TokenDeMetricasFilter filtro = new TokenDeMetricasFilter(configurado);

            assertThat(filtro.valido(conCabecera(""))).isFalse();
            assertThat(filtro.valido(new MockHttpServletRequest())).isFalse();
        }
    }

    @Test
    void unTokenCortoNoDejaArrancar() {
        assertThatThrownBy(() -> new TokenDeMetricasFilter("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("METRICAS_TOKEN");
    }

    @Test
    void cubreLaRutaDeMetricasConTodasSusFormasYNadaMas() {
        TokenDeMetricasFilter filtro = new TokenDeMetricasFilter(TOKEN);

        // la ruta y las formas en que Spring MVC la sigue reconociendo
        for (String ruta : new String[]{"/actuator/prometheus", "/actuator/prometheus/", "/actuator/prometheus;x=1",
                "/actuator/prometh%65us", "/actuator/prometheus/otra"}) {
            assertThat(filtro.shouldNotFilter(peticionA(ruta))).as(ruta).isFalse();
        }
        // lo demas no pasa por el filtro
        for (String ruta : new String[]{"/actuator/health", "/actuator/health/readiness", "/api/salas", "/ws",
                "/actuator/prometheusx"}) {
            assertThat(filtro.shouldNotFilter(peticionA(ruta))).as(ruta).isTrue();
        }
    }

    private static MockHttpServletRequest conCabecera(String valor) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader(TokenDeMetricasFilter.CABECERA, valor);
        return peticion;
    }

    private static MockHttpServletRequest peticionA(String uri) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", uri);
        peticion.setRequestURI(uri);
        return peticion;
    }
}
