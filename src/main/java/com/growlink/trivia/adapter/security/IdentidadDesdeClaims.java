package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;

import java.util.Collection;
import java.util.Objects;

// auth.identity-source=claims: la identidad sale del token mismo, que es lo que hacia el servicio
// antes de que existiera la consulta a usuarios-service. Se conserva para desarrollo y para las
// pruebas que no quieren levantar usuarios-service.
//
// OJO con el limite de este modo: exige que el sub sea un numero, asi que solo sirve cuando el
// emisor comparte el padron de usuarios de GrowLink. Con tokens de Microsoft Entra el sub es un
// texto opaco y aqui se rechazarian todos; para eso esta IdentidadDesdeUsuariosService.
public class IdentidadDesdeClaims implements ResolvedorDeIdentidad {

    private final String rolClaim;

    public IdentidadDesdeClaims(String rolClaim) {
        this.rolClaim = (rolClaim == null || rolClaim.isBlank())
                ? OidcTokenVerificador.ROL_CLAIM_POR_DEFECTO
                : rolClaim.trim();
    }

    @Override
    public Identidad resolver(String jwt, Claims claims) {
        return new Identidad(usuarioIdDeSub(claims), rolDe(claims));
    }

    // En este modo el sub ES el id numerico del usuario (el mismo que emite usuarios-service), asi
    // que un sub con otra forma significa que el token viene de un emisor que no comparte el mismo
    // padron: mejor rechazarlo en la puerta que dejarlo entrar y reventar despues
    private Long usuarioIdDeSub(Claims claims) {
        String sub = claims.getSubject();
        try {
            return Long.valueOf(sub.trim());
        } catch (NumberFormatException e) {
            throw new TokenInvalidoException("el sub del token (" + sub + ") no es un id de usuario."
                    + " Con tokens de Entra hay que usar auth.identity-source=usuarios-service", e);
        }
    }

    // El rol es lo que mira TokenService.exigirAdmin para /api/metricas/dashboard. El nombre del
    // claim se configura (auth.oidc.rol-claim) porque cada proveedor lo llama distinto; por
    // defecto es "rol", el mismo que ya usa usuarios-service, para que el modo oidc y el legacy
    // decidan igual. Se aceptan las dos formas con que los proveedores lo publican: un texto
    // suelto o una lista de roles.
    //
    // Si el claim no viene, el rol queda null y exigirAdmin responde 403: un token sin rol no es
    // un token invalido (los endpoints que solo piden sesion siguen funcionando), simplemente no
    // alcanza para el dashboard de admin.
    private String rolDe(Claims claims) {
        Object valor = claims.get(rolClaim);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Collection<?> roles) {
            return roles.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(TokenService.ROL_ADMIN::equals)
                    .findFirst()
                    .orElseGet(() -> roles.isEmpty() ? null : String.valueOf(roles.iterator().next()));
        }
        return valor.toString();
    }
}
