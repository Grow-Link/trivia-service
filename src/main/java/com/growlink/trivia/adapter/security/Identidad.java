package com.growlink.trivia.adapter.security;

// Quien es la persona detras de un token, ya resuelto: el usuarioId NUMERICO que este servicio usa
// en sus propias tablas (SalaParticipante, RespuestaSala, PartidaPuesto) y su rol.
//
// De donde sale cada cosa depende de auth.identity-source, ver ResolvedorDeIdentidad: del token
// mismo (modo claims) o de GET /api/usuarios/me en usuarios-service, que es lo que hace falta con
// tokens de Microsoft Entra, donde el sub es un texto opaco y no un id.
//
// El rol queda como String y no como enum porque el modo legacy lo lee de un claim del JWT de
// usuarios-service, donde puede venir cualquier cosa; quien lo compara es TokenService.exigirAdmin.
public record Identidad(Long usuarioId, String rol) {
}
