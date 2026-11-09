package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Quien decide si el emisor (iss) de un token es aceptable. Son dos modos, y el de siempre es el
// primero:
//
//   fijo (auth.multi-tenant=false, el valor por defecto)
//       el iss tiene que ser exactamente el que diga AUTH_ISSUER_URI. Es lo que hacia el
//       servicio antes de que existiera esta clase, sin un solo cambio.
//
//   multi-tenant de Entra (auth.multi-tenant=true)
//       la app esta registrada para cuentas de CUALQUIER directorio organizacional y cuentas
//       personales de Microsoft, asi que el iss trae el tenant de quien inicia sesion y no se
//       puede comparar contra uno fijo: seria rechazar a casi todo el mundo. En vez de eso se
//       exige que el iss tenga exactamente la forma del emisor v2 de Entra y, lo importante,
//       que el claim tid cuadre con el GUID que viene DENTRO del iss.
//
// Por que el tid no es un detalle: sin el, aceptar "cualquier tenant" significaria creerle al iss
// a secas. Con el, el token tiene que ser internamente consistente, y como el iss va firmado,
// nadie puede cambiarle el tenant a un token ajeno. Opcionalmente AUTH_ALLOWED_TENANTS cierra la
// puerta a una lista corta de directorios.
public final class EmisorPermitido {

    // El emisor v2 de Entra, anclado y con el patron completo. Anclado y usado con matches() a
    // proposito: lo que se esta filtrando aqui son dominios que SE PARECEN al de Microsoft
    // (login.microsoftonline.com.evil.com), el mismo host por http, o un iss con basura pegada
    // al final. Un find() sin anclas dejaria pasar todos esos.
    private static final Pattern ENTRA_V2 = Pattern.compile(
            "^https://login\\.microsoftonline\\.com/"
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                    + "/v2\\.0$");

    // el GUID suelto, para validar la lista de tenants permitidos al arrancar
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    // el tenant con el que Entra emite los tokens de las cuentas personales de Microsoft; no es
    // un caso aparte, cae por el mismo patron de arriba, pero vale tenerlo nombrado
    public static final String TENANT_CUENTAS_PERSONALES = "9188040d-6c67-4c5b-b112-36a304b66dad";

    private final boolean multiTenant;
    private final String issuerFijo;
    private final Set<String> tenantsPermitidos;

    private EmisorPermitido(boolean multiTenant, String issuerFijo, Set<String> tenantsPermitidos) {
        this.multiTenant = multiTenant;
        this.issuerFijo = issuerFijo;
        this.tenantsPermitidos = tenantsPermitidos;
    }

    // un solo emisor, comparado tal cual: el comportamiento de siempre
    public static EmisorPermitido fijo(String issuerUri) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("el emisor fijo no puede estar vacio");
        }
        return new EmisorPermitido(false, issuerUri.trim(), Set.of());
    }

    // cualquier tenant de Entra que cumpla el patron, o solo los de la lista si viene con valores.
    // La lista se valida aqui (al arrancar) y no en cada token: un GUID mal escrito en la
    // configuracion tiene que tumbar el arranque, no quedarse callado rechazando a ese tenant
    public static EmisorPermitido entraMultiTenant(Set<String> tenantsPermitidos) {
        Set<String> normalizados = new LinkedHashSet<>();
        if (tenantsPermitidos != null) {
            for (String tenant : tenantsPermitidos) {
                if (tenant == null || tenant.isBlank()) {
                    continue;
                }
                String limpio = tenant.trim();
                if (!GUID.matcher(limpio).matches()) {
                    throw new IllegalStateException(
                            "auth.allowed-tenants (AUTH_ALLOWED_TENANTS) trae algo que no es un GUID de tenant: " + limpio);
                }
                // en minusculas porque los GUID se comparan sin distinguir mayusculas
                normalizados.add(limpio.toLowerCase(Locale.ROOT));
            }
        }
        return new EmisorPermitido(true, null, Set.copyOf(normalizados));
    }

    public boolean esMultiTenant() {
        return multiTenant;
    }

    // visible para el log de arranque y para las pruebas
    public Set<String> tenantsPermitidos() {
        return tenantsPermitidos;
    }

    public void exigir(Claims claims) {
        String iss = claims.getIssuer();
        if (iss == null || iss.isBlank()) {
            throw new TokenInvalidoException("el token no trae iss, no se sabe quien lo emitio");
        }
        if (!multiTenant) {
            if (!issuerFijo.equals(iss)) {
                throw new TokenInvalidoException("el emisor del token (" + iss + ") no es " + issuerFijo);
            }
            return;
        }

        Matcher emisor = ENTRA_V2.matcher(iss);
        if (!emisor.matches()) {
            throw new TokenInvalidoException(
                    "el emisor del token (" + iss + ") no tiene la forma de un emisor v2 de Entra");
        }
        String tenantDelIss = emisor.group(1).toLowerCase(Locale.ROOT);

        // El tid es lo que hace que "cualquier tenant" siga siendo seguro: el token tiene que
        // decir el mismo directorio en el iss y en el tid, y el iss va firmado
        String tid = claims.get("tid", String.class);
        if (tid == null || tid.isBlank()) {
            throw new TokenInvalidoException("el token no trae tid, no se sabe de que directorio viene");
        }
        if (!tenantDelIss.equals(tid.trim().toLowerCase(Locale.ROOT))) {
            throw new TokenInvalidoException(
                    "el tid del token (" + tid + ") no es el tenant de su iss (" + tenantDelIss + ")");
        }

        if (!tenantsPermitidos.isEmpty() && !tenantsPermitidos.contains(tenantDelIss)) {
            throw new TokenInvalidoException("el tenant " + tenantDelIss + " no esta en auth.allowed-tenants");
        }
    }
}
