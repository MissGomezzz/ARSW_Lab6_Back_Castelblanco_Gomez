## Laboratorio 6 - Blueprints Real Time Sockets

## Nota 
La documentación y video del laboratorio 6 se encuentra en este mismo repositorio, al final del README.  

Para realizar este laboratorio, se realizó un fork del laboratorio 4 (backend), y un fork del laboratorio 5 (fontend). Es decir, la solución del laboratorio 6 se maneja en los dos repositorios mencionados. 

- BACKEND: este mismo repositorio

- [FRONTEND](https://github.com/Queruubin/Lab6_Front_Castelblanco_Gomez)

## Autores:

- Samuel Castelblanco 
- Ángela Gómez
---

Este laboratorio extiende la **Parte 1** ([Lab_P1_BluePrints_Java21_API](https://github.com/DECSIS-ECI/Lab_P1_BluePrints_Java21_API)) agregando **seguridad a la API** usando **Spring Boot 3, Java 21 y JWT (OAuth 2.0)**.  
El API se convierte en un **Resource Server** protegido por tokens Bearer firmados con **RS256**.  
Incluye un endpoint didáctico `/auth/login` que emite el token para facilitar las pruebas.

---

## Objetivos
- Implementar seguridad en servicios REST usando **OAuth2 Resource Server**.
- Configurar emisión y validación de **JWT**.
- Proteger endpoints con **roles y scopes** (`blueprints.read`, `blueprints.write`).
- Integrar la documentación de seguridad en **Swagger/OpenAPI**.

---

## Requisitos
- JDK 21
- Maven 3.9+
- Git

---

## Ejecución del proyecto
1. Clonar o descomprimir el proyecto:
   ```bash
   git clone https://github.com/DECSIS-ECI/Lab_P2_BluePrints_Java21_API_Security_JWT.git
   cd Lab_P2_BluePrints_Java21_API_Security_JWT
   ```
   ó si el profesor entrega el `.zip`, descomprimirlo y entrar en la carpeta.

2. Ejecutar con Maven:
   ```bash
   mvn -q -DskipTests spring-boot:run
   ```

3. Verificar que la aplicación levante en `http://localhost:8080`.

---

## Endpoints principales

### 1. Login (emite token)
```
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "username": "student",
  "password": "student123"
}
```
Respuesta:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### 2. Consultar blueprints (requiere scope `blueprints.read`)
```
GET http://localhost:8080/api/v1/blueprints
Authorization: Bearer <ACCESS_TOKEN>
```

### 3. Crear blueprint (requiere scope `blueprints.write`)
```
POST http://localhost:8080/api/v1/blueprints
Authorization: Bearer <ACCESS_TOKEN>
Content-Type: application/json

{
  "author": "samuel",
  "name": "office",
  "points": [{"x": 1, "y": 1}, {"x": 2, "y": 2}]
}
```

### 4. Blueprints de un autor con total de puntos (requiere scope `blueprints.read`)
```
GET http://localhost:8080/api/v1/blueprints?author=john
Authorization: Bearer <ACCESS_TOKEN>
```
Respuesta (`404` si el autor no tiene planos):
```json
{ "code": 200, "message": "execute ok",
  "data": { "author": "john", "totalPoints": 11, "blueprints": [ ... ] } }
```

Resto del CRUD: `GET /api/v1/blueprints/{author}/{name}`, `PUT /api/v1/blueprints/{author}/{name}`
(reemplaza puntos), `PUT /api/v1/blueprints/{author}/{name}/points` (agrega un punto `{x,y}`, ambos `>= 0`)
y `DELETE /api/v1/blueprints/{author}/{name}`.

---

## Tiempo real (STOMP)

Colaboración en vivo sobre STOMP con WebSocket nativo (sin SockJS).

| Elemento | Valor |
|---|---|
| Endpoint WebSocket | `ws://localhost:8080/ws-blueprints` |
| Prefijo de aplicación (cliente → servidor) | `/app` (publicar en `/app/draw`) |
| Broker simple | `/topic`, `/queue` |
| Prefijo de usuario | `/user` |
| Tópico por plano | `/topic/blueprints.{author}.{name}` (ej. `/topic/blueprints.john.house`) |
| Cola de errores (solo el emisor) | `/user/queue/errors` |

**Autenticación en CONNECT.** El handshake HTTP es público, pero el frame `CONNECT` debe incluir el
header nativo `Authorization: Bearer <ACCESS_TOKEN>` (el mismo JWT de `/auth/login`). Sin token o con
token inválido el servidor responde `ERROR` y cierra la sesión. Publicar en `/app/draw` exige el scope
`blueprints.write`.

**Payload de entrada** (`SEND /app/draw`, `DrawEvent`):
```json
{ "author": "john", "name": "house", "point": { "x": 10, "y": 20 }, "clientId": "tab-123" }
```
`author` y `name` obligatorios, `point` obligatorio con `x, y >= 0`; `clientId` es opcional.
El punto se persiste (igual que `PUT .../points`) y luego se difunde.

**Payload de salida** (`/topic/blueprints.{author}.{name}`, `BlueprintUpdate`):
```json
{ "author": "john", "name": "house", "points": [ { "x": 10, "y": 20 } ], "clientId": "tab-123" }
```
`points` contiene solo los puntos nuevos (mismo formato que la guía de Socket.IO). El emisor puede
ignorar su propio eco comparando `clientId`.

**Errores** (validación o plano inexistente) llegan solo al emisor en `/user/queue/errors`:
```json
{ "message": "point.x: x must be >= 0" }
```

Ejemplo con `@stomp/stompjs`:
```js
const client = new Client({
  brokerURL: 'ws://localhost:8080/ws-blueprints',
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => {
    client.subscribe(`/topic/blueprints.${author}.${name}`, (m) => { /* append JSON.parse(m.body).points */ })
    client.subscribe('/user/queue/errors', (m) => console.warn(JSON.parse(m.body).message))
  },
})
client.activate()
client.publish({ destination: '/app/draw', body: JSON.stringify({ author, name, point: { x, y }, clientId }) })
```

**Configuración.** Los orígenes permitidos (CORS REST y handshake WebSocket) salen de
`blueprints.cors.allowed-origins` en `application.yml` (por defecto `http://localhost:5173`). En
producción deben restringirse al origen real del front, p. ej. con la variable de entorno
`BLUEPRINTS_CORS_ALLOWED_ORIGINS=https://mi-front.example.com`.

**Observabilidad.** Se registran (SLF4J) las escrituras REST, cada evento de dibujo y el ciclo de vida
de las sesiones STOMP (connect, subscribe, unsubscribe, disconnect). Health check público en
`GET /actuator/health` (e info en `GET /actuator/info`).

---

## Swagger UI
- URL: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- Pulsa **Authorize**, ingresa el token en el formato:
  ```
  Bearer eyJhbGciOi...
  ```

---

## Estructura del proyecto
```
src/main/java/co/edu/eci/blueprints/
  ├── api/BlueprintsAPIController.java   # Endpoints protegidos del P1 (/api/v1/blueprints)
  ├── api/GlobalExceptionHandler.java    # Traducción de errores a ApiResponse
  ├── auth/AuthController.java           # Login didáctico para emitir tokens
  ├── config/OpenApiConfig.java          # Configuración Swagger + JWT
  ├── config/PostgresDataSourceConfig.java # DataSource (perfil "postgres")
  ├── dto/                               # ApiResponse, NewBlueprintRequest
  ├── filters/                           # Identity, Redundancy, Undersampling (por perfiles)
  ├── model/                             # Blueprint, Point
  ├── persistence/                       # In-memory (default) y PostgreSQL (JSONB)
  ├── services/BlueprintsServices.java
  └── security/
       ├── SecurityConfig.java
       ├── MethodSecurityConfig.java
       ├── JwtKeyProvider.java
       ├── InMemoryUserService.java
       └── RsaKeyProperties.java
src/main/resources/
  ├── application.yml
  ├── application-postgres.properties
  └── schema.sql
```

---

## Actividades propuestas
1. Revisar el código de configuración de seguridad (`SecurityConfig`) e identificar cómo se definen los endpoints públicos y protegidos.
2. Explorar el flujo de login y analizar las claims del JWT emitido.
3. Extender los scopes (`blueprints.read`, `blueprints.write`) para controlar otros endpoints de la API, del laboratorio P1 trabajado.
4. Modificar el tiempo de expiración del token y observar el efecto.
5. Documentar en Swagger los endpoints de autenticación y de negocio.

---

## Lecturas recomendadas
- [Spring Security Reference – OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Spring Boot – Securing Web Applications](https://spring.io/guides/gs/securing-web/)
- [JSON Web Tokens – jwt.io](https://jwt.io/introduction)

---

## Licencia
Proyecto educativo con fines académicos – Escuela Colombiana de Ingeniería Julio Garavito.

---

## Respuestas

### Migración del laboratorio P1
Se migró toda la funcionalidad del laboratorio anterior al paquete `co.edu.eci.blueprints`: el modelo (`Blueprint`, `Point`), los DTOs (`ApiResponse`, `NewBlueprintRequest`), los filtros (identity, redundancy, undersampling por perfiles de Spring), la capa de persistencia (in-memory por defecto y PostgreSQL con JDBC/JSONB bajo el perfil `postgres`, con `docker-compose.yml` y `schema.sql`), el servicio `BlueprintsServices`, el controlador REST `api/BlueprintsAPIController` (`/api/v1/blueprints`) con su manejo global de errores y la documentación Swagger/OpenAPI. El controlador de demostración `BlueprintController` se reemplazó por el controlador real del P1.

### Actividad 1 — Endpoints públicos y protegidos en `SecurityConfig`
En `SecurityConfig.filterChain` se definen las reglas con `authorizeHttpRequests`:
- **Públicos** (`permitAll`): `/auth/login`, `/actuator/health` y la documentación (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`).
- **Protegidos**: todo `/api/**` exige un JWT válido con alguno de los scopes `blueprints.read` o `blueprints.write` (`hasAnyAuthority("SCOPE_...")`), y cualquier otra ruta cae en `anyRequest().authenticated()`. La validación del token la hace el `oauth2ResourceServer` con el `JwtDecoder` basado en la llave pública RSA.

### Actividad 2 — Flujo de login y claims del JWT
`POST /auth/login` valida las credenciales contra `InMemoryUserService` (BCrypt). Si son válidas, `AuthController` construye un `JwtClaimsSet` y lo firma con RS256 usando la llave privada generada por `JwtKeyProvider`. Las claims del token emitido son:
- `iss`: `https://decsis-eci/blueprints` (configurado en `application.yml`).
- `sub`: el username autenticado (p. ej. `student`).
- `iat` / `exp`: emisión y expiración (TTL de 3600 s, `blueprints.security.token-ttl-seconds`).
- `scope`: `blueprints.read blueprints.write`, que Spring convierte en las authorities `SCOPE_blueprints.read` y `SCOPE_blueprints.write`.

### Actividad 3 — Scopes extendidos a los endpoints del P1
Los endpoints migrados del P1 quedaron protegidos con `@PreAuthorize` (habilitado por `MethodSecurityConfig`):
- Lecturas con `SCOPE_blueprints.read`: `GET /api/v1/blueprints`, `GET /api/v1/blueprints/{author}` y `GET /api/v1/blueprints/{author}/{bpname}`.
- Escrituras con `SCOPE_blueprints.write`: `POST /api/v1/blueprints` y `PUT /api/v1/blueprints/{author}/{bpname}/points`.

Con esto, un token que solo tenga el scope de lectura puede consultar planos pero recibe `403 Forbidden` al intentar crear o modificar.

### Actividad 4 - Tiempo de expiración de tokens

Primero se entró al `application.yml`, el cual tiene en la sección de blueprints la propiedad `token-ttl-seconds: 3600`. Su valor se cambió a `20`, con lo que el time-to-live del token pasó de una hora a 20 segundos.

Después se entró a Swagger y con las credenciales establecidas en el laboratorio nos autenticamos: 

![Swagger auth example](/src/docs/img/auth-controller.png)

Este nos regresó la respuesta 200 junto con un token de verificación. 

![Initial GET response](/src/docs/img/initial-get.png)

Para confirmar que el tiempo del token es correcto, este token luego fue copiado y pegado en la página [jwt.io](https://www.jwt.io/), la cual procesó el token otorgado, y en la sección de JWT Decoder pudimos confirmar que efectivamente el tiempo de vida del token es de 20 segundos. 

![jwt.io answer](/src/docs/img/jwt-io.png)

**Nota**: se debe restar el valor de exp con el iat. En este caso, la diferencia es de 20. 

Ahora bien, para observar el efecto que tiene el cambio del ttl del token, se utilizó el mismo token que se obtuvo del ejercicio anterior: este se pegó en el espacio indicado después de darle click al botón Authorize en Swagger. Este luego mostró el candado verde cerrado, lo que indica que sí funcionó ese proceso de autenticación. 

Después de esto, se ejecutó el GET /api/v1/blueprints, en donde obtenemos la siguiente respuesta: 

![answer before 20 seconds](/src/docs/img/answer-before-20.png)

Todo esto se realizó apenas nos autenticamos con el access token en Swagger, antes de los 20 segundos. 

Pasados los 20 segundos, se volvió a ejecutar el GET /api/v1/blueprints, el cual nos devolvió el siguiente resultado:

![answer after 20 seconds](/src/docs/img/answer-after-20.png)

Acá es posible observar que la razón de por qué ya no es válido el token access es porque expiró, como se indica en las siguientes líneas:

```
error_description="An error occurred while attempting to decode the Jwt: Jwt expired at 2026-09-17T00:04:00Z"
```

Para volver a los parámetros originales del TTL, se volvió a cambiar el valor del TTL del token en el `application.yml` a 3600. 


### Actividad 5 - Documentación de Swagger con endpoints de autenticación y negocio 

Para complementar la documentación de Swagger con los elementos de autenticación y negocio, se trabajaron con las clases de `BlueprintsAPIController.java` y `AuthController.java`. 

Actualmente, la clase `BlueprintsAPIController.java` ya cuenta con su documentación completa, incluidos los `@Tag`, `@Operation`, `@ApiResponses` y `@SecurityRequirement` en cada endpoint. 

Ahora bien, en el `AuthController.java` se agregaron algunas líneas de código con sus imports y sus anotaciones. 

Se usó @Tag para agrupar los endpoints entre "Autenticación" y "Blueprints", junto con su scope y códigos de respuesta correspondientes. 

Acá se cambió el endpoint /auth/login para que fuera un endpoint público (se le quitó el candado global de bearer-jwt), pues la idea es que los usuarios puedan hacer login sin necesidad de un access token; no tiene sentido autenticarse antes de loguearse. 

Después de realizar esa modificación en el código, se ven los siguientes cambios.

Antes del cambio en el controlador de autenticación: 

![before-change-lock](/src/docs/img/auth-login-with-lock.png)

Después del cambio en el controlador de autenticación: 

![after-change-lock](/src/docs/img/auth-login.without-lock.png)

Acá se evidencia como el endpoint /auth/login ahora es público, a diferencia de los demás endpoints que sí muestran un candado al lado. 

---

## RESPUESTAS LABORATORIO 6 BluePrints en Tiempo Real


**Repos del equipo**
- Front (React + Vite): https://github.com/Queruubin/Lab6_Front_Castelblanco_Gomez
- Back (Spring Boot, CRUD + JWT + STOMP): https://github.com/MissGomezzz/ARSW_Lab6_Back_Castelblanco_Gomez
- Video demo (≤ 90 s): [Video One Drive](https://pruebacorreoescuelaingeduco-my.sharepoint.com/:v:/g/personal/angela_gomez-v_mail_escuelaing_edu_co/IQB-SbokG-VKTKi9JMC4ttx8AVoUHK7eSCKMxR_6Wn3pUpA?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=PAKZIL)
- Video observabilidad en inspección de la página: [Video One Drive 20 seg](https://pruebacorreoescuelaingeduco-my.sharepoint.com/:v:/g/personal/angela_gomez-v_mail_escuelaing_edu_co/IQA3WzXinxcsSLMSRZHeDwOQAWUtdVtZEc0LqvpwfWPdxwY?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=D03KcZ)

**Autores:** 

- Samuel Castelblanco
- Ángela Gómez

### 1. Arquitectura

```
React (Vite) :5173
 ├─ HTTP (REST CRUD + estado inicial, JWT Bearer) ──> Spring Boot :8080  (/api/v1, /auth/login)
 └─ Tiempo real (selector Ninguno / Socket.IO / STOMP):
     ├─ STOMP: /app/draw -> /topic/blueprints.{author}.{name} ──> Spring WebSocket (/ws-blueprints)
     └─ Socket.IO: join-room / draw-event -> blueprint-update ──> Servidor Node guía :3001
```

El backend propio implementa **STOMP** (con persistencia y seguridad JWT). Para **Socket.IO** el Front se conecta al servidor Node del repo guía
(`DECSIS-ECI/example-backend-socketio-node-`), de modo que el selector permite comparar ambas tecnologías sobre el mismo canvas.

### 2. Puesta en marcha

**Backend (Spring Boot, Java 21, Maven)**
```bash
git clone https://github.com/MissGomezzz/ARSW_Lab6_Back_Castelblanco_Gomez
cd ARSW_Lab6_Back_Castelblanco_Gomez
mvn -q -DskipTests spring-boot:run      # http://localhost:8080
```

**(Opcional) Servidor Socket.IO guía**
```bash
git clone https://github.com/DECSIS-ECI/example-backend-socketio-node-
cd example-backend-socketio-node-
npm i && npm run dev                    # http://localhost:3001
```

**Front**
```bash
git clone https://github.com/Queruubin/Lab6_Front_Castelblanco_Gomez
cd Lab6_Front_Castelblanco_Gomez
npm install
cp .env.example .env
npm run dev                             # http://localhost:5173
```

**Todo con Docker:** `docker compose up --build` (front en `:5173`, back en `:8080`).
**Credenciales de prueba:** `student` / `student123`.

**Variables de entorno (Front)**

| Variable | Valor típico | Uso |
|---|---|---|
| `VITE_USE_MOCK` | `false` | `true` = apiMock, `false` = apiClient (API real) |
| `VITE_API_BASE_URL` | *(vacío)* | vacío = proxy de Vite (sin CORS) |
| `VITE_BACKEND_URL` | `http://localhost:8080` | destino del proxy `/api`, `/auth`, `/ws-blueprints` |
| `VITE_STOMP_BASE` | *(vacío)* | origen STOMP; vacío = mismo origen vía proxy |
| `VITE_IO_BASE` | `http://localhost:3001` | servidor Socket.IO |

### 3. Endpoints usados

**REST** (todos los `/api/v1` requieren `Authorization: Bearer <JWT>`; respuestas en `{ code, message, data }`)

| Método | Ruta | Uso en el Front | Scope |
|---|---|---|---|
| POST | `/auth/login` | Login, obtiene `access_token` | público |
| GET | `/api/v1/blueprints?author=:a` | Tabla del autor + total de puntos | `blueprints.read` |
| GET | `/api/v1/blueprints/:author/:name` | Estado inicial del canvas (botón Open) | `blueprints.read` |
| POST | `/api/v1/blueprints` | Create | `blueprints.write` |
| PUT | `/api/v1/blueprints/:author/:name` | Save/Update (reemplaza puntos) | `blueprints.write` |
| PUT | `/api/v1/blueprints/:author/:name/points` | Agregar un punto (lo usa STOMP al persistir) | `blueprints.write` |
| DELETE | `/api/v1/blueprints/:author/:name` | Delete | `blueprints.write` |

> Diferencia con el enunciado: nuestras rutas llevan el prefijo `/api/v1` y están protegidas con JWT (Lab 4).

**Tiempo real**

| Tecnología | Conexión | Unirse | Enviar | Recibir |
|---|---|---|---|---|
| STOMP | `ws://localhost:8080/ws-blueprints` (WebSocket nativo, `Authorization: Bearer` en CONNECT) | `SUBSCRIBE /topic/blueprints.{author}.{name}` | `SEND /app/draw` `{author,name,point,clientId}` | `{author,name,points,clientId}`; errores en `/user/queue/errors` |
| Socket.IO | `io(VITE_IO_BASE, {transports:['websocket']})` | `join-room` `blueprints.{author}.{name}` | `draw-event` `{room,author,name,point,clientId}` | `blueprint-update` `{author,name,points}` |

### 4. Decisiones de diseño

- **Un plano = un canal/sala:** `blueprints.{author}.{name}`. Solo reciben los puntos quienes están en el mismo plano (aislamiento por plano).
- **Payload de punto** `{x, y}` con validación en el servidor (`x, y >= 0`, `author` y `name` obligatorios).
- **Eco propio:** cada pestaña genera un `clientId` (`crypto.randomUUID`) y descarta los mensajes que ella misma emitió, para no duplicar puntos.
- **Persistencia:** con STOMP el servidor guarda cada punto; con Socket.IO no persiste, así que se usa **Guardar** (PUT).
- **Seguridad:** el handshake es público pero el frame CONNECT exige JWT; publicar en `/app/draw` exige `blueprints.write`. Los orígenes permitidos salen de `blueprints.cors.allowed-origins`.
- **Reconexión:** STOMP reintenta cada 2 s, se resuscribe y recarga los puntos con `GET` para cubrir lo perdido durante la caída; Socket.IO vuelve a hacer `join-room`.
- **UX del selector:** deshabilitado al crear un plano nuevo (primero se guarda); la elección se recuerda en `localStorage`; indicador de estado Conectando / Conectado / Reconectando / Error.
- **Deshacer / Limpiar** son locales; solo Guardar persiste el estado completo.

### 5. Casos de prueba mínimos (evidencia)

| Caso | Cómo se verifica | 
|---|---
| Estado inicial | Open en un plano → el canvas carga los puntos del `GET` | 
| Dibujo local | Clic en el canvas agrega punto y redibuja | 
| RT multi-pestaña | 2 pestañas, mismo plano y misma tecnología → los puntos se replican |
| Aislamiento | Pestaña B en otro plano **no** recibe los puntos de A |
| CRUD | Create / Save / Delete refrescan la lista y el **Total** del autor | 
| Pruebas automáticas | `npm test` en verde + CI en GitHub Actions | 

### 6. Observabilidad

- **Back (SLF4J):** escrituras REST, cada evento de dibujo y el ciclo de vida de sesiones STOMP (connect, subscribe, unsubscribe, disconnect).
- **Health check:** `GET /actuator/health` (público) y `GET /actuator/info`.
- **Front:** la consola del navegador muestra los eventos `[rt:stomp]` y `[rt:socketio]`.
- Para mostrar la observabilidad de forma más clara y directa, se realizó un video de 20 segundos mostrando los resultados al inspeccionar la página cuando los usuarios dibujan blueprints. 
Video disponible [aquí](https://pruebacorreoescuelaingeduco-my.sharepoint.com/:v:/g/personal/angela_gomez-v_mail_escuelaing_edu_co/IQA3WzXinxcsSLMSRZHeDwOQAWUtdVtZEc0LqvpwfWPdxwY?nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&e=D03KcZ). 


### 7. Comparativa Socket.IO vs STOMP

| Aspecto | Socket.IO (Node) | STOMP (Spring) |
|---|---|---|
| Modelo | Eventos y *rooms* propios (`join-room`, `draw-event`) | Protocolo de mensajería sobre WebSocket (destinos `/app`, `/topic`) |
| Aislamiento por plano | Room manual | Tópico por plano, nativo del broker |
| Seguridad | En nuestra integración el cliente no envía JWT al conectar | JWT validado en CONNECT + scope en `/app/draw` |
| Persistencia | No persiste (requiere Guardar) | El servidor persiste cada punto |
| Reconexión | Automática del cliente; hay que volver a hacer `join-room` | Reintento cada 2 s + resuscripción + `GET` para resincronizar |
| Integración con el backend | Servidor aparte (Node, puerto 3001) | Mismo servidor y misma seguridad que el CRUD |
| Errores | `connect_error` | Frames `ERROR` y `/user/queue/errors` |

### 8. Hallazgos (latencia y reconexión)

<!-- TODO: completar con mediciones reales, por ejemplo:
- Latencia aproximada entre dibujar en la pestaña A y ver el punto en la B (STOMP vs Socket.IO).
- Qué pasó al apagar y volver a encender el backend con las pestañas abiertas.
- Qué pasó al expirar el JWT con la conexión STOMP abierta.
- Qué pasó con puntos dibujados mientras una pestaña estaba desconectada. -->

### 9. Troubleshooting

- **No hay broadcast:** ambas pestañas deben estar en el **mismo** plano y con la **misma** tecnología.
- **Error 401 / CONNECT rechazado:** falta iniciar sesión o el token expiró; volver a hacer login.
- **CORS:** permitir `http://localhost:5173` en `blueprints.cors.allowed-origins` o usar el proxy de Vite con `VITE_API_BASE_URL` vacío.
- **Socket.IO no conecta:** confirmar que el servidor Node está en `:3001` y `VITE_IO_BASE` apunta a él.
- **STOMP no recibe:** revisar la ruta `/ws-blueprints` y los prefijos `/app` y `/topic`.
- **Datos viejos con Docker:** `docker compose down -v` para recrear el volumen de PostgreSQL.


---