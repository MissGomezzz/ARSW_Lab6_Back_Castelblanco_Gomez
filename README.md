# Escuela Colombiana de Ingeniería Julio Garavito
## Arquitectura de Software – ARSW
### Laboratorio – Parte 2: BluePrints API con Seguridad JWT (OAuth 2.0)

## Autores
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
