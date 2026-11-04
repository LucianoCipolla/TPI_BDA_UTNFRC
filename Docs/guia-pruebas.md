# CampusReservas: guía para levantar y probar el sistema

Paso a paso para compilar, levantar Keycloak y los microservicios, y recorrer todas las reglas de negocio con comandos listos para copiar. Sirve como guion para la presentación.

> **Estado actual (Fase 3):** están `campus-commons`, `ms-espacios`, `ms-reservas`, el **API Gateway** y Keycloak. Todavía no está Docker Compose para los servicios (Fase 4), así que cada uno se levanta en su terminal y **los microservicios también se pueden llamar directo** (8081 y 8082). En esta guía:
> - las secciones 6.2 a 6.9 le pegan **directo** a cada microservicio, con el usuario a mano en el header `X-Usuario`: es la forma más rápida de mostrar las reglas de negocio;
> - la sección 6.10 entra **por el gateway** (8080) con un JWT de Keycloak: muestra los 401 y 403, y que el `X-Usuario` lo pone el gateway a partir del token.
>
> En la Fase 4 los microservicios dejan de publicar puertos y todo pasa a entrar por el gateway.

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
| 8080 | API Gateway |

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

**2.3. Compilar el gateway**

```bash
cd api-gateway && ./mvnw clean verify && cd ..
```

Los tres tienen que terminar con `BUILD SUCCESS`. Los avisos de Mockito (*"A Java agent has been loaded dynamically"*) y de SpringDoc son normales.

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

**Terminal 3: api-gateway (puerto 8080)**

```bash
cd api-gateway && ./mvnw spring-boot:run
```

Están listos cuando en el log aparece `Started MsEspaciosApplication` / `Started MsReservasApplication` / `Started ApiGatewayApplication` (el gateway además muestra `Netty started on port 8080`: es reactivo y corre sobre Netty, no sobre Tomcat).

> El gateway no necesita que Keycloak esté arriba para arrancar: baja las claves públicas recién cuando llega el primer pedido con token.

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

Abrí una **cuarta terminal** de Git Bash y pegá este bloque entero. Define las URLs, una función para ver el código HTTP de cada respuesta, otra para pedir tokens a Keycloak y las fechas de prueba.

```bash
ESP=http://localhost:8081
RES=http://localhost:8082
GW=http://localhost:8080
KC=http://localhost:8180
JSON='Content-Type: application/json'
pedir() { curl -s -w "\n→ HTTP %{http_code}\n\n" "$@"; }
# token usuario1 → imprime el access_token de ese usuario (la contraseña es igual al usuario)
token() { curl -s -X POST "$KC/realms/campus/protocol/openid-connect/token" -d grant_type=password -d client_id=campus-api -d "username=$1" -d "password=$1" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }

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
- `preferred_username`: `usuario1` → lo que el gateway pone en `X-Usuario`.
- `realm_access.roles`: `["USUARIO"]` → lo que el gateway usa para autorizar.
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

**Sin header `X-Usuario`** → `400` (llamando directo a ms-reservas; por el gateway, sin token es `401`: ver 6.10)

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

### 6.10. Seguridad: todo por el gateway

Ahora el cliente le habla **solo al gateway** (`$GW`, puerto 8080) y manda el JWT en `Authorization: Bearer`. Ya no hay `X-Usuario` a mano: lo pone el gateway a partir del token. Es la tabla 7.4 del plan.

**Pedir los tokens de los tres usuarios** (duran 5 minutos: si algo empieza a dar `401`, volvé a correr esta línea)

```bash
U1=$(token usuario1); U2=$(token usuario2); AD=$(token admin1); echo "tokens listos"
```

**Sin token** → `401` (no sé quién sos)

```bash
pedir $GW/api/espacios
```

**Listar espacios con usuario1** → `200` (GET de espacios: USUARIO o ADMIN)

```bash
pedir $GW/api/espacios -H "Authorization: Bearer $U1"
```

**Token modificado a mano** → `401` (cambiamos las últimas letras: la firma ya no coincide)

```bash
pedir $GW/api/espacios -H "Authorization: Bearer ${U1%?????}xxxxx"
```

**usuario1 intenta crear un espacio** → `403` (sé quién sos, pero tu rol no alcanza: solo ADMIN)

```bash
pedir -X POST $GW/api/espacios -H "Authorization: Bearer $U1" -H "$JSON" -d '{
  "nombre": "Aula Gateway", "tipo": "AULA", "capacidad": 30, "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "22:00", "actividadesPermitidas": ["CLASE"]
}'
```

**admin1 lo crea** → `201`

```bash
pedir -X POST $GW/api/espacios -H "Authorization: Bearer $AD" -H "$JSON" -d '{
  "nombre": "Aula Gateway", "tipo": "AULA", "capacidad": 30, "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "22:00", "actividadesPermitidas": ["CLASE"]
}'
```

**Buscar disponibles con usuario1** → `200`

```bash
pedir "$GW/api/reservas/disponibilidad?inicio=${DIA}T08:00:00&fin=${DIA}T09:00:00&asistentes=20&actividad=CLASE" -H "Authorization: Bearer $U1"
```

**usuario1 reserva, y además manda un `X-Usuario: usuario2` falso** → `201`, y en la respuesta dice `"usuario":"usuario1"`: el gateway borró el header del cliente y puso el del token

```bash
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $U1" -H "X-Usuario: usuario2" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T08:00:00\", \"fin\": \"${DIA}T09:00:00\",
  \"asistentes\": 20, \"actividad\": \"CLASE\"
}"
```

**Mis reservas con usuario1** → `200`, sin mandar `X-Usuario`

```bash
pedir $GW/api/reservas/mias -H "Authorization: Bearer $U1"
```

**Todas las reservas: usuario1** → `403`; **admin1** → `200`

```bash
pedir $GW/api/reservas -H "Authorization: Bearer $U1"
```

```bash
pedir $GW/api/reservas -H "Authorization: Bearer $AD"
```

**admin1 intenta reservar** → `403` (crear reservas es solo para USUARIO)

```bash
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $AD" -H "$JSON" -d '{}'
```

**Aprobar un evento por el gateway.** usuario1 crea un evento (queda `PENDIENTE`) y guardamos su id en `EV`:

```bash
EV=$(curl -s -X POST $GW/api/reservas -H "Authorization: Bearer $U1" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T19:00:00\", \"fin\": \"${DIA}T21:00:00\",
  \"asistentes\": 40, \"actividad\": \"EVENTO\"
}" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2); echo "evento id=$EV"
```

**usuario1 intenta aprobarlo** → `403` (aprobar es solo ADMIN)

```bash
pedir -X PATCH $GW/api/reservas/$EV/aprobar -H "Authorization: Bearer $U1"
```

**admin1 ve los pendientes y lo aprueba** → `200` y `200`, pasa a `CONFIRMADA`

```bash
pedir "$GW/api/reservas?estado=PENDIENTE" -H "Authorization: Bearer $AD"
```

```bash
pedir -X PATCH $GW/api/reservas/$EV/aprobar -H "Authorization: Bearer $AD"
```

**usuario2 intenta cancelar el evento de usuario1** → `403`, pero **este 403 lo da ms-reservas** (RN7), no el gateway: usuario2 tiene el rol USUARIO y la ruta está permitida. El gateway le pasó `X-Usuario: usuario2` y ms-reservas vio que la reserva no es suya.

```bash
pedir -X PATCH $GW/api/reservas/$EV/cancelar -H "Authorization: Bearer $U2"
```

**Una ruta que no existe, con token válido** → `403` (la corta `denyAll()`: nunca llega a ningún servicio)

```bash
pedir $GW/api/otra -H "Authorization: Bearer $U1"
```

Qué señalar en la presentación:

- **401 vs. 403:** sin token o con un token inválido, 401; con un token válido pero sin el rol, 403. Los dos los decide el gateway, antes de rutear, y salen sin cuerpo.
- **El 403 de RN7 es distinto:** ese sí tiene el `ApiResponse` con el mensaje, porque lo da ms-reservas.
- **Token vencido:** si esperás más de 5 minutos y repetís cualquier pedido con el mismo token, da `401`.

---

## 7. Resumen de códigos HTTP

| Código | Cuándo | Ejemplo en la demo |
|---|---|---|
| 200 | Consulta o cambio de estado correcto | Aprobar, cancelar, listar |
| 201 | Se creó algo | Crear espacio o reserva |
| 400 | El pedido está mal formado | JSON inválido, falta `X-Usuario` |
| 401 | No sé quién sos | Contraseña incorrecta en Keycloak; por el gateway, sin token o con un token modificado o vencido |
| 403 | Sé quién sos, pero no podés | Por el gateway, usuario1 crea un espacio (rol); en ms-reservas, cancelar una reserva ajena (RN7) |
| 404 | No existe | Espacio 99 |
| 409 | Choca con el estado actual | Superposición, aprobar dos veces |
| 422 | Viola una regla de negocio | Capacidad, horario, sala por 3 h |
| 503 | Una dependencia no responde | ms-espacios caído, Open-Meteo sin responder |

---

## 8. Apagar todo

- Microservicios y gateway: **Ctrl+C** en cada terminal.
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
