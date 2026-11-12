package com.growlink.trivia.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Cada mensaje del WebSocket tiene su X-Request-Id mientras se atiende, en los dos hilos que lo tocan, y no se le
// queda pegado al hilo cuando termina (ver RequestIdStompInterceptor). Con el servicio levantado lo prueba
// ReenvioDeRequestIdIntegrationTest.
class RequestIdStompInterceptorTest {

    private final RequestIdStompInterceptor interceptor = new RequestIdStompInterceptor();
    private final MessageChannel canal = mock(MessageChannel.class);
    private final MessageHandler handler = mock(MessageHandler.class);

    @AfterEach
    void limpiar() {
        MDC.clear();
    }

    @Test
    void unIdValidoDelClienteSeRespetaEnLosDosHilosYSeBorraAlTerminar() {
        Message<?> mensaje = frame("respuesta-del-cliente-01");

        // el hilo del WebSocket: los interceptores que siguen ya ven el id
        Message<?> enviado = interceptor.preSend(mensaje, canal);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("respuesta-del-cliente-01");
        interceptor.afterSendCompletion(enviado, canal, true, null);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        // el hilo del canal de entrada, que corre el @MessageMapping: el id viajo en el mensaje
        interceptor.beforeHandle(enviado, canal, handler);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("respuesta-del-cliente-01");
        interceptor.afterMessageHandled(enviado, canal, handler, null);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void sinIdOConUnoInvalidoSeCreaUnoNuevoPorMensaje() {
        for (String recibido : new String[]{null, "corto", "con espacios y saltos\nde linea", "a:b:c:d:e:f:g"}) {
            Message<?> enviado = interceptor.preSend(frame(recibido), canal);
            String id = MDC.get(RequestIdFilter.MDC_KEY);

            assertThat(id).as("para " + recibido).matches("^[A-Za-z0-9._-]{8,64}$").isNotEqualTo(recibido);
            assertThat(enviado.getHeaders().get(RequestIdStompInterceptor.CABECERA_DEL_MENSAJE)).isEqualTo(id);
            interceptor.afterSendCompletion(enviado, canal, true, null);
        }
        // y dos mensajes sin id no comparten el mismo
        String uno = idDe(interceptor.preSend(frame(null), canal));
        String otro = idDe(interceptor.preSend(frame(null), canal));
        assertThat(uno).isNotEqualTo(otro);
    }

    @Test
    void siElMensajeNoEsMutableSeArmaUnoNuevoConElId() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/salas/ABC123/responder");
        Message<byte[]> inmutable = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> enviado = interceptor.preSend(inmutable, canal);

        assertThat(enviado.getHeaders().get(RequestIdStompInterceptor.CABECERA_DEL_MENSAJE))
                .isEqualTo(MDC.get(RequestIdFilter.MDC_KEY));
    }

    private static Message<byte[]> frame(String requestId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/salas/ABC123/responder");
        if (requestId != null) {
            accessor.setNativeHeader(RequestIdFilter.HEADER, requestId);
        }
        // como llega del WebSocket: con el accessor todavia mutable
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static String idDe(Message<?> mensaje) {
        return (String) mensaje.getHeaders().get(RequestIdStompInterceptor.CABECERA_DEL_MENSAJE);
    }
}
