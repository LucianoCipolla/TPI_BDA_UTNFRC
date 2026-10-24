package ar.edu.utn.frc.bda.campus.reservas.controllers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.frc.bda.campus.commons.ApiResponse;
import ar.edu.utn.frc.bda.campus.reservas.clients.EspacioDisponible;
import ar.edu.utn.frc.bda.campus.reservas.entities.EstadoReserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.CrearReservaRequest;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.ReservaDto;
import ar.edu.utn.frc.bda.campus.reservas.services.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API REST de reservas. El usuario llega en el header X-Usuario, que pone el gateway a partir del token. */
@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Crear, aprobar, rechazar, cancelar y listar reservas; buscar espacios disponibles")
public class ReservaController {

    private static final String USUARIO = "X-Usuario";

    private final ReservaService service;

    @PostMapping
    @Operation(summary = "Crear una reserva (USUARIO)",
            description = "201: CONFIRMADA, o PENDIENTE si es un EVENTO. 400 formato; 404 espacio inexistente; "
                    + "409 superposición o choque simultáneo; 422 regla de negocio; 503 ms-espacios o el clima no responden")
    public ResponseEntity<ApiResponse<ReservaDto>> crear(@RequestHeader(USUARIO) String usuario, @Valid @RequestBody CrearReservaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.create(true, service.crear(req, usuario), "Reserva creada"));
    }

    @GetMapping("/mias")
    @Operation(summary = "Mis reservas (USUARIO)", description = "Funciona aunque ms-espacios no responda")
    public ResponseEntity<ApiResponse<List<ReservaDto>>> mias(@RequestHeader(USUARIO) String usuario) {
        return ResponseEntity.ok(ApiResponse.create(true, service.mias(usuario), "Reservas encontradas"));
    }

    @GetMapping
    @Operation(summary = "Todas las reservas (ADMIN)", description = "Filtro opcional por estado, por ejemplo ?estado=PENDIENTE")
    public ResponseEntity<ApiResponse<List<ReservaDto>>> listar(@RequestParam(required = false) EstadoReserva estado) {
        return ResponseEntity.ok(ApiResponse.create(true, service.listar(estado), "Reservas encontradas"));
    }

    @PatchMapping("/{id}/aprobar")
    @Operation(summary = "Aprobar un evento pendiente (ADMIN)", description = "404 si no existe; 409 si no está PENDIENTE; 422 si ya empezó")
    public ResponseEntity<ApiResponse<ReservaDto>> aprobar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.create(true, service.aprobar(id), "Reserva aprobada"));
    }

    @PatchMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar un evento pendiente (ADMIN)", description = "404 si no existe; 409 si no está PENDIENTE")
    public ResponseEntity<ApiResponse<ReservaDto>> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.create(true, service.rechazar(id), "Reserva rechazada"));
    }

    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una reserva propia (USUARIO)",
            description = "404 si no existe; 403 si no es tuya; 422 si ya empezó; 409 si ya está cancelada o rechazada")
    public ResponseEntity<ApiResponse<ReservaDto>> cancelar(@RequestHeader(USUARIO) String usuario, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.create(true, service.cancelar(id, usuario), "Reserva cancelada"));
    }

    @GetMapping("/disponibilidad")
    @Operation(summary = "Buscar espacios disponibles (USUARIO)",
            description = "Espacios que cumplen las reglas de ms-espacios y no tienen una reserva activa en la franja")
    public ResponseEntity<ApiResponse<List<EspacioDisponible>>> disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam int asistentes,
            @RequestParam TipoActividad actividad,
            @RequestParam(required = false) Set<String> equipamiento) {
        return ResponseEntity.ok(ApiResponse.create(true,
                service.disponibles(inicio, fin, asistentes, actividad, equipamiento), "Espacios disponibles"));
    }
}