package ar.edu.utn.frc.bda.campus.espacios.controllers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.frc.bda.campus.commons.ApiResponse;
import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.EspacioDto;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.EspacioRequest;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.ValidacionDto;
import ar.edu.utn.frc.bda.campus.espacios.services.EspacioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API REST de espacios: ABM, validación de una franja y búsqueda de compatibles. */
@RestController
@RequestMapping("/api/espacios")
@RequiredArgsConstructor
@Tag(name = "Espacios", description = "Catálogo de espacios, validación de franjas y búsqueda de compatibles")
public class EspacioController {

    private final EspacioService service;

    @GetMapping
    @Operation(summary = "Listar espacios")
    public ResponseEntity<ApiResponse<List<EspacioDto>>> listar() {
        return ResponseEntity.ok(ApiResponse.create(true, service.listar(), "Espacios encontrados"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar un espacio", description = "404 si no existe")
    public ResponseEntity<ApiResponse<EspacioDto>> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.create(true, service.buscar(id), "Espacio encontrado"));
    }

    @PostMapping
    @Operation(summary = "Crear un espacio (ADMIN)",
            description = "201 si se crea; 400 si faltan datos; 422 si la apertura no es anterior al cierre")
    public ResponseEntity<ApiResponse<EspacioDto>> crear(@Valid @RequestBody EspacioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.create(true, service.crear(req), "Espacio creado"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un espacio (ADMIN)", description = "404 si no existe; 400 y 422 como al crear")
    public ResponseEntity<ApiResponse<EspacioDto>> modificar(@PathVariable Long id,
                                                             @Valid @RequestBody EspacioRequest req) {
        return ResponseEntity.ok(ApiResponse.create(true, service.modificar(id, req), "Espacio modificado"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Borrar un espacio (ADMIN)", description = "404 si no existe")
    public ResponseEntity<ApiResponse<Void>> borrar(@PathVariable Long id) {
        service.borrar(id);
        return ResponseEntity.ok(ApiResponse.create(true, null, "Espacio eliminado"));
    }

    @GetMapping("/{id}/validacion")
    @Operation(summary = "¿Sirve este espacio para la franja? (lo usa ms-reservas)",
            description = "200 con disponible true o false y el motivo; 404 si no existe; 503 si es una cancha y no hay pronóstico")
    public ResponseEntity<ApiResponse<ValidacionDto>> validar(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam TipoActividad actividad,
            @RequestParam(required = false) Set<Equipamiento> equipamiento) {
        return ResponseEntity.ok(ApiResponse.create(true,
                service.validar(id, inicio, fin, actividad, equipamiento), "Validación realizada"));
    }

    @GetMapping("/compatibles")
    @Operation(summary = "Espacios que sirven para la franja y el pedido (lo usa ms-reservas)",
            description = "Capacidad, actividad, equipamiento, horario y, en canchas, clima. Si no hay pronóstico, las canchas se omiten")
    public ResponseEntity<ApiResponse<List<EspacioDto>>> compatibles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam int capacidad,
            @RequestParam TipoActividad actividad,
            @RequestParam(required = false) Set<Equipamiento> equipamiento) {
        return ResponseEntity.ok(ApiResponse.create(true,
                service.compatibles(inicio, fin, capacidad, actividad, equipamiento), "Espacios compatibles"));
    }
}