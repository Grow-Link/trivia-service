package com.growlink.trivia.config;

import com.growlink.trivia.adapter.security.IdentidadNoDisponibleException;
import com.growlink.trivia.adapter.security.JugadorPrincipal;
import com.growlink.trivia.adapter.security.OidcTokenVerificador;
import com.growlink.trivia.adapter.security.TokenInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.List;

// Interceptor del canal de entrada de STOMP, solo activo cuando auth.mode=oidc (ver AuthModeConfig).
//
// Autentica una sola vez, en el CONNECT, y no en cada mensaje: el token se valida ahi, el sub
// queda como Principal de la sesion y Spring se lo pega a todos los SEND que lleguen despues por
// esa misma conexion. Si el CONNECT no trae un token bueno, la conexion no se abre: asi un
// cliente sin token nunca llega a mandar un SEND, en vez de que cada handler tenga que acordarse
// de revisar la identidad por su cuenta.
//
// Lo que NO se hace aqui es revalidar el vencimiento en cada mensaje: una sesion ya abierta
// sobrevive al vencimiento de su token, igual que una sesion HTTP sobrevive al suyo. Para cortar
// sesiones viejas esta la reconexion del cliente, que vuelve a pasar por este CONNECT con un
// token nuevo.
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final OidcTokenVerificador verificador;

    public StompAuthChannelInterceptor(OidcTokenVerificador verificador) {
        this.verificador = verificador;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // getAccessor (y no StompHeaderAccessor.wrap) porque devuelve el accessor MUTABLE que el
        // mensaje ya trae: lo que se le escriba aqui, incluido el Principal, se queda pegado al
        // mismo mensaje que sigue camino. Un wrap() seria una copia y el Principal se perderia
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        // el X-Request-Id del CONNECT (si el cliente lo manda como header nativo de STOMP) entra
        // al MDC solo mientras se atiende este frame, para que el "conexion aceptada/rechazada"
        // quede en la misma traza que la peticion HTTP que trajo al jugador hasta aqui
        String requestId = primerHeader(accessor, RequestIdFilter.HEADER);
        boolean enElMdc = requestId != null && !requestId.isBlank();
        if (enElMdc) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }
        try {
            Long usuarioId = verificador.usuarioIdDe(primerHeader(accessor, "Authorization"));
            accessor.setUser(new JugadorPrincipal(usuarioId));
            log.info("CONNECT aceptado, sesion {} identificada como el usuario {}",
                    accessor.getSessionId(), usuarioId);
            return accessor.isMutable()
                    ? message
                    : MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } catch (TokenInvalidoException e) {
            log.warn("CONNECT rechazado en la sesion {}: {}", accessor.getSessionId(), e.getMessage());
            // la excepcion hace que Spring le mande un frame ERROR al cliente y cierre el
            // WebSocket; el motivo exacto se queda en el log del servidor y no viaja al cliente
            throw new MessageDeliveryException(message, "CONNECT rechazado: token invalido o ausente");
        } catch (IdentidadNoDisponibleException e) {
            // el token estaba bien, lo que fallo fue usuarios-service. La conexion se rechaza
            // igual: sin identidad no se puede saber quien juega, y caer al sub del token seria
            // deshacer el proposito de resolverla contra usuarios-service. El cliente reintenta
            log.error("CONNECT rechazado en la sesion {}, no se pudo resolver la identidad: {}",
                    accessor.getSessionId(), e.getMessage());
            throw new MessageDeliveryException(message,
                    "CONNECT rechazado: no se pudo verificar la sesion, intente de nuevo");
        } finally {
            if (enElMdc) {
                MDC.remove(RequestIdFilter.MDC_KEY);
            }
        }
    }

    // los headers nativos de STOMP son multivalor; aqui solo interesa el primero. Se busca
    // tambien en minusculas porque "Authorization" y "authorization" son el mismo header para
    // los clientes de STOMP que andan por ahi
    private static String primerHeader(StompHeaderAccessor accessor, String nombre) {
        List<String> valores = accessor.getNativeHeader(nombre);
        if (valores == null || valores.isEmpty()) {
            valores = accessor.getNativeHeader(nombre.toLowerCase());
        }
        return (valores == null || valores.isEmpty()) ? null : valores.get(0);
    }
}
