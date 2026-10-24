# CampusReservas: resúmenes para la defensa

Respuestas cortas para explicar cada parte del TP con nuestras palabras. Se va completando a medida que avanzamos, paso por paso.

Al final está la sección **Decisiones que tomamos**: cada elección que hicimos al programar, con por qué y qué descartamos. Las decisiones de arquitectura del plan (D1 a D25) están en el plan de desarrollo, sección 10. Las nuestras se numeran T1, T2… para no confundirlas.

---

## Estructura del repositorio

**Por qué todos los paquetes empiezan con `ar.edu.utn.frc.bda.campus`**

> "Es la convención de Java de usar el dominio invertido (`utn.edu.ar` → `ar.edu.utn`) para que los nombres de paquete sean únicos y no choquen con los de otras librerías. Además, cada parte del paquete es una carpeta dentro de `src/main/java`, que es donde Maven busca el código por convención. Los tres proyectos comparten el prefijo `ar.edu.utn.frc.bda.campus`, y por eso los microservicios usan `@SpringBootApplication(scanBasePackages = "ar.edu.utn.frc.bda.campus")`: así Spring encuentra también el `GlobalExceptionHandler`, que está en otro paquete (`...campus.commons`) dentro del JAR común."

**Qué no subimos al repo (`.gitignore`)**

> "No subimos `target/`, porque lo genera Maven y se puede regenerar; tampoco la configuración del IDE (`.idea/`, `.vscode/`), que es personal de cada uno; ni las bases H2 (`data/`), que son datos locales que cada servicio crea al arrancar."

---

## Fase 0: JAR común `campus-commons`

**Qué es y por qué es el componente común reutilizable**

> "campus-commons es un JAR Maven, una librería, no una aplicación: no tiene `main` y no se ejecuta solo. Lo instalamos con `mvn install` en el repositorio local `.m2`, y los dos microservicios lo declaran como dependencia en su `pom.xml`. Contiene solo lo transversal: el formato de respuesta `ApiResponse`, las excepciones de negocio y el manejador global que las traduce a códigos HTTP. Así las dos APIs responden y fallan igual sin duplicar código. No ponemos entidades ni reglas de negocio, para que los servicios sigan siendo independientes."

Ideas clave por si repreguntan:

- **JAR librería vs. JAR ejecutable:** campus-commons es una librería, sin `main`, que usan otros proyectos. `ms-espacios.jar` es ejecutable: levanta el servidor con `java -jar`.
- **`.m2`:** es el repositorio local de Maven. Primero busca ahí y, si no encuentra la dependencia, la baja de Maven Central. Como campus-commons no está en internet, hay que instalarlo con `mvn install`.
- **Coordenadas Maven:** `groupId` + `artifactId` + `version` (`ar.edu.utn.frc.bda:campus-commons:1.0.0`) identifican el JAR de forma única.
- **Si cambia el JAR:** hay que volver a correr `mvn install` antes de compilar los servicios. Por eso `build.sh` lo compila primero.
- **D13: qué NO va en el JAR.** Entidades, DTOs de dominio y reglas de negocio. Ponerlos ahí acoplaría los servicios: un cambio en el modelo de uno obligaría a recompilar y desplegar el otro. Por eso `TipoActividad` está declarado en los dos servicios, una duplicación chica a propósito para no acoplarlos.

**El `pom.xml` de campus-commons**

> "Es un proyecto Maven simple, no una aplicación Spring Boot: no tiene `spring-boot-starter-parent`, ni `@SpringBootApplication`, ni el `spring-boot-maven-plugin`, porque es una librería y no tiene que generar un JAR ejecutable. Para usar las mismas versiones de Spring que los microservicios, importa la BOM `spring-boot-dependencies` en `dependencyManagement`; así declaramos `spring-web` sin versión y queda alineada. `spring-web` va con `scope provided`: la necesitamos para compilar (`ResponseEntity`, `@RestControllerAdvice`), pero no la metemos dentro del JAR, porque la aporta el microservicio que lo use. Así no hay dos copias de Spring ni choques de versiones."

Ideas clave por si repreguntan:

- **`packaging jar`:** le dice a Maven que el resultado es un `.jar`. Es el valor por defecto, pero lo dejamos explícito.
- **Parent vs. BOM:** heredar de `spring-boot-starter-parent` trae versiones *y* configuración de plugins pensada para apps Boot. Importar la BOM trae **solo las versiones**, que es lo único que necesita una librería.
- **`scope import` + `type pom`:** es la forma de "traer" la tabla de versiones de otro `pom` sin heredarlo.
- **`dependencyManagement` no agrega dependencias:** solo fija versiones. Las dependencias reales son las de `<dependencies>`.
- **Tres dependencias `provided`:** `spring-web` (`ResponseEntity`, `@RestControllerAdvice`), `spring-context` (`BindingResult`, que usa el handler de `@Valid`) y `jakarta.servlet-api` (`ServletException`, de la que hereda la excepción de parámetro faltante; ver T37).
- **`provided` vs. `compile`:** `compile` (el default) viaja con el JAR y se pasa a quien lo use. `provided` se usa para compilar, pero se asume que el entorno la tiene.
- **`maven.compiler.release 25`:** compila para Java 25 y verifica que no se use API de otra versión. Tiene que coincidir con el Java de los microservicios.
- **`project.build.sourceEncoding UTF-8`:** para que los acentos de los mensajes de error compilen igual en todas las máquinas del grupo.
- **Versión del compilador fijada:** para que todos compilen igual aunque tengan distinta versión de Maven.

**`ApiResponse<T>`**

> "Es el formato único de respuesta de todas nuestras APIs, el mismo del ejemplo de la cátedra: `success` dice si salió bien, `responseValue` trae el dato, `message` explica qué pasó y `executedOn` dice cuándo. Es un `record` porque es un dato inmutable que solo transporta información: Java genera solo el constructor, los getters, `equals`, `hashCode` y `toString`. Es genérico (`<T>`) para que sirva para cualquier dato: `ApiResponse<EspacioDto>`, `ApiResponse<List<ReservaDto>>` o `ApiResponse<Void>` en los errores. El método estático `create` completa la fecha solo, así nadie se olvida de ponerla."

Ideas clave por si repreguntan:

- **Por qué un formato único:** el cliente siempre lee la misma estructura, sea éxito o error, y sea de `ms-espacios` o de `ms-reservas`. Además, `ms-reservas` puede leer las respuestas de `ms-espacios` con el mismo record (`ApiResponse<ValidacionEspacio>`).
- **`record`:** clase inmutable (campos `final`) para datos. Los "getters" se llaman como el campo (`success()`, no `getSuccess()`). Jackson, la librería de Spring que convierte objetos a JSON, los serializa con esos nombres.
- **Genéricos:** `T` es un "tipo a completar". Una sola clase sirve para cualquier contenido, con chequeo de tipos al compilar.
- **`ApiResponse<Void>`:** en los errores no hay dato; `Void` deja claro que `responseValue` siempre es `null`.
- **Método de fábrica (`create`):** un constructor con nombre que además pone `LocalDateTime.now()`. El código que lo usa queda más corto: `ApiResponse.create(true, dto, "Reserva creada")`.
- **El código HTTP no va adentro:** viaja en la respuesta HTTP misma (200, 404, 409…). `success` es un atajo para el cliente.

**¿Por qué el código HTTP no está dentro de `ApiResponse`?**

> "Porque una respuesta HTTP ya tiene un lugar para el código: la línea de estado, antes de los headers y del body. Nosotros lo ponemos ahí con `ResponseEntity.status(...)`. Repetirlo en el JSON sería tener dos fuentes de verdad que podrían contradecirse. Además, todo el que está en el medio decide por la línea de estado y no lee nuestro body: `RestClient` lanza `HttpClientErrorException.NotFound` cuando ve un 404, el gateway devuelve 401 y 403 sin pasar por nuestro código, y Postman y Swagger muestran el código de la línea de estado. Así respetamos la semántica de REST: el código dice qué pasó, y el body da el detalle."

- **Anatomía de una respuesta HTTP:** línea de estado (`HTTP/1.1 404 Not Found`), headers (`Content-Type: application/json`) y body (nuestro `ApiResponse`).
- **Dónde se pone en el código:** `ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.create(false, null, "..."))`. El `status` va a la línea de estado, y el `body`, al JSON.
- **Hay respuestas sin nuestro body:** el 401 y el 403 del gateway, o un 503 de Docker si un servicio no levanta. Si el cliente dependiera de un `status` dentro del JSON, en esos casos no lo tendría.
- **No es una regla absoluta:** algunos formatos (por ejemplo Problem Details, RFC 9457) repiten el código en el body como información extra. Nosotros seguimos el `ApiResponse` de la cátedra, que no lo incluye.

**Las cinco excepciones de negocio**

> "Tenemos una excepción por cada situación de error del negocio, y cada una corresponde a un código HTTP: `RecursoNoEncontradoException` → 404, `AccesoDenegadoException` → 403, `ConflictoException` → 409, `ReglaNegocioException` → 422 y `ServicioNoDisponibleException` → 503. Los servicios solo hacen `throw new ...("mensaje")` y el `GlobalExceptionHandler` las traduce al código y al `ApiResponse` de error. Extienden `RuntimeException` porque son *unchecked*: no obligan a poner `throws` ni `try/catch` en cada capa, y además Spring deshace la transacción (`@Transactional`) automáticamente cuando sale una `RuntimeException`."

Ideas clave por si repreguntan:

| Excepción | Código | Cuándo, en el TP |
|---|---|---|
| `RecursoNoEncontradoException` | 404 | Espacio o reserva que no existe (espacio 999) |
| `AccesoDenegadoException` | 403 | `usuario2` cancela la reserva de `usuario1` (RN7) |
| `ConflictoException` | 409 | Superposición (RN6), transición de estado inválida (RN14) |
| `ReglaNegocioException` | 422 | RN1, RN2, RN5, espacio no disponible (RN3, RN4, RN8 a RN12), reserva ya empezada |
| `ServicioNoDisponibleException` | 503 | `ms-espacios` caído u Open-Meteo sin responder |

- **Checked vs. unchecked:** las *checked* (`Exception`) obligan a declararlas con `throws` o capturarlas. Las *unchecked* (`RuntimeException`) no. Con checked, cada método entre el service y el controller tendría que declarar todas las excepciones.
- **Rollback:** por defecto, `@Transactional` hace rollback solo con `RuntimeException`. Si fueran checked, una reserva inválida podría quedar guardada.
- **Por qué una clase por situación y no una sola con el código:** el handler elige el código por el **tipo** de la excepción, y el service expresa *qué pasó* (un conflicto, una regla) sin saber nada de HTTP.
- **422 vs. 400:** 400 es "el pedido está mal formado" (falta un campo, una fecha que no es fecha). 422 es "está bien formado, pero una regla de negocio no lo permite".
- **409 vs. 422:** 409 es un choque con el **estado actual** del recurso (otra reserva ya ocupa la franja, la reserva ya está cancelada). Con otro estado, el mismo pedido podría pasar. 422 es que el pedido en sí viola una regla, sin importar el estado (50 personas en un aula para 40).
- **403 acá vs. 403 del gateway:** el gateway da 403 cuando el **rol** no alcanza. Esta excepción es para cuando el rol está bien, pero el recurso no es tuyo (RN7). Eso solo lo sabe `ms-reservas`.
- **`super(mensaje)`:** guarda el mensaje en la excepción; el handler lo recupera con `getMessage()` y lo pone en el `message` del `ApiResponse`.

**`GlobalExceptionHandler`**

> "Es la clase que traduce excepciones a respuestas HTTP, en un solo lugar para toda la aplicación. Está anotada con `@RestControllerAdvice`, así que Spring la aplica a todos los controllers: cuando un controller o un service lanza una excepción que nadie atrapó, Spring busca el método `@ExceptionHandler` de ese tipo y devuelve lo que ese método arme. Cada uno elige el código HTTP (404, 403, 409, 422, 503) y arma un `ApiResponse` con `success: false` y el mensaje de la excepción. También maneja `MethodArgumentNotValidException`, que es lo que lanza `@Valid` cuando un DTO viene mal formado, y responde 400 con el detalle de cada campo. Gracias a esto, los controllers no tienen ningún `try/catch`."

Ideas clave por si repreguntan:

- **Cómo lo encuentra Spring si está en otro JAR:** por `@SpringBootApplication(scanBasePackages = "ar.edu.utn.frc.bda.campus")` en cada microservicio. Por defecto, Spring escanea solo el paquete de la clase principal (`...campus.espacios`), y el handler está en `...campus.commons`.
- **`@RestControllerAdvice`** = `@ControllerAdvice` + `@ResponseBody`: lo que devuelven los métodos se escribe como JSON en el body.
- **`ResponseEntity`:** permite elegir el código HTTP además del body. Sin él, todo saldría 200.
- **Por qué los métodos reciben `RuntimeException` y no la excepción concreta:** solo usan `getMessage()`, que tienen todas. El tipo concreto lo decide la anotación `@ExceptionHandler(...)`.
- **El método privado `error(...)`:** evita repetir la misma línea en cinco métodos (DRY).
- **400 de `@Valid`:** recorre los `FieldError` y arma un mensaje del tipo `"capacidad: debe ser mayor que 0; nombre: no debe estar vacío"`.
- **Los otros cuatro 400:** parámetro obligatorio que falta, parámetro que no se puede convertir (`actividad=NADAR`), JSON ilegible o con un enum inválido, y header obligatorio que falta (`X-Usuario`). Sin estos handlers, Spring responde 400 con su propio formato y sin explicar qué está mal (T37 y T57).
- **`UNPROCESSABLE_CONTENT` y no `UNPROCESSABLE_ENTITY`:** es el mismo 422. Spring 7 renombró la constante siguiendo la especificación HTTP actual (RFC 9110) y marcó la vieja como *deprecated*. El plan usa el nombre viejo.
- **Por qué el `pom.xml` también tiene `spring-context`:** `MethodArgumentNotValidException` está en `spring-web`, pero hereda de `BindException` y devuelve un `BindingResult`, que están en `spring-context` (paquete `org.springframework.validation`). `spring-web` no trae `spring-context`, así que sin esa dependencia no compila ("cannot access org.springframework.validation.BindException"). Va `provided` por el mismo motivo que `spring-web`: los microservicios ya lo tienen.
- **Excepciones no previstas (500):** si salta una que no tiene handler (por ejemplo, un `NullPointerException`), Spring responde 500 con su formato por defecto. No la atrapamos a propósito: un 500 indica un bug que hay que corregir, no un error de negocio.

---

## Fase 0: Keycloak

**Qué es Keycloak y qué hace en el TP**

> "Keycloak es nuestro Identity Provider: el sistema que se encarga de la identidad. Guarda los usuarios, sus contraseñas y sus roles (USUARIO y ADMIN), hace el login con el protocolo OIDC y entrega un JWT firmado. Nosotros no programamos el login ni guardamos contraseñas: el usuario se autentica en Keycloak y después manda el JWT en cada pedido (`Authorization: Bearer <jwt>`). El gateway verifica la firma del token con la clave pública de Keycloak y lee los roles para decidir si deja pasar el pedido."

**Por qué el realm se importa desde un JSON**

> "En vez de configurar Keycloak a mano desde la consola, definimos el realm `campus` en `realm-campus.json` (roles, el cliente `campus-api` y los usuarios de prueba) y Keycloak lo importa al arrancar con `--import-realm`. Así cualquiera del grupo levanta exactamente la misma configuración con `docker compose up`, y la configuración queda versionada en Git."

Ideas clave por si repreguntan:

- **Realm:** un espacio aislado dentro de Keycloak, con sus propios usuarios, roles y clientes. El nuestro es `campus`.
- **Rol de realm:** un rol válido en todo el realm. Viaja en el token en `realm_access.roles`.
- **Cliente (`campus-api`):** la aplicación que pide tokens. Es **público** (sin *client secret*), porque quien lo usa (Postman, un futuro frontend) no puede guardar un secreto.
- **`directAccessGrantsEnabled`:** habilita el flujo *password* (usuario y contraseña directo al endpoint de token). Lo usamos **solo para probar con Postman**, porque no hay frontend. En producción se usaría *Authorization Code* con redirección al login de Keycloak (D7).
- **OIDC vs. OAuth 2:** OAuth 2 es para *autorizar* (dar acceso). OIDC (OpenID Connect) es una capa encima que agrega la *identidad*: quién es el usuario.
- **JWT:** un token con tres partes separadas por puntos: header, payload (los *claims*) y firma. No está cifrado, se puede leer; pero está **firmado** por Keycloak, así que si alguien lo modifica la firma deja de coincidir y el gateway lo rechaza (401).
- **Claims que usamos:** `iss` (quién lo emitió; el gateway exige `http://localhost:8180/realms/campus`), `exp` (vencimiento), `preferred_username` (va al header `X-Usuario`) y `realm_access.roles` (USUARIO o ADMIN).
- **Nombre, apellido y email en los usuarios:** Keycloak 26 los exige por su *user profile*. Sin ellos, el login falla con "Account is not fully set up".
- **`start-dev`:** modo desarrollo, con HTTP sin certificado y base embebida. No es para producción.
- **`8180:8080`:** Keycloak escucha en el 8080 *dentro* del contenedor, y lo publicamos en el 8180 de la máquina porque el 8080 lo va a usar el gateway.
- **Volumen `./keycloak:/opt/keycloak/data/import`:** monta nuestra carpeta dentro del contenedor, en la carpeta donde Keycloak busca realms para importar.

---

## Fase 1: ms-espacios

**Cómo se creó el proyecto y qué tiene el `pom.xml`**

> "Lo generamos con Spring Initializr: Maven, Java 25, Spring Boot 4.1.2 y las dependencias Spring Web, Spring Data JPA, H2, Validation y Lombok. A diferencia de campus-commons, es una aplicación, así que hereda de `spring-boot-starter-parent` (versiones y configuración de plugins) y tiene el `spring-boot-maven-plugin`, que arma el JAR ejecutable. A mano agregamos dos dependencias: `campus-commons` (nuestro JAR del `.m2`) y `springdoc-openapi`, que genera el Swagger leyendo los controllers."

Ideas clave por si repreguntan:

- **Starters:** dependencias "paquete" de Spring Boot que traen todo lo necesario para algo. `spring-boot-starter-webmvc` trae Spring MVC, Jackson y un Tomcat embebido; `spring-boot-starter-data-jpa` trae Hibernate y Spring Data. En Boot 4, el viejo `starter-web` se llama `starter-webmvc`.
- **Tomcat embebido:** el servidor web viene dentro del JAR. No hay que instalar un servidor: `java -jar ms-espacios.jar` levanta la API.
- **H2 con `scope runtime`:** el código nunca usa clases de H2 directamente (habla con JPA), solo hace falta al ejecutar.
- **Lombok `optional` + `annotationProcessorPaths`:** Lombok genera código (getters, constructores) durante la compilación, así que solo hace falta al compilar.
- **`mvnw` (Maven Wrapper):** un script que baja la versión exacta de Maven del proyecto. Sirve para compilar en una máquina sin Maven instalado.

**La clase principal y `scanBasePackages`**

> "`@SpringBootApplication` hace tres cosas: marca la clase como configuración, activa la autoconfiguración de Spring Boot y escanea componentes (`@Component`, `@Service`, `@RestController`…). Por defecto escanea solo el paquete de la clase principal, `ar.edu.utn.frc.bda.campus.espacios`. Con `scanBasePackages = "ar.edu.utn.frc.bda.campus"` ampliamos el escaneo un nivel más arriba, y así Spring también encuentra el `GlobalExceptionHandler`, que está en `...campus.commons` dentro del JAR común. Sin eso, los errores saldrían como 500 en vez de 404, 409 o 422."

**`application.yaml`**

> "Es la configuración del servicio: puerto 8081, la base H2 en archivo (`jdbc:h2:file:./data/espacios`), `ddl-auto: update` para que Hibernate cree las tablas a partir de las entidades, la ruta del Swagger y nuestras propiedades propias (`campus.clima.url` y `campus.clima.umbral-lluvia`). Los valores con `${VARIABLE:defecto}` se pueden pisar con variables de entorno: así el mismo JAR corre desde el IDE y en Docker sin cambiar código."

Ideas clave por si repreguntan:

- **`jdbc:h2:file:` vs. `jdbc:h2:mem:`:** en archivo, los datos sobreviven a un reinicio. En memoria se pierden al apagar (D10).
- **`ddl-auto: update`:** Hibernate compara las entidades con las tablas y crea o agrega lo que falte, sin borrar datos. Es cómodo para desarrollo; en producción se usan migraciones (Flyway o Liquibase).
- **`open-in-view: false`:** por defecto, Spring deja abierta la sesión de JPA hasta que termina de escribirse la respuesta HTTP, y eso puede disparar consultas fuera del service. Lo apagamos para que todo el acceso a datos pase por el service, dentro de su transacción. Además, saca un warning al arrancar.
- **`${OPEN_METEO_URL:https://api.open-meteo.com}`:** si existe la variable de entorno `OPEN_METEO_URL`, usa esa; si no, usa el valor después de los dos puntos. Sirve para probar la falla de Open-Meteo (`OPEN_METEO_URL=http://localhost:9`) y para forzar lluvia (`CLIMA_UMBRAL=0`).
- **Puerto 8081:** cada servicio tiene el suyo en la máquina (8080 gateway, 8081 espacios, 8082 reservas). En Docker, solo el gateway lo publica.

**Los enums `TipoEspacio`, `Equipamiento` y `TipoActividad`**

> "Son listas cortas y estables de valores, así que las modelamos como enums de Java (D12). Un enum valida solo: si llega un valor que no existe, como `"PILETA"`, Spring responde 400 sin que escribamos código. `TipoEspacio` además decide qué estrategia de validación se usa (Strategy). La contra es que agregar un valor requiere recompilar, y `TipoActividad` se declara también en `ms-reservas` con los mismos valores, para no compartir clases de dominio entre servicios."

**La entidad `Espacio`**

> "Es la clase que JPA mapea a la tabla `espacios`. `@Entity` la marca como persistente, `@Id` con `GenerationType.IDENTITY` hace que la base genere el id autoincremental, y los enums se guardan como texto (`EnumType.STRING`) para que la base sea legible y no dependa del orden de los valores. El equipamiento y las actividades son conjuntos de enums, así que usamos `@ElementCollection`, que los guarda en tablas aparte (`espacio_equipamiento` y `espacio_actividades`) con el id del espacio, sin necesidad de crear una entidad para cada uno. Lombok genera los getters, setters, constructores y el builder."

Ideas clave por si repreguntan:

- **Anotaciones de JPA (`jakarta.persistence`):** son el estándar. Hibernate es la implementación que las lee y genera el SQL.
- **`IDENTITY`:** la columna `id` es autoincremental en la base; el id se conoce recién al insertar.
- **`EnumType.STRING` vs. `ORDINAL`:** con `ORDINAL` se guarda la posición (0, 1, 2…). Si alguien reordena o inserta un valor en el medio del enum, todos los datos viejos pasan a significar otra cosa. Con `STRING` se guarda `"AULA"`.
- **`@JdbcTypeCode(SqlTypes.VARCHAR)`:** con H2, Hibernate 7 crea las columnas de enums como tipo `ENUM('AULA', ...)` de la base, con la lista fija. Con `ddl-auto: update` esa lista no se actualiza, así que agregar un tipo nuevo (un auditorio) fallaría al insertar. Forzamos `VARCHAR` para que agregar un valor al enum de Java alcance, que es justamente el argumento del patrón Strategy.
- **`@ElementCollection`:** para colecciones de valores simples (enums, strings) que no tienen identidad propia. Alternativa descartada: una entidad `Equipamiento` con `@ManyToMany`, que sería más tablas y un ABM sin valor para el TP.
- **`FetchType.EAGER`:** trae los conjuntos junto con el espacio. Con `open-in-view: false` y sin transacción en el controller, una colección `LAZY` daría `LazyInitializationException` al convertirla a DTO. Son conjuntos chicos, así que el costo es mínimo.
- **`Set` y no `List`:** un espacio no puede tener dos veces el mismo equipamiento.
- **`latitud` y `longitud` como `double`:** se mandan a Open-Meteo. Solo importan en las canchas, pero todos los espacios las tienen; así no hace falta herencia de entidades (D16).
- **`LocalTime` para el horario:** solo la hora del día (08:00), sin fecha. RN3 compara la hora de la franja contra estas dos.
- **Lombok:** `@Data` (getters, setters, `equals`, `hashCode`, `toString`), `@Builder` (`Espacio.builder().nombre(...).build()`), `@NoArgsConstructor` (lo exige JPA para crear objetos al leer de la base) y `@AllArgsConstructor` (lo necesita `@Builder`).
- **`@Builder.Default`:** sin esto, el builder ignora el `= new HashSet<>()` y deja el campo en `null`.
- **La entidad nunca sale del servicio:** los controllers devuelven DTOs (records), no entidades. Así la API no depende de cómo está guardado el dato.
- **`@Data` en una entidad:** genera `equals` y `hashCode` con todos los campos, incluido el `id`, que cambia de `null` a un número al guardar. Si una entidad se mete en un `Set` antes de guardarla, se "pierde" adentro. No nos pasa, porque nunca ponemos `Espacio` en un `Set`, y es lo que usa el plan y la cátedra. Si preguntan, la alternativa es `@Getter` + `@Setter` y un `equals` solo por `id`.

**`EspacioRepository`**

> "Es una interfaz que extiende `JpaRepository<Espacio, Long>`: con eso, Spring Data genera en tiempo de ejecución una implementación con `findAll`, `findById`, `save`, `deleteById`, `count` y más, sin que escribamos SQL ni una clase. Además, declaramos `findByCapacidadGreaterThanEqual(int capacidad)`, una consulta derivada: Spring Data lee el nombre del método y arma solo `SELECT ... WHERE capacidad >= ?`. La usa la búsqueda de compatibles para filtrar por capacidad en la base, antes de aplicar las estrategias."

Ideas clave por si repreguntan:

- **Patrón Repository:** separa el acceso a datos del resto. El service pide "los espacios con capacidad ≥ 30" sin saber de SQL ni de H2.
- **`<Espacio, Long>`:** la entidad que maneja y el tipo de su `@Id`.
- **Consulta derivada del nombre:** `findBy` + campo (`Capacidad`) + operador (`GreaterThanEqual`). Si el nombre no coincide con un campo de la entidad, la app **no arranca**: el error aparece temprano.
- **Interfaz sin implementación:** Spring crea un *proxy* al arrancar. Por eso se puede inyectar aunque no exista ninguna clase que la implemente.
- **Filtrar en la base y no en Java:** traer solo los espacios con capacidad suficiente es más eficiente que traer todos y descartar en memoria.

**Los DTOs: `EspacioRequest`, `EspacioDto` y `ValidacionDto`**

> "Los DTOs son los objetos que entran y salen por la API; la entidad nunca se expone. `EspacioRequest` es lo que manda el ADMIN para crear o modificar un espacio, y tiene las validaciones de formato (`@NotBlank`, `@Positive`, `@NotNull`…), que con `@Valid` dan 400 si algo viene mal. `EspacioDto` es lo que devolvemos al consultar espacios. `ValidacionDto` es la respuesta a ms-reservas cuando pregunta si un espacio sirve para una franja: nombre, capacidad, `disponible` y, si no está disponible, el `motivo`. Son `record` porque solo transportan datos."

Ideas clave por si repreguntan:

- **Por qué no exponer la entidad:** la API queda desacoplada de la base (si cambia una tabla, el JSON sigue igual), el cliente no puede mandar campos que no debe (como el `id` al crear) y elegimos exactamente qué se muestra.
- **Request vs. DTO de respuesta:** el request no tiene `id` (lo genera la base); la respuesta sí.
- **Validación de formato (400) vs. regla de negocio (422):** las anotaciones validan cada campo por separado. Que la hora de apertura sea anterior a la de cierre compara dos campos, y lo controla el service.
- **`ValidacionDto` no es un error:** que un espacio no sirva para una franja es una respuesta normal (200 con `disponible: false` y el `motivo`). Quien decide qué hacer es ms-reservas.
- **`ValidacionDto` trae nombre y capacidad:** ms-reservas guarda el nombre en la reserva (D11) y controla RN5 con la capacidad, sin otra llamada.
- **Paquete `entities.models`:** la convención del ejemplo de la cátedra para los DTOs.

**El cliente de Open-Meteo (`RestClientConfig`, `OpenMeteoResponse` y `OpenMeteoClient`)**

> "Open-Meteo es la API pública que nos da la probabilidad de lluvia por hora; es gratuita y no pide API key. La consultamos con `RestClient`, el cliente HTTP de Spring. En `RestClientConfig` creamos un `RestClient` como bean, con la URL base de la configuración y dos timeouts: 2 segundos para conectar y 3 para leer. `OpenMeteoResponse` es un record con solo los campos que usamos de la respuesta. `OpenMeteoClient` arma el pedido con la latitud, la longitud y la fecha, y recorre las horas de la franja para quedarse con la probabilidad máxima. Si Open-Meteo no responde, tarda demasiado, devuelve un error o un JSON incompleto, lanzamos `ServicioNoDisponibleException`, que el JAR común convierte en 503. Nunca sale un 500 por culpa de la API externa."

Ideas clave por si repreguntan:

- **Por qué se consulta afuera:** el pronóstico cambia hora a hora; no se puede guardar como configuración (5.3 de la Entrega 1).
- **Pedido real:** `GET /v1/forecast?latitude=-31.44&longitude=-64.19&hourly=precipitation_probability&timezone=America/Argentina/Cordoba&start_date=...&end_date=...`.
- **Respuesta:** dos listas paralelas, `hourly.time` (`"2026-10-24T10:00"`) y `hourly.precipitation_probability` (`73`). La posición `i` de una corresponde a la posición `i` de la otra.
- **Timeouts:** sin ellos, si Open-Meteo se cuelga, el pedido del usuario queda esperando indefinidamente y ocupa un hilo del servidor. Con 2 + 3 segundos, el peor caso es un 503 rápido.
- **Bean con nombre:** el método `openMeteoRestClient` crea un bean con ese nombre. El campo `private final RestClient openMeteoRestClient` se inyecta por constructor (`@RequiredArgsConstructor`).
- **`@JsonProperty("precipitation_probability")`:** el JSON usa *snake_case*; en Java el campo se llama `lluvia`. La anotación dice qué clave del JSON va a ese campo.
- **Solo los campos que usamos:** Jackson ignora el resto del JSON (latitud, elevación, unidades…). Si Open-Meteo agrega campos, no nos rompe.
- **`timezone=America/Argentina/Cordoba`:** las horas vienen en hora local, igual que las de la reserva. Sin esto vendrían en UTC, 3 horas corridas.
- **Cómo se calcula:** se toman las horas desde el inicio redondeado a la hora (`truncatedTo(HOURS)`) hasta antes del fin. Una franja de 10:30 a 13:00 mira las 10, 11 y 12.
- **`catch (RestClientException)`:** atrapa las tres fallas posibles de la llamada: no conecta, timeout o respuesta HTTP de error (por ejemplo, 400 si la fecha está fuera del rango del pronóstico).
- **Validar la respuesta:** un 200 no garantiza datos útiles. Si faltan las listas, tienen distinto tamaño o no cubren la franja, también es 503.
- **Horizonte:** Open-Meteo da pronóstico hasta 16 días. RN1 limita las reservas a 14 días, así que siempre hay pronóstico para una reserva válida.
- **Por qué está en ms-espacios:** el clima depende de la ubicación del espacio, no de quién reserva.

**Patrón Strategy: una validación por tipo de espacio**

> "Cada tipo de espacio se valida distinto: un laboratorio no se reserva el fin de semana, una sala no más de 2 horas, una cancha solo en horas enteras y sin lluvia. Por eso aplicamos Strategy: una clase abstracta `ValidadorEspacioBase`, que es el tipo común de todas las estrategias, y una subclase por tipo de espacio (`ValidadorAula`, `ValidadorLaboratorio`, `ValidadorSala` y `ValidadorCancha`). Cada estrategia dice qué tipo atiende con `tipo()` y responde `motivoRechazo(...)`: `null` si el espacio sirve, o el motivo si no. Usamos una clase abstracta y no una interfaz porque, además de ser el contrato, reutiliza el código de las reglas comunes. El service recibe todas las estrategias, las guarda en un mapa por tipo y usa la que corresponde al espacio, sin ningún `if` ni `switch`. Para sumar un tipo nuevo, como un auditorio, alcanza con agregar el valor al enum y una clase `ValidadorAuditorio`: el service no cambia. Eso es abierto a extensión y cerrado a modificación."

**Patrón Template Method: las reglas comunes primero**

> "Dentro de las estrategias usamos Template Method. La clase abstracta `ValidadorEspacioBase` tiene el método `motivoRechazo`, marcado `final`, que fija el orden de los pasos: primero las reglas comunes a todos los espacios (RN3 horario, RN9 actividad y RN8 equipamiento) y al final llama a `reglasPropias(...)`, el paso que cada subclase completa. Por defecto no hace nada, que es justo lo que necesita el aula. Laboratorio, sala y cancha lo sobrescriben con sus reglas (RN10, RN11, RN12 y RN4). Así las reglas comunes están escritas una sola vez y ninguna subclase se las puede saltear."

Ideas clave por si repreguntan:

- **Strategy en una frase:** varias formas intercambiables de hacer lo mismo detrás de un tipo común; se elige una según el caso. Acá, el caso es el tipo de espacio.
- **Strategy con clase abstracta:** el patrón pide un tipo común para todas las estrategias, que puede ser una interfaz o una clase abstracta (el libro de GoF muestra las dos). Nosotros usamos la clase abstracta porque, además, nos da el Template Method.
- **Una sola clase, dos patrones:** `ValidadorEspacioBase` cumple dos roles. Como tipo común que conoce el service, es el Strategy (`tipo()` abstracto y `motivoRechazo`). Como esqueleto con pasos fijos y un paso a completar, es el Template Method.
- **Template Method en una frase:** una clase base fija los pasos de un algoritmo y deja uno (o más) para que lo complete cada subclase.
- **Por qué `final` en `motivoRechazo`:** para que ninguna subclase pueda reemplazar el método entero y saltearse RN3, RN8 o RN9. Solo puede completar el paso `reglasPropias`.
- **Por qué `protected` en `reglasPropias`:** lo ven las subclases para sobrescribirlo, pero no el resto del código. Nadie de afuera debería llamar solo a las reglas propias.
- **Por qué devuelve el motivo (`String`) y no lanza excepción:** que un espacio no sirva no es un error. La búsqueda de compatibles necesita preguntar por muchos espacios y quedarse con los que sirven; con excepciones, cada "no" sería un `try/catch`.
- **La única excepción es la de Open-Meteo:** si no hay pronóstico, la estrategia no puede responder ni sí ni no. Por eso `ServicioNoDisponibleException` (503) sí se propaga.
- **Orden de los controles:** de lo barato a lo caro. En la cancha, RN12 (una cuenta) va antes que RN4 (una llamada HTTP): si la franja dura 90 minutos, no se consulta el clima.
- **`@Component` en cada estrategia:** Spring las crea y las puede entregar todas juntas como `List<ValidadorEspacioBase>`.
- **`tipo()` es abstracto:** la clase base no sabe qué tipo atiende; cada subclase está obligada a decirlo. Si una subclase se olvida, no compila.
- **`tipo()`:** cada estrategia se identifica sola. El service no tiene una lista "aula → ValidadorAula" escrita a mano.
- **Por qué no un `switch` por tipo:** crece con cada tipo y con cada regla; todos los cambios tocan el mismo método, y es fácil romper un tipo al cambiar otro (D16).
- **Por qué no herencia de entidades:** lo que cambia por tipo son las reglas, no los datos (T16).
- **RN8 con conjuntos:** `faltante = pedido − equipamiento del espacio` (`removeAll`). Si queda algo, eso es lo que falta, y va en el mensaje.
- **RN10 usa `LocalDateTime.now()`:** las 24 horas se cuentan desde el momento de la validación.
- **`Duration.between(inicio, fin).toMinutes()`:** la duración de la franja en minutos. RN11: `> 120`. RN12: `% 60 != 0` (el resto de dividir por 60 tiene que ser 0).

**`EspacioService`**

> "Es la lógica de ms-espacios. Tiene el ABM (listar, buscar, crear, modificar y borrar) y los dos métodos que usa ms-reservas: `validar`, que dice si un espacio concreto sirve para una franja, y `compatibles`, que devuelve todos los espacios que sirven. En el constructor, Spring le pasa todas las estrategias como `List<ValidadorEspacioBase>`, y el service las guarda en un mapa por tipo (`EnumMap<TipoEspacio, ValidadorEspacioBase>`). Para validar un espacio busca en el mapa la estrategia de su tipo y la usa: no hay ningún `if` ni `switch` por tipo. En `compatibles`, si Open-Meteo no responde, la cancha se omite y se siguen devolviendo los demás espacios."

Ideas clave por si repreguntan:

- **Cómo Spring arma el Strategy:** al pedir `List<ValidadorEspacioBase>` en el constructor, Spring busca todos los beans de ese tipo (las cuatro subclases con `@Component`) y los entrega en una lista. El service no conoce ninguna clase concreta.
- **`EnumMap`:** un `Map` optimizado para claves enum (internamente es un arreglo). `validadores.get(e.getTipo())` devuelve la estrategia en tiempo constante.
- **Agregar un tipo no toca el service:** la nueva estrategia entra sola a la lista y al mapa (abierto/cerrado).
- **`validar` no es un error si no sirve:** devuelve 200 con `disponible: false` y el motivo. Sí es error si el espacio no existe (404) o si la franja está mal armada (422).
- **`compatibles` filtra en dos etapas:** la base filtra por capacidad (`findByCapacidadGreaterThanEqual`) y después cada estrategia aplica sus reglas. La capacidad (RN5) es de ms-reservas, pero se aprovecha para no traer espacios chicos.
- **`try/catch` de `ServicioNoDisponibleException` en `compatibles`:** sin pronóstico no se puede garantizar una cancha, así que se omite; aulas, laboratorios y salas se siguen devolviendo (D15). En `validar` no se atrapa: al reservar una cancha sin pronóstico, 503.
- **`obtener(id)`:** `findById` devuelve un `Optional`; `orElseThrow` lanza `RecursoNoEncontradoException` (404) si está vacío. Lo usan buscar, modificar, borrar y validar.
- **`@Transactional` en las escrituras:** crear, modificar y borrar corren en una sola transacción: o se guarda todo (el espacio y sus conjuntos) o nada.
- **`copiarDatos`:** pasa los datos del request a la entidad. Lo comparten crear (sobre una entidad nueva) y modificar (sobre la existente), así no se repite.
- **`aDto`:** convierte la entidad en DTO. La entidad nunca sale del service.
- **`Optional`:** un contenedor que puede tener un valor o estar vacío. Obliga a decidir qué hacer si no hay nada, en vez de devolver `null`.
- **`stream().map(this::aDto).toList()`:** convierte cada entidad de la lista en un DTO. `this::aDto` es una referencia a método, lo mismo que `e -> aDto(e)`.

**`EspacioController` y Swagger**

> "El controller expone la API REST de ms-espacios bajo `/api/espacios`: el ABM (GET, GET por id, POST, PUT y DELETE) y los dos endpoints que usa ms-reservas, `GET /{id}/validacion` y `GET /compatibles`. No tiene lógica: recibe el pedido, se lo pasa al service y envuelve el resultado en un `ApiResponse` con el código HTTP que corresponde (200, o 201 al crear). Los errores no se manejan acá: los lanza el service y el `GlobalExceptionHandler` del JAR común los traduce. Con `@Tag` y `@Operation`, springdoc arma la documentación de Swagger, donde se puede probar cada endpoint."

Ideas clave por si repreguntan:

- **`@RestController`:** un controller cuyos métodos devuelven datos (JSON), no páginas. Es `@Controller` + `@ResponseBody`.
- **`@RequestMapping("/api/espacios")`:** el prefijo común de todas las rutas de la clase.
- **Métodos HTTP:** `GET` consulta (no cambia nada), `POST` crea, `PUT` reemplaza un recurso existente, `DELETE` borra. El verbo dice qué se hace; la URL dice sobre qué.
- **`@PathVariable`:** un valor que es parte de la ruta (`/api/espacios/3` → `id = 3`). Identifica el recurso.
- **`@RequestParam`:** un valor de la *query string* (`?inicio=...&actividad=CLASE`). Filtra o parametriza la consulta.
- **`@RequestBody`:** el JSON del cuerpo del pedido, convertido al record. Va con `@Valid` para activar las validaciones.
- **`@DateTimeFormat(iso = DATE_TIME)`:** indica que las fechas llegan en formato ISO: `2026-10-24T10:00`.
- **Equipamiento repitiendo el parámetro:** `equipamiento=PROYECTOR&equipamiento=PIZARRA` se convierte en un `Set`. Es opcional (`required = false`).
- **201 Created al crear:** el código correcto cuando un `POST` crea un recurso. El cuerpo trae el espacio con su `id`.
- **Rutas `/compatibles` y `/{id}`:** no chocan, porque Spring prefiere la ruta literal antes que la que tiene variables.
- **Controller sin `try/catch` ni lógica:** solo traduce HTTP ↔ service. Si mañana la API fuera por otro medio, el service no cambiaría.
- **Swagger UI** (`/swagger-ui.html`): springdoc lee los controllers (rutas, parámetros, records y anotaciones) y genera la especificación OpenAPI en `/v3/api-docs`. Swagger UI la muestra como página para probar la API.
- **`@Tag`** agrupa los endpoints en Swagger; **`@Operation`** describe cada uno. En la descripción pusimos los códigos de error posibles.
- **Errores 400 de formato:** un parámetro que falta, un valor que no se puede convertir (`actividad=NADAR`, `id=abc`, una fecha mal escrita) o un JSON roto también salen con `ApiResponse` y un mensaje claro, gracias a tres handlers más en el JAR común (T37).

**`DatosIniciales`**

> "Es un `CommandLineRunner`: Spring Boot ejecuta su método `run` una vez, apenas termina de arrancar la aplicación. Si la tabla de espacios está vacía, carga los cinco espacios de prueba del plan: dos aulas, un laboratorio, una sala y una cancha, con distinto equipamiento, actividades y horarios, de forma que cada regla se pueda mostrar en la demo. Si ya hay datos, no hace nada, así un reinicio no duplica los espacios."

Ideas clave por si repreguntan:

- **`CommandLineRunner`:** una interfaz de Spring Boot con un solo método, `run`. Todo bean que la implemente se ejecuta al arrancar, con el contexto ya armado (repositorios y services disponibles).
- **`if (repository.count() > 0) return;`:** la base es un archivo (H2 en archivo), así que los datos sobreviven a un reinicio. Sin este control, cada arranque agregaría otros cinco espacios.
- **`saveAll`:** guarda la lista entera en una sola llamada.
- **Builder de Lombok:** `Espacio.builder().nombre(...).capacidad(...).build()` se lee mejor que un constructor de diez parámetros, donde es fácil confundir el orden.
- **Por qué esos cinco espacios:** cada uno permite mostrar reglas distintas. El Aula 101 tiene proyector y el Aula 102 no (RN8, y la búsqueda con proyector devuelve solo el Aula 101). El laboratorio sirve para RN10, la sala tiene un horario más corto (RN3) y sirve para RN11, y la cancha para RN4 y RN12. El laboratorio (25) y la sala (12) no entran en una búsqueda para 30 personas.
- **Todos en la misma ubicación:** la latitud y longitud de la UTN FRC. Solo importa para la cancha (Open-Meteo).
- **Paquete `config`:** la convención de la cátedra para la carga de datos de prueba.
- **Detalle de arranque:** el runner se ejecuta justo después del log "Started", así que durante un instante, en el primer arranque, la lista puede venir vacía.

**Cómo cambiar la configuración sin tocar código (fuentes de configuración)**

> "La URL de Open-Meteo está en el `application.yaml` como `${OPEN_METEO_URL:https://api.open-meteo.com}`. Si existe la variable de entorno `OPEN_METEO_URL`, Spring usa esa; si no, el valor por defecto. Así el mismo JAR corre en el IDE y en Docker, y para la demo de la falla solo cambiamos la variable, sin tocar el código."

- **Prioridad de las fuentes:** un argumento de línea de comandos (`--campus.clima.url=...`) le gana a una variable de entorno, y una variable de entorno le gana al `application.yaml`.
- **En PowerShell:** `$env:OPEN_METEO_URL = "http://localhost:9"` vale solo en esa terminal; se borra con `Remove-Item Env:OPEN_METEO_URL`.
- **En Docker:** se pone en el `docker-compose.yml`, bajo `environment:`.
- **Por qué no editar el yaml para probar:** hay que acordarse de volver atrás, y si se commitea, los demás quedan con las canchas rotas sin saber por qué.
- **`CLIMA_UMBRAL=0`:** cualquier pronóstico cuenta como lluvia, así la cancha da 422 (RN4) en la demo, aunque no llueva.

---

## Fase 2: ms-reservas

**Cómo se creó el proyecto**

> "Igual que ms-espacios: con Spring Initializr, Boot 4.1.2, Java 25 y las mismas cinco dependencias (Spring Web, Spring Data JPA, H2, Validation y Lombok), con el paquete `ar.edu.utn.frc.bda.campus.reservas`. A mano sumamos `campus-commons` y springdoc, y en la clase principal `scanBasePackages = "ar.edu.utn.frc.bda.campus"` para que encuentre el `GlobalExceptionHandler` del JAR común. Corre en el puerto 8082, con su propia base H2 (`data/reservas`)."

Ideas clave por si repreguntan:

- **Su propia base:** `reservas.mv.db` es distinta de `espacios.mv.db`. ms-reservas no puede leer las tablas de ms-espacios; si necesita algo de un espacio, se lo pide por HTTP (D9, una base por servicio).
- **`campus.ms-espacios-url: ${MS_ESPACIOS_URL:http://localhost:8081}`:** desde el IDE, ms-espacios está en `localhost:8081`. En Docker, cada contenedor tiene su propio `localhost`, así que ahí la variable vale `http://ms-espacios:8081`: el nombre del servicio en la red interna de Compose.
- **Mismas decisiones que ms-espacios:** `open-in-view: false` (T14), H2 en archivo (D10), `ddl-auto: update` y el mismo patrón de propiedades con variable de entorno (T15).

**`EstadoReserva`: la máquina de estados**

> "Una reserva tiene cuatro estados: PENDIENTE, CONFIRMADA, RECHAZADA y CANCELADA. Nace PENDIENTE si es un evento y CONFIRMADA si no (RN13). El enum sabe a qué estados puede pasar cada uno con el método `puedePasarA`: una pendiente puede confirmarse, rechazarse o cancelarse; una confirmada solo cancelarse; rechazada y cancelada son finales. Cualquier otro cambio es un 409 (RN14). Como las reglas están dentro del enum, ningún método del service puede saltearlas. Además, `ACTIVOS` agrupa los estados que ocupan el espacio (PENDIENTE y CONFIRMADA), que es lo que usan la superposición (RN6) y la búsqueda de disponibles."

Ideas clave por si repreguntan:

- **Máquina de estados:** un objeto solo cambia de estado por las transiciones permitidas. Es el diagrama de estados de la Entrega 1 escrito en código.
- **Por qué en el enum y no en el service:** la regla queda en un solo lugar, junto al dato. Aprobar, rechazar y cancelar preguntan lo mismo (`puedePasarA`) y no pueden contradecirse.
- **`switch` como expresión (`->`):** cada `case` devuelve un valor. Si se agrega un estado nuevo y no se contempla, **no compila**: el compilador obliga a cubrir todos los casos.
- **`RECHAZADA, CANCELADA -> false`:** son estados finales; ya no pueden cambiar.
- **`ACTIVOS = EnumSet.of(PENDIENTE, CONFIRMADA)`:** una constante del enum. `EnumSet` es un `Set` optimizado para enums. Las pendientes cuentan como ocupadas para que el ADMIN no pueda aprobar dos eventos en la misma franja (D19).
- **Por qué no el patrón State (una clase por estado):** para cuatro estados con reglas de una línea, un `switch` es más claro (D18).
- **Estados que cambian solos con el tiempo** (FINALIZADA, VENCIDA): no los tenemos, porque necesitarían una tarea programada. Quedan fuera del alcance (D18).

**`TipoActividad` repetido en ms-reservas**

> "Es el mismo enum que en ms-espacios, con los mismos valores, pero declarado otra vez en este servicio. No lo compartimos por el JAR común porque es una clase de dominio, y compartir dominio acoplaría los servicios (D13). Viaja entre ellos como texto en la URL (`actividad=CLASE`), así que alcanza con que los valores coincidan."

**La entidad `Reserva`**

> "Es la reserva guardada en la base de ms-reservas: quién reservó (`usuario`, que viene del header `X-Usuario`), qué espacio, en qué franja, para cuántas personas, qué actividad y en qué estado. Del espacio guarda solo el `espacioId` y una copia del nombre, no una relación JPA, porque el espacio vive en la base de otro servicio. Tiene `@Version` para el bloqueo optimista: si un ADMIN aprueba y el dueño cancela la misma reserva al mismo tiempo, el segundo recibe 409 en vez de pisar el cambio del primero."

Ideas clave por si repreguntan:

- **`espacioId` y no `@ManyToOne Espacio`:** la tabla `espacios` no existe en esta base (D9). Una relación JPA necesita una clave foránea a una tabla de la misma base.
- **`espacioNombre` (copia):** para listar y cancelar reservas aunque ms-espacios esté caído, y para no hacer una llamada por cada reserva al listar (D11). El costo: si se renombra un espacio, las reservas viejas muestran el nombre anterior.
- **`usuario` es un `String`:** el `preferred_username` del token (por ejemplo `usuario1`). No hay tabla de usuarios: los usuarios los maneja Keycloak.
- **`LocalDateTime`:** fecha y hora de inicio y de fin, sin zona horaria, en hora local.
- **`@Version Long version`:** JPA guarda un número de versión por fila. Al hacer `UPDATE ... WHERE id = ? AND version = ?`, incrementa la versión. Si otro la cambió antes, el `WHERE` no encuentra la fila, y JPA lanza `OptimisticLockException` → 409.
- **Enums como `VARCHAR`:** mismo motivo que T18. Si se agrega un estado, la base lo acepta sin migrar la columna.

**La entidad `AgendaEspacio`**

> "Es una tabla con una fila por espacio y un campo `@Version`, que existe solo para la concurrencia (RN15). Dos pedidos que reservan el mismo espacio al mismo tiempo crean dos reservas **nuevas y distintas**, así que no chocan entre sí: el `@Version` de `Reserva` no las detecta. Por eso cada reserva nueva incrementa también la versión de la fila de agenda de su espacio, dentro de la misma transacción. Si dos transacciones parten de la misma versión, la segunda falla al hacer commit, se deshace y recibe 409. La agenda es el punto en común donde los dos pedidos tienen que chocar."

Ideas clave por si repreguntan:

- **`@Id` sin `@GeneratedValue`:** el id **es** el id del espacio; lo ponemos nosotros, no la base.
- **Solo dos columnas:** `espacio_id` y `version`. No guarda nada más: su única función es tener una versión que incrementar.
- **`@Getter` y no `@Data`:** no hay nada que modificar a mano; la versión la maneja JPA. Sin setters, nadie puede cambiarla por error.
- **Constructor `AgendaEspacio(Long espacioId)`:** crea la fila la primera vez que se reserva un espacio. El constructor sin argumentos (`@NoArgsConstructor`) es el que exige JPA.
- **Por qué no un `synchronized`:** solo protege dentro de una JVM. Con dos instancias de ms-reservas ya no sirve; el control tiene que estar en la base, que es lo compartido (D21).

**Los DTOs: `CrearReservaRequest` y `ReservaDto`**

> "`CrearReservaRequest` es lo que manda el usuario para reservar: espacio, inicio, fin, asistentes, actividad y, opcionalmente, el equipamiento que necesita. No trae el usuario: ese dato sale del token, a través del header `X-Usuario`, para que nadie pueda reservar a nombre de otro. `ReservaDto` es lo que devolvemos: la reserva con su id, el nombre del espacio, el dueño y el estado. El equipamiento viaja como texto (`Set<String>`) porque ms-reservas no necesita conocer la lista de valores posibles: se lo pasa tal cual a ms-espacios, que es quien la conoce."

Ideas clave por si repreguntan:

- **El usuario no va en el body:** si viniera en el JSON, cualquiera podría crear reservas a nombre de otro (D8). Lo pone el gateway, a partir del token ya validado.
- **No tiene estado:** el estado inicial lo decide el service según la actividad (RN13), no el cliente.
- **Solo validaciones de formato:** que vengan los campos y que haya al menos un asistente. Las reglas de fechas (RN1, RN2) y de capacidad (RN5) están en la cadena de validaciones, porque son reglas de negocio (422), no de formato (400).
- **`version` no está en el DTO:** es un detalle interno del control de concurrencia; al cliente no le sirve.

**Los repositorios: `ReservaRepository` y `AgendaEspacioRepository`**

> "`ReservaRepository` tiene dos consultas escritas a mano con `@Query` en JPQL: `existeSuperposicion`, que dice si un espacio ya tiene una reserva activa que se pisa con la franja pedida (RN6), y `espaciosOcupados`, que devuelve los ids de los espacios ocupados en una franja, para la búsqueda de disponibles. Además tiene consultas derivadas del nombre para listar las reservas de un usuario, las de un estado y todas, ordenadas por inicio. `AgendaEspacioRepository` tiene `findByEspacioId` con `@Lock(OPTIMISTIC_FORCE_INCREMENT)`: al leer la fila de agenda, JPA queda obligado a incrementar su versión al hacer commit, aunque no cambie ningún dato. Eso es lo que hace chocar a dos reservas simultáneas del mismo espacio."

Ideas clave por si repreguntan:

- **Condición de superposición:** dos franjas se pisan si **cada una empieza antes de que termine la otra**: `r.inicio < :fin AND r.fin > :inicio`. Cubre todos los casos: se pisa por un borde, una contiene a la otra o son iguales.
- **Franjas contiguas no se pisan:** una reserva de 10 a 12 y otra de 12 a 13 son válidas, porque las comparaciones son estrictas (`<` y `>`). Lo probamos.
- **`r.estado IN :estados`:** se pasan los `ACTIVOS` (PENDIENTE y CONFIRMADA). Una reserva cancelada o rechazada no ocupa el espacio.
- **JPQL, no SQL:** se escribe sobre las **entidades y sus campos** (`Reserva r`, `r.espacioId`), no sobre tablas y columnas. Hibernate lo traduce al SQL del motor.
- **`:espacioId`:** parámetro con nombre; Spring lo une con el parámetro del método que se llama igual.
- **`SELECT COUNT(r) > 0`:** la consulta devuelve directamente un `boolean`.
- **`SELECT DISTINCT r.espacioId`:** solo los ids, sin repetir. Se devuelve un `Set<Long>` para preguntar después `ocupados.contains(id)`.
- **Por qué `@Query` y no un nombre derivado:** la condición de superposición sería un nombre de método larguísimo e ilegible.
- **`OPTIMISTIC_FORCE_INCREMENT`:** el bloqueo optimista normal incrementa la versión solo si la entidad cambió. Este modo la incrementa **siempre**, que es lo que necesitamos: la agenda no cambia ningún dato, pero cada reserva tiene que "tocarla". Lo probamos: dos lecturas en dos transacciones dejaron la versión en 2.

**El cliente de ms-espacios (`EspaciosClient`): la colaboración entre microservicios**

> "`EspaciosClient` es la forma en que ms-reservas le habla a ms-espacios, con `RestClient` por HTTP, y en un solo sentido (D2). Tiene dos métodos, uno por cada proceso colaborativo: `validar`, que al crear una reserva pregunta si un espacio concreto sirve para la franja, y `compatibles`, que en la búsqueda pide todos los espacios que sirven. Las respuestas de ms-espacios vienen envueltas en `ApiResponse`, así que se leen con un `ParameterizedTypeReference` para no perder el tipo genérico. Lo más importante es la traducción de errores: un 404 remoto se convierte en un 404 nuestro, un 400 remoto (por ejemplo, un equipamiento que no existe) en un 422, el 503 del clima en un 503 con el mismo mensaje, y si ms-espacios no responde o tarda más de 3 segundos, en un 503 'El servicio de espacios no está disponible'. Así el usuario siempre recibe un error claro, nunca un 500."

Ideas clave por si repreguntan:

- **Mismo `RestClient` que con Open-Meteo:** URL base de la configuración (`campus.ms-espacios-url`) y timeouts de 2 s para conectar y 3 s para leer (D3, D4).
- **Records propios (`ValidacionEspacio` y `EspacioDisponible`):** ms-reservas no usa los DTOs de ms-espacios; declara sus propios records con los campos que necesita (D13). Jackson los llena por nombre de campo e ignora el resto (por ejemplo, el horario y las coordenadas que vienen en `EspacioDto`).
- **`tipo` y `equipamiento` como texto:** ms-reservas no necesita conocer los enums de ms-espacios.
- **`ParameterizedTypeReference<ApiResponse<ValidacionEspacio>>`:** en Java, los genéricos se borran al compilar (*type erasure*), así que `ApiResponse.class` no alcanza para decir "un `ApiResponse` de `ValidacionEspacio`". Esta clase anónima (`{ }`) conserva el tipo completo para que Jackson sepa qué construir.
- **El orden de los `catch` importa:** del más específico al más general. `NotFound` es un `HttpClientErrorException`, que es un `RestClientException`. Si el `catch` general fuera primero, atraparía todo.
- **`mensajeRemoto`:** lee el `message` del `ApiResponse` de error que mandó ms-espacios, y lo reusa. Así el usuario ve "No se pudo consultar el pronóstico del clima" o "Valor inválido para 'equipamiento': PILETA", y no un mensaje genérico.
- **Por qué un 400 de ms-espacios se convierte en 422 y no en 400:** el pedido que recibió ms-reservas está bien formado (un `Set<String>`); lo que falla es que ese equipamiento no existe en el catálogo, y eso solo lo sabe ms-espacios. Es una regla del dominio del otro servicio.
- **`contenido(r)`:** si ms-espacios responde 200 pero sin datos, es una respuesta inesperada → 503. Mismo criterio que con Open-Meteo.
- **`queryParam("equipamiento", set.toArray())`:** con un arreglo, `RestClient` repite el parámetro (`equipamiento=PROYECTOR&equipamiento=PIZARRA`), que es como lo espera el controller de ms-espacios.
- **`build(espacioId)`:** reemplaza `{id}` en el path por el valor, codificado.
- **Qué pasa si ms-espacios está caído:** crear y buscar dan 503 (no se puede validar). Listar y cancelar siguen funcionando porque no lo llaman: usan el nombre copiado (D11).

**Patrón Chain of Responsibility: la cadena de validaciones de una reserva nueva**

> "Antes de guardar una reserva hay que controlar cinco cosas, en orden: que empiece en el futuro y dentro de 14 días (RN1), que dure entre 30 minutos y 4 horas en el mismo día (RN2), que el espacio sirva para esa franja, que lo decide ms-espacios (RN3, RN4, RN8 a RN12), que los asistentes entren en el espacio (RN5) y que no se pise con otra reserva activa (RN6). Cada control es un eslabón: una clase que extiende `ValidadorReserva` y hace una sola cosa en su método `verificar`. Si pasa, el pedido sigue al siguiente eslabón; si falla, lanza su excepción y la cadena se corta ahí. Spring nos entrega los cinco eslabones ordenados por `@Order`, y `CadenaValidacionReserva` los enlaza uno con otro al arrancar. El service solo llama a `cadena.validar(ctx)`."

Ideas clave por si repreguntan:

- **Chain of Responsibility en una frase:** un pedido pasa por una cadena de manejadores; cada uno hace su control y lo pasa al siguiente, o corta la cadena.
- **Cada eslabón conoce solo al siguiente** (`siguiente`), no a toda la cadena. El primero no sabe cuántos hay después.
- **`validar` es `final` y `verificar` es abstracto:** la clase base fija el recorrido (verificar y pasar al siguiente) y cada eslabón solo completa su control. Es un Template Method chiquito dentro del patrón.
- **El orden importa, de lo barato a lo caro:** primero los controles locales e instantáneos (RN1, RN2), después la llamada HTTP a ms-espacios y al final la consulta a la base (RN6). Un pedido con una fecha de ayer no llega a llamar a ms-espacios: lo probamos, y con ms-espacios apagado RN1 y RN2 siguen respondiendo 422 al instante.
- **RN5 va después del eslabón del espacio** porque necesita la capacidad, que la trae ms-espacios.
- **`ContextoReserva`:** lo que viaja por la cadena. Lleva el pedido, el usuario y lo que los eslabones van averiguando: el eslabón remoto guarda ahí la validación del espacio, y el de capacidad y después el service la usan, sin volver a llamar a ms-espacios.
- **`@Order(n)`:** Spring ordena la `List<ValidadorReserva>` que inyecta según esta anotación. Agregar una regla es agregar un eslabón con su `@Order`, sin tocar los demás.
- **`enlazar` es de paquete (sin `public`):** solo `CadenaValidacionReserva`, que está en el mismo paquete, puede armar la cadena. Nadie de afuera puede reenlazarla.
- **La cadena se arma una sola vez:** en el constructor de `CadenaValidacionReserva`, al arrancar. Los eslabones son beans únicos (*singleton*), así que el enlace dura toda la vida de la aplicación.
- **`ReglasFranja`:** RN1 y RN2 están en una clase utilitaria (constructor privado, métodos `static`) porque las usan dos lugares: los dos primeros eslabones y la búsqueda de disponibles, que no pasa por la cadena.
- **Por qué no Strategy acá:** Strategy elige **una** forma entre varias; acá se aplican **todas**, en orden (D17).
- **Por qué no `if` seguidos en `crear()`:** un método largo que mezcla todas las reglas, difícil de probar y de extender (D17).
- **Por qué no Bean Validation:** sirve para el formato de un campo (`@NotNull`), no para reglas que consultan la base u otro servicio (D17).

**`ReservaService`**

> "Es la lógica de ms-reservas. Para crear una reserva hace tres cosas dentro de una transacción: toma la agenda del espacio (RN15), pasa el pedido por la cadena de validaciones y decide el estado inicial: PENDIENTE si es un evento y CONFIRMADA si no (RN13). Después guarda la reserva con el nombre del espacio que trajo ms-espacios. Aprobar, rechazar y cancelar buscan la reserva y piden el cambio de estado a `cambiarEstado`, que pregunta al enum si la transición es válida; si no, 409 (RN14). Cancelar además controla que la reserva sea del usuario (403) y que no haya empezado (422), que es RN7. La búsqueda de disponibles pide a ms-espacios los compatibles y descarta los que ya tienen una reserva activa en esa franja."

Ideas clave por si repreguntan:

- **Todo `crear` es una sola transacción (`@Transactional`):** si cualquier eslabón de la cadena falla, se deshace todo, incluida la fila de agenda que se haya creado. La reserva se guarda o no se guarda; nunca queda a medias.
- **`tomarAgenda` va primero:** lee la fila de agenda con `OPTIMISTIC_FORCE_INCREMENT`, así esta transacción queda "anotada" para incrementar su versión al hacer commit. Si es la primera reserva del espacio, la crea con `saveAndFlush` (`flush` para que el `INSERT` se haga en ese momento, y un choque se detecte enseguida).
- **Qué pasa con dos pedidos simultáneos:** los dos pasan la cadena, pero al hacer commit solo uno puede subir la versión de la agenda. El otro recibe `ObjectOptimisticLockingFailureException`, se deshace (su reserva no queda guardada) y responde 409. Si era la primera reserva del espacio, los dos intentan insertar la misma fila de agenda y el segundo recibe `DataIntegrityViolationException` (clave repetida), que también es 409.
- **Lo probamos con hilos simultáneos:** 2 y 5 pedidos idénticos al mismo tiempo → **siempre una sola reserva guardada**. Y **sin la agenda**, 5 pedidos simultáneos guardaron **5 reservas en la misma franja** del mismo espacio. Esa es la prueba de que hace falta.
- **Los cambios de estado no llaman a `save`:** dentro de una transacción, la entidad leída está "administrada" por JPA, que detecta los cambios (*dirty checking*) y hace el `UPDATE` al hacer commit. Ahí también se aplica el `@Version` de la reserva.
- **Orden de los controles en `cancelar`:** primero que exista (404), después que sea tuya (403), después que no haya empezado (422) y por último que la transición sea válida (409). Así nadie puede averiguar el estado de una reserva ajena.
- **`disponibles` no pasa por la cadena:** no hay un espacio concreto. Reusa RN1 y RN2 (`ReglasFranja`), le pide a ms-espacios los compatibles y descarta los ocupados con una sola consulta (`espaciosOcupados`), sin una consulta por espacio.
- **Por qué la búsqueda necesita a los dos servicios:** ms-espacios sabe qué espacios sirven, pero no cuáles están ocupados; ms-reservas sabe la ocupación, pero no conoce el catálogo ni el clima.

**`ReservaController`**

> "Expone la API de reservas bajo `/api/reservas`: `POST` para crear, `GET /mias` para las propias, `GET` (con `?estado=` opcional) para que bedelía vea todas, `PATCH /{id}/aprobar`, `/{id}/rechazar` y `/{id}/cancelar` para los cambios de estado, y `GET /disponibilidad` para buscar espacios libres. El usuario llega en el header `X-Usuario`, que pone el gateway a partir del token; el controller lo lee con `@RequestHeader` y se lo pasa al service. Como en ms-espacios, el controller no tiene lógica ni `try/catch`: los errores los traducen el `GlobalExceptionHandler` del JAR común y el `ConcurrenciaExceptionHandler`."

Ideas clave por si repreguntan:

- **`@RequestHeader("X-Usuario")`:** lee un header HTTP del pedido. Es obligatorio: sin él, 400 "Falta el header obligatorio 'X-Usuario'".
- **¿Y si el cliente manda un `X-Usuario` falso?** Desde afuera no puede: el gateway borra cualquier `X-Usuario` que venga del cliente y pone el del token. Y los microservicios no publican puertos, así que solo se llega a ellos a través del gateway (trusted zone, D6 y D8). Hasta la fase 3 lo completamos a mano en Swagger.
- **`PATCH` para los cambios de estado:** modifica **una parte** del recurso (su estado), no lo reemplaza entero como `PUT`. La acción va en la ruta (`/aprobar`, `/cancelar`) porque cada una tiene reglas distintas.
- **Quién puede llamar a cada endpoint no se controla acá:** lo decide el gateway por rol (fase 3). El controller solo recibe pedidos ya autorizados.
- **`/mias` y `/disponibilidad` son rutas literales:** Spring las prefiere antes que `/{id}`, igual que en ms-espacios.
- **`?estado=PENDIENTE` es opcional (`required = false`):** sin él, todas las reservas.

**`ConcurrenciaExceptionHandler`: el 409 de RN15**

> "Cuando dos pedidos chocan, la excepción no la lanza nuestro código, sino JPA, y recién al hacer commit, cuando el método del service ya terminó: por eso no se puede atrapar con un `try/catch` dentro del service. La atrapa un `@RestControllerAdvice` propio de ms-reservas, que traduce `OptimisticLockingFailureException` (la versión de la agenda o de la reserva cambió) y `DataIntegrityViolationException` (dos pedidos crearon a la vez la fila de agenda de un espacio) a 409 con un mensaje que invita a reintentar."

Ideas clave por si repreguntan:

- **Por qué no está en el JAR común:** solo ms-reservas tiene control de concurrencia. ms-espacios no lo necesita.
- **`@Order(Ordered.HIGHEST_PRECEDENCE)`:** si en algún momento otro handler atrapara una excepción más general, este se evalúa primero.
- **`OptimisticLockingFailureException`:** es la excepción de Spring que envuelve la de JPA. La que realmente aparece es su subclase `ObjectOptimisticLockingFailureException`, y el handler la atrapa por herencia.
- **El mensaje dice "Intentá de nuevo":** RN15 dice que el que pierde "recibe 409 y puede reintentar". Si reintenta, ahora RN6 ve la reserva del ganador y responde 409 por superposición, o se guarda si era otra franja.
- **Lo probamos con dos `POST` idénticos en paralelo:** uno 201 y el otro 409.

**¿De dónde salen esas dos excepciones?**

> "No las lanza nuestro código: nacen en la base o en Hibernate, y Spring las traduce a su propia jerarquía de excepciones de acceso a datos (`DataAccessException`). `OptimisticLockingFailureException` sale al hacer commit: Hibernate ejecuta `UPDATE agenda_espacio SET version = 6 WHERE espacio_id = 1 AND version = 5`, la base responde que modificó 0 filas porque otro ya cambió la versión, Hibernate lanza `StaleObjectStateException` y el administrador de transacciones de Spring la traduce. `DataIntegrityViolationException` sale cuando dos pedidos insertan a la vez la fila de agenda de un espacio nuevo: el segundo `INSERT` viola la clave primaria, el driver de H2 lanza una `SQLException`, Hibernate la envuelve en su `ConstraintViolationException` y el repositorio de Spring Data la traduce. Nosotros atrapamos las excepciones de Spring, no las de Hibernate ni las de H2, así que el handler funcionaría igual con otra base o con otro proveedor de JPA."

El recorrido de cada una:

| | `OptimisticLockingFailureException` | `DataIntegrityViolationException` |
|---|---|---|
| **Cuándo** | Al hacer **commit** (ya terminó el método del service) | En `saveAndFlush` (todavía dentro del service) |
| **SQL que falla** | `UPDATE ... WHERE version = ?` → **0 filas** | `INSERT` con una clave primaria repetida |
| **Quién detecta el problema** | **Hibernate**: la base no da error, solo informa 0 filas | **La base (H2)**: rechaza el `INSERT` |
| **Excepción original** | `org.hibernate.StaleObjectStateException` | `JdbcSQLIntegrityConstraintViolationException` (de H2) → `org.hibernate.exception.ConstraintViolationException` |
| **Quién la traduce** | `JpaTransactionManager` (el que hace el commit de `@Transactional`) | El *proxy* del repositorio de Spring Data |
| **La que nos llega** | `ObjectOptimisticLockingFailureException` | `DataIntegrityViolationException` |

Ideas clave por si repreguntan:

- **Jerarquía de Spring:** `RuntimeException` → `DataAccessException` → (`ConcurrencyFailureException` → `OptimisticLockingFailureException` → `ObjectOptimisticLockingFailureException`) y (`NonTransientDataAccessException` → `DataIntegrityViolationException`).
- **Por qué Spring las traduce:** cada base y cada proveedor de JPA tiene sus propias excepciones y códigos de error. Spring las convierte a una jerarquía común, así el código no depende de H2 ni de Hibernate.
- **Optimista no espera, la clave primaria sí:** con la versión, nadie se bloquea; el choque se detecta al final. Con el `INSERT` repetido, H2 hace esperar al segundo hasta que el primero confirma, y recién ahí lo rechaza.
- **Las dos son `RuntimeException`:** por eso hacen rollback de la transacción, y la reserva del perdedor no queda guardada.
- **"Transitoria" vs. "no transitoria":** Spring clasifica el choque de versiones como transitorio (si reintentás, puede funcionar) y la violación de integridad como no transitoria (el mismo `INSERT` va a fallar siempre). En nuestro caso las dos se resuelven reintentando, porque el reintento ya no es el mismo pedido: la fila de agenda ya existe.

---

## Decisiones que tomamos

Cada decisión dice qué elegimos, por qué y qué descartamos. Complementan las D1 a D25 del plan de desarrollo, y algunas se apoyan en ellas.

### Proyecto y herramientas

**T1. Versiones exactas y actuales: Spring Boot 4.1.2, springdoc 3.1.1 y Keycloak 26.8.1**
- **Decisión:** usamos la última versión estable de cada línea que pide el plan, y la fijamos con el número exacto.
- **Por qué:** el plan pide Boot 4.1.x, springdoc 3.x y Keycloak 26.x. Dentro de esa línea, la última versión trae correcciones de errores sin cambiar la forma de usarla. Con la versión exacta, todo el grupo compila y levanta lo mismo, y nada cambia solo de un día para el otro.
- **Descartamos:**
  - Las versiones puntuales del plan (Boot 4.1.1, Keycloak 26.4): son de la misma línea, pero más viejas.
  - `latest` o rangos de versiones: cada uno podría terminar con una versión distinta según el día en que descargó.
  - Versiones *RC* o *SNAPSHOT* (por ejemplo, Boot 4.2.0-RC1): todavía no son estables.

**T2. Los tres proyectos con la misma versión de Spring**
- **Decisión:** `campus-commons` importa la BOM de Boot 4.1.2, y los microservicios heredan del parent 4.1.2.
- **Por qué:** el JAR común se compila contra Spring y después corre dentro de los microservicios. Si las versiones no coinciden, puede compilar pero fallar al ejecutar porque falta un método.
- **Descartamos:** escribir a mano la versión de `spring-web` en `campus-commons`, porque es fácil que se desincronice.

**T3. La raíz del repo se queda como `TPI_BDA_UTNFRC` y la carpeta de documentos como `Docs`**
- **Decisión:** no renombramos a `tpi-campus-reservas/` ni a `docs/`, como dice el plan.
- **Por qué:** el repo ya existía con ese nombre, y renombrarlo no cambia nada del TP. Windows no distingue mayúsculas, así que `Docs` y `docs` son la misma carpeta.

**T4. Los microservicios se generan con Spring Initializr; `campus-commons` se arma a mano**
- **Decisión:** `ms-espacios`, `ms-reservas` y `api-gateway` salen de start.spring.io. `campus-commons` lo escribimos nosotros.
- **Por qué:** Initializr arma bien una **aplicación** Boot (parent, plugin, Maven Wrapper, clase principal) con los nombres de dependencias correctos de Boot 4. `campus-commons` es una **librería**: no tiene que tener nada de eso.
- **Descartamos:** generar `campus-commons` con Initializr y después borrarle cosas, porque es más fácil olvidarse algo (como el `spring-boot-maven-plugin`) que no agregarlo.

**T5. El paquete de cada servicio va bajo `ar.edu.utn.frc.bda.campus`**
- **Decisión:** `...campus.espacios`, `...campus.reservas` y `...campus.commons`, cambiando a mano el *Package name* que propone Initializr (`ar.edu.utn.frc.bda.ms_espacios`).
- **Por qué:** así un solo `scanBasePackages = "ar.edu.utn.frc.bda.campus"` cubre el servicio y el JAR común.
- **Descartamos:** dejar el paquete de Initializr y listar los dos paquetes en `scanBasePackages`. Funciona, pero es más fácil equivocarse y queda menos ordenado.

### campus-commons

**T6. BOM importada en vez de parent en `campus-commons`**
- **Decisión:** `campus-commons` importa `spring-boot-dependencies` con `scope import`, y no hereda de `spring-boot-starter-parent`.
- **Por qué:** una librería solo necesita las **versiones**. El parent además trae configuración de plugins pensada para aplicaciones.
- **Descartamos:** heredar del parent, porque sumaría configuración de app a algo que no es una app.

**T7. `spring-web` y `spring-context` con `scope provided`**
- **Decisión:** el JAR común declara esas dependencias como `provided`.
- **Por qué:** las necesita para compilar, pero los microservicios ya las tienen por sus starters. Así no viajan dos copias de Spring ni se arriesgan choques de versiones. `spring-context` hace falta porque `MethodArgumentNotValidException` hereda de `BindException`, que está en ese módulo; sin ella, no compila.
- **Descartamos:** `scope compile` (el default), que haría que el JAR arrastre su propio Spring a cada servicio.

**T8. Compilador y codificación fijados en `campus-commons`**
- **Decisión:** fijamos la versión del `maven-compiler-plugin` (3.14.1) y `project.build.sourceEncoding` en UTF-8.
- **Por qué:** sin parent, la versión del compilador depende de la versión de Maven que tenga cada uno. Y con UTF-8, los acentos de los mensajes de error compilan igual en todas las máquinas del grupo. Los microservicios no lo necesitan: el parent ya lo hace.
- **Descartamos:** confiar en los valores por defecto de cada instalación.

**T9. `HttpStatus.UNPROCESSABLE_CONTENT` en vez de `UNPROCESSABLE_ENTITY`**
- **Decisión:** para el 422 usamos la constante nueva.
- **Por qué:** en Spring 7, `UNPROCESSABLE_ENTITY` está *deprecated* y fue reemplazada por `UNPROCESSABLE_CONTENT`, el nombre que usa la especificación HTTP actual (RFC 9110). Es el mismo código 422.
- **Descartamos:** la constante del plan, que compila pero con un warning y desaparecerá en una versión futura.

**T10. Sin handler genérico para errores no previstos**
- **Decisión:** el `GlobalExceptionHandler` no atrapa `Exception`. Un error inesperado sale como 500.
- **Por qué:** un 500 significa "hay un bug en nuestro código". Queremos que se note para corregirlo, no disfrazarlo de error de negocio.
- **Descartamos:** un `@ExceptionHandler(Exception.class)` que devuelva un 400 o un 422 genérico: escondería errores reales.

**T11. El código HTTP no va dentro de `ApiResponse`**
- **Decisión:** el código viaja solo en la línea de estado HTTP.
- **Por qué:** repetirlo en el body serían dos fuentes de verdad que podrían contradecirse. Además, `RestClient`, el gateway y Postman deciden por la línea de estado, y hay respuestas (el 401 y el 403 del gateway) que ni siquiera tienen nuestro body. Respetamos el formato del ejemplo de la cátedra.
- **Descartamos:** un campo `status` en el JSON, como hace Problem Details (RFC 9457).

### Keycloak

**T12. Keycloak publicado en el puerto 8180**
- **Decisión:** `ports: "8180:8080"`.
- **Por qué:** Keycloak escucha en el 8080 dentro de su contenedor, pero el 8080 de la máquina es del gateway, la única puerta de entrada al backend.

**T13. Mantenemos la opción *Full scope allowed* del cliente `campus-api`**
- **Decisión:** dejamos la configuración por defecto, aunque Keycloak 26 muestra un warning de que está deprecada.
- **Por qué:** es la opción que hace que los roles del realm viajen en el token (`realm_access.roles`), que es lo que lee el gateway para autorizar.
- **Descartamos:** apagarla, porque obligaría a configurar a mano qué roles puede recibir el cliente, sin ganar nada en el TP.

### ms-espacios: configuración

**T14. `open-in-view: false`**
- **Decisión:** apagamos *Open Session in View*.
- **Por qué:** por defecto, Spring deja abierta la conexión a la base hasta que termina de escribir la respuesta HTTP, y eso permite que se hagan consultas fuera del service sin darse cuenta. Apagándolo, todo el acceso a datos queda en el service. Además, saca un warning al arrancar.
- **Descartamos:** dejar el valor por defecto.

**T15. Propiedades propias con valor por defecto y variable de entorno**
- **Decisión:** `campus.clima.url: ${OPEN_METEO_URL:https://api.open-meteo.com}` y `campus.clima.umbral-lluvia: ${CLIMA_UMBRAL:60}`.
- **Por qué:** el mismo JAR corre desde el IDE y en Docker sin tocar código, y permite probar casos difíciles: una URL rota para la falla de Open-Meteo (503) o un umbral de 0 para forzar la lluvia (RN4).
- **Descartamos:** escribir la URL y el umbral en el código, porque habría que recompilar para cada prueba.

### ms-espacios: modelo

**T16. Latitud y longitud en todos los espacios, sin herencia de entidades**
- **Decisión:** `Espacio` es una sola entidad con `latitud` y `longitud`, aunque solo las canchas las usan (se mandan a Open-Meteo).
- **Por qué:** lo que cambia según el tipo de espacio son las **reglas**, no los **datos**. Las reglas por tipo viven en las estrategias de validación (Strategy, D16), no en la entidad. Una sola tabla es simple de consultar: "todos los espacios con capacidad ≥ 30" es un `SELECT` común.
- **Descartamos:** herencia de entidades JPA (`Cancha extends Espacio`), que el plan descarta en D16 porque mezclaría persistencia con reglas de negocio y obligaría a mapear una jerarquía (tablas por subtipo o columna discriminadora) sin ganar nada.

**T17. Enums guardados como texto (`EnumType.STRING`)**
- **Decisión:** la base guarda `"AULA"`, no un número.
- **Por qué:** la base se lee fácil, y los datos no dependen del orden de los valores en el enum.
- **Descartamos:** `EnumType.ORDINAL` (el default de JPA). Guarda la posición (0, 1, 2…), y si alguien agrega o reordena un valor, los datos viejos pasan a significar otra cosa.

**T18. Columnas de enum como `VARCHAR` (`@JdbcTypeCode(SqlTypes.VARCHAR)`)**
- **Decisión:** forzamos que las columnas de enums sean de texto común.
- **Por qué:** con H2, Hibernate 7 crea por defecto columnas del tipo `ENUM('AULA', 'CANCHA', ...)`, con la lista fija. `ddl-auto: update` no modifica columnas existentes, así que agregar un valor al enum de Java (por ejemplo, un auditorio) fallaría al insertar. Eso contradice el argumento del patrón Strategy: "para sumar un tipo alcanza con agregar el valor al enum y una clase". Lo detectamos al revisar las tablas que creó Hibernate.
- **Descartamos:**
  - Las columnas `ENUM` nativas: rompen la extensibilidad.
  - La propiedad `hibernate.type.prefer_native_enum_types=false`: la probamos y, con H2, no cambió el tipo de columna.

**T19. `@ElementCollection` para equipamiento y actividades**
- **Decisión:** los conjuntos de enums se guardan en tablas aparte (`espacio_equipamiento` y `espacio_actividades`) que maneja Hibernate.
- **Por qué:** son valores simples, sin identidad ni ciclo de vida propio: un "PROYECTOR" solo existe como parte de un espacio.
- **Descartamos:**
  - Entidades `Equipamiento` y `Actividad` con `@ManyToMany`: más clases, más tablas y un ABM que el TP no necesita.
  - Guardar la lista en una sola columna de texto (`"PROYECTOR,PIZARRA"`): no se puede consultar bien y rompe la primera forma normal.

**T20. Conjuntos con `FetchType.EAGER`**
- **Decisión:** al leer un espacio se traen sus conjuntos en el mismo momento.
- **Por qué:** con `open-in-view: false`, cuando el controller arma el DTO la conexión ya está cerrada, y una colección `LAZY` daría `LazyInitializationException`. Son conjuntos de 2 o 3 elementos, así que el costo es mínimo.
- **Descartamos:** `LAZY` con `@Transactional` en cada lectura, o `JOIN FETCH` en cada consulta: más código para un beneficio que no se nota con estos tamaños.

**T21. `Set` en vez de `List` para los conjuntos**
- **Decisión:** `Set<Equipamiento>` y `Set<TipoActividad>`.
- **Por qué:** un espacio no puede tener dos veces el mismo equipamiento ni la misma actividad. `Set` lo garantiza solo, y para RN8 se usa `removeAll` (lo que falta = lo pedido menos lo que hay), que es una operación de conjuntos.
- **Descartamos:** `List`, que admite duplicados.

### ms-espacios: API

**T22. `EspacioDto` incluye el horario y la ubicación**
- **Decisión:** además de lo que propone el plan (id, nombre, tipo, capacidad, actividades y equipamiento), `EspacioDto` devuelve `horaApertura`, `horaCierre`, `latitud` y `longitud`.
- **Por qué:** sin el horario, un usuario no sabe en qué franjas puede reservar (RN3) y tiene que adivinar. Y el ADMIN, para modificar un espacio con `PUT`, necesita ver todos sus datos actuales.
- **Descartamos:** el DTO del plan, que oculta el horario aunque sea un dato que decide si la reserva es válida.

**T23. Validaciones extra en `EspacioRequest`: rangos de coordenadas y al menos una actividad**
- **Decisión:** `latitud` entre -90 y 90, `longitud` entre -180 y 180, y `actividadesPermitidas` con `@NotEmpty`.
- **Por qué:** son coordenadas imposibles que Open-Meteo rechazaría (y daría 503 en vez del 400 que corresponde). Y un espacio sin ninguna actividad permitida no se podría reservar nunca (RN9).
- **Descartamos:** dejar estos campos sin validar, como en el plan: el error aparecería más tarde y con un código engañoso.

**T24. La regla "apertura antes que cierre" va en el service y no en el DTO**
- **Decisión:** el service lanza `ReglaNegocioException` (422) si `horaApertura` no es anterior a `horaCierre`.
- **Por qué:** compara dos campos entre sí, así que es una regla de negocio y no de formato. Las anotaciones de Bean Validation validan cada campo por separado.
- **Descartamos:** una anotación de validación propia a nivel de clase: más código para una sola regla.

### ms-espacios: Open-Meteo

**T25. `OpenMeteoResponse` va en el paquete `clients`, junto a su cliente**
- **Decisión:** el record de la respuesta de Open-Meteo vive en `clients` y no en `entities.models`.
- **Por qué:** describe el formato de una API **ajena**, no de la nuestra. Lo usa solo `OpenMeteoClient`, y nunca sale del servicio. Tenerlos juntos deja claro que, si Open-Meteo cambia su formato, se toca un solo paquete.
- **Descartamos:** mezclarlo con nuestros DTOs, que son el contrato de **nuestra** API.

**T26. Probamos el cliente contra la API real antes de usarlo**
- **Decisión:** antes de escribir las estrategias, verificamos el cliente con un pedido real (devolvió 73 % de lluvia para el día siguiente de 10:30 a 13), con una fecha fuera de rango (Open-Meteo responde 400 → nosotros 503) y con una URL rota (no conecta → 503).
- **Por qué:** la API externa es lo que más puede fallar, y en la defensa piden mostrar qué pasa cuando falla.

### ms-espacios: estrategias

**T27. Las estrategias van en el subpaquete `services.validadores`**
- **Decisión:** la clase base y las cuatro estrategias viven juntas en `services/validadores/`.
- **Por qué:** son cinco clases de una misma pieza (el patrón Strategy). Juntas se encuentran fácil, y no se mezclan con los services.
- **Descartamos:** ponerlas sueltas en `services`, donde se perdería que forman un grupo.

**T28. `ValidadorCancha` con constructor explícito y el umbral como campo `final`**
- **Decisión:** el constructor recibe el `OpenMeteoClient` y el umbral (`@Value("${campus.clima.umbral-lluvia}")` en el parámetro), y los dos campos son `final`.
- **Por qué:** toda la configuración llega por constructor, igual que las demás dependencias, y el umbral no puede cambiar después de creado el bean.
- **Descartamos:** lo del plan, `@RequiredArgsConstructor` más un campo con `@Value` que Spring completa después de construir el objeto. Funciona, pero mezcla dos formas de inyección y el campo no puede ser `final`.

**T29. Probamos cada regla de las estrategias antes de seguir**
- **Decisión:** antes de escribir el service, probamos las cuatro estrategias con 11 casos, uno por regla y algunos válidos: aula fuera de horario (RN3), deporte en un aula (RN9), computadoras en un aula (RN8), laboratorio un sábado y con menos de 24 h (RN10), sala por 3 horas (RN11), cancha por 90 minutos (RN12) y una cancha válida con el pronóstico real (RN4). Todos dieron el resultado esperado.
- **Por qué:** cada estrategia se puede probar sola, que es una de las ventajas del patrón.

**T30. Strategy con una clase abstracta, sin interfaz aparte**
- **Decisión:** eliminamos la interfaz `ValidadorEspacio` que propone el plan. El tipo común de las estrategias es la clase abstracta `ValidadorEspacioBase`, con `tipo()` abstracto, y el service depende de ella (`List<ValidadorEspacioBase>`).
- **Por qué:**
  - **Reutilización de código:** todas las estrategias comparten RN3, RN8 y RN9. La clase abstracta es a la vez el contrato y el lugar donde viven esas reglas, así que una interfaz encima no agregaba nada: las cuatro estrategias la implementaban a través de la base.
  - **Garantiza las reglas comunes:** como la única forma de ser una estrategia es extender la base, y `motivoRechazo` es `final`, **ninguna estrategia nueva puede saltearse RN3, RN8 y RN9**. Con la interfaz, alguien podría implementarla directamente y saltearlas.
  - **Menos piezas:** una clase menos que mantener y explicar, sin perder el patrón. Strategy pide un tipo común para las estrategias, y una clase abstracta lo es.
- **El costo, si preguntan:**
  - El service queda atado a una jerarquía de herencia, y no a una abstracción "pura".
  - Java tiene herencia simple: una estrategia no podría extender otra clase. Hoy no hace falta.
  - Para probar el service con una estrategia falsa hay que extender la base (y heredar las reglas comunes), en vez de implementar una interfaz chica.
- **Descartamos:** la interfaz del plan más la clase abstracta. Separa mejor el *qué* (contrato) del *cómo* (implementación), pero hoy era una capa sin uso y además permitía estrategias que se salteen las reglas comunes.

### ms-espacios: service

**T31. ms-espacios controla que la franja esté bien armada**
- **Decisión:** `validar` y `compatibles` rechazan con 422 una franja que termina antes de empezar, o que no empieza y termina el mismo día.
- **Por qué:** ms-reservas ya lo controla (RN2), pero estos endpoints se pueden llamar directamente, por ejemplo desde Swagger. Con una franja invertida, las estrategias darían respuestas sin sentido: una sala "de 12 a 10" tendría una duración negativa y pasaría RN11.
- **Descartamos:** confiar en que solo ms-reservas los llama. Cada servicio valida lo que recibe.

**T32. `@Transactional` en crear, modificar y borrar**
- **Decisión:** las tres escrituras corren en una transacción.
- **Por qué:** un espacio se guarda en tres tablas (`espacios` y sus dos conjuntos). Si algo falla a la mitad, se deshace todo. En `modificar`, además, la lectura y la escritura quedan en la misma transacción.
- **Descartamos:** depender solo de la transacción que abre cada método del repositorio, que en `modificar` serían dos transacciones separadas.

**T33. Los conjuntos que pueden venir vacíos se normalizan**
- **Decisión:** si el request no trae `equipamiento`, se guarda un conjunto vacío; si una validación no trae equipamiento pedido, se toma como `Set.of()`.
- **Por qué:** "no pido nada" y "pido un conjunto vacío" significan lo mismo, y así las estrategias nunca reciben `null` (RN8 haría `new HashSet<>(null)` y daría 500).
- **Descartamos:** hacer obligatorio el equipamiento: una cancha no tiene ninguno.

**T34. `aDto` copia los conjuntos con `Set.copyOf`**
- **Decisión:** el DTO recibe copias inmutables de los conjuntos, no los de la entidad.
- **Por qué:** los conjuntos de una entidad son colecciones especiales de Hibernate, atadas a la entidad. El DTO es un record inmutable, así que sus conjuntos también deberían serlo, y nadie que lo reciba puede modificar la entidad por accidente.

**T35. Borrar un espacio que no existe da 404**
- **Decisión:** `borrar` busca primero el espacio con `obtener(id)` y después lo borra.
- **Por qué:** con `deleteById` directo, borrar el espacio 999 no daría ningún error y el cliente creería que borró algo.
- **Qué pasa con las reservas de un espacio borrado:** siguen existiendo en ms-reservas con el nombre copiado (D11), así que se pueden listar y cancelar. Lo que ya no se puede es crear reservas nuevas en ese espacio (404).

**T36. Probamos el service antes de exponerlo**
- **Decisión:** antes del controller probamos el ABM, `validar` y `compatibles`, también sin pronóstico. Con el pronóstico real, la cancha no apareció (había lluvia); con un umbral de 100 % apareció; y con Open-Meteo caído se omitió, mientras el aula se siguió devolviendo.

### ms-espacios: controller y errores

**T37. Todos los 400 salen con `ApiResponse` (tres handlers más en el JAR común)**
- **Decisión:** el `GlobalExceptionHandler` maneja también `MissingServletRequestParameterException` (falta un parámetro obligatorio), `MethodArgumentTypeMismatchException` (un parámetro que no se puede convertir: `actividad=NADAR`, `id=abc`, una fecha mal escrita) y `HttpMessageNotReadableException` (JSON roto, o un enum inválido en el body como `"tipo": "PILETA"`). Para compilar, `campus-commons` suma `jakarta.servlet-api` como `provided`.
- **Por qué:** al probar el controller por HTTP vimos que esos casos daban 400, pero con el formato por defecto de Spring (`timestamp`, `status`, `error`, `path`) y **sin decir qué estaba mal**. Eso rompía D25 ("todas las respuestas usan `ApiResponse`"). Ahora responden, por ejemplo, `"Falta el parámetro obligatorio 'actividad'"` o `"Valor inválido para 'actividad': NADAR"`.
- **Por qué en el JAR común:** los dos microservicios tienen el mismo problema; es transversal (D13).
- **Por qué `jakarta.servlet-api`:** `MissingServletRequestParameterException` hereda de `ServletException`, que está en la API de Servlets. Es el mismo caso que `spring-context` (T7): hace falta para compilar, y los microservicios ya la tienen por Tomcat.
- **Descartamos:** dejar los 400 con el formato por defecto. Funcionan, pero el cliente recibe dos formatos de error distintos y no sabe qué corregir.

**T38. Borrar responde 200 con `ApiResponse`, no 204**
- **Decisión:** `DELETE /api/espacios/{id}` responde 200 con `"Espacio eliminado"`.
- **Por qué:** todas las respuestas usan `ApiResponse` (D25). Un 204 No Content no puede tener cuerpo.
- **Descartamos:** 204, que también es correcto en REST, pero rompe el formato único.

**T39. Crear responde 201 sin header `Location`**
- **Decisión:** `POST` responde 201 Created con el espacio creado (incluido su `id`) en el cuerpo.
- **Por qué:** el cliente ya recibe el `id` en la respuesta, que es lo que necesita.
- **Descartamos:** agregar el header `Location: /api/espacios/{id}`, que es lo más completo en REST, pero suma código sin que nadie lo use en el TP.

**T40. Probamos el controller por HTTP**
- **Decisión:** antes de seguir, probamos cada endpoint con pedidos reales: alta (201), alta inválida (400 con el detalle de cada campo), espacio 999 (404 en GET y DELETE), validación disponible y no disponible (200), compatibles (200) y los 400 de formato. Swagger respondió en `/swagger-ui.html`.

### ms-espacios: datos de prueba y cierre de la Fase 1

**T41. Las coordenadas de la UTN como constantes en `DatosIniciales`**
- **Decisión:** `LATITUD_UTN` y `LONGITUD_UTN` (`-31.44` y `-64.19`) se declaran una vez y se usan en los cinco espacios.
- **Por qué:** el plan repite los dos números cinco veces. Con constantes, el nombre explica qué son y se cambian en un solo lugar.

**T42. Verificamos el criterio de terminado de la Fase 1**
- **Decisión:** con los datos iniciales, probamos por HTTP cada punto del "Terminado cuando" del plan:
  - Aula 101 de 22 a 23 → `disponible: false`, "Fuera del horario de apertura" (RN3).
  - Aula 101 pidiendo computadoras → `disponible: false`, "Al espacio le falta: [COMPUTADORAS]" (RN8).
  - Compatibles para 30 personas con proyector → solo el Aula 101.
  - Laboratorio 3 un sábado → `disponible: false` (RN10).
  - Sala por 3 horas → `disponible: false` (RN11).
  - Con Open-Meteo caído (`OPEN_METEO_URL=http://localhost:9`): la validación de la cancha da 503 y compatibles la omite, devolviendo igual las aulas.
  - Reiniciar el servicio no duplica los espacios.
- **Por qué:** es lo que el plan pide para dar por cerrada la fase, y lo que se muestra en la demo.

### ms-reservas: modelo

**T43. Enums de `Reserva` como `VARCHAR`, igual que en ms-espacios**
- **Decisión:** `estado` y `actividad` llevan `@JdbcTypeCode(SqlTypes.VARCHAR)`.
- **Por qué:** mismo problema que T18: H2 crearía columnas `ENUM(...)` con la lista fija, y `ddl-auto: update` no las amplía. Si se agrega un estado (el plan menciona FINALIZADA como posible mejora) o una actividad, la base lo rechazaría. Lo verificamos: las dos columnas quedaron `CHARACTER VARYING`.
- **Descartamos:** dejar el comportamiento por defecto, que obligaría a borrar la base para agregar un valor.

**T44. `AgendaEspacio` solo con `@Getter`, sin setters**
- **Decisión:** a diferencia de `Reserva` (`@Data`), la agenda no tiene setters.
- **Por qué:** sus dos campos no se modifican nunca a mano: el id se fija al crearla y la versión la incrementa JPA. Sin setters, nadie puede romper el control de concurrencia por error. Es lo que propone el plan.

**T45. Los listados de reservas salen ordenados por inicio**
- **Decisión:** `findByUsuarioOrderByInicioAsc`, `findByEstadoOrderByInicioAsc` y `findAllByOrderByInicioAsc`.
- **Por qué:** sin `ORDER BY`, la base devuelve las filas en cualquier orden. Un usuario que mira sus reservas, o bedelía que mira los eventos pendientes, espera verlas por fecha.
- **Descartamos:** los `findByEstado` y `findAll` del plan sin orden, y ordenar en Java después de traerlas.

**T46. Probamos las consultas de los repositorios**
- **Decisión:** antes de usarlas, probamos `existeSuperposicion` con franjas que se pisan, que se tocan en el borde, que contienen a otra y con una reserva cancelada; `espaciosOcupados`; los tres listados ordenados; y que `OPTIMISTIC_FORCE_INCREMENT` sube la versión de la agenda en cada transacción. Todo dio lo esperado.
- **Por qué:** RN6 y RN15 dependen de estas consultas. Un `<` cambiado por `<=` haría que dos reservas contiguas (10 a 12 y 12 a 13) se rechacen.

### ms-reservas: cliente de ms-espacios

**T47. Un 4xx de ms-espacios (que no sea 404) se traduce a 422, no a 503**
- **Decisión:** `EspaciosClient` atrapa `HttpClientErrorException` (después de `NotFound`) y lanza `ReglaNegocioException`.
- **Por qué:** en el código del plan, cualquier error que no fuera 404 o 503 caía en el `catch (RestClientException)` y daba **503 "El servicio de espacios no está disponible"**. Pero si el usuario pide `"equipamiento": ["PILETA"]`, ms-espacios responde 400, y decir que el servicio está caído sería mentir: el servicio anda, el pedido no sirve. Lo probamos: con la versión del plan sería 503; con la nuestra, 422 "Valor inválido para 'equipamiento': PILETA".
- **Descartamos:** devolver 400, que exigiría otra excepción en el JAR común; y validar el equipamiento en ms-reservas, que lo obligaría a conocer el catálogo de otro servicio.

**T48. Se reusa el mensaje de error de ms-espacios**
- **Decisión:** `mensajeRemoto` lee el `message` del `ApiResponse` de error que respondió ms-espacios y lo usa en nuestra excepción. Si no puede leerlo, usa un mensaje por defecto.
- **Por qué:** ms-espacios ya explica qué pasó ("No se pudo consultar el pronóstico del clima", "Valor inválido para 'equipamiento': PILETA"). Reescribirlo perdería información; es posible porque los dos servicios usan el mismo `ApiResponse` del JAR común.
- **Descartamos:** los mensajes fijos del plan, que en el caso del clima son parecidos, pero en el caso de un 400 no dirían qué valor estaba mal.

**T49. Una respuesta 200 sin datos también es 503**
- **Decisión:** si ms-espacios responde sin cuerpo o sin `responseValue`, lanzamos `ServicioNoDisponibleException("Respuesta inesperada del servicio de espacios")`.
- **Por qué:** sin este control, el plan haría `.responseValue()` sobre `null` → `NullPointerException` → 500. Mismo criterio que validar la respuesta de Open-Meteo.

**T50. `ValidacionEspacio` y `EspacioDisponible` van en el paquete `clients`**
- **Decisión:** igual que T25: los records que describen la respuesta de otro servicio viven junto a su cliente, no con nuestros DTOs.

**T51. Probamos el cliente contra ms-espacios de verdad**
- **Decisión:** levantamos ms-espacios en tres situaciones y probamos `validar` y `compatibles` en cada una:
  - **Normal:** espacio que sirve (`disponible: true`), que no sirve (`false` con el motivo), espacio 999 (404) y equipamiento PILETA (422).
  - **Con Open-Meteo caído:** la cancha da 503 "No se pudo consultar el pronóstico del clima", y las aulas siguen funcionando.
  - **Con ms-espacios apagado:** todo da 503 "El servicio de espacios no está disponible".

### ms-reservas: cadena de validaciones

**T52. La cadena vive en el subpaquete `services.validadores`**
- **Decisión:** `ValidadorReserva`, los cinco eslabones, `ContextoReserva`, `CadenaValidacionReserva` y `ReglasFranja` van juntos en `services/validadores/`, igual que las estrategias de ms-espacios (T27).
- **Por qué:** son las piezas de un mismo patrón. Además, `enlazar` es de paquete: solo puede llamarlo una clase del mismo paquete, y así únicamente `CadenaValidacionReserva` puede armar la cadena.

**T53. Probamos la cadena con un caso por regla**
- **Decisión:** antes del service probamos once casos: uno válido y uno por cada regla (RN1 con fecha pasada y a 20 días, RN2 con 15 minutos, 5 horas y fin antes del inicio, RN3, RN9, RN5, RN6 y espacio 999). Todos dieron la excepción esperada. Además confirmamos que Spring arma la cadena en el orden de `@Order`.
- **El orden se nota:** con ms-espacios apagado, los casos de RN1 y RN2 siguieron dando 422 en 0 ms, porque se cortan antes de llegar al eslabón remoto. Los demás dieron 503.

### ms-reservas: service

**T54. La búsqueda de disponibles controla que los asistentes sean más de 0**
- **Decisión:** `disponibles` lanza 422 si `asistentes <= 0`.
- **Por qué:** con 0 o un número negativo, ms-espacios devolvería todos los espacios (capacidad ≥ 0), y la búsqueda no tendría sentido. En `crear` ya lo controla `@Positive` del request; la búsqueda recibe parámetros sueltos y no pasa por esa validación.

**T55. "La reserva ya empezó" es 422, no 409**
- **Decisión:** aprobar o cancelar una reserva que ya empezó da 422, como en el plan.
- **Por qué:** no es una transición que el diagrama prohíba (PENDIENTE → CONFIRMADA está permitida); es una regla de tiempo (RN7 y "aprobar antes del inicio" de RN14). El 409 queda para las transiciones que el diagrama no permite.

**T56. Probamos el service, incluida la concurrencia con hilos**
- **Decisión:** probamos el ciclo completo (clase → CONFIRMADA, evento → PENDIENTE, aprobar, aprobar otra vez → 409, rechazar una confirmada → 409, cancelar ajena → 403, cancelar → CANCELADA, cancelar dos veces → 409, reserva 999 → 404), la búsqueda (devuelve el Aula 101 y, después de reservarla, nada) y la concurrencia con 2 y 5 hilos simultáneos.
- **Resultado de la concurrencia:** con la agenda, siempre se guardó **una sola** reserva por franja; los demás pedidos recibieron `ObjectOptimisticLockingFailureException` o, en la primera reserva de un espacio, `DataIntegrityViolationException`. **Sin la agenda**, 5 pedidos simultáneos guardaron **5 reservas** en la misma franja. Es la demostración de por qué existe `AgendaEspacio`.
- **Pendiente:** esas dos excepciones todavía salen como 500. Las traduce a 409 el `ConcurrenciaExceptionHandler`, en el paso del controller.

### ms-reservas: controller y cierre de la Fase 2

**T57. Un header obligatorio que falta también sale con `ApiResponse`**
- **Decisión:** el `GlobalExceptionHandler` del JAR común maneja `MissingRequestHeaderException` y responde 400 "Falta el header obligatorio 'X-Usuario'".
- **Por qué:** mismo motivo que T37. Sin este handler, un pedido sin `X-Usuario` daba 400 con el formato por defecto de Spring, sin decir qué faltaba. Va en el JAR común porque es transversal: cualquier servicio que use un header obligatorio lo necesita.
- **Descartamos:** dejarlo solo en ms-reservas, que es el único que hoy usa headers: la regla es que el formato de error sea uno solo para todo el sistema.

**T58. `ConcurrenciaExceptionHandler` va en el paquete `controllers`**
- **Decisión:** el handler de concurrencia vive junto a `ReservaController`.
- **Por qué:** traduce errores a respuestas HTTP, que es trabajo de la capa web. Es propio de ms-reservas (no va al JAR común) porque solo este servicio tiene control de concurrencia.

**T59. Verificamos el criterio de terminado de la Fase 2 por HTTP**
- **Decisión:** con ms-espacios y ms-reservas corriendo, recorrimos la tabla 6.8 del plan con pedidos HTTP reales. Todos dieron el código esperado:
  - Búsqueda de una clase para 30 personas con proyector → 200 con el Aula 101; después de reservarla → 200 con lista vacía.
  - Reserva válida → 201 CONFIRMADA.
  - RN1 (ayer y a 20 días), RN2 (15 minutos y 5 horas), RN3, RN5, RN8, RN9, RN10, RN11 y RN12 → 422 con su motivo.
  - RN6 (misma franja, y en la franja del evento pendiente) → 409.
  - RN7 (usuario2 cancela la de usuario1) → 403.
  - RN13: el evento nace PENDIENTE, aparece en `?estado=PENDIENTE`, el ADMIN lo aprueba → CONFIRMADA. RN14: aprobarlo otra vez → 409.
  - RN15: dos `POST` idénticos en paralelo → uno 201 y el otro 409.
  - Cancelar dos veces → 409; espacio 999 → 404; equipamiento PILETA → 422; sin header → 400; estado inválido → 400.
  - **ms-espacios apagado:** crear y buscar → 503; `GET /mias` → 200, con los nombres de los espacios (D11).
- **Lo que no corrimos acá:** RN4 con lluvia (`CLIMA_UMBRAL=0`). Ya está probado en ms-espacios (T42), y ms-reservas solo reenvía su motivo.
