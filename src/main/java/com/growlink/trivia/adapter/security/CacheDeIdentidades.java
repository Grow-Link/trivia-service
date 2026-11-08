package com.growlink.trivia.adapter.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Cache de "este token es el usuario N con el rol R", para no preguntarle a usuarios-service en
// cada peticion. Sin esto, cada GET de /api/partidas y cada CONNECT del WebSocket se convertiria
// en una llamada HTTP extra, y usuarios-service pasaria a ser el cuello de botella de todo.
//
// Tres decisiones que vale explicar:
//
// 1. La clave es el SHA-256 del token, no el token. El valor cacheado va asociado a un token
//    concreto (dos personas distintas tienen tokens distintos, y el mismo token renovado es otra
//    clave), pero guardar el token entero en memoria es guardar una credencial viva: si algo
//    volcara este mapa a un log o a un heap dump, serian sesiones regaladas. El hash alcanza para
//    comparar y no sirve para suplantar a nadie.
//
// 2. La entrada nunca vive mas alla del exp del token. Si no, un token vencido seguiria entrando
//    por el cache hasta que se cumpliera el TTL, que es exactamente lo que el exp viene a evitar.
//    Se guarda el minimo entre (ahora + TTL) y el exp.
//
// 3. Tope de tamano con descarte del menos usado. Sin tope, cada token distinto deja una entrada
//    para siempre: un atacante que mande miles de tokens validos de usuarios distintos (o
//    simplemente mucho trafico real con tokens que se renuevan) llenaria la memoria.
//
// LinkedHashMap en modo acceso + synchronizedMap: da LRU de verdad con tope duro, y el candado no
// pesa al lado de la llamada HTTP que esta evitando. get() en modo acceso es una modificacion
// estructural, asi que tiene que ir sincronizado igual que put().
public class CacheDeIdentidades {

    public static final int TAMANO_MAXIMO_POR_DEFECTO = 10_000;

    private record Entrada(Identidad identidad, Instant vence) {
    }

    private final Map<String, Entrada> entradas;
    private final Duration duracion;

    public CacheDeIdentidades(Duration duracion, int tamanoMaximo) {
        this.duracion = duracion;
        this.entradas = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Entrada> eldest) {
                        return size() > tamanoMaximo;
                    }
                });
    }

    public static String clave(String jwt) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(jwt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo esta muy roto y no hay que seguir
            throw new IllegalStateException("no hay SHA-256 en esta JVM", e);
        }
    }

    // vacio si no esta o si ya vencio. Una entrada vencida se borra al leerla: asi los tokens que
    // nadie vuelve a usar no se quedan ocupando lugar hasta que el tope los empuje
    public Optional<Identidad> buscar(String jwt) {
        String clave = clave(jwt);
        Entrada entrada = entradas.get(clave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (!Instant.now().isBefore(entrada.vence())) {
            entradas.remove(clave);
            return Optional.empty();
        }
        return Optional.of(entrada.identidad());
    }

    // expDelToken puede ser null solo en pruebas; en produccion OidcTokenVerificador ya exige exp
    public void guardar(String jwt, Identidad identidad, Instant expDelToken) {
        Instant porTtl = Instant.now().plus(duracion);
        Instant vence = (expDelToken == null || porTtl.isBefore(expDelToken)) ? porTtl : expDelToken;
        // un token al que ya no le queda vida no se cachea: la entrada naceria vencida
        if (!Instant.now().isBefore(vence)) {
            return;
        }
        entradas.put(clave(jwt), new Entrada(identidad, vence));
    }

    // para las pruebas y para el log de arranque
    public int tamano() {
        return entradas.size();
    }
}
