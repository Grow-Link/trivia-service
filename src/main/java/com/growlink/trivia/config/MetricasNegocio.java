package com.growlink.trivia.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// Los contadores de negocio que se miran en el tablero de Prometheus/Grafana. Son distintos de
// MetricaEvento (que guarda cada evento en la base de datos para el dashboard del curso): estos
// son acumulados en memoria, baratos, y los raspa el scrape de /actuator/prometheus.
//
// Quien los incrementa es MetricaService, que ya era el unico sitio por donde pasan estos tres
// eventos, asi que no hay que repartir llamadas de metricas por todos los servicios.
@Component
public class MetricasNegocio {

    private final Counter salasCreadas;
    private final Counter jugadoresUnidos;
    private final Counter partidasTerminadas;

    public MetricasNegocio(MeterRegistry registry) {
        this.salasCreadas = Counter.builder("trivia.salas.creadas")
                .description("salas de trivia creadas")
                .register(registry);
        this.jugadoresUnidos = Counter.builder("trivia.jugadores.unidos")
                .description("jugadores que se unieron a una sala (no cuenta las reconexiones)")
                .register(registry);
        this.partidasTerminadas = Counter.builder("trivia.partidas.terminadas")
                .description("partidas que llegaron al final, por respuestas, por tiempo o por abandono")
                .register(registry);
    }

    public void salaCreada() {
        salasCreadas.increment();
    }

    public void jugadorUnido() {
        jugadoresUnidos.increment();
    }

    public void partidaTerminada() {
        partidasTerminadas.increment();
    }
}
