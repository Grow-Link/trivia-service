package com.growlink.trivia.config;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

// ATRIBUTO: observabilidad. Lo mismo que RequestIdFilter, pero para lo que llega por el WebSocket: cada mensaje (el
// CONNECT, cada "unirse", "responder"...) tiene su X-Request-Id mientras se atiende. Sin esto, lo que pasa en una
// partida no tenia id: ni las lineas del log, ni la llamada a usuarios-service que suma la trivia ganada cuando la
// ultima respuesta termina la partida (ver ReenvioDeRequestId).
//
// Si el cliente manda un X-Request-Id valido como cabecera nativa del frame, se respeta (misma regla que
// RequestIdFilter); si no, se crea uno por mensaje.
//
// Un mensaje se atiende en dos hilos y el id tiene que estar en el MDC en los dos:
//   preSend / afterSendCompletion           el hilo del WebSocket, donde corren los interceptores (por ejemplo
//                                           StompAuthChannelInterceptor, que en modo oidc resuelve la identidad
//                                           contra usuarios-service en el CONNECT)
//   beforeHandle / afterMessageHandled      el hilo del canal de entrada que corre el @MessageMapping
// Para pasar de uno al otro, el id viaja en una cabecera del mensaje. Al terminar se borra del MDC: los dos hilos
// son de un pool y el siguiente mensaje no puede quedarse con el id de este.
//
// Va PRIMERO en el canal de entrada (ver WebSocketConfig), para que los interceptores que siguen ya lo tengan.
@Component
public class RequestIdStompInterceptor implements ExecutorChannelInterceptor {

    // la cabecera interna del mensaje (no la nativa de STOMP, que es la que manda el cliente)
    static final String CABECERA_DEL_MENSAJE = "growlinkRequestId";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        String id = RequestIdFilter.idValidoONuevo(primerHeaderNativo(accessor));
        MDC.put(RequestIdFilter.MDC_KEY, id);
        // el accessor del mensaje que llega del WebSocket es mutable: lo que se le escriba queda en el mismo mensaje
        // (como el Principal que pone StompAuthChannelInterceptor). Si no lo fuera, se arma un mensaje con la copia
        if (accessor.isMutable()) {
            accessor.setHeader(CABECERA_DEL_MENSAJE, id);
            return message;
        }
        StompHeaderAccessor copia = StompHeaderAccessor.wrap(message);
        copia.setHeader(CABECERA_DEL_MENSAJE, id);
        return MessageBuilder.createMessage(message.getPayload(), copia.getMessageHeaders());
    }

    @Override
    public void afterSendCompletion(@NonNull Message<?> message, @NonNull MessageChannel channel, boolean sent,
                                    Exception ex) {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Override
    public Message<?> beforeHandle(@NonNull Message<?> message, @NonNull MessageChannel channel,
                                   @NonNull MessageHandler handler) {
        Object id = message.getHeaders().get(CABECERA_DEL_MENSAJE);
        if (id instanceof String valor) {
            MDC.put(RequestIdFilter.MDC_KEY, valor);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(@NonNull Message<?> message, @NonNull MessageChannel channel,
                                    @NonNull MessageHandler handler, Exception ex) {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    // las cabeceras nativas de STOMP son multivalor; aqui solo interesa la primera, con el nombre en cualquier forma
    private static String primerHeaderNativo(StompHeaderAccessor accessor) {
        List<String> valores = accessor.getNativeHeader(RequestIdFilter.HEADER);
        if (valores == null || valores.isEmpty()) {
            valores = accessor.getNativeHeader(RequestIdFilter.HEADER.toLowerCase());
        }
        return valores == null || valores.isEmpty() ? null : valores.get(0);
    }
}
