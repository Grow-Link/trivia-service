package com.growlink.trivia;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

// Un emisor de OIDC de mentiras para las pruebas del modo auth.mode=oidc: genera un par de llaves
// RSA en el momento (nada de llaves de verdad ni de secretos guardados en el repo), publica la
// publica en un JWKS servido por un HttpServer del JDK, y firma tokens con la privada.
//
// El emisor (issuer) ES la direccion de ese servidor, asi la prueba tambien ejercita la ruta por
// defecto que arma AuthModeConfig cuando no se configura auth.oidc.jwks-uri:
// <issuer>/.well-known/jwks.json.
//
// Tambien hay un segundo par de llaves, el "impostor": sirve para comprobar que un token con
// pinta perfecta pero firmado por otro no entra.
public final class LlavesDePrueba {

    public static final String AUDIENCE = "trivia-service";
    public static final String KID = "llave-de-pruebas-1";

    private static final KeyPair PAR = generarPar();
    private static final KeyPair PAR_IMPOSTOR = generarPar();

    private static HttpServer servidor;
    private static String issuer;

    private LlavesDePrueba() {
    }

    // arranca el emisor la primera vez que alguien lo pide y lo deja arriba para toda la corrida;
    // el HttpServer es un daemon de su propio pool, no deja la JVM colgada al terminar
    public static synchronized String issuer() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (Exception e) {
                throw new IllegalStateException("no se pudo levantar el emisor de pruebas", e);
            }
            servidor.createContext("/.well-known/jwks.json", intercambio -> {
                byte[] cuerpo = jwksJson().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            issuer = "http://127.0.0.1:" + servidor.getAddress().getPort();
        }
        return issuer;
    }

    public static String jwksUri() {
        return issuer() + "/.well-known/jwks.json";
    }

    // ---- tokens con forma de Entra ID, para las pruebas de multi-tenant ----
    //
    // Se firman con el MISMO par de llaves de arriba (nada sale a internet, el JWKS sigue siendo
    // el local): lo que cambia es el iss y el tid, que es justo lo que valida EmisorPermitido.

    public static final String TENANT_A = "11111111-2222-3333-4444-555555555555";
    public static final String TENANT_B = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    // el tenant con el que Entra emite los tokens de las cuentas personales de Microsoft
    public static final String TENANT_CUENTAS_PERSONALES = "9188040d-6c67-4c5b-b112-36a304b66dad";

    public static String issuerDeEntra(String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/v2.0";
    }

    // el caso normal: iss y tid del mismo directorio, que es lo que emite Entra de verdad
    public static String tokenDeEntra(long usuarioId, String tenant) {
        return tokenDeEntra(usuarioId, issuerDeEntra(tenant), tenant, AUDIENCE);
    }

    public static String tokenDeEntraConAudiencia(long usuarioId, String tenant, String aud) {
        return tokenDeEntra(usuarioId, issuerDeEntra(tenant), tenant, aud);
    }

    // iss y tid que no cuadran: el token dice venir de un directorio y declarar otro
    public static String tokenDeEntraConTidDeOtroTenant(long usuarioId, String tenantDelIss, String tid) {
        return tokenDeEntra(usuarioId, issuerDeEntra(tenantDelIss), tid, AUDIENCE);
    }

    public static String tokenDeEntraSinTid(long usuarioId, String tenant) {
        return tokenDeEntra(usuarioId, issuerDeEntra(tenant), null, AUDIENCE);
    }

    // para los emisores que SE PARECEN al de Microsoft pero no lo son
    public static String tokenConEseIssuer(long usuarioId, String iss, String tenant) {
        return tokenDeEntra(usuarioId, iss, tenant, AUDIENCE);
    }

    // El sub TAL COMO lo emite Entra de verdad: un texto opaco, no un id. Es el token con el que
    // el servicio tiene que ir a preguntarle a usuarios-service quien es esta persona; si se
    // intentara leer el sub como numero, se rechazaria
    public static String tokenDeEntraConSub(String sub, String tenant) {
        return tokenDeEntraCrudo(sub, issuerDeEntra(tenant), tenant, AUDIENCE, enUnRato());
    }

    public static String tokenDeEntraConSubYAudiencia(String sub, String tenant, String aud) {
        return tokenDeEntraCrudo(sub, issuerDeEntra(tenant), tenant, aud, enUnRato());
    }

    // para probar que el cache de identidades nunca vive mas alla del exp del token
    public static String tokenDeEntraQueVenceEn(long segundos, String sub, String tenant) {
        return tokenDeEntraCrudo(sub, issuerDeEntra(tenant), tenant, AUDIENCE,
                Instant.now().plusSeconds(segundos));
    }

    private static String tokenDeEntra(long usuarioId, String iss, String tid, String aud) {
        return tokenDeEntraCrudo(String.valueOf(usuarioId), iss, tid, aud, enUnRato());
    }

    private static String tokenDeEntraCrudo(String sub, String iss, String tid, String aud, Instant vence) {
        var builder = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(iss)
                .audience().add(aud).and()
                .subject(sub)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(vence));
        if (tid != null) {
            builder.claim("tid", tid);
        }
        return builder.signWith(PAR.getPrivate(), Jwts.SIG.RS256).compact();
    }

    // el JWKS tal como lo publicaria cualquier proveedor de OIDC: modulo y exponente en base64url
    public static String jwksJson() {
        RSAPublicKey publica = (RSAPublicKey) PAR.getPublic();
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"kid\":\"" + KID + "\","
                + "\"n\":\"" + base64url(publica.getModulus().toByteArray()) + "\","
                + "\"e\":\"" + base64url(publica.getPublicExponent().toByteArray()) + "\""
                + "}]}";
    }

    public static String tokenValido(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), KID, null, PAR);
    }

    // el rol sale de un claim del token (auth.oidc.rol-claim, "rol" por defecto): es lo que mira
    // exigirAdmin para /api/metricas/dashboard
    public static String tokenValidoConRol(long usuarioId, String rol) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), KID, rol, PAR);
    }

    // varios proveedores publican el rol como lista en vez de como texto suelto
    public static String tokenValidoConRoles(long usuarioId, List<String> roles) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), KID, roles, PAR);
    }

    // vencido hace un minuto: jjwt no aplica tolerancia de reloj por defecto, asi que esto basta
    public static String tokenVencido(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE,
                Instant.now().minusSeconds(60), KID, null, PAR);
    }

    // sin exp: un token asi no vencería nunca, y uno robado serviria para siempre
    public static String tokenSinVencimiento(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, null, KID, null, PAR);
    }

    public static String tokenDeOtroEmisor(long usuarioId) {
        return rs256(String.valueOf(usuarioId), "https://otro-emisor.invalido", AUDIENCE,
                enUnRato(), KID, null, PAR);
    }

    public static String tokenParaOtraAudiencia(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), "otro-servicio", enUnRato(), KID, null, PAR);
    }

    // mismo kid, mismo emisor, misma audiencia, todo bien... pero firmado con otra llave privada
    public static String tokenFirmadoPorUnImpostor(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), KID, null, PAR_IMPOSTOR);
    }

    public static String tokenSinKid(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), null, null, PAR);
    }

    public static String tokenConUnKidQueNoExiste(long usuarioId) {
        return rs256(String.valueOf(usuarioId), issuer(), AUDIENCE, enUnRato(), "kid-inventado", null, PAR);
    }

    public static String tokenConSubQueNoEsUnId() {
        return rs256("no-soy-un-numero", issuer(), AUDIENCE, enUnRato(), KID, null, PAR);
    }

    public static String tokenSinSub() {
        return rs256(null, issuer(), AUDIENCE, enUnRato(), KID, null, PAR);
    }

    // ---- los dos ataques clasicos contra un verificador de JWT ----

    // alg=none: el token sin firma. Se arma a mano (jjwt no deja firmar "con nada" sin pedirlo
    // aparte) para que sea exactamente lo que mandaria un atacante: header, claims y una tercera
    // parte vacia. Todo lo demas cuadra: emisor, audiencia, kid y un exp del futuro
    public static String tokenConAlgNone(long usuarioId) {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}";
        String claims = "{\"iss\":\"" + issuer() + "\",\"aud\":\"" + AUDIENCE + "\","
                + "\"sub\":\"" + usuarioId + "\",\"exp\":" + enUnRato().getEpochSecond() + "}";
        return base64url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64url(claims.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    // HS256 con un secreto cualquiera: el token no tiene nada que ver con el emisor, pero un
    // verificador que no fije el algoritmo podria intentar validarlo igual
    public static String tokenConHs256(long usuarioId) {
        byte[] secreto = new byte[32];
        new java.security.SecureRandom().nextBytes(secreto);
        return hs256(usuarioId, Keys.hmacShaKeyFor(secreto));
    }

    // La confusion de algoritmo, el ataque que de verdad importa: la llave PUBLICA del emisor es
    // publica (cualquiera la baja del JWKS), asi que el atacante la usa como secreto de HMAC y
    // firma un token con el sub que quiera. Un verificador que acepte cualquier algoritmo lo
    // valida contra esa misma llave y lo da por bueno.
    public static String tokenConHs256FirmadoConLaLlavePublica(long usuarioId) {
        return hs256(usuarioId, Keys.hmacShaKeyFor(PAR.getPublic().getEncoded()));
    }

    private static String hs256(long usuarioId, SecretKey secreto) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer())
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(usuarioId))
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(enUnRato()))
                .signWith(secreto, Jwts.SIG.HS256)
                .compact();
    }

    private static Instant enUnRato() {
        return Instant.now().plusSeconds(600);
    }

    private static String rs256(String sub, String iss, String aud, Instant vence, String kid,
                                Object rol, KeyPair par) {
        var builder = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(iss)
                .audience().add(aud).and()
                .issuedAt(Date.from(Instant.now().minusSeconds(5)));
        if (vence != null) {
            builder.expiration(Date.from(vence));
        }
        if (sub != null) {
            builder.subject(sub);
        }
        if (rol != null) {
            builder.claim("rol", rol);
        }
        return builder.signWith(par.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair generarPar() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo generar el par de llaves RSA de prueba", e);
        }
    }
}
