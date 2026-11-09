package com.growlink.trivia.adapter.security;

import io.jsonwebtoken.Claims;

// Traduce un token YA VERIFICADO (firma RS256, kid, exp, emisor, audiencia) a la identidad que
// este servicio necesita: un usuarioId numerico y un rol. Es el paso siguiente a
// OidcTokenVerificador, no un reemplazo: aqui no se valida nada del token, eso ya paso.
//
// Hay dos implementaciones, y las elige auth.identity-source:
//
//   usuarios-service (por defecto en modo oidc)  IdentidadDesdeUsuariosService
//       le pregunta a usuarios-service quien es el dueno del token. Es lo que hace falta con
//       Microsoft Entra: su sub es un texto opaco como "AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy78...",
//       no un id, y la identidad numerica y el rol viven en usuarios-service.
//
//   claims                                        IdentidadDesdeClaims
//       saca el usuarioId del sub y el rol de un claim. Es el comportamiento que tenia el
//       servicio antes de esta HU, y se conserva para desarrollo y pruebas sin usuarios-service
//       arriba. Solo sirve si el emisor comparte el padron de usuarios (el sub tiene que ser un
//       numero), que es el caso del JWT propio de GrowLink pero NO el de Entra.
public interface ResolvedorDeIdentidad {

    // jwt es el token serializado tal como llego (sin el "Bearer "), porque la implementacion que
    // habla con usuarios-service tiene que reenviarlo; claims es el contenido ya verificado
    Identidad resolver(String jwt, Claims claims);
}
