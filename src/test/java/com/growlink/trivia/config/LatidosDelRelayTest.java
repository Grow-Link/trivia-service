package com.growlink.trivia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Parameter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Los latidos de la conexion de sistema del relay (ver WebSocketConfig). Importan porque el valor de
// RECEPCION es, por tres, la paciencia de Spring antes de dar la conexion con RabbitMQ por muerta: con
// los 10 s por defecto, 30 s sin un solo frame del broker la cierran, y mientras esta caida lo que la
// aplicacion publique se pierde. En la medicion P1 en Azure eso paso 9 veces en las 3 copias.
//
// Se prueba sin levantar contexto y sin RabbitMQ: se le pasa a configureMessageBroker un
// MessageBrokerRegistry de verdad y se le pregunta al StompBrokerRelayMessageHandler que armo. El
// handler se construye pero nunca se arranca (start()), asi que no abre ninguna conexion TCP. Es el
// mismo criterio de ConfiguracionDeIdentidadTest: llamar a la configuracion derecho se lee mejor y es
// mas rapido que un contexto entero.
class LatidosDelRelayTest {

    private static final long POR_DEFECTO_MS = 10_000;
    private static final String PROPIEDAD_ENVIO = "trivia.broker.relay.heartbeat-send-ms";
    private static final String PROPIEDAD_RECEPCION = "trivia.broker.relay.heartbeat-receive-ms";

    @Test
    void losValoresConfiguradosLleganALosLatidosDelRelay() {
        StompBrokerRelayMessageHandler relay = relayConLatidos(5_000, 20_000);

        assertThat(relay.getSystemHeartbeatSendInterval()).isEqualTo(5_000);
        assertThat(relay.getSystemHeartbeatReceiveInterval()).isEqualTo(20_000);
    }

    // sin las variables puestas el servicio tiene que seguir comportandose igual que antes de este
    // cambio: 10 s y 10 s, los de Spring. Si algun dia alguien "mejora" el valor por defecto aqui, el
    // despliegue cambia de comportamiento sin que nadie toque una variable, y esta prueba lo frena
    @Test
    void sinVariablesPuestasQuedanLosDiezSegundosDeSiempre() {
        StandardEnvironment sinNada = new StandardEnvironment();

        assertThat(sinNada.resolvePlaceholders(expresionQueLee(PROPIEDAD_ENVIO)))
                .isEqualTo(String.valueOf(POR_DEFECTO_MS));
        assertThat(sinNada.resolvePlaceholders(expresionQueLee(PROPIEDAD_RECEPCION)))
                .isEqualTo(String.valueOf(POR_DEFECTO_MS));
        assertThat(relayConLatidos(POR_DEFECTO_MS, POR_DEFECTO_MS).getSystemHeartbeatReceiveInterval())
                .isEqualTo(POR_DEFECTO_MS);
    }

    // y que los nombres de las propiedades sean los que estan en application.yml (y por tanto los que
    // las variables TRIVIA_BROKER_RELAY_HEARTBEAT_* alimentan): un nombre mal escrito no falla, se queda
    // callado en el valor por defecto, que es justo lo que no se podria explicar despues en Azure
    @Test
    void lasPropiedadesSeLeenConElNombreQueTraeApplicationYml() {
        MockEnvironment puestas = new MockEnvironment()
                .withProperty(PROPIEDAD_ENVIO, "15000")
                .withProperty(PROPIEDAD_RECEPCION, "45000");

        assertThat(puestas.resolvePlaceholders(expresionQueLee(PROPIEDAD_ENVIO))).isEqualTo("15000");
        assertThat(puestas.resolvePlaceholders(expresionQueLee(PROPIEDAD_RECEPCION))).isEqualTo("45000");
    }

    // 0 es un valor valido y significa "no mandar latidos" / "no vigilar": se deja pasar tal cual para
    // poder apagar la vigilancia sin recompilar, por si el corte resulta no ser de RabbitMQ
    @Test
    void elCeroPasaTalCualParaPoderApagarLaVigilancia() {
        StompBrokerRelayMessageHandler relay = relayConLatidos(0, 0);

        assertThat(relay.getSystemHeartbeatSendInterval()).isZero();
        assertThat(relay.getSystemHeartbeatReceiveInterval()).isZero();
    }

    // con el relay apagado (una sola instancia, broker en memoria) no hay conexion de sistema que
    // vigilar, y los latidos no tienen donde aplicarse: lo que se registra es el broker simple
    @Test
    void conElRelayApagadoNoSeRegistraNingunRelay() {
        Registro registro = configurar(false, POR_DEFECTO_MS, POR_DEFECTO_MS);

        assertThat(registro.relay()).isNull();
    }

    private StompBrokerRelayMessageHandler relayConLatidos(long envioMs, long recepcionMs) {
        StompBrokerRelayMessageHandler relay = configurar(true, envioMs, recepcionMs).relay();
        assertThat(relay).as("con el relay prendido se tiene que registrar un relay STOMP").isNotNull();
        return relay;
    }

    private Registro configurar(boolean relayActivo, long envioMs, long recepcionMs) {
        Registro registro = new Registro();
        new WebSocketConfig(relayActivo, "rabbitmq", 61613, "growlink", "clave", envioMs, recepcionMs,
                Optional.empty(), new RequestIdStompInterceptor())
                .configureMessageBroker(registro);
        return registro;
    }

    /** La expresion @Value con la que WebSocketConfig lee esa propiedad, tal como esta en el codigo. */
    private static String expresionQueLee(String propiedad) {
        for (Parameter parametro : WebSocketConfig.class.getDeclaredConstructors()[0].getParameters()) {
            Value valor = parametro.getAnnotation(Value.class);
            if (valor != null && valor.value().contains(propiedad)) {
                return valor.value();
            }
        }
        throw new AssertionError("WebSocketConfig no lee ninguna propiedad " + propiedad);
    }

    // getStompBrokerRelay es protected en Spring: una subclase es la forma de preguntarle al registro
    // que quedo configurado, sin reflexion y sin tener que arrancar el handler
    private static final class Registro extends MessageBrokerRegistry {

        private Registro() {
            super(new ExecutorSubscribableChannel(), new ExecutorSubscribableChannel());
        }

        private StompBrokerRelayMessageHandler relay() {
            return getStompBrokerRelay(new ExecutorSubscribableChannel());
        }
    }
}
