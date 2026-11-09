# CampusReservas: guía de pruebas

Los comandos, en orden, para levantar el sistema y recorrer la demo. Todo entra por el gateway (8080) con el JWT de Keycloak (8180). Al lado de cada comando está lo que tiene que responder. El porqué de cada cosa está en `defensa.md`.

> Los comandos son para **Git Bash** (en VS Code: flecha junto al `+` de la terminal → Git Bash). Requisitos: Java 25, Maven y Docker Desktop.

---

## 1. Levantar

```bash
sh build.sh                       # 4 × BUILD SUCCESS (antes, frená cualquier servicio que tengas corriendo en el IDE)
docker compose up -d --build      # keycloak, ms-espacios, ms-reservas, api-gateway
```

En PowerShell, `sh` no existe: `& "C:\Program Files\Git\bin\bash.exe" build.sh`.

Esperá unos 40 segundos y verificá:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8180/realms/campus      # 200
docker compose logs ms-espacios ms-reservas api-gateway | grep -c Started          # 3
```

En otra terminal, dejá los logs a la vista durante toda la demo:

```bash
docker compose logs -f ms-espacios ms-reservas api-gateway
```

**Datos limpios** (antes de presentar): `docker compose down` y `docker compose up -d`. Las bases viven dentro de los contenedores, así que arranca con los 5 espacios de prueba y sin reservas.

| Usuario / contraseña | Rol |
|---|---|
| `usuario1` / `usuario1` | USUARIO |
| `usuario2` / `usuario2` | USUARIO |
| `admin1` / `admin1` | ADMIN |

Espacios de prueba: **1** Aula 101 (40, proyector) · **2** Aula 102 (60, sin proyector) · **3** Laboratorio 3 (25) · **4** Sala de reuniones (12) · **5** Cancha de fútbol (22).

---

## 2. Preparar la terminal

Pegá este bloque entero en la terminal de pruebas. Si la cerrás, hay que volver a pegarlo.

```bash
GW=http://localhost:8080; KC=http://localhost:8180; JSON='Content-Type: application/json'
pedir() { curl -s -w "\n→ HTTP %{http_code}\n\n" "$@"; }
token() { curl -s -X POST "$KC/realms/campus/protocol/openid-connect/token" -d grant_type=password -d client_id=campus-api -d "username=$1" -d "password=$1" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'; }
# como <usuario> <curl...>: el pedido con un token nuevo de ese usuario
como() { local u=$1; shift; pedir -H "Authorization: Bearer $(token "$u")" "$@"; }
# reservar <usuario> <espacio> <inicio> <fin> <asistentes> <actividad> [equipamiento]
reservar() { como "$1" -X POST $GW/api/reservas -H "$JSON" -d "{\"espacioId\":$2,\"inicio\":\"$3\",\"fin\":\"$4\",\"asistentes\":$5,\"actividad\":\"$6\"${7:+,\"equipamiento\":[\"$7\"]}}"; }
# D: próximo día hábil a 2 o más días · SAB: próximo sábado · MAN: mañana · LEJOS: a 20 días
D=$(date -d "+2 days" +%F); while [ "$(date -d "$D" +%u)" -gt 5 ]; do D=$(date -d "$D +1 day" +%F); done
SAB=$(date -d "next saturday" +%F); MAN=$(date -d "+1 day" +%F); LEJOS=$(date -d "+20 days" +%F)
echo "D=$D SAB=$SAB MAN=$MAN LEJOS=$LEJOS"
```

---

## 3. Demo

### 3.1. Login y JWT

```bash
TOKEN=$(token usuario1); echo "$TOKEN"
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo     # iss, preferred_username, realm_access.roles: ["USUARIO"], exp − iat = 300
pedir -X POST $KC/realms/campus/protocol/openid-connect/token -d grant_type=password -d client_id=campus-api -d username=usuario1 -d password=mal   # 400 invalid_grant
```

### 3.2. Catálogo de espacios

```bash
como usuario1 $GW/api/espacios                    # 200: los 5 espacios
como usuario1 $GW/api/espacios/99                 # 404
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{"nombre":"Aula Magna","tipo":"AULA","capacidad":200,"latitud":-31.44,"longitud":-64.19,"horaApertura":"08:00","horaCierre":"23:00","actividadesPermitidas":["CLASE","EVENTO"]}'   # 201, id 6
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{"nombre":"Aula X","tipo":"AULA","capacidad":10,"latitud":0,"longitud":0,"horaApertura":"20:00","horaCierre":"08:00","actividadesPermitidas":["CLASE"]}'          # 422 horario al revés
como admin1 -X POST $GW/api/espacios -H "$JSON" -d '{"nombre":"","tipo":"AULA","capacidad":0}'    # 400
como admin1 -X DELETE $GW/api/espacios/6          # 200
```

### 3.3. Buscar y reservar

```bash
como usuario1 "$GW/api/reservas/disponibilidad?inicio=${D}T10:00&fin=${D}T12:00&asistentes=20&actividad=CLASE&equipamiento=PROYECTOR"   # 200: Aula 101 y Laboratorio 3
reservar usuario1 1 ${D}T10:00 ${D}T12:00 30 CLASE PROYECTOR    # 201 CONFIRMADA (reserva 1)
reservar usuario2 4 ${D}T14:00 ${D}T15:30 10 EVENTO             # 201 PENDIENTE: los eventos esperan aprobación (reserva 2)
reservar usuario1 5 ${D}T15:00 ${D}T17:00 14 DEPORTE            # 201 si no se pronostica lluvia; 422 si llueve (RN4) (reserva 3)
como usuario1 "$GW/api/reservas/disponibilidad?inicio=${D}T10:00&fin=${D}T12:00&asistentes=20&actividad=CLASE&equipamiento=PROYECTOR"   # 200: ya no aparece el Aula 101
```

### 3.4. Reglas de negocio (todas fallan)

```bash
reservar usuario1 2 ${LEJOS}T10:00 ${LEJOS}T11:00 5 CLASE       # 422 RN1: más de 14 días adelante
reservar usuario1 2 ${D}T10:00 ${D}T10:15 5 CLASE               # 422 RN2: dura 15 minutos
reservar usuario1 2 ${D}T06:00 ${D}T07:00 5 CLASE               # 422 RN3: fuera del horario
reservar usuario1 3 ${D}T10:00 ${D}T12:00 50 CLASE              # 422 RN5: 50 personas en un lugar para 25
reservar usuario2 1 ${D}T11:00 ${D}T13:00 10 CLASE              # 409 RN6: se superpone con la reserva 1
reservar usuario1 2 ${D}T10:00 ${D}T11:00 5 CLASE PROYECTOR     # 422 RN8: el Aula 102 no tiene proyector
reservar usuario1 3 ${D}T15:00 ${D}T16:00 5 DEPORTE             # 422 RN9: deporte en un laboratorio
reservar usuario1 3 ${SAB}T10:00 ${SAB}T12:00 10 CLASE          # 422 RN10: laboratorio un sábado
reservar usuario1 3 ${MAN}T08:00 ${MAN}T09:00 10 CLASE          # 422 RN10: menos de 24 h (después de las 8; si mañana es sábado, por el fin de semana)
reservar usuario1 4 ${D}T09:00 ${D}T12:00 5 CLASE               # 422 RN11: sala por 3 horas
reservar usuario1 5 ${D}T10:00 ${D}T11:30 10 DEPORTE            # 422 RN12: cancha por 90 minutos
reservar usuario1 99 ${D}T10:00 ${D}T11:00 5 CLASE              # 404: el espacio no existe
reservar usuario1 2 ${D}T10:00 ${D}T11:00 5 NADAR               # 400: actividad que no existe
```

### 3.5. Estados

```bash
como admin1 "$GW/api/reservas?estado=PENDIENTE"       # 200: el evento 2
como admin1 -X PATCH $GW/api/reservas/2/aprobar       # 200 CONFIRMADA
como admin1 -X PATCH $GW/api/reservas/2/aprobar       # 409 RN14: no se aprueba dos veces
reservar usuario2 1 ${D}T18:00 ${D}T20:00 30 EVENTO   # 201 PENDIENTE (reserva 4)
como admin1 -X PATCH $GW/api/reservas/4/rechazar      # 200 RECHAZADA
como usuario2 -X PATCH $GW/api/reservas/1/cancelar    # 403 RN7: no es suya
como usuario1 -X PATCH $GW/api/reservas/1/cancelar    # 200 CANCELADA
como usuario1 -X PATCH $GW/api/reservas/1/cancelar    # 409: ya está cancelada
como usuario1 $GW/api/reservas/mias                   # 200: las de usuario1
como admin1 $GW/api/reservas                          # 200: todas
```

### 3.6. Concurrencia (RN15)

Dos usuarios piden lo mismo a la vez:

```bash
T1=$(token usuario1); T2=$(token usuario2)
CUERPO="{\"espacioId\":2,\"inicio\":\"${D}T16:00\",\"fin\":\"${D}T17:00\",\"asistentes\":5,\"actividad\":\"CLASE\"}"
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $T1" -H "$JSON" -d "$CUERPO" &
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $T2" -H "$JSON" -d "$CUERPO" &
wait                                                   # uno 201 y el otro 409
```

### 3.7. Seguridad del gateway

```bash
U1=$(token usuario1); U2=$(token usuario2); AD=$(token admin1)     # duran 5 minutos
pedir $GW/api/espacios                                             # 401: sin token
pedir $GW/api/espacios -H "Authorization: Bearer ${U1%?????}xxxxx" # 401: token adulterado
pedir -X POST $GW/api/espacios -H "Authorization: Bearer $U1" -H "$JSON" -d '{}'    # 403: crear espacios es de ADMIN
pedir $GW/api/reservas -H "Authorization: Bearer $U1"              # 403: listar todas es de ADMIN
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $AD" -H "$JSON" -d '{}'    # 403: reservar es de USUARIO
pedir $GW/api/otra -H "Authorization: Bearer $U1"                  # 403: ruta inexistente (denyAll)
pedir -X POST $GW/api/reservas -H "Authorization: Bearer $U1" -H "X-Usuario: usuario2" -H "$JSON" -d "{\"espacioId\":2,\"inicio\":\"${D}T08:00\",\"fin\":\"${D}T09:00\",\"asistentes\":20,\"actividad\":\"CLASE\"}"   # 201 con "usuario":"usuario1": el gateway ignora el X-Usuario falso
curl -s -o /dev/null -w "%{http_code}\n" --max-time 3 http://localhost:8081/api/espacios   # 000: los microservicios no se ven desde afuera
```

Los 401 y 403 de rol los da el gateway, sin cuerpo. El 403 de RN7 (3.5) lo da ms-reservas, con su mensaje.

### 3.8. Resiliencia: ms-espacios caído

```bash
docker compose stop ms-espacios
reservar usuario1 2 ${D}T10:00 ${D}T11:00 5 CLASE                  # 503: no se puede validar el espacio
como usuario1 $GW/api/reservas/mias                                # 200: listar no necesita a ms-espacios
como usuario1 $GW/api/espacios                                     # 503 en ~2 s: lo responde el gateway
docker compose start ms-espacios
```

En los logs aparecen los `WARN` con el error real: `EspaciosClient : ms-espacios no respondió...` y `ServicioCaidoFilter : 503 /api/espacios no responde...`.

### 3.9. Logs

En la terminal de logs, cada pedido de la demo deja su línea:

| Nivel | Cuándo | Ejemplo |
|---|---|---|
| INFO | Acciones y reglas que rechazan un pedido | `Reserva 1 creada por usuario1...` · `422 Las salas se reservan por un máximo de 2 horas` |
| WARN | Falla una dependencia (ms-espacios, Open-Meteo) | `503 El servicio de espacios no está disponible` |
| ERROR | Un bug nuestro (500). No tiene que aparecer nunca | — |

Los 400 y los 401/403 del gateway no dejan log. Al arrancar aparecen `WARN` de SpringDoc y `WARNING` de Netty: son normales.

```bash
docker compose logs ms-espacios ms-reservas api-gateway | grep -E " WARN | ERROR "    # solo las fallas
```

---

## 4. Postman

La misma demo, con un test por pedido: `Docs/CampusReservas.postman_collection.json` y `Docs/CampusReservas.postman_environment.json`.

1. Postman → **Import** → los dos archivos.
2. Arriba a la derecha, elegí el environment **CampusReservas (Docker)**.
3. Clic derecho en la colección → **Run collection** → destildá la carpeta 8 → **Run**: 67 tests en verde.

Los tokens se renuevan solos y las fechas se calculan solas. La carpeta 8 (resiliencia) se corre aparte, con `docker compose stop ms-espacios` antes y `docker compose start ms-espacios` después. La concurrencia no está en Postman, porque manda los pedidos de a uno: va con `curl` (3.6).

---

## 5. Apagar

```bash
docker compose stop     # pausa: "docker compose start" sigue con los mismos datos
docker compose down     # borra los contenedores: la próxima vez arranca con la base vacía
```

---

## 6. Modo local (Swagger y falla de Open-Meteo)

En Docker los microservicios no publican puertos, así que Swagger y las pruebas que cambian la configuración se hacen corriendo los servicios a mano:

```bash
docker compose stop api-gateway ms-reservas ms-espacios    # deja solo Keycloak
cd ms-espacios && ./mvnw spring-boot:run                   # una terminal por servicio, en este orden
cd ms-reservas && ./mvnw spring-boot:run
cd api-gateway && ./mvnw spring-boot:run
```

- **Swagger:** <http://localhost:8081/swagger-ui.html> y <http://localhost:8082/swagger-ui.html>.
- **Llamarlos directo**, sin gateway: `pedir http://localhost:8082/api/reservas/mias -H "X-Usuario: usuario1"`.
- **Open-Meteo caído:** levantá ms-espacios con `OPEN_METEO_URL=http://localhost:9 ./mvnw spring-boot:run` y buscá disponibles para `DEPORTE`. Responde 200 sin la cancha, y en el log aparece `WARN ... Se omite 'Cancha de fútbol'`. Reservar la cancha da 503.
- **Solo las fallas en el log:** `LOG_LEVEL=WARN ./mvnw spring-boot:run`.
- **Volver a Docker:** Ctrl+C en cada terminal y `docker compose up -d`.

---

## 7. Problemas comunes

| Síntoma | Solución |
|---|---|
| `sh: El término 'sh' no se reconoce` | Estás en PowerShell: usá Git Bash (o el comando de la sección 1) |
| `build.sh` falla en `clean` | Hay un servicio corriendo en el IDE que bloquea su JAR: frenalo |
| Cambié el código y en Docker sigue igual | Faltó `sh build.sh` antes de `docker compose up -d --build` |
| `Could not find artifact ...campus-commons` | Corré `build.sh`, que instala primero el JAR común |
| `port is already allocated` / `Port 8080 was already in use` | Docker y el modo local a la vez: frená uno de los dos |
| Recién levantado, todo da 503 o 401 | Todavía están arrancando: esperá las 3 líneas `Started` |
| Con `$U1`, `$U2` o `$AD` empieza a dar 401 | Vencieron (5 minutos): volvé a pedirlos (3.7). `como` y `reservar` piden uno nuevo cada vez |
| La cancha da 422 o 503 | 422 = pronóstico de lluvia (RN4, probá otro día); 503 = no hay internet |
