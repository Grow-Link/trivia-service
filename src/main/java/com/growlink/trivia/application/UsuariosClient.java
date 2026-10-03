package com.growlink.trivia.application;

// Puerto hacia usuarios-service. La implementacion HTTP vive en adapter/client.
public interface UsuariosClient {

    // HU-22: suma uno al contador de trivias ganadas del perfil del ganador
    // si usuarios-service no contesta no se tumba la partida, solo queda en el log
    void registrarVictoria(Long usuarioId);
}
