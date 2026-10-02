package com.growlink.trivia.config;

import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// preguntas sembradas para poder probar sin depender de que un publicador
// agregue las suyas (HU-23, POST /api/preguntas). solo hay de unas pocas
// categorias, las demas se llenan con las que agreguen los publicadores
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

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que garantiza que un UPDATE con WHERE sea atomico bajo concurrencia",
                List.of("El bloqueo de fila durante el UPDATE", "Que Java use synchronized",
                        "Que Spring use Transactional siempre", "Nada, hay que usar locks a mano"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que problema evita el bloqueo optimista con Version en JPA",
                List.of("Lost update", "SQL injection", "Memory leak", "Deadlock"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "En Spring, que anotacion recibe mensajes STOMP entrantes",
                List.of("MessageMapping", "GetMapping", "Scheduled", "EventListener"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que protocolo corre sobre WebSocket para pub sub con topicos",
                List.of("STOMP", "FTP", "gRPC", "SOAP"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que hace SELECT FOR UPDATE en una base de datos",
                List.of("Bloquea la fila hasta que termine la transaccion", "Borra la fila",
                        "Crea un indice nuevo", "Solo sirve para leer mas rapido"), 0));

        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es el comparativo de superioridad correcto de 'good' en ingles",
                List.of("better", "gooder", "more good", "best"), 0));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que tiempo verbal usa 'I have eaten' en ingles",
                List.of("Present perfect", "Past simple", "Future perfect", "Present continuous"), 0));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual de estas palabras es un falso cognado entre ingles y espanol",
                List.of("Embarrassed (no significa embarazada)", "Important", "Family", "Animal"), 0));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Como se dice 'sin embargo' en ingles",
                List.of("However", "Although", "Because", "Meanwhile"), 0));

        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que principio establece que nadie puede ser juzgado dos veces por el mismo hecho",
                List.of("Non bis in idem", "In dubio pro reo", "Pacta sunt servanda", "Habeas corpus"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que accion constitucional protege los derechos fundamentales en Colombia",
                List.of("Accion de tutela", "Accion popular", "Accion de nulidad", "Accion de cumplimiento"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que rama del derecho regula las relaciones entre particulares",
                List.of("Derecho civil", "Derecho penal", "Derecho administrativo", "Derecho constitucional"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que efecto tiene la prescripcion en una obligacion civil",
                List.of("Extingue la posibilidad de exigirla judicialmente", "La hace nula desde el inicio",
                        "La convierte en obligacion natural inmediatamente", "No tiene ningun efecto"), 0));
    }
}
