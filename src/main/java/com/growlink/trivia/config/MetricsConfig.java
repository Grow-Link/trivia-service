package com.growlink.trivia.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.util.List;

// Etiqueta comun "instancia" en TODAS las metricas. Sin ella, cuando hay varias replicas detras
// del balanceador, Prometheus suma los valores de todas y no se puede ver que una sola esta mal
// (ni cuanto trafico le toca a cada una).
//
// El valor sale de la variable de entorno que ya pone el hosting; en Azure App Service es
// WEBSITE_INSTANCE_ID. Si no viene (local, docker-compose), se usa el nombre del host, que es el
// mismo criterio del header X-Instancia de InstanciaHeaderFilter: asi el header y las metricas
// hablan de la misma replica con el mismo nombre.
@Configuration
public class MetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

    private final String instancia;

    public MetricsConfig(@Value("${observabilidad.instancia:}") String instanciaConfigurada) {
        this.instancia = (instanciaConfigurada == null || instanciaConfigurada.isBlank())
                ? nombreDeHost()
                : instanciaConfigurada.trim();
        log.info("metricas etiquetadas con instancia={}", instancia);
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> etiquetaDeInstancia() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.commonTags(List.of(Tag.of("instancia", instancia))));
    }

    // visible para las pruebas, que comprueban que la etiqueta trae el valor esperado
    public String instancia() {
        return instancia;
    }

    private static String nombreDeHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "desconocida";
        }
    }
}
