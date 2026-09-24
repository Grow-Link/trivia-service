package com.growlink.trivia.config;

import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// preguntas sembradas para poder probar, mientras no exista HU-23
// (que el publicador agregue las suyas). por ahora solo hay de
// BACKEND y FRONTEND, las demas categorias no tienen preguntas todavia
@Component
public class PreguntaSeeder implements CommandLineRunner {

    private final PreguntaBancoRepository repository;

    public PreguntaSeeder(PreguntaBancoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(new PreguntaBanco(Categoria.BACKEND,
                "Que garantiza que un UPDATE con WHERE sea atomico bajo concurrencia",
                List.of("El bloqueo de fila durante el UPDATE", "Que Java use synchronized",
                        "Que Spring use Transactional siempre", "Nada, hay que usar locks a mano"), 0));
        repository.save(new PreguntaBanco(Categoria.BACKEND,
                "Que problema evita el bloqueo optimista con Version en JPA",
                List.of("Lost update", "SQL injection", "Memory leak", "Deadlock"), 0));
        repository.save(new PreguntaBanco(Categoria.BACKEND,
                "En Spring, que anotacion recibe mensajes STOMP entrantes",
                List.of("MessageMapping", "GetMapping", "Scheduled", "EventListener"), 0));
        repository.save(new PreguntaBanco(Categoria.BACKEND,
                "Que protocolo corre sobre WebSocket para pub sub con topicos",
                List.of("STOMP", "FTP", "gRPC", "SOAP"), 0));
        repository.save(new PreguntaBanco(Categoria.BACKEND,
                "Que hace SELECT FOR UPDATE en una base de datos",
                List.of("Bloquea la fila hasta que termine la transaccion", "Borra la fila",
                        "Crea un indice nuevo", "Solo sirve para leer mas rapido"), 0));

        repository.save(new PreguntaBanco(Categoria.FRONTEND,
                "Que hook de React se usa para guardar estado local de un componente",
                List.of("useState", "useEffect", "useMemo", "useRef"), 0));
        repository.save(new PreguntaBanco(Categoria.FRONTEND,
                "Que hace el CSS flexbox principalmente",
                List.of("Acomodar elementos en fila o columna de forma flexible", "Definir colores",
                        "Cargar fuentes", "Animar transiciones"), 0));
        repository.save(new PreguntaBanco(Categoria.FRONTEND,
                "Que es el virtual DOM en React",
                List.of("Una copia en memoria del DOM para comparar cambios", "El navegador mismo",
                        "Un servidor de archivos estaticos", "Una base de datos"), 0));
        repository.save(new PreguntaBanco(Categoria.FRONTEND,
                "Para que sirve un WebSocket en el frontend",
                List.of("Mantener una conexion abierta y recibir datos en tiempo real", "Guardar cookies",
                        "Compilar el codigo", "Validar formularios"), 0));
        repository.save(new PreguntaBanco(Categoria.FRONTEND,
                "Que problema resuelve usar una key en una lista de React",
                List.of("Ayuda a React a saber que elemento cambio", "Le pone color a la lista",
                        "Ordena la lista alfabeticamente", "Hace la lista mas rapida de cargar sin importar el tamano"), 0));
    }
}
