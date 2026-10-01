package co.edu.eci.blueprints.api;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.eci.blueprints.dto.ApiResponse;
import co.edu.eci.blueprints.dto.AuthorBlueprints;
import co.edu.eci.blueprints.dto.NewBlueprintRequest;
import co.edu.eci.blueprints.dto.UpdateBlueprintRequest;
import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import co.edu.eci.blueprints.services.BlueprintsServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * API REST de Blueprints (P1), versionada bajo {@code /api/v1/blueprints} y
 * protegida como Resource Server OAuth2 (P2).
 *
 * <p>Las lecturas requieren el scope {@code blueprints.read} y las escrituras
 * el scope {@code blueprints.write}. Todas las respuestas se envuelven en
 * {@link ApiResponse} y los errores se traducen a códigos HTTP mediante
 * {@link GlobalExceptionHandler}.</p>
 */
@Tag(name = "Blueprints", description = "Operaciones sobre planos y sus puntos (requiere JWT)")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/blueprints")
public class BlueprintsAPIController {

    private static final Logger log = LoggerFactory.getLogger(BlueprintsAPIController.class);

    private final BlueprintsServices services;

    public BlueprintsAPIController(BlueprintsServices services) { this.services = services; }

    @Operation(summary = "Obtener todos los blueprints", description = "Retorna la lista completa de blueprints almacenados. Requiere scope blueprints.read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consulta exitosa"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Set<Blueprint>>> getAll() {
        return ApiResponse.ok(services.getAllBlueprints());
    }

    @Operation(summary = "Obtener blueprints de un autor con su total de puntos",
            description = "GET /api/v1/blueprints?author={author}. Requiere scope blueprints.read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consulta exitosa"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Autor sin blueprints")
    })
    @GetMapping(params = "author")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<AuthorBlueprints>> byAuthorWithTotal(@RequestParam String author)
            throws BlueprintNotFoundException {
        return ApiResponse.ok(services.getAuthorBlueprints(author));
    }

    @Operation(summary = "Obtener blueprints de un autor", description = "Requiere scope blueprints.read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consulta exitosa"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Autor no encontrado")
    })
    @GetMapping("/{author}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Set<Blueprint>>> byAuthor(@PathVariable String author)
            throws BlueprintNotFoundException {
        return ApiResponse.ok(services.getBlueprintsByAuthor(author));
    }

    @Operation(summary = "Obtener un blueprint específico por autor y nombre", description = "Requiere scope blueprints.read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Consulta exitosa"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @GetMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Blueprint>> byAuthorAndName(
            @PathVariable String author, @PathVariable String bpname) throws BlueprintNotFoundException {
        return ApiResponse.ok(services.getBlueprint(author, bpname));
    }

    @Operation(summary = "Crear un nuevo blueprint", description = "Requiere scope blueprints.write")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Blueprint creado exitosamente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "El blueprint ya existe")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Blueprint>> add(@Valid @RequestBody NewBlueprintRequest req)
            throws BlueprintPersistenceException {
        Blueprint bp = new Blueprint(req.author(), req.name(), req.points());
        services.addNewBlueprint(bp);
        log.info("Blueprint created: {}/{} ({} points)", bp.getAuthor(), bp.getName(), bp.getPoints().size());
        return ApiResponse.created(bp);
    }

    @Operation(summary = "Agregar un punto a un blueprint existente", description = "Requiere scope blueprints.write")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Punto agregado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @PutMapping("/{author}/{bpname}/points")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Blueprint>> addPoint(
            @PathVariable String author, @PathVariable String bpname, @Valid @RequestBody Point p)
            throws BlueprintNotFoundException {
        services.addPoint(author, bpname, p.x(), p.y());
        log.info("Point added via REST: {}/{} -> ({}, {})", author, bpname, p.x(), p.y());
        return ApiResponse.accepted(services.getBlueprint(author, bpname));
    }

    @Operation(summary = "Reemplazar los puntos de un blueprint", description = "Requiere scope blueprints.write")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprint actualizado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @PutMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Blueprint>> update(
            @PathVariable String author, @PathVariable String bpname,
            @Valid @RequestBody UpdateBlueprintRequest req) throws BlueprintNotFoundException {
        services.updatePoints(author, bpname, req.points());
        log.info("Blueprint updated: {}/{} ({} points)", author, bpname, req.points().size());
        return ApiResponse.ok(services.getBlueprint(author, bpname));
    }

    @Operation(summary = "Eliminar un blueprint", description = "Requiere scope blueprints.write")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprint eliminado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @DeleteMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String author, @PathVariable String bpname)
            throws BlueprintNotFoundException {
        services.deleteBlueprint(author, bpname);
        log.info("Blueprint deleted: {}/{}", author, bpname);
        return ApiResponse.ok(null);
    }
}
