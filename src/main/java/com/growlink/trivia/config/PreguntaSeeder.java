package com.growlink.trivia.config;

import com.growlink.trivia.adapter.persistence.PreguntaBancoRepository;
import com.growlink.trivia.domain.Categoria;
import com.growlink.trivia.domain.PreguntaBanco;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// preguntas sembradas para poder probar sin depender de que un publicador
// agregue las suyas (HU-23, POST /api/preguntas). cada una de las 10 categorias
// tiene al menos 20 preguntas, para que alcance una partida de hasta 15 preguntas
// y una revancha despues sin repetir exactamente el mismo set (Collections.shuffle
// en JuegoService elige al azar del banco disponible de la categoria)
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
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que estructura de datos sigue el principio LIFO (ultimo en entrar, primero en salir)",
                List.of("Cola (Queue)", "Pila (Stack)", "Lista enlazada", "Arbol binario"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Cual es la complejidad temporal en el peor caso de la busqueda binaria sobre un arreglo ordenado",
                List.of("O(n)", "O(n log n)", "O(log n)", "O(1)"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que capa del modelo OSI se encarga del direccionamiento IP y el enrutamiento",
                List.of("Capa de enlace de datos", "Capa de red", "Capa de transporte", "Capa de aplicacion"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "En SQL, que clausula se usa para filtrar grupos despues de un GROUP BY",
                List.of("WHERE", "HAVING", "ORDER BY", "FILTER"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que principio de la programacion orientada a objetos permite que una subclase redefina el comportamiento de un metodo de su superclase",
                List.of("Encapsulamiento", "Herencia", "Polimorfismo", "Abstraccion"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Cual de estos algoritmos de ordenamiento tiene complejidad promedio O(n log n)",
                List.of("Bubble sort", "Insertion sort", "Quicksort", "Selection sort"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que significa el acronimo REST en el contexto de APIs web",
                List.of("Representational State Transfer", "Remote Execution State Transfer",
                        "Resource State Transmission", "Reliable State Transfer"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que tipo de prueba verifica el funcionamiento de un modulo de software de forma aislada",
                List.of("Prueba de integracion", "Prueba unitaria", "Prueba de aceptacion", "Prueba de carga"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "En Git, que comando combina los cambios de una rama en la rama actual preservando el historial de ambas",
                List.of("git merge", "git rebase", "git cherry-pick", "git stash"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que patron de diseño garantiza que una clase tenga una unica instancia y provee un punto de acceso global a ella",
                List.of("Factory", "Observer", "Singleton", "Strategy"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Cual de estas es una base de datos NoSQL orientada a documentos",
                List.of("PostgreSQL", "MongoDB", "MySQL", "Oracle"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que codigo de estado HTTP indica que el recurso solicitado no fue encontrado",
                List.of("200", "301", "404", "500"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que significa ACID en el contexto de transacciones de bases de datos",
                List.of("Atomicity, Consistency, Isolation, Durability", "Access, Control, Integrity, Data",
                        "Atomic, Concurrent, Isolated, Distributed", "Availability, Consistency, Integrity, Durability"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que tecnica usa un balanceador de carga round robin para distribuir las peticiones entre servidores",
                List.of("Asigna cada peticion al servidor con menos carga",
                        "Asigna las peticiones de forma secuencial y ciclica entre los servidores",
                        "Asigna todas las peticiones al mismo servidor",
                        "Usa un hash del cliente para siempre mandarlo al mismo servidor"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_SISTEMAS,
                "Que diferencia principal hay entre TCP y UDP",
                List.of("TCP es mas rapido pero no garantiza entrega, UDP si la garantiza",
                        "TCP garantiza entrega ordenada y confiable, UDP no",
                        "TCP es solo para correo electronico", "No hay diferencia, son el mismo protocolo"), 1));

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
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es el pasado simple del verbo irregular 'to go'",
                List.of("Goed", "Went", "Gone", "Going"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que preposicion completa correctamente: 'She is interested ___ learning Spanish'",
                List.of("on", "at", "in", "for"), 2));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es el plural correcto de 'child'",
                List.of("childs", "childes", "children", "childrens"), 2));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que palabra es un sinonimo de 'happy' en ingles",
                List.of("Sad", "Joyful", "Angry", "Tired"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es la forma correcta del comparativo de 'expensive'",
                List.of("expensiver", "more expensive", "most expensive", "expensiest"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que tipo de oracion es: 'If I had studied, I would have passed the exam'",
                List.of("Primer condicional", "Segundo condicional", "Tercer condicional", "Condicional cero"), 2));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es el antonimo de 'generous'",
                List.of("Kind", "Stingy", "Wealthy", "Polite"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que significa la expresion idiomatica 'to break the ice'",
                List.of("Romper algo fisicamente", "Iniciar una conversacion en un ambiente tenso",
                        "Congelar un alimento", "Terminar una relacion"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es la forma correcta del verbo en: 'By next year, she ___ here for a decade'",
                List.of("will live", "will have lived", "lives", "lived"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que palabra NO es un verbo modal en ingles",
                List.of("Can", "Must", "Would", "Quickly"), 3));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es la traduccion correcta de 'Aunque llueva, saldre' al ingles",
                List.of("Although it rains, I will go out", "Even if it rains, I will go out",
                        "Because it rains, I will go out", "Unless it rains, I will go out"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que articulo se usa antes de una palabra que empieza con sonido vocalico, como 'hour'",
                List.of("a", "an", "the", "no requiere articulo"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual es la forma en pasado participio del verbo 'to write'",
                List.of("Wrote", "Writed", "Written", "Writing"), 2));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que significa 'however' en espanol",
                List.of("Por lo tanto", "Sin embargo", "Mientras tanto", "Ademas"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Cual de estas oraciones esta en voz pasiva",
                List.of("The chef cooked the meal", "The meal was cooked by the chef",
                        "The chef is cooking the meal", "The chef will cook the meal"), 1));
        repository.save(new PreguntaBanco(Categoria.IDIOMAS,
                "Que palabra completa correctamente: 'I have lived here ___ 2015'",
                List.of("for", "since", "during", "while"), 1));

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
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que principio establece que la ley no se puede aplicar a hechos ocurridos antes de su vigencia, salvo excepciones favorables",
                List.of("Irretroactividad de la ley", "Cosa juzgada", "Debido proceso", "Buena fe"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que organo es el maximo tribunal de la jurisdiccion constitucional en Colombia",
                List.of("Corte Suprema de Justicia", "Consejo de Estado", "Corte Constitucional", "Fiscalia General"), 2));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que significa el termino juridico 'dolo'",
                List.of("Negligencia leve", "Intencion de causar daño a sabiendas", "Caso fortuito", "Fuerza mayor"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que tipo de contrato se perfecciona con el simple acuerdo de voluntades, sin necesidad de formalidad adicional",
                List.of("Contrato real", "Contrato solemne", "Contrato consensual", "Contrato unilateral"), 2));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que rama del derecho regula la relacion entre el Estado y los particulares en el ejercicio de la funcion publica",
                List.of("Derecho civil", "Derecho administrativo", "Derecho mercantil", "Derecho laboral"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que significa la presuncion de inocencia en el derecho penal",
                List.of("El acusado debe probar que es culpable",
                        "El acusado se considera inocente hasta que se demuestre lo contrario",
                        "El juez decide sin pruebas", "Solo aplica en delitos menores"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que es la 'cosa juzgada' en un proceso judicial",
                List.of("La posibilidad de apelar indefinidamente",
                        "El efecto que impide volver a litigar lo ya decidido por sentencia en firme",
                        "Un tipo de prueba documental", "La etapa inicial del proceso"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que diferencia principal hay entre un delito y una falta administrativa",
                List.of("No hay diferencia",
                        "El delito esta tipificado en el codigo penal y conlleva penas privativas de libertad, la falta administrativa se sanciona por via administrativa",
                        "La falta administrativa siempre es mas grave", "Solo el delito requiere denuncia"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que es el habeas corpus",
                List.of("Un recurso para proteger la libertad personal contra detenciones arbitrarias",
                        "Un contrato de compraventa", "Una accion para reclamar perjuicios economicos",
                        "Un tipo de testamento"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que elemento NO es esencial para la validez de un contrato",
                List.of("Consentimiento", "Objeto licito", "Capacidad de las partes", "Que sea notariado"), 3));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que principio obliga a los jueces a fallar en caso de duda a favor del acusado",
                List.of("Non bis in idem", "In dubio pro reo", "Pacta sunt servanda", "Res judicata"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que organo tiene a su cargo investigar y acusar penalmente en Colombia",
                List.of("Procuraduria General", "Fiscalia General de la Nacion", "Defensoria del Pueblo",
                        "Contraloria General"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que es la responsabilidad civil extracontractual",
                List.of("La que surge de un contrato valido entre las partes",
                        "La obligacion de reparar un daño causado sin que exista un contrato previo entre las partes",
                        "La que solo aplica al Estado", "Una sancion penal"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que significa el principio de 'pacta sunt servanda'",
                List.of("Los contratos deben cumplirse de buena fe", "Las leyes no tienen efecto retroactivo",
                        "Nadie puede ser juzgado dos veces", "El juez debe fallar a favor del mas debil"), 0));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Cual es la funcion principal del Consejo de Estado en Colombia",
                List.of("Juzgar delitos comunes", "Ser el maximo tribunal de lo contencioso administrativo",
                        "Legislar sobre materia penal", "Administrar el presupuesto nacional"), 1));
        repository.save(new PreguntaBanco(Categoria.DERECHO,
                "Que tipo de norma esta por encima de todas las demas en el ordenamiento juridico colombiano",
                List.of("Un decreto reglamentario", "Una ley ordinaria", "La Constitucion Politica",
                        "Una resolucion administrativa"), 2));

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que material de construccion se obtiene mezclando cemento, agua, arena y agregados gruesos",
                List.of("Mortero", "Concreto", "Yeso", "Asfalto"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de fundacion se usa cuando el suelo superficial no tiene suficiente capacidad portante y se requiere llegar a estratos profundos",
                List.of("Zapata aislada", "Fundacion corrida", "Pilotes", "Losa de cimentacion"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que ensayo se usa para determinar la resistencia a la compresion del concreto",
                List.of("Ensayo de slump", "Ensayo de cilindros a compresion", "Ensayo de granulometria",
                        "Ensayo Proctor"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que unidad se usa para medir el esfuerzo en ingenieria estructural",
                List.of("Newton", "Pascal", "Joule", "Watt"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de estructura trabaja principalmente a traccion y compresion en sus elementos, formando triangulos",
                List.of("Portico", "Cercha (armadura)", "Muro de carga", "Losa maciza"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que fenomeno describe la deformacion permanente del concreto bajo carga sostenida en el tiempo",
                List.of("Fatiga", "Fluencia lenta (creep)", "Resonancia", "Pandeo"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que es el modulo de elasticidad de un material",
                List.of("La relacion entre el esfuerzo aplicado y la deformacion unitaria en el rango elastico",
                        "La maxima carga que soporta antes de romperse", "El peso especifico del material",
                        "La capacidad de absorber agua"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que norma colombiana regula los requisitos de diseño sismo resistente de edificaciones",
                List.of("NSR-10", "RETIE", "NTC 1500", "ISO 9001"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de via se diseña con pendientes y radios de curvatura para vehiculos a altas velocidades, con control total de acceso",
                List.of("Via urbana local", "Autopista", "Calle peatonal", "Ciclorruta"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que instrumento se usa en topografia para medir angulos horizontales y verticales",
                List.of("Nivel de manguera", "Teodolito", "Flexometro", "Plomada"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que es el asentamiento diferencial en una cimentacion",
                List.of("Cuando toda la estructura se hunde de manera uniforme",
                        "Cuando distintas partes de la cimentacion se hunden en diferente magnitud",
                        "La expansion del suelo por humedad", "La corrosion del acero de refuerzo"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de acero se usa comunmente como refuerzo dentro del concreto",
                List.of("Acero inoxidable", "Acero de refuerzo corrugado", "Acero galvanizado liso", "Acero fundido"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que proposito tiene una junta de dilatacion en una estructura",
                List.of("Permitir el paso de instalaciones electricas",
                        "Permitir movimientos por cambios de temperatura sin daño estructural",
                        "Reforzar la cimentacion", "Decorar la fachada"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que es la capacidad portante de un suelo",
                List.of("La cantidad de agua que puede retener",
                        "La maxima presion que el suelo puede soportar sin fallar", "Su color caracteristico",
                        "La velocidad con que se erosiona"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de puente se caracteriza por usar cables tensados desde torres hasta el tablero",
                List.of("Puente de arco", "Puente colgante o atirantado", "Puente de vigas simples",
                        "Puente tipo losa"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que significa la sigla CBR en estudios de suelos para pavimentos",
                List.of("California Bearing Ratio", "Concrete Base Resistance", "Civil Building Regulation",
                        "Compressive Base Rate"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que elemento estructural resiste principalmente esfuerzos de flexion en una edificacion",
                List.of("Columna", "Viga", "Zapata", "Muro de contencion"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que tipo de agregado se usa para mejorar la trabajabilidad del concreto sin afectar su resistencia",
                List.of("Agregado fino bien gradado", "Arcilla organica", "Madera triturada",
                        "Residuos de metal"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Cual es el proposito principal de un muro de contencion",
                List.of("Decorar un jardin", "Resistir el empuje lateral del suelo y evitar deslizamientos",
                        "Servir de fundacion a una torre", "Aislar el ruido"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_CIVIL,
                "Que ensayo determina la densidad maxima y humedad optima de compactacion de un suelo",
                List.of("Ensayo de Proctor", "Ensayo de slump", "Ensayo de compresion triaxial",
                        "Ensayo de permeabilidad"), 0));

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que metodologia japonesa busca la mejora continua en los procesos productivos",
                List.of("Kaizen", "Six Sigma", "Lean Startup", "Benchmarking"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que herramienta de calidad se usa para identificar las causas raiz de un problema mediante un diagrama tipo espina de pescado",
                List.of("Diagrama de Pareto", "Diagrama de Ishikawa", "Diagrama de flujo", "Histograma"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que indicador mide la eficiencia global de un equipo productivo considerando disponibilidad, rendimiento y calidad",
                List.of("ROI", "OEE (Overall Equipment Effectiveness)", "KPI generico", "TIR"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que tecnica de pronostico de demanda usa el promedio de los datos historicos recientes dandoles mas peso a los mas cercanos",
                List.of("Promedio movil simple", "Suavizacion exponencial", "Regresion lineal", "Metodo Delphi"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que principio del estudio de tiempos y movimientos busca eliminar pasos innecesarios en un proceso",
                List.of("Just in time", "Simplificacion del trabajo", "Control estadistico de procesos",
                        "Cadena de suministro"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que sistema de produccion busca fabricar solo lo necesario, en el momento necesario y en la cantidad necesaria",
                List.of("MRP", "Just in Time (JIT)", "Produccion por lotes", "Produccion push"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que herramienta estadistica identifica que el 80 por ciento de los problemas provienen del 20 por ciento de las causas",
                List.of("Diagrama de Pareto", "Diagrama de dispersion", "Carta de control", "Diagrama de Gantt"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que significa la sigla ERP en sistemas de gestion empresarial",
                List.of("Enterprise Resource Planning", "External Resource Process", "Enterprise Risk Protocol",
                        "Economic Resource Projection"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que tipo de distribucion de planta agrupa las maquinas segun el proceso que realizan, sin importar el producto",
                List.of("Distribucion por producto", "Distribucion por proceso", "Distribucion de posicion fija",
                        "Distribucion celular"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que metodologia busca reducir la variabilidad de los procesos hasta niveles de 3.4 defectos por millon",
                List.of("Six Sigma", "5S", "SMED", "Kanban"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que herramienta visual se usa para controlar el flujo de trabajo limitando el trabajo en proceso",
                List.of("Tablero Kanban", "Diagrama de Ishikawa", "Arbol de decision", "Matriz FODA"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que tecnica busca reducir el tiempo de cambio de referencia en una maquina (cambio de molde o herramienta)",
                List.of("SMED", "FIFO", "ABC", "JIT"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que significa el termino 'cuello de botella' en gestion de procesos",
                List.of("La etapa mas rapida del proceso",
                        "La etapa que limita la capacidad total del sistema por ser la mas lenta",
                        "El area de almacenamiento final", "El control de calidad inicial"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que metodo de clasificacion de inventarios agrupa los articulos segun su importancia relativa en el valor total",
                List.of("Metodo ABC", "Metodo FIFO", "Metodo LIFO", "Metodo EOQ"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que significa la sigla EOQ en gestion de inventarios",
                List.of("Economic Order Quantity (cantidad economica de pedido)", "External Order Qualification",
                        "Estimated Operation Quality", "Equipment Output Quantity"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que herramienta de planificacion muestra graficamente la duracion y secuencia de las actividades de un proyecto",
                List.of("Diagrama de Gantt", "Diagrama de Ishikawa", "Diagrama de Pareto",
                        "Histograma de frecuencias"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que busca la metodologia 5S originaria de Japon",
                List.of("Reducir costos financieros", "Organizar y estandarizar el lugar de trabajo para mejorar la eficiencia",
                        "Aumentar el precio de venta", "Automatizar procesos contables"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que tipo de mantenimiento se realiza de forma planificada antes de que ocurra una falla",
                List.of("Mantenimiento correctivo", "Mantenimiento preventivo", "Mantenimiento de emergencia",
                        "Mantenimiento reactivo"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que concepto mide la relacion entre las salidas obtenidas y los recursos utilizados en un proceso",
                List.of("Rentabilidad", "Productividad", "Liquidez", "Solvencia"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_INDUSTRIAL,
                "Que tecnica de pronostico cualitativo consulta a un panel de expertos de forma anonima e iterativa",
                List.of("Metodo Delphi", "Suavizacion exponencial", "Promedio movil", "Regresion multiple"), 0));

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que ley relaciona el voltaje, la corriente y la resistencia en un circuito electrico",
                List.of("Ley de Faraday", "Ley de Ohm", "Ley de Coulomb", "Ley de Kirchhoff"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que componente electronico permite el paso de corriente en un solo sentido",
                List.of("Resistencia", "Diodo", "Capacitor", "Inductor"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que tipo de señal varia de forma continua en el tiempo, tomando infinitos valores",
                List.of("Señal digital", "Señal analogica", "Señal binaria", "Señal cuantizada"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que dispositivo semiconductor se usa como amplificador o interruptor electronico controlado por corriente o voltaje",
                List.of("Transistor", "Resistencia", "Fusible", "Bobina"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que ley establece que la suma de corrientes que entran a un nodo es igual a la suma de las que salen",
                List.of("Ley de Ohm", "Primera ley de Kirchhoff (de corrientes)", "Ley de Faraday", "Ley de Lenz"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que unidad mide la capacitancia electrica",
                List.of("Henrio", "Ohmio", "Faradio", "Voltio"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que tipo de filtro permite pasar las frecuencias bajas y atenua las altas",
                List.of("Filtro pasa altas", "Filtro pasa bajas", "Filtro pasa banda", "Filtro rechaza banda"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que es un amplificador operacional (Op-Amp)",
                List.of("Un circuito integrado analogico de alta ganancia usado para amplificar señales",
                        "Un tipo de bateria recargable", "Un sensor de temperatura", "Un tipo de antena"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que codigo numerico representa la informacion usando solo los digitos 0 y 1",
                List.of("Codigo decimal", "Codigo binario", "Codigo hexadecimal", "Codigo octal"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que dispositivo convierte una señal analogica en una señal digital",
                List.of("DAC (Convertidor digital-analogico)", "ADC (Convertidor analogico-digital)",
                        "Oscilador", "Rectificador"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que fenomeno describe la induccion de una fuerza electromotriz por un campo magnetico variable",
                List.of("Ley de Faraday", "Ley de Ohm", "Efecto fotoelectrico", "Efecto Joule"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que componente almacena energia en forma de campo electrico",
                List.of("Inductor", "Capacitor", "Resistencia", "Transformador"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que tipo de logica digital opera con los valores de verdad verdadero y falso representados como 1 y 0",
                List.of("Logica difusa", "Logica booleana", "Logica continua", "Logica analogica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que microcontrolador es ampliamente usado en proyectos de electronica embebida de bajo costo, como el usado en Arduino",
                List.of("ATmega328", "Intel Core i7", "AMD Ryzen", "Pentium 4"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que efecto produce el calentamiento de un conductor por el paso de corriente electrica",
                List.of("Efecto Joule", "Efecto Hall", "Efecto fotovoltaico", "Efecto Doppler"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que tipo de corriente cambia periodicamente de sentido y es la que llega a los hogares",
                List.of("Corriente continua (DC)", "Corriente alterna (AC)", "Corriente estatica",
                        "Corriente pulsante"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que instrumento se usa para medir el voltaje, la corriente y la resistencia en un circuito",
                List.of("Osciloscopio", "Multimetro", "Generador de funciones", "Analizador de espectro"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que proposito tiene un transformador electrico",
                List.of("Almacenar energia quimica",
                        "Elevar o reducir el nivel de voltaje de una señal de corriente alterna",
                        "Convertir corriente alterna en continua", "Medir la frecuencia de una señal"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que es la impedancia en un circuito de corriente alterna",
                List.of("La resistencia total al paso de corriente continua unicamente",
                        "La oposicion total al paso de corriente alterna, incluyendo resistencia y reactancia",
                        "La capacidad de almacenar carga", "La frecuencia de resonancia del circuito"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_ELECTRONICA,
                "Que puerta logica entrega un uno solo cuando todas sus entradas son uno",
                List.of("OR", "AND", "NOT", "XOR"), 1));

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que ley de Newton establece que a toda accion corresponde una reaccion igual y opuesta",
                List.of("Primera ley", "Segunda ley", "Tercera ley", "Ley de gravitacion universal"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que maquina termica convierte la energia termica de un combustible en trabajo mecanico mediante combustion interna",
                List.of("Turbina hidraulica", "Motor de combustion interna", "Panel solar", "Generador eolico"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que ciclo termodinamico idealizado describe el funcionamiento de los motores diesel",
                List.of("Ciclo de Carnot", "Ciclo de Otto", "Ciclo Diesel", "Ciclo de Rankine"), 2));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que propiedad de un material describe su capacidad de deformarse plasticamente sin romperse",
                List.of("Dureza", "Ductilidad", "Fragilidad", "Densidad"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que tipo de esfuerzo se produce cuando una fuerza tiende a alargar un elemento",
                List.of("Esfuerzo de compresion", "Esfuerzo de traccion", "Esfuerzo cortante", "Esfuerzo de torsion"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que principio establece que la presion ejercida sobre un fluido incompresible se transmite por igual a todos los puntos",
                List.of("Principio de Arquimedes", "Principio de Pascal", "Principio de Bernoulli",
                        "Principio de Pascal y Bernoulli combinados"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que elemento mecanico transmite movimiento rotativo entre ejes mediante dientes engranados",
                List.of("Correa", "Engranaje", "Resorte", "Cojinete"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que ley de la termodinamica establece que la energia no se crea ni se destruye, solo se transforma",
                List.of("Primera ley", "Segunda ley", "Tercera ley", "Ley cero"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que tipo de transmision de potencia usa una banda flexible para conectar dos poleas",
                List.of("Transmision por engranajes", "Transmision por correa", "Transmision hidraulica",
                        "Transmision por cadena rigida"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que dispositivo reduce la velocidad de rotacion de un eje mientras aumenta el torque",
                List.of("Embrague", "Reductor de velocidad (caja reductora)", "Volante de inercia", "Amortiguador"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que fenomeno describe la falla de un material por esfuerzos ciclicos repetidos, incluso por debajo de su limite de resistencia",
                List.of("Fatiga del material", "Fluencia", "Corrosion galvanica", "Resonancia"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que principio de Bernoulli relaciona la velocidad de un fluido con su presion",
                List.of("A mayor velocidad del fluido, mayor presion", "A mayor velocidad del fluido, menor presion",
                        "La velocidad y la presion son independientes", "Solo aplica a fluidos compresibles"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que tipo de union mecanica permite el desmontaje repetido de dos piezas, a diferencia de la soldadura",
                List.of("Union soldada", "Union atornillada", "Union remachada", "Union pegada"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que instrumento se usa para medir con alta precision dimensiones pequeñas, como el espesor de una pieza",
                List.of("Flexometro", "Calibrador pie de rey (vernier)", "Nivel de burbuja", "Cinta metrica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que es la tolerancia dimensional en el diseño mecanico de piezas",
                List.of("El margen permitido de variacion respecto a una medida nominal",
                        "El peso maximo que soporta una pieza", "La temperatura maxima de operacion",
                        "El color especificado para la pieza"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que tipo de energia almacena un resorte comprimido",
                List.of("Energia cinetica", "Energia potencial elastica", "Energia termica", "Energia quimica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que maquina convierte la energia cinetica del viento en energia mecanica rotacional",
                List.of("Turbina eolica", "Motor electrico", "Compresor", "Bomba centrifuga"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que elemento mecanico se usa para reducir las vibraciones y absorber impactos en un vehiculo",
                List.of("Amortiguador", "Engranaje", "Polea", "Biela"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que proceso de manufactura da forma a un metal mediante la aplicacion de calor y fuerza de compresion con un martillo o prensa",
                List.of("Forjado", "Fundicion", "Mecanizado por arranque de viruta", "Soldadura"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_MECANICA,
                "Que ciclo termodinamico es la base teorica del funcionamiento de los motores de gasolina de combustion interna",
                List.of("Ciclo de Carnot", "Ciclo de Otto", "Ciclo Diesel", "Ciclo de Rankine"), 1));

        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que gas es el principal responsable del efecto invernadero producido por actividades humanas",
                List.of("Oxigeno", "Dioxido de carbono (CO2)", "Nitrogeno", "Argon"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que proceso de tratamiento de agua potable elimina particulas suspendidas mediante la adicion de coagulantes",
                List.of("Cloracion", "Coagulacion y floculacion", "Aireacion", "Osmosis inversa"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que termino describe la capacidad de un ecosistema de satisfacer las necesidades actuales sin comprometer las de futuras generaciones",
                List.of("Biodiversidad", "Desarrollo sostenible", "Resiliencia ecologica", "Huella de carbono"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que indicador mide la cantidad de oxigeno requerido para descomponer la materia organica presente en el agua",
                List.of("pH", "DBO (Demanda Bioquimica de Oxigeno)", "Turbidez", "Conductividad"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que capa de la atmosfera contiene la mayor concentracion de ozono que protege a la Tierra de la radiacion ultravioleta",
                List.of("Troposfera", "Estratosfera", "Mesosfera", "Termosfera"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que tipo de energia renovable aprovecha el calor interno de la Tierra",
                List.of("Energia eolica", "Energia geotermica", "Energia mareomotriz", "Energia solar fotovoltaica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que proceso biologico usan las plantas para convertir CO2 y agua en materia organica usando luz solar",
                List.of("Respiracion celular", "Fotosintesis", "Fermentacion", "Transpiracion"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que fenomeno se produce cuando sustancias quimicas como el SO2 reaccionan en la atmosfera y caen en forma de precipitacion acidificada",
                List.of("Eutroficacion", "Lluvia acida", "Marea roja", "Inversion termica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que proceso de gestion de residuos busca transformar materiales usados en nuevos productos",
                List.of("Incineracion", "Reciclaje", "Relleno sanitario", "Compostaje"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que termino describe el exceso de nutrientes (como nitrogeno y fosforo) en un cuerpo de agua que provoca crecimiento excesivo de algas",
                List.of("Eutroficacion", "Bioacumulacion", "Desertificacion", "Salinizacion"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que tipo de tratamiento de aguas residuales usa microorganismos para degradar la materia organica",
                List.of("Tratamiento fisico", "Tratamiento biologico", "Tratamiento quimico avanzado",
                        "Desalinizacion"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que acuerdo internacional busca limitar el aumento de la temperatura global a menos de 2 grados Celsius",
                List.of("Protocolo de Kioto", "Acuerdo de Paris", "Convenio de Basilea", "Protocolo de Montreal"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que protocolo internacional se enfoco en reducir las sustancias que agotan la capa de ozono",
                List.of("Protocolo de Montreal", "Acuerdo de Paris", "Protocolo de Kioto", "Convenio de Estocolmo"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que termino mide la cantidad total de gases de efecto invernadero emitidos directa o indirectamente por una persona o actividad",
                List.of("Huella hidrica", "Huella de carbono", "Huella ecologica", "Indice de biodiversidad"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que tipo de contaminacion se produce por particulas y gases liberados por vehiculos e industrias en la atmosfera",
                List.of("Contaminacion del suelo", "Contaminacion del aire", "Contaminacion acustica",
                        "Contaminacion luminica"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que estrategia de las 3R en gestion de residuos se refiere a disminuir el consumo de recursos desde el origen",
                List.of("Reciclar", "Reducir", "Reutilizar", "Rechazar"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que fenomeno describe la acumulacion progresiva de sustancias toxicas en los tejidos de organismos a lo largo de la cadena alimenticia",
                List.of("Biomagnificacion o bioacumulacion", "Eutroficacion", "Erosion", "Desertificacion"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que tipo de energia renovable se obtiene del movimiento de las mareas",
                List.of("Energia mareomotriz", "Energia geotermica", "Energia solar termica", "Energia de biomasa"), 0));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que parametro del agua indica su grado de acidez o alcalinidad",
                List.of("Turbidez", "pH", "Conductividad electrica", "Salinidad"), 1));
        repository.save(new PreguntaBanco(Categoria.INGENIERIA_AMBIENTAL,
                "Que proceso de degradacion del suelo provoca la perdida de su capa fertil por accion del agua o el viento",
                List.of("Salinizacion", "Erosion", "Compactacion", "Lixiviacion"), 1));

        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el resultado de resolver la ecuacion 2x + 6 = 14",
                List.of("x = 2", "x = 4", "x = 6", "x = 8"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que teorema relaciona los lados de un triangulo rectangulo mediante a^2 + b^2 = c^2",
                List.of("Teorema de Tales", "Teorema de Pitagoras", "Teorema del seno", "Teorema del coseno"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es la derivada de la funcion f(x) = x^2",
                List.of("x", "2x", "x^2", "2"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el valor de la raiz cuadrada de 144",
                List.of("10", "11", "12", "14"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que tipo de numero no puede expresarse como una fraccion de dos enteros, como pi",
                List.of("Numero racional", "Numero irracional", "Numero entero", "Numero natural"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el resultado de la integral indefinida de la funcion f(x) = 2x",
                List.of("x + C", "x^2 + C", "2x^2 + C", "x^2/2 + C"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "En una distribucion de datos, que medida representa el valor que mas se repite",
                List.of("Media", "Mediana", "Moda", "Varianza"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cuanto es el factorial de 5 (5!)",
                List.of("20", "60", "120", "720"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que figura geometrica tiene todos sus lados y angulos iguales y exactamente 6 lados",
                List.of("Pentagono regular", "Hexagono regular", "Octogono regular", "Heptagono regular"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el area de un circulo de radio r",
                List.of("2 * pi * r", "pi * r^2", "pi * r", "4 * pi * r^2"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que termino describe un sistema de dos ecuaciones lineales con dos incognitas que no tiene solucion",
                List.of("Sistema compatible determinado", "Sistema compatible indeterminado", "Sistema incompatible",
                        "Sistema homogeneo"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el resultado de log base 10 de 1000",
                List.of("1", "2", "3", "10"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que nombre recibe el conjunto de numeros que incluye a los naturales, enteros, racionales e irracionales",
                List.of("Numeros complejos", "Numeros reales", "Numeros imaginarios", "Numeros primos"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el valor de sen(90 grados) en trigonometria",
                List.of("0", "0.5", "1", "-1"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que operacion matematica representa la pendiente de la recta tangente a una curva en un punto",
                List.of("La integral definida", "La derivada", "El limite al infinito", "La raiz de la funcion"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cuantas soluciones reales tiene la ecuacion cuadratica si su discriminante es negativo",
                List.of("Dos soluciones reales distintas", "Una solucion real doble", "Ninguna solucion real",
                        "Infinitas soluciones"), 2));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que es un numero primo",
                List.of("Un numero divisible unicamente por 1 y por si mismo, mayor que 1",
                        "Un numero par cualquiera", "Un numero que termina en cero", "Un numero negativo"), 0));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es la suma de los angulos internos de cualquier triangulo",
                List.of("90 grados", "180 grados", "270 grados", "360 grados"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Que representa la desviacion estandar en estadistica",
                List.of("El valor mas frecuente de los datos", "La dispersion de los datos respecto a la media",
                        "El valor maximo del conjunto de datos", "La suma total de los datos"), 1));
        repository.save(new PreguntaBanco(Categoria.MATEMATICAS,
                "Cual es el resultado de simplificar la fraccion 12/18 a su minima expresion",
                List.of("2/3", "3/4", "4/6", "6/9"), 0));

        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que funcion administrativa consiste en definir objetivos y los medios para alcanzarlos antes de actuar",
                List.of("Organizacion", "Planeacion", "Direccion", "Control"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que herramienta estrategica analiza las Fortalezas, Oportunidades, Debilidades y Amenazas de una organizacion",
                List.of("Analisis FODA (SWOT)", "Cinco fuerzas de Porter", "Cadena de valor", "Balanced Scorecard"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que tipo de estructura organizacional agrupa a los empleados segun la funcion que desempeñan, como ventas o finanzas",
                List.of("Estructura matricial", "Estructura funcional", "Estructura divisional", "Estructura en red"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que concepto financiero representa la diferencia entre los ingresos totales y los costos totales de una empresa",
                List.of("Flujo de caja", "Utilidad o beneficio", "Activo corriente", "Patrimonio neto"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que estrategia de mercadeo se basa en las 4P: Producto, Precio, Plaza y Promocion",
                List.of("Marketing mix", "Benchmarking", "Posicionamiento de marca", "Segmentacion de mercado"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que indicador financiero mide la capacidad de una empresa para cubrir sus obligaciones de corto plazo",
                List.of("Razon de liquidez", "Margen neto", "ROE", "Rotacion de inventarios"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que termino describe el proceso de dividir el mercado total en grupos de consumidores con necesidades similares",
                List.of("Posicionamiento", "Segmentacion de mercado", "Diferenciacion", "Fidelizacion"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que modelo estrategico de Michael Porter analiza la rivalidad, el poder de proveedores y clientes, y las barreras de entrada en una industria",
                List.of("Las cinco fuerzas de Porter", "La matriz BCG", "El ciclo de vida del producto",
                        "El analisis PESTEL"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que termino contable representa todo lo que una empresa posee y le genera valor economico",
                List.of("Pasivo", "Activo", "Patrimonio", "Gasto"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que tipo de liderazgo permite a los colaboradores participar activamente en la toma de decisiones",
                List.of("Liderazgo autocratico", "Liderazgo democratico o participativo",
                        "Liderazgo laissez faire sin ninguna direccion", "Liderazgo transaccional puro"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que concepto mide la rentabilidad de una empresa en relacion con el patrimonio de los accionistas",
                List.of("ROI", "ROE (Retorno sobre el patrimonio)", "EBITDA", "Punto de equilibrio"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que herramienta de gestion muestra la posicion de los productos de una empresa segun su participacion de mercado y crecimiento",
                List.of("Matriz BCG", "Diagrama de Gantt", "Organigrama", "Diagrama de Ishikawa"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que termino describe el nivel de ventas en el cual los ingresos totales igualan a los costos totales",
                List.of("Margen de contribucion", "Punto de equilibrio", "Flujo de caja libre", "Valor presente neto"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que proceso de recursos humanos busca atraer e identificar a los candidatos mas calificados para un puesto vacante",
                List.of("Induccion", "Reclutamiento y seleccion", "Evaluacion de desempeño", "Capacitacion"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que concepto financiero representa el valor actual de los flujos de caja futuros de un proyecto descontados a una tasa",
                List.of("Tasa interna de retorno (TIR)", "Valor presente neto (VPN o VAN)", "Punto de equilibrio",
                        "Costo de oportunidad"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que estrategia de crecimiento consiste en vender nuevos productos en los mercados actuales",
                List.of("Penetracion de mercado", "Desarrollo de producto", "Diversificacion", "Desarrollo de mercado"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que principio contable establece que los registros financieros deben hacerse por separado de las finanzas personales del propietario",
                List.of("Principio de entidad economica", "Principio de devengado", "Principio de prudencia",
                        "Principio de materialidad"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que termino describe la ventaja que obtiene una empresa al ofrecer algo unico y dificil de imitar por sus competidores",
                List.of("Economia de escala", "Ventaja competitiva", "Diversificacion de riesgo",
                        "Economia de alcance"), 1));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que tipo de presupuesto se elabora proyectando los ingresos y egresos esperados de una empresa para un periodo futuro",
                List.of("Presupuesto maestro", "Balance general", "Estado de resultados historico",
                        "Flujo de caja pasado"), 0));
        repository.save(new PreguntaBanco(Categoria.ADMINISTRACION_EMPRESAS,
                "Que termino de gestion de calidad busca la satisfaccion total del cliente involucrando a toda la organizacion",
                List.of("Gestion de la Calidad Total (TQM)", "Just in Time", "Benchmarking",
                        "Reingenieria de procesos"), 0));
    }
}
