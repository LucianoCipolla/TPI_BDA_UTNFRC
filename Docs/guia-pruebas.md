# CampusReservas: guía para levantar y probar el sistema

Paso a paso para compilar, levantar Keycloak y los microservicios, y recorrer todas las reglas de negocio con comandos listos para copiar. Sirve como guion para la presentación.

> **Estado actual (Fase 2):** están `campus-commons`, `ms-espacios`, `ms-reservas` y Keycloak. El **API Gateway todavía no existe**, así que en esta guía:
> - le pegamos **directo** a cada microservicio (8081 y 8082);
> - el usuario se manda a mano en el header `X-Usuario`, que es lo que después va a poner el gateway a partir del JWT;
> - las rutas de ADMIN no están protegidas todavía (de eso se va a encargar el gateway).
>
> Keycloak ya funciona: mostramos el login y el JWT que emite, que es lo que el gateway va a validar.

---

## 1. Requisitos

| Herramienta | Versión | Para qué | Cómo verificar |
|---|---|---|---|
| Java (JDK) | 25 | Compilar y correr los microservicios | `java -version` |
| Maven | 3.9 o más | Instalar `campus-commons` (no tiene `mvnw`) | `mvn -v` |
| Docker Desktop | cualquiera reciente | Correr Keycloak | `docker --version` |
| Git Bash | viene con Git | Correr los comandos de esta guía (`curl`, `date`) | — |

> Todos los comandos de esta guía son para **Git Bash**. En PowerShell, `curl` es otro comando y no funcionan igual.

Puertos que usa el sistema:

| Puerto | Qué |
|---|---|
| 8180 | Keycloak |
| 8081 | ms-espacios |
| 8082 | ms-reservas |
| 8080 | API Gateway (Fase 3, todavía no existe) |

---

## 2. Compilar

Siempre desde la raíz del repo. **El orden importa:** los microservicios dependen de `campus-commons`, que tiene que estar instalado en el repositorio local de Maven (`~/.m2`).

**2.1. Instalar el JAR común**

```bash
mvn -f campus-commons/pom.xml clean install
```

**2.2. Compilar y correr los tests de cada microservicio**

```bash
cd ms-espacios && ./mvnw clean verify && cd ..
```

```bash
cd ms-reservas && ./mvnw clean verify && cd ..
```

Los dos tienen que terminar con `BUILD SUCCESS`. Los avisos de Mockito (*"A Java agent has been loaded dynamically"*) y de SpringDoc son normales.

> Si cambiás algo en `campus-commons`, volvé a correr el paso 2.1 antes de compilar los microservicios.

---

## 3. Levantar Keycloak

Con Docker Desktop abierto, desde la raíz del repo:

```bash
docker compose up -d
```

Tarda unos 20 a 40 segundos. Para saber si ya está listo:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8180/realms/campus
```

Cuando devuelve `200`, está listo. Al arrancar importa el realm `campus` desde `keycloak/realm-campus.json`.

**Consola de administración:** <http://localhost:8180/admin> con usuario `admin` y contraseña `admin`. Elegí el realm **campus** arriba a la izquierda para mostrar usuarios, roles y el cliente `campus-api`.

**Usuarios de prueba** (la contraseña es igual al usuario):

| Usuario | Contraseña | Rol | Nombre |
|---|---|---|---|
| `usuario1` | `usuario1` | USUARIO | Ana Pérez |
| `usuario2` | `usuario2` | USUARIO | — |
| `admin1` | `admin1` | ADMIN | — |

---

## 4. Levantar los microservicios

Cada uno en **su propia terminal** de Git Bash, porque quedan corriendo. Primero ms-espacios, después ms-reservas (ms-reservas le consulta a ms-espacios).

**Terminal 1: ms-espacios (puerto 8081)**

```bash
cd ms-espacios && ./mvnw spring-boot:run
```

**Terminal 2: ms-reservas (puerto 8082)**

```bash
cd ms-reservas && ./mvnw spring-boot:run
```

Están listos cuando en el log aparece `Started MsEspaciosApplication` / `Started MsReservasApplication`.

Al arrancar por primera vez, ms-espacios carga 5 espacios de prueba (`DatosIniciales`):

| id | Nombre | Tipo | Capacidad | Horario | Equipamiento | Actividades |
|---|---|---|---|---|---|---|
| 1 | Aula 101 | AULA | 40 | 08 a 22 | PROYECTOR, PIZARRA | CLASE, EXAMEN, EVENTO |
| 2 | Aula 102 | AULA | 60 | 08 a 22 | PIZARRA | CLASE, EXAMEN, EVENTO |
| 3 | Laboratorio 3 | LABORATORIO | 25 | 08 a 22 | COMPUTADORAS, PROYECTOR | CLASE, EXAMEN |
| 4 | Sala de reuniones | SALA | 12 | 09 a 18 | PROYECTOR, SONIDO | CLASE, EVENTO |
| 5 | Cancha de fútbol | CANCHA | 22 | 08 a 21 | — | DEPORTE, CLASE |

**Swagger** (documentación interactiva de la API, sirve para mostrar en la presentación):

- ms-espacios: <http://localhost:8081/swagger-ui.html>
- ms-reservas: <http://localhost:8082/swagger-ui.html>

---

## 5. Preparar la terminal de pruebas

Abrí una **tercera terminal** de Git Bash y pegá este bloque entero. Define las URLs, una función para ver el código HTTP de cada respuesta y las fechas de prueba.

```bash
ESP=http://localhost:8081
RES=http://localhost:8082
KC=http://localhost:8180
JSON='Content-Type: application/json'
pedir() { curl -s -w "\n→ HTTP %{http_code}\n\n" "$@"; }

# DIA: el próximo día hábil a 2 o más días de hoy (sirve para el laboratorio, que pide lunes a viernes y 24 h de anticipación)
DIA=$(date -d "+2 days" +%F); while [ "$(date -d "$DIA" +%u)" -gt 5 ]; do DIA=$(date -d "$DIA +1 day" +%F); done
# MANANA: mañana (para mostrar que el laboratorio pide 24 h de anticipación)
MANANA=$(date -d "+1 day" +%F)
# SABADO: el próximo sábado (para mostrar que el laboratorio no se reserva el fin de semana)
SABADO=$(date -d "next saturday" +%F)
echo "DIA=$DIA  MANANA=$MANANA  SABADO=$SABADO"
```

> Si cerrás la terminal, tenés que volver a pegar este bloque.

Todas las respuestas tienen el mismo formato (`ApiResponse` de `campus-commons`):

```json
{ "success": true, "responseValue": { ... }, "message": "Reserva creada", "executedOn": "2026-11-03T20:11:08" }
```

---

## 6. Guion de la demo

### 6.1. Keycloak: login y JWT

**Pedir un token como `usuario1`** (flujo *password*, habilitado solo para probar sin frontend):

```bash
TOKEN=$(curl -s -X POST "$KC/realms/campus/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=campus-api \
  -d username=usuario1 -d password=usuario1 \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
echo "$TOKEN"
```

**Ver qué tiene adentro el JWT** (el payload es la segunda parte, en Base64):

```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo
```

Qué señalar en la salida:

- `iss`: `http://localhost:8180/realms/campus` → quién lo emitió.
- `preferred_username`: `usuario1` → lo que el gateway va a poner en `X-Usuario`.
- `realm_access.roles`: `["USUARIO"]` → lo que el gateway va a usar para autorizar.
- `exp` − `iat` = 300 → **el token dura 5 minutos**.

También se puede pegar el token en <https://jwt.io> para verlo decodificado.

Repetí con `admin1` / `admin1` para mostrar que el rol es `ADMIN`.

**Contraseña incorrecta** → `401`:

```bash
pedir -X POST "$KC/realms/campus/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=campus-api -d username=usuario1 -d password=mal
```

### 6.2. ms-espacios: catálogo (ADMIN)

**Listar espacios**

```bash
pedir $ESP/api/espacios
```

**Buscar uno**

```bash
pedir $ESP/api/espacios/1
```

**Espacio inexistente** → `404`

```bash
pedir $ESP/api/espacios/99
```

**Crear un espacio** → `201` (anotá el `id` que devuelve; con los datos de prueba es el `6`)

```bash
pedir -X POST $ESP/api/espacios -H "$JSON" -d '{
  "nombre": "Aula Magna", "tipo": "AULA", "capacidad": 200,
  "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "23:00",
  "actividadesPermitidas": ["CLASE", "EVENTO"],
  "equipamiento": ["PROYECTOR", "SONIDO"]
}'
```

**Modificarlo** → `200`

```bash
pedir -X PUT $ESP/api/espacios/6 -H "$JSON" -d '{
  "nombre": "Aula Magna", "tipo": "AULA", "capacidad": 250,
  "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "23:00",
  "actividadesPermitidas": ["CLASE", "EVENTO", "EXAMEN"],
  "equipamiento": ["PROYECTOR", "SONIDO"]
}'
```

**Horario al revés** → `422` (está bien formado, pero viola una regla)

```bash
pedir -X POST $ESP/api/espacios -H "$JSON" -d '{
  "nombre": "Aula X", "tipo": "AULA", "capacidad": 10, "latitud": 0, "longitud": 0,
  "horaApertura": "20:00", "horaCierre": "08:00", "actividadesPermitidas": ["CLASE"]
}'
```

**Datos inválidos** → `400` (el pedido está mal formado)

```bash
pedir -X POST $ESP/api/espacios -H "$JSON" -d '{"nombre": "", "tipo": "AULA", "capacidad": 0}'
```

**Borrarlo** → `200`, y después buscarlo da `404`

```bash
pedir -X DELETE $ESP/api/espacios/6
```

```bash
pedir $ESP/api/espacios/6
```

### 6.3. ms-espacios: validación de una franja

Es el endpoint que usa ms-reservas por dentro. Devuelve `disponible: true/false` y el motivo.

**Aula 101 con proyector, de 10 a 12** → disponible

```bash
pedir "$ESP/api/espacios/1/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&actividad=CLASE&equipamiento=PROYECTOR"
```

**Aula 102 con proyector** → `disponible: false`, motivo *"Al espacio le falta: [PROYECTOR]"* (RN8)

```bash
pedir "$ESP/api/espacios/2/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&actividad=CLASE&equipamiento=PROYECTOR"
```

**Cancha** → consulta el pronóstico de Open-Meteo (RN4)

```bash
pedir "$ESP/api/espacios/5/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T11:00:00&actividad=DEPORTE"
```

> Si ese día se pronostica más de 60 % de lluvia, la respuesta es `disponible: false` con *"Pronóstico de lluvia de N %"*. Si no hay internet, `503`.

### 6.4. ms-reservas: buscar espacios disponibles

**20 personas, clase con proyector, de 10 a 12** → Aula 101 y Laboratorio 3

```bash
pedir "$RES/api/reservas/disponibilidad?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&asistentes=20&actividad=CLASE&equipamiento=PROYECTOR"
```

Volvé a correr esta búsqueda después del paso 6.5: el Aula 101 ya no aparece porque quedó ocupada.

### 6.5. ms-reservas: crear reservas (caso feliz)

**Clase en el Aula 101 (usuario1)** → `201`, estado `CONFIRMADA` (id 1)

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 30, \"actividad\": \"CLASE\", \"equipamiento\": [\"PROYECTOR\"]
}"
```

**Evento en la Sala de reuniones (usuario2)** → `201`, estado `PENDIENTE` (RN13) (id 2)

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario2" -H "$JSON" -d "{
  \"espacioId\": 4, \"inicio\": \"${DIA}T14:00:00\", \"fin\": \"${DIA}T15:30:00\",
  \"asistentes\": 10, \"actividad\": \"EVENTO\"
}"
```

**Partido en la cancha, de 15 a 17 (usuario1)** → `201`, `CONFIRMADA` (id 3; depende del clima)

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 5, \"inicio\": \"${DIA}T15:00:00\", \"fin\": \"${DIA}T17:00:00\",
  \"asistentes\": 14, \"actividad\": \"DEPORTE\"
}"
```

### 6.6. Reglas de negocio: un pedido por regla

Todos estos tienen que **fallar**. El mensaje explica cuál regla se violó.

| # | Caso | Esperado |
|---|---|---|
| RN1 | Más de 14 días adelante | 422 |
| RN2 | Dura 15 minutos | 422 |
| RN3 | Fuera del horario de apertura | 422 |
| RN5 | Más asistentes que la capacidad | 422 |
| RN6 | Se superpone con la reserva 1 | 409 |
| RN8 | Falta equipamiento | 422 |
| RN9 | Actividad no admitida | 422 |
| RN10 | Laboratorio en sábado / con menos de 24 h | 422 |
| RN11 | Sala por 3 horas | 422 |
| RN12 | Cancha por 90 minutos | 422 |
| — | Espacio inexistente | 404 |
| — | Falta el header `X-Usuario` | 400 |

**RN1: más de 14 días adelante**

```bash
LEJOS=$(date -d "+20 days" +%F)
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${LEJOS}T10:00:00\", \"fin\": \"${LEJOS}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN2: dura 15 minutos**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T10:15:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN3: fuera del horario (el Aula 102 abre a las 8)**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T06:00:00\", \"fin\": \"${DIA}T07:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN5: 50 personas en un laboratorio para 25**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 50, \"actividad\": \"CLASE\"
}"
```

**RN6: se superpone con la reserva 1 (Aula 101, de 10 a 12)** → `409`

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario2" -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T11:00:00\", \"fin\": \"${DIA}T13:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

**RN8: el Aula 102 no tiene proyector**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\", \"equipamiento\": [\"PROYECTOR\"]
}"
```

**RN9: deporte en un laboratorio**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${DIA}T15:00:00\", \"fin\": \"${DIA}T16:00:00\",
  \"asistentes\": 5, \"actividad\": \"DEPORTE\"
}"
```

**RN10: laboratorio un sábado**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${SABADO}T10:00:00\", \"fin\": \"${SABADO}T12:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

**RN10: laboratorio con menos de 24 h de anticipación (mañana a las 8)**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${MANANA}T08:00:00\", \"fin\": \"${MANANA}T09:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

> Este caso muestra el mensaje de las 24 h si lo corrés **después de las 8 de la mañana** y mañana es día hábil. Si mañana es sábado, el rechazo es por el fin de semana.

**RN11: sala por 3 horas**

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 4, \"inicio\": \"${DIA}T09:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN12: cancha por 90 minutos** (la duración tiene que ser de horas enteras)

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 5, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:30:00\",
  \"asistentes\": 10, \"actividad\": \"DEPORTE\"
}"
```

**Espacio inexistente** → `404`

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 99, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**Sin header `X-Usuario`** → `400` (cuando esté el gateway, sin token va a ser `401`)

```bash
pedir -X POST $RES/api/reservas -H "$JSON" -d '{}'
```

### 6.7. Estados de una reserva

**Ver los eventos pendientes (ADMIN)**

```bash
pedir "$RES/api/reservas?estado=PENDIENTE"
```

**Aprobar el evento 2** → `200`, pasa a `CONFIRMADA`

```bash
pedir -X PATCH $RES/api/reservas/2/aprobar
```

**Aprobarlo de nuevo** → `409` (RN14: ese cambio de estado no está en el diagrama)

```bash
pedir -X PATCH $RES/api/reservas/2/aprobar
```

**Crear otro evento y rechazarlo** → queda `RECHAZADA` (id 4)

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario2" -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T18:00:00\", \"fin\": \"${DIA}T20:00:00\",
  \"asistentes\": 30, \"actividad\": \"EVENTO\"
}"
```

```bash
pedir -X PATCH $RES/api/reservas/4/rechazar
```

**usuario2 intenta cancelar la reserva de usuario1** → `403` (RN7)

```bash
pedir -X PATCH $RES/api/reservas/1/cancelar -H "X-Usuario: usuario2"
```

**usuario1 cancela la suya** → `200`, `CANCELADA`

```bash
pedir -X PATCH $RES/api/reservas/1/cancelar -H "X-Usuario: usuario1"
```

**Cancelarla otra vez** → `409`

```bash
pedir -X PATCH $RES/api/reservas/1/cancelar -H "X-Usuario: usuario1"
```

Ahora el Aula 101 de 10 a 12 quedó libre de nuevo: volvé a correr la búsqueda del paso 6.4 y aparece otra vez.

**Mis reservas (USUARIO)**

```bash
pedir $RES/api/reservas/mias -H "X-Usuario: usuario1"
```

**Todas las reservas (ADMIN)**, con o sin filtro por estado (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`, `CANCELADA`)

```bash
pedir $RES/api/reservas
```

```bash
pedir "$RES/api/reservas?estado=CANCELADA"
```

### 6.8. Concurrencia (RN15)

Dos usuarios piden **el mismo espacio y la misma franja al mismo tiempo**. Solo uno se guarda; el otro recibe `409`.

```bash
CUERPO="{\"espacioId\": 2, \"inicio\": \"${DIA}T16:00:00\", \"fin\": \"${DIA}T17:00:00\", \"asistentes\": 5, \"actividad\": \"CLASE\"}"
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "$CUERPO" &
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario2" -H "$JSON" -d "$CUERPO" &
wait
```

Una respuesta es `201` y la otra `409`. El mensaje del `409` depende de quién llegó primero: *"El espacio ya está reservado en esa franja"* o *"Otro usuario modificó ese espacio o esa reserva al mismo tiempo"*.

### 6.9. Resiliencia: ms-espacios caído

Cortá ms-espacios con **Ctrl+C** en la Terminal 1 y probá:

**Crear una reserva** → `503` *"El servicio de espacios no está disponible"*

```bash
pedir -X POST $RES/api/reservas -H "X-Usuario: usuario1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**Buscar disponibles** → `503`

```bash
pedir "$RES/api/reservas/disponibilidad?inicio=${DIA}T10:00:00&fin=${DIA}T11:00:00&asistentes=5&actividad=CLASE"
```

**Mis reservas** → `200`: listar y cancelar siguen funcionando, porque no necesitan a ms-espacios

```bash
pedir $RES/api/reservas/mias -H "X-Usuario: usuario1"
```

Después volvé a levantar ms-espacios (`./mvnw spring-boot:run` en la Terminal 1).

---

## 7. Resumen de códigos HTTP

| Código | Cuándo | Ejemplo en la demo |
|---|---|---|
| 200 | Consulta o cambio de estado correcto | Aprobar, cancelar, listar |
| 201 | Se creó algo | Crear espacio o reserva |
| 400 | El pedido está mal formado | JSON inválido, falta `X-Usuario` |
| 401 | Credenciales inválidas | Token con contraseña incorrecta |
| 403 | El recurso no es tuyo | Cancelar una reserva ajena |
| 404 | No existe | Espacio 99 |
| 409 | Choca con el estado actual | Superposición, aprobar dos veces |
| 422 | Viola una regla de negocio | Capacidad, horario, sala por 3 h |
| 503 | Una dependencia no responde | ms-espacios caído, Open-Meteo sin responder |

---

## 8. Apagar todo

- Microservicios: **Ctrl+C** en cada terminal.
- Keycloak:

```bash
docker compose down
```

**Empezar con datos limpios:** las bases H2 se guardan en `ms-espacios/data/` y `ms-reservas/data/` (no se suben a Git). Con los servicios apagados, borralas y al arrancar de nuevo se cargan los 5 espacios de prueba:

```bash
rm -rf ms-espacios/data ms-reservas/data
```

> Conviene hacerlo antes de la presentación, así los ids de las reservas coinciden con los de esta guía (1, 2, 3, 4).

---

## 9. Problemas comunes

**`Port 8080/8081/8082 was already in use` o `Web server failed to start`**
Otro programa o contenedor usa ese puerto. Para ver los contenedores que están corriendo:

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

Pará el que moleste con `docker stop <nombre>`. Si no podés, levantá ms-espacios en otro puerto y avisale a ms-reservas dónde está:

```bash
cd ms-espacios && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8091
```

```bash
cd ms-reservas && MS_ESPACIOS_URL=http://localhost:8091 ./mvnw spring-boot:run
```

En ese caso, en el paso 5 usá `ESP=http://localhost:8091`.

**`Could not find artifact ar.edu.utn.frc.bda.campus:campus-commons`**
No está instalado el JAR común. Corré el paso 2.1.

**`JAVA_HOME is not defined correctly` o `release version 25 not supported`**
Falta Java 25 o la terminal se abrió antes de instalarlo. Cerrá y volvé a abrir la terminal y revisá `java -version`.

**El token da error o la consulta dice que expiró**
El token dura 5 minutos. Volvé a pedirlo (paso 6.1).

**Keycloak dice "Account is not fully set up"**
A un usuario le faltan nombre, apellido o email. Revisá `keycloak/realm-campus.json` y reiniciá Keycloak con `docker compose down` y `docker compose up -d`.

**La cancha da `503` o `422` por lluvia**
El `503` es porque no hay internet y no se puede consultar Open-Meteo. El `422` con *"Pronóstico de lluvia"* es la RN4 funcionando: probá otro día.

**`Unable to establish loopback connection` al arrancar un microservicio**
Algún antivirus o política de Windows bloquea los sockets locales que usa Java en la carpeta temporal. Probá indicarle otra carpeta:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=C:/temp"
```
