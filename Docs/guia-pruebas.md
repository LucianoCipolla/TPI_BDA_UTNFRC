# CampusReservas: guía para levantar y probar el sistema

Paso a paso para compilar, levantar todo con Docker Compose y recorrer todas las reglas de negocio con comandos listos para copiar. Sirve como guion para la presentación.

> **Estado actual (Fase 4):** todo el sistema corre en Docker: Keycloak, `ms-espacios`, `ms-reservas` y el **API Gateway**. Solo el gateway (8080) y Keycloak (8180) publican puertos; los microservicios quedan en la red interna de Docker (trusted zone, D6). Por eso **todos los pedidos de esta guía entran por el gateway**, con el JWT de cada usuario.
>
> Para desarrollar (correr un servicio desde el IDE, usar Swagger, llamarlos directo) está el **modo local**, en la sección 10.

---

## 1. Requisitos

| Herramienta | Versión | Para qué | Cómo verificar |
|---|---|---|---|
| Java (JDK) | 25 | Compilar los JAR (la imagen de Docker no compila: copia el JAR) | `java -version` |
| Maven | 3.9 o más | Instalar `campus-commons` (no tiene `mvnw`) | `mvn -v` |
| Docker Desktop | cualquiera reciente | Correr todo el sistema | `docker --version` |
| Git Bash | viene con Git | Correr `build.sh` y los comandos de esta guía (`curl`, `date`) | — |

> Todos los comandos de esta guía son para **Git Bash**. En PowerShell, `curl` es otro comando y `sh` no existe. En VS Code se abre con la flecha junto al `+` de la terminal → **Git Bash**.

Puertos que usa el sistema:

| Puerto | Qué | ¿Accesible desde afuera? |
|---|---|---|
| 8080 | API Gateway | Sí: el único punto de entrada a la API |
| 8180 | Keycloak | Sí: para pedir tokens y la consola de administración |
| 8081 | ms-espacios | No: solo por la red interna de Docker (sí en modo local) |
| 8082 | ms-reservas | No: solo por la red interna de Docker (sí en modo local) |

---

## 2. Compilar: `build.sh`

Desde la raíz del repo, en Git Bash:

```bash
sh build.sh
```

Si estás en **PowerShell**, llamá al bash que trae Git:

```powershell
& "C:\Program Files\Git\bin\bash.exe" build.sh
```

Compila los cuatro proyectos **en orden**: primero instala `campus-commons` en el repositorio local de Maven (`~/.m2`), porque los otros tres dependen de él, y después empaqueta ms-espacios, ms-reservas y el gateway. Tienen que salir cuatro `BUILD SUCCESS` y al final:

```
Listo: los tres JAR están en */target/. Ahora: docker compose up --build
```

> **Antes de correrlo, frená cualquier servicio que esté corriendo en modo local** (en el IDE o en una terminal). En Windows, un servicio corriendo bloquea su JAR y `clean` no lo puede borrar.

`build.sh` saltea los tests para ir rápido. Para correrlos, en cada microservicio:

```bash
cd ms-espacios && ./mvnw verify && cd ..
```

```bash
cd ms-reservas && ./mvnw verify && cd ..
```

---

## 3. Levantar todo con Docker Compose

Con Docker Desktop abierto, desde la raíz del repo:

```bash
docker compose up -d --build
```

- `--build` arma de nuevo las imágenes con los JAR que dejó `build.sh`. **Si cambiaste código, primero `build.sh` y después esto**: sin `build.sh`, la imagen se arma con el JAR viejo.
- `-d` lo deja corriendo en segundo plano y te devuelve la terminal.

**Ver que estén los cuatro arriba:**

```bash
docker compose ps
```

Tienen que aparecer `keycloak`, `ms-espacios`, `ms-reservas` y `api-gateway` con estado `Up`. Que estén `Up` no quiere decir que ya arrancaron: Spring tarda unos 10 a 20 segundos y Keycloak de 20 a 40. Para saber si están listos:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8180/realms/campus
```

```bash
docker compose logs ms-espacios ms-reservas api-gateway | grep Started
```

Cuando Keycloak devuelve `200` y aparecen las tres líneas `Started ...Application`, está todo listo. Al arrancar, Keycloak importa el realm `campus` desde `keycloak/realm-campus.json`.

**Consola de administración de Keycloak:** <http://localhost:8180/admin> con usuario `admin` y contraseña `admin`. Elegí el realm **campus** arriba a la izquierda para mostrar usuarios, roles y el cliente `campus-api`.

**Usuarios de prueba** (la contraseña es igual al usuario):

| Usuario | Contraseña | Rol | Nombre |
|---|---|---|---|
| `usuario1` | `usuario1` | USUARIO | Ana Pérez |
| `usuario2` | `usuario2` | USUARIO | — |
| `admin1` | `admin1` | ADMIN | — |

Al arrancar con la base vacía, ms-espacios carga 5 espacios de prueba (`DatosIniciales`):

| id | Nombre | Tipo | Capacidad | Horario | Equipamiento | Actividades |
|---|---|---|---|---|---|---|
| 1 | Aula 101 | AULA | 40 | 08 a 22 | PROYECTOR, PIZARRA | CLASE, EXAMEN, EVENTO |
| 2 | Aula 102 | AULA | 60 | 08 a 22 | PIZARRA | CLASE, EXAMEN, EVENTO |
| 3 | Laboratorio 3 | LABORATORIO | 25 | 08 a 22 | COMPUTADORAS, PROYECTOR | CLASE, EXAMEN |
| 4 | Sala de reuniones | SALA | 12 | 09 a 18 | PROYECTOR, SONIDO | CLASE, EVENTO |
| 5 | Cancha de fútbol | CANCHA | 22 | 08 a 21 | — | DEPORTE, CLASE |

**Ver los logs durante la demo.** Abrí otra terminal y dejala a la vista: cada reserva creada, cada regla que rechaza un pedido y cada falla de una dependencia deja una línea ahí. Qué buscar está en la sección 6.11.

```bash
docker compose logs -f ms-espacios ms-reservas api-gateway
```

> `-f` sigue mostrando las líneas nuevas; se corta con **Ctrl+C** (eso no apaga los contenedores). Cada línea empieza con el nombre del contenedor, así se sabe de qué servicio es.

---

## 4. Preparar la terminal de pruebas

Abrí una terminal de Git Bash y pegá este bloque entero. Define las URLs, una función para ver el código HTTP de cada respuesta, otra para pedir tokens a Keycloak, otra para hacer un pedido como un usuario y las fechas de prueba.

```bash
GW=http://localhost:8080
KC=http://localhost:8180
JSON='Content-Type: application/json'
pedir() { curl -s -w "\n→ HTTP %{http_code}\n\n" "$@"; }
# token usuario1 → imprime el access_token de ese usuario (la contraseña es igual al usuario)
token() { curl -s -X POST "$KC/realms/campus/protocol/openid-connect/token" -d grant_type=password -d client_id=campus-api -d "username=$1" -d "password=$1" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }
# como usuario1 <lo de curl> → hace el pedido con un token nuevo de ese usuario (nunca vence a mitad de la demo)
como() { local u=$1; shift; pedir -H "Authorization: Bearer $(token "$u")" "$@"; }

# DIA: el próximo día hábil a 2 o más días de hoy (sirve para el laboratorio, que pide lunes a viernes y 24 h de anticipación)
DIA=$(date -d "+2 days" +%F); while [ "$(date -d "$DIA" +%u)" -gt 5 ]; do DIA=$(date -d "$DIA +1 day" +%F); done
# MANANA: mañana (para mostrar que el laboratorio pide 24 h de anticipación)
MANANA=$(date -d "+1 day" +%F)
# SABADO: el próximo sábado (para mostrar que el laboratorio no se reserva el fin de semana)
SABADO=$(date -d "next saturday" +%F)
echo "DIA=$DIA  MANANA=$MANANA  SABADO=$SABADO"
```

> Si cerrás la terminal, tenés que volver a pegar este bloque.

**Por qué `como`:** el token dura 5 minutos y la demo dura más. `como usuario1 ...` pide un token nuevo en cada pedido y lo manda en `Authorization: Bearer`, así nunca vence. Por dentro es lo mismo que hace Postman: primero el login en Keycloak, después el pedido al gateway con el token. El `X-Usuario` no lo mandamos nosotros: lo pone el gateway a partir del token.

Todas las respuestas de los microservicios tienen el mismo formato (`ApiResponse` de `campus-commons`):

```json
{ "success": true, "responseValue": { ... }, "message": "Reserva creada", "executedOn": "2026-11-03T20:11:08" }
```

---

## 5. Antes de la presentación: datos limpios

Los ids de esta guía (espacio 6, reservas 1 a 4) son los que salen con la base vacía. Las bases H2 viven **dentro de cada contenedor**, así que para empezar de cero alcanza con borrar los contenedores y levantarlos de nuevo:

```bash
docker compose down
```

```bash
docker compose up -d
```

> `down` borra los contenedores y con ellos los datos; Keycloak vuelve a importar el realm. Si solo querés pausar sin perder nada, usá `docker compose stop` y `docker compose start` (ver la sección 8).

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

**Contraseña incorrecta** → `400` con `invalid_grant` (lo responde Keycloak, no nuestra API):

```bash
pedir -X POST "$KC/realms/campus/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=campus-api -d username=usuario1 -d password=mal
```

### 6.2. ms-espacios: catálogo (ADMIN)

Consultar espacios lo puede hacer cualquier usuario logueado; crear, modificar y borrar, solo ADMIN.

**Listar espacios**

```bash
como usuario1 $GW/api/espacios
```

**Buscar uno**

```bash
como usuario1 $GW/api/espacios/1
```

**Espacio inexistente** → `404`

```bash
como usuario1 $GW/api/espacios/99
```

**Crear un espacio** → `201` (anotá el `id` que devuelve; con los datos de prueba es el `6`)

```bash
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{
  "nombre": "Aula Magna", "tipo": "AULA", "capacidad": 200,
  "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "23:00",
  "actividadesPermitidas": ["CLASE", "EVENTO"],
  "equipamiento": ["PROYECTOR", "SONIDO"]
}'
```

**Modificarlo** → `200`

```bash
como admin1 -X PUT $GW/api/espacios/6 -H "$JSON" -d '{
  "nombre": "Aula Magna", "tipo": "AULA", "capacidad": 250,
  "latitud": -31.44, "longitud": -64.19,
  "horaApertura": "08:00", "horaCierre": "23:00",
  "actividadesPermitidas": ["CLASE", "EVENTO", "EXAMEN"],
  "equipamiento": ["PROYECTOR", "SONIDO"]
}'
```

**Horario al revés** → `422` (está bien formado, pero viola una regla)

```bash
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{
  "nombre": "Aula X", "tipo": "AULA", "capacidad": 10, "latitud": 0, "longitud": 0,
  "horaApertura": "20:00", "horaCierre": "08:00", "actividadesPermitidas": ["CLASE"]
}'
```

**Datos inválidos** → `400` (el pedido está mal formado)

```bash
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{"nombre": "", "tipo": "AULA", "capacidad": 0}'
```

**Borrarlo** → `200`, y después buscarlo da `404`

```bash
como admin1 -X DELETE $GW/api/espacios/6
```

```bash
como admin1 $GW/api/espacios/6
```

### 6.3. ms-espacios: validación de una franja

Es el endpoint que usa ms-reservas por dentro. Devuelve `disponible: true/false` y el motivo.

**Aula 101 con proyector, de 10 a 12** → disponible

```bash
como usuario1 "$GW/api/espacios/1/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&actividad=CLASE&equipamiento=PROYECTOR"
```

**Aula 102 con proyector** → `disponible: false`, motivo *"Al espacio le falta: [PROYECTOR]"* (RN8)

```bash
como usuario1 "$GW/api/espacios/2/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&actividad=CLASE&equipamiento=PROYECTOR"
```

**Cancha** → consulta el pronóstico de Open-Meteo (RN4)

```bash
como usuario1 "$GW/api/espacios/5/validacion?inicio=${DIA}T10:00:00&fin=${DIA}T11:00:00&actividad=DEPORTE"
```

> Si ese día se pronostica más de 60 % de lluvia, la respuesta es `disponible: false` con *"Pronóstico de lluvia de N %"*. Si no hay internet, `503`.

### 6.4. ms-reservas: buscar espacios disponibles

**20 personas, clase con proyector, de 10 a 12** → Aula 101 y Laboratorio 3

```bash
como usuario1 "$GW/api/reservas/disponibilidad?inicio=${DIA}T10:00:00&fin=${DIA}T12:00:00&asistentes=20&actividad=CLASE&equipamiento=PROYECTOR"
```

Volvé a correr esta búsqueda después del paso 6.5: el Aula 101 ya no aparece porque quedó ocupada.

### 6.5. ms-reservas: crear reservas (caso feliz)

**Clase en el Aula 101 (usuario1)** → `201`, estado `CONFIRMADA` (id 1)

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 30, \"actividad\": \"CLASE\", \"equipamiento\": [\"PROYECTOR\"]
}"
```

**Evento en la Sala de reuniones (usuario2)** → `201`, estado `PENDIENTE` (RN13) (id 2)

```bash
como usuario2 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 4, \"inicio\": \"${DIA}T14:00:00\", \"fin\": \"${DIA}T15:30:00\",
  \"asistentes\": 10, \"actividad\": \"EVENTO\"
}"
```

**Partido en la cancha, de 15 a 17 (usuario1)** → `201`, `CONFIRMADA` (id 3; depende del clima)

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
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
| — | Actividad que no existe | 400 |

**RN1: más de 14 días adelante**

```bash
LEJOS=$(date -d "+20 days" +%F)
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${LEJOS}T10:00:00\", \"fin\": \"${LEJOS}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN2: dura 15 minutos**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T10:15:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN3: fuera del horario (el Aula 102 abre a las 8)**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T06:00:00\", \"fin\": \"${DIA}T07:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN5: 50 personas en un laboratorio para 25**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 50, \"actividad\": \"CLASE\"
}"
```

**RN6: se superpone con la reserva 1 (Aula 101, de 10 a 12)** → `409`

```bash
como usuario2 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T11:00:00\", \"fin\": \"${DIA}T13:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

**RN8: el Aula 102 no tiene proyector**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\", \"equipamiento\": [\"PROYECTOR\"]
}"
```

**RN9: deporte en un laboratorio**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${DIA}T15:00:00\", \"fin\": \"${DIA}T16:00:00\",
  \"asistentes\": 5, \"actividad\": \"DEPORTE\"
}"
```

**RN10: laboratorio un sábado**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${SABADO}T10:00:00\", \"fin\": \"${SABADO}T12:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

**RN10: laboratorio con menos de 24 h de anticipación (mañana a las 8)**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 3, \"inicio\": \"${MANANA}T08:00:00\", \"fin\": \"${MANANA}T09:00:00\",
  \"asistentes\": 10, \"actividad\": \"CLASE\"
}"
```

> Este caso muestra el mensaje de las 24 h si lo corrés **después de las 8 de la mañana** y mañana es día hábil. Si mañana es sábado, el rechazo es por el fin de semana.

**RN11: sala por 3 horas**

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 4, \"inicio\": \"${DIA}T09:00:00\", \"fin\": \"${DIA}T12:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**RN12: cancha por 90 minutos** (la duración tiene que ser de horas enteras)

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 5, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:30:00\",
  \"asistentes\": 10, \"actividad\": \"DEPORTE\"
}"
```

**Espacio inexistente** → `404`

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 99, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**Actividad que no existe** → `400` (el pedido está mal formado: `NADAR` no es un valor del enum)

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"NADAR\"
}"
```

### 6.7. Estados de una reserva

**Ver los eventos pendientes (ADMIN)**

```bash
como admin1 "$GW/api/reservas?estado=PENDIENTE"
```

**Aprobar el evento 2** → `200`, pasa a `CONFIRMADA`

```bash
como admin1 -X PATCH $GW/api/reservas/2/aprobar
```

**Aprobarlo de nuevo** → `409` (RN14: ese cambio de estado no está en el diagrama)

```bash
como admin1 -X PATCH $GW/api/reservas/2/aprobar
```

**Crear otro evento y rechazarlo** → queda `RECHAZADA` (id 4)

```bash
como usuario2 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 1, \"inicio\": \"${DIA}T18:00:00\", \"fin\": \"${DIA}T20:00:00\",
  \"asistentes\": 30, \"actividad\": \"EVENTO\"
}"
```

```bash
como admin1 -X PATCH $GW/api/reservas/4/rechazar
```

**usuario2 intenta cancelar la reserva de usuario1** → `403` (RN7)

```bash
como usuario2 -X PATCH $GW/api/reservas/1/cancelar
```

**usuario1 cancela la suya** → `200`, `CANCELADA`

```bash
como usuario1 -X PATCH $GW/api/reservas/1/cancelar
```

**Cancelarla otra vez** → `409`

```bash
como usuario1 -X PATCH $GW/api/reservas/1/cancelar
```

Ahora el Aula 101 de 10 a 12 quedó libre de nuevo: volvé a correr la búsqueda del paso 6.4 y aparece otra vez.

**Mis reservas (USUARIO)**

```bash
como usuario1 $GW/api/reservas/mias
```

**Todas las reservas (ADMIN)**, con o sin filtro por estado (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`, `CANCELADA`)

```bash
como admin1 $GW/api/reservas
```

```bash
como admin1 "$GW/api/reservas?estado=CANCELADA"
```

### 6.8. Concurrencia (RN15)

Dos usuarios piden **el mismo espacio y la misma franja al mismo tiempo**. Solo uno se guarda; el otro recibe `409`. Los tokens se piden antes, para que los dos pedidos salgan juntos:

```bash
T1=$(token usuario1); T2=$(token usuario2)
CUERPO="{\"espacioId\": 2, \"inicio\": \"${DIA}T16:00:00\", \"fin\": \"${DIA}T17:00:00\", \"asistentes\": 5, \"actividad\": \"CLASE\"}"
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $T1" -H "$JSON" -d "$CUERPO" &
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $T2" -H "$JSON" -d "$CUERPO" &
wait
```

Una respuesta es `201` y la otra `409`. El mensaje del `409` depende de quién llegó primero: *"El espacio ya está reservado en esa franja"* o *"Otro usuario modificó ese espacio o esa reserva al mismo tiempo"*.

### 6.9. Resiliencia: ms-espacios caído

Apagá el contenedor de ms-espacios:

```bash
docker compose stop ms-espacios
```

**Crear una reserva** → `503` *"El servicio de espacios no está disponible"* (lo responde ms-reservas, que no pudo validar el espacio)

```bash
como usuario1 -X POST $GW/api/reservas -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T10:00:00\", \"fin\": \"${DIA}T11:00:00\",
  \"asistentes\": 5, \"actividad\": \"CLASE\"
}"
```

**Buscar disponibles** → `503`

```bash
como usuario1 "$GW/api/reservas/disponibilidad?inicio=${DIA}T10:00:00&fin=${DIA}T11:00:00&asistentes=5&actividad=CLASE"
```

**Mis reservas** → `200`: listar y cancelar siguen funcionando, porque no necesitan a ms-espacios

```bash
como usuario1 $GW/api/reservas/mias
```

**Listar espacios** → `503` *"El servicio no está disponible..."*: este pedido va directo del gateway a ms-espacios, y el `503` lo responde el gateway (`ServicioCaidoFilter`), con el mismo `ApiResponse`. Tarda unos 2 segundos: es lo que el gateway espera para conectarse (T70)

```bash
como usuario1 $GW/api/espacios
```

**Mirá los logs** (`docker compose logs -f ...` de la sección 3). Por cada `503` de ms-reservas aparecen dos líneas `WARN`: la primera dice qué falló de verdad (lo que el usuario no ve) y la segunda, qué se le respondió. El de listar espacios lo escribe el gateway:

```
ms-reservas-1  | WARN ... EspaciosClient         : ms-espacios no respondió al validar el espacio 2: I/O error on GET request for "http://ms-espacios:8081/api/espacios/2/validacion": ms-espacios
ms-reservas-1  | WARN ... GlobalExceptionHandler : 503 El servicio de espacios no está disponible
api-gateway-1  | WARN ... ServicioCaidoFilter    : 503 /api/espacios no responde: connection timed out after 2000 ms: ms-espacios/172.18.0.3:8081
```

Después volvé a levantarlo; conserva sus datos, porque `stop` no borra el contenedor:

```bash
docker compose start ms-espacios
```

### 6.10. Seguridad: todo por el gateway

Hasta acá usamos `como`, que siempre manda un token válido. Esta sección muestra qué pasa cuando no: sin token, con uno adulterado, o con un rol que no alcanza. Es la tabla 7.4 del plan. Usamos los tokens en variables para poder modificarlos.

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

**usuario1 reserva, y además manda un `X-Usuario: usuario2` falso** → `201`, y en la respuesta dice `"usuario":"usuario1"`: el gateway borró el header del cliente y puso el del token

```bash
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $U1" -H "X-Usuario: usuario2" -H "$JSON" -d "{
  \"espacioId\": 2, \"inicio\": \"${DIA}T08:00:00\", \"fin\": \"${DIA}T09:00:00\",
  \"asistentes\": 20, \"actividad\": \"CLASE\"
}"
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

**usuario1 intenta aprobar un evento** → `403` (aprobar es solo ADMIN; lo corta el gateway, sin llegar a ms-reservas)

```bash
pedir -X PATCH $GW/api/reservas/2/aprobar -H "Authorization: Bearer $U1"
```

**usuario2 intenta cancelar una reserva de usuario1** → `403`, pero **este 403 lo da ms-reservas** (RN7), no el gateway: usuario2 tiene el rol USUARIO y la ruta está permitida. El gateway le pasó `X-Usuario: usuario2` y ms-reservas vio que la reserva no es suya.

```bash
pedir -X PATCH $GW/api/reservas/3/cancelar -H "Authorization: Bearer $U2"
```

**Una ruta que no existe, con token válido** → `403` (la corta `denyAll()`: nunca llega a ningún servicio)

```bash
pedir $GW/api/otra -H "Authorization: Bearer $U1"
```

**Los microservicios no se pueden llamar directo:** no publican puertos, así que desde afuera no hay forma de saltearse el gateway (trusted zone). El `000` de curl quiere decir que ni siquiera se pudo conectar:

```bash
curl -s -o /dev/null -w "%{http_code}\n" --max-time 3 http://localhost:8081/api/espacios
```

```bash
curl -s -o /dev/null -w "%{http_code}\n" --max-time 3 http://localhost:8082/api/reservas/mias
```

Qué señalar en la presentación:

- **401 vs. 403:** sin token o con un token inválido, 401; con un token válido pero sin el rol, 403. Los dos los decide el gateway, antes de rutear, y salen sin cuerpo.
- **El 403 de RN7 es distinto:** ese sí tiene el `ApiResponse` con el mensaje, porque lo da ms-reservas.
- **Token vencido:** si esperás más de 5 minutos y repetís cualquier pedido con el mismo `$U1`, da `401`.

### 6.11. Logs: qué mirar

Mientras corrés la demo, `docker compose logs -f ms-espacios ms-reservas api-gateway` (sección 3) muestra qué pasó con cada pedido. Cada línea empieza con el contenedor y sigue con el formato de Spring: fecha y hora, **nivel**, `[servicio]`, la clase que escribió y el mensaje.

```
ms-reservas-1  | 2026-11-04T09:22:40.881-03:00  INFO 1 --- [ms-reservas] [nio-8082-exec-6] a.e.u.f.b.c.r.services.ReservaService    : Reserva 1 creada por usuario1: espacio 1 (Aula 101), 2026-11-06T10:00 a 2026-11-06T12:00, CONFIRMADA
```

> La hora sale en hora de Argentina (`-03:00`) porque el compose les pone `TZ: America/Argentina/Cordoba` (T68). Sin eso, los contenedores corren en UTC.

| Qué hiciste en la demo | Contenedor | Qué aparece |
|---|---|---|
| Crear una reserva (6.5) | ms-reservas | `INFO ... Reserva 1 creada por usuario1: espacio 1 (Aula 101), ..., CONFIRMADA` |
| Aprobar, rechazar o cancelar (6.7) | ms-reservas | `INFO ... Reserva 2 aprobada por bedelía` · `Reserva 4 rechazada por bedelía` · `Reserva 1 cancelada por usuario1` |
| Romper una regla (6.6) | ms-reservas | `INFO ... GlobalExceptionHandler : 422 Las salas se reservan por un máximo de 2 horas` (o `409 El espacio ya está reservado en esa franja`, `404 No existe el espacio 99`…) |
| Cancelar una reserva ajena (6.7 y 6.10) | ms-reservas | `INFO ... 403 La reserva no es tuya` |
| Aprobar dos veces (6.7) | ms-reservas | `INFO ... 409 Una reserva CONFIRMADA no puede pasar a CONFIRMADA` |
| Horario al revés o espacio inexistente (6.2) | ms-espacios | `INFO ... 422 La hora de apertura debe ser anterior a la de cierre` · `404 No existe el espacio 99` |
| ms-espacios caído (6.9) | ms-reservas y api-gateway | `WARN ... ms-espacios no respondió ...`, `WARN ... 503 El servicio de espacios no está disponible` y `WARN ... ServicioCaidoFilter : 503 /api/espacios no responde` |
| Open-Meteo caído (modo local, sección 10) | ms-espacios | `WARN ... Open-Meteo no respondió para (-31.44, -64.19): ...` y, en una búsqueda, `WARN ... Se omite 'Cancha de fútbol' de la búsqueda: ...` |
| Choque por concurrencia (6.8) | ms-reservas | `INFO ... 409 El espacio ya está reservado en esa franja`, o `INFO ... 409 choque de concurrencia: ObjectOptimisticLockingFailureException` si los dos pedidos pasaron la cadena a la vez |

Qué significa cada nivel:

- **INFO:** el sistema funcionando normalmente, incluso cuando rechaza un pedido por una regla.
- **WARN:** falló algo de lo que dependemos (ms-espacios u Open-Meteo). El usuario recibió un `503` claro.
- **ERROR:** un bug nuestro (el usuario recibió `500`), con el stack trace completo debajo. En la demo no tiene que aparecer ninguno.

Lo que **no** deja log: los `400` (formato inválido) y los `401`/`403` del gateway. Esos se ven solo en la respuesta.

> **Al arrancar aparecen algunos avisos que son normales:** dos `WARN ... SpringDocAppInitializer` por servicio (Swagger está habilitado) y unas líneas `WARNING: java.lang.System::loadLibrary` del gateway (Netty, con Java 25). No son errores de nuestro código.

**Ver los logs de un solo servicio, o solo las fallas:**

```bash
docker compose logs -f ms-reservas
```

```bash
docker compose logs ms-espacios ms-reservas api-gateway | grep -E "WARN|ERROR"
```

---

## 7. Resumen de códigos HTTP

| Código | Cuándo | Ejemplo en la demo |
|---|---|---|
| 200 | Consulta o cambio de estado correcto | Aprobar, cancelar, listar |
| 201 | Se creó algo | Crear espacio o reserva |
| 400 | El pedido está mal formado | Campos vacíos, una actividad que no existe; en Keycloak, contraseña incorrecta (`invalid_grant`) |
| 401 | No sé quién sos | Por el gateway, sin token o con un token modificado o vencido |
| 403 | Sé quién sos, pero no podés | Por el gateway, usuario1 crea un espacio (rol); en ms-reservas, cancelar una reserva ajena (RN7) |
| 404 | No existe | Espacio 99 |
| 409 | Choca con el estado actual | Superposición, aprobar dos veces |
| 422 | Viola una regla de negocio | Capacidad, horario, sala por 3 h |
| 503 | Una dependencia no responde | ms-espacios caído (lo responde ms-reservas, o el gateway si la ruta va directo a ms-espacios), Open-Meteo sin responder |

> **500 vs. 503:** el `503` dice "ahora no puedo, reintentá más tarde": falla algo de lo que dependemos. El `500` dice "algo se rompió adentro": es un bug nuestro. En la demo nunca tiene que aparecer un `500`.

---

## 8. Apagar todo

| Comando | Qué hace | ¿Se pierden los datos? |
|---|---|---|
| `docker compose stop` | Apaga los contenedores sin borrarlos | No: `docker compose start` los levanta como estaban |
| `docker compose down` | Apaga y **borra** los contenedores | Sí: las bases H2 están dentro de los contenedores (T69). Al volver a levantar, ms-espacios carga los 5 espacios de prueba |

```bash
docker compose down
```

> Las imágenes quedan guardadas: el próximo `docker compose up -d` arranca enseguida sin volver a armarlas. Solo hace falta `--build` (y antes `build.sh`) si cambió el código.

---

## 9. Problemas comunes

**`sh : El término 'sh' no se reconoce...`**
Estás en PowerShell. Corré `build.sh` desde Git Bash, o con `& "C:\Program Files\Git\bin\bash.exe" build.sh` (sección 2).

**`build.sh` falla en el `clean` o no actualiza el JAR**
Algún servicio está corriendo en modo local (IDE o terminal) y Windows tiene bloqueado su JAR. Frenalo y volvé a correr `build.sh`.

**Cambié código y en Docker sigue igual**
Faltó `build.sh` antes de `docker compose up -d --build`. La imagen copia el JAR de `target/`; no compila nada.

**`Could not find artifact ar.edu.utn.frc.bda:campus-commons`**
No está instalado el JAR común. `build.sh` lo instala primero; si compilás un proyecto suelto, antes corré `mvn -f campus-commons/pom.xml clean install`.

**`JAVA_HOME is not defined correctly` o `release version 25 not supported`**
Falta Java 25 o la terminal se abrió antes de instalarlo. Cerrá y volvé a abrir la terminal y revisá `java -version`.

**`Bind for 0.0.0.0:8080 failed: port is already allocated`**
Otro programa o contenedor usa ese puerto, por ejemplo el gateway corriendo en modo local. Para ver los contenedores que están corriendo:

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}"
```

Pará el que moleste con `docker stop <nombre>`, o frená el servicio local con Ctrl+C.

**Recién levantado, todo da `503` o `401`**
Los contenedores están `Up` pero Spring o Keycloak todavía están arrancando. Esperá a que aparezcan las líneas `Started` (sección 3).

**El token da error o la consulta dice que expiró**
El token dura 5 minutos. `como` pide uno nuevo en cada pedido; si usás `$U1`, `$U2` o `$AD`, volvé a pedirlos (6.10).

**Keycloak dice "Account is not fully set up"**
A un usuario le faltan nombre, apellido o email. Revisá `keycloak/realm-campus.json` y reiniciá con `docker compose down` y `docker compose up -d`.

**La cancha da `503` o `422` por lluvia**
El `503` es porque no hay internet y no se puede consultar Open-Meteo. El `422` con *"Pronóstico de lluvia"* es la RN4 funcionando: probá otro día.

---

## 10. Modo local (para desarrollar)

Para correr los servicios desde el IDE o desde una terminal, por ejemplo para depurar, usar **Swagger** o probar la falla de Open-Meteo. En este modo los microservicios **sí** publican sus puertos y se los puede llamar directo, mandando el usuario a mano en `X-Usuario`.

**10.1. Dejar solo Keycloak en Docker.** Si está el compose completo levantado, frená los otros tres para liberar los puertos:

```bash
docker compose stop api-gateway ms-reservas ms-espacios
```

O, si no estaba levantado nada:

```bash
docker compose up -d keycloak
```

**10.2. Levantar cada servicio en su propia terminal de Git Bash** (quedan corriendo). Primero ms-espacios, después ms-reservas (le consulta a ms-espacios) y el gateway. Antes, `build.sh` o al menos `mvn -f campus-commons/pom.xml clean install`.

```bash
cd ms-espacios && ./mvnw spring-boot:run
```

```bash
cd ms-reservas && ./mvnw spring-boot:run
```

```bash
cd api-gateway && ./mvnw spring-boot:run
```

Están listos cuando el log muestra `Started MsEspaciosApplication` / `Started MsReservasApplication` / `Started ApiGatewayApplication` (el gateway además muestra `Netty started on port 8080`: es reactivo y corre sobre Netty, no sobre Tomcat). Los logs salen directo en cada terminal.

> Por defecto cada servicio busca a los demás en `localhost` (`MS_ESPACIOS_URL`, `MS_RESERVAS_URL` y `JWK_SET_URI` tienen ese valor por defecto). En Docker, el compose los cambia por los nombres de los contenedores.

Las bases H2 quedan en `ms-espacios/data/` y `ms-reservas/data/` (no se suben a Git). Para empezar de cero, con los servicios apagados:

```bash
rm -rf ms-espacios/data ms-reservas/data
```

**10.3. Swagger** (documentación interactiva de la API):

- ms-espacios: <http://localhost:8081/swagger-ui.html>
- ms-reservas: <http://localhost:8082/swagger-ui.html>

**10.4. Llamar directo a un microservicio**, sin gateway ni token. Todos los pedidos de la sección 6 funcionan igual cambiando `$GW` por `$ESP` o `$RES` y `como usuarioX` por `pedir ... -H "X-Usuario: usuarioX"`:

```bash
ESP=http://localhost:8081
RES=http://localhost:8082
```

```bash
pedir $RES/api/reservas/mias -H "X-Usuario: usuario1"
```

Sin el header, ms-reservas responde `400` (*"Falta el header X-Usuario"*). Por el gateway eso no puede pasar: siempre lo pone a partir del token.

**10.5. Ver solo las fallas en el log.** Levantá el servicio con `LOG_LEVEL=WARN` (los logs de arranque de Spring no cambian):

```bash
cd ms-reservas && LOG_LEVEL=WARN ./mvnw spring-boot:run
```

> `LOG_LEVEL=DEBUG` no muestra nada extra: hoy nuestro código no tiene logs de nivel DEBUG.

**10.6. Probar la falla de Open-Meteo.** Reiniciá ms-espacios apuntando a una URL que no responde y buscá disponibles para un deporte:

```bash
cd ms-espacios && OPEN_METEO_URL=http://localhost:9 ./mvnw spring-boot:run
```

```bash
pedir "$RES/api/reservas/disponibilidad?inicio=${DIA}T10:00:00&fin=${DIA}T11:00:00&asistentes=10&actividad=DEPORTE"
```

La búsqueda responde `200` sin la cancha, y en la terminal de ms-espacios aparecen los dos `WARN` (*"Open-Meteo no respondió..."* y *"Se omite 'Cancha de fútbol' de la búsqueda..."*). Después volvé a levantar ms-espacios normalmente.

**10.7. Problemas del modo local.**

**`Port 8080/8081/8082 was already in use` o `Web server failed to start`:** el compose completo sigue levantado (10.1) u otro programa usa el puerto. Si no podés liberarlo, levantá ms-espacios en otro puerto y avisale a ms-reservas dónde está:

```bash
cd ms-espacios && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8091
```

```bash
cd ms-reservas && MS_ESPACIOS_URL=http://localhost:8091 ./mvnw spring-boot:run
```

**`Unable to establish loopback connection` al arrancar un microservicio:** algún antivirus o política de Windows bloquea los sockets locales que usa Java en la carpeta temporal. Probá indicarle otra carpeta:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.net.unixdomain.tmpdir=C:/temp"
```

**Apagar:** Ctrl+C en cada terminal, y `docker compose down` para Keycloak.
