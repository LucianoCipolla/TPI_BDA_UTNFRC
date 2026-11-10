package ar.edu.utn.frc.bda.campus.reservas.services;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.frc.bda.campus.commons.AccesoDenegadoException;
import ar.edu.utn.frc.bda.campus.commons.ConflictoException;
import ar.edu.utn.frc.bda.campus.commons.RecursoNoEncontradoException;
import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;
import ar.edu.utn.frc.bda.campus.reservas.clients.EspacioDisponible;
import ar.edu.utn.frc.bda.campus.reservas.clients.EspaciosClient;
import ar.edu.utn.frc.bda.campus.reservas.entities.AgendaEspacio;
import ar.edu.utn.frc.bda.campus.reservas.entities.EstadoReserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.Reserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.CrearReservaRequest;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.ReservaDto;
import ar.edu.utn.frc.bda.campus.reservas.repositories.AgendaEspacioRepository;
import ar.edu.utn.frc.bda.campus.reservas.repositories.ReservaRepository;
import ar.edu.utn.frc.bda.campus.reservas.services.validadores.CadenaValidacionReserva;
import ar.edu.utn.frc.bda.campus.reservas.services.validadores.ContextoReserva;
import ar.edu.utn.frc.bda.campus.reservas.services.validadores.ReglasFranja;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Ciclo de vida de la reserva: crear, aprobar, rechazar, cancelar, listar y buscar disponibles. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final ReservaRepository repository;
    private final AgendaEspacioRepository agendaRepository;
    private final CadenaValidacionReserva cadena;
    private final EspaciosClient espaciosClient;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    // ---------- Crear ----------

    @Transactional
    public ReservaDto crear(CrearReservaRequest req, String usuario) {
        tomarAgenda(req.espacioId());                    // RN15: ver tomarAgenda
        ContextoReserva ctx = new ContextoReserva(req, usuario);
        cadena.validar(ctx);           // RN1, RN2, espacio, RN5 y RN6
        EstadoReserva estado = req.actividad() == TipoActividad.EVENTO
                ? EstadoReserva.PENDIENTE                // RN13: los eventos esperan aprobación
                : EstadoReserva.CONFIRMADA;
        Reserva r = repository.save(Reserva.builder()
                .espacioId(req.espacioId())
                .espacioNombre(ctx.getEspacio().nombre())
                .usuario(usuario)
                .inicio(req.inicio())
                .fin(req.fin())
                .asistentes(req.asistentes())
                .actividad(req.actividad())
                .estado(estado)
                .build());
        log.info("Reserva #{} CREADA | {} reservó '{}' (espacio {}) el {} para {} ({} asistentes) | estado: {}",
                r.getId(), usuario, r.getEspacioNombre(), r.getEspacioId(), franja(r),
                r.getActividad(), r.getAsistentes(),
                estado == EstadoReserva.PENDIENTE ? "PENDIENTE (los eventos esperan aprobación de bedelía)" : estado);
        return aDto(r);
    }

    // ---------- Cambios de estado ----------

    @Transactional
    public ReservaDto aprobar(Long id) {                         // ADMIN
        Reserva r = buscar(id);
        if (!r.getInicio().isAfter(LocalDateTime.now())) {
            throw new ReglaNegocioException("La reserva ya empezó");
        }
        EstadoReserva antes = cambiarEstado(r, EstadoReserva.CONFIRMADA);
        log.info("Reserva #{} APROBADA por bedelía | {} → CONFIRMADA | '{}' el {}", id, antes, r.getEspacioNombre(), franja(r));
        return aDto(r);
    }

    @Transactional
    public ReservaDto rechazar(Long id) {                        // ADMIN
        Reserva r = buscar(id);
        EstadoReserva antes = cambiarEstado(r, EstadoReserva.RECHAZADA);
        log.info("Reserva #{} RECHAZADA por bedelía | {} → RECHAZADA | '{}' el {}", id, antes, r.getEspacioNombre(), franja(r));
        return aDto(r);
    }

    @Transactional
    public ReservaDto cancelar(Long id, String usuario) {        // dueño
        Reserva r = buscar(id);
        if (!r.getUsuario().equals(usuario)) {
            throw new AccesoDenegadoException("La reserva no es tuya");                  // RN7
        }
        if (!r.getInicio().isAfter(LocalDateTime.now())) {
            throw new ReglaNegocioException("La reserva ya empezó");                     // RN7
        }
        EstadoReserva antes = cambiarEstado(r, EstadoReserva.CANCELADA);
        log.info("Reserva #{} CANCELADA por {} | {} → CANCELADA | '{}' el {}", id, usuario, antes, r.getEspacioNombre(), franja(r));
        return aDto(r);
    }

    // ---------- Consultas ----------

    public List<ReservaDto> mias(String usuario) {
        return repository.findByUsuarioOrderByInicioAsc(usuario).stream()
                .map(this::aDto)
                .toList();
    }

    public List<ReservaDto> listar(EstadoReserva estado) {      // ADMIN; estado opcional
        List<Reserva> reservas = estado == null
                ? repository.findAllByOrderByInicioAsc()
                : repository.findByEstadoOrderByInicioAsc(estado);
        return reservas.stream()
                .map(this::aDto)
                .toList();
    }

    /** Búsqueda: espacios compatibles (ms-espacios) que además están libres (ms-reservas). */
    public List<EspacioDisponible> disponibles(LocalDateTime inicio, LocalDateTime fin, int asistentes, TipoActividad actividad, Set<String> equipamiento) {
        if (asistentes <= 0) {
            throw new ReglaNegocioException("La cantidad de asistentes debe ser mayor que 0");
        }
        ReglasFranja.validar(inicio, fin);                                                // RN1 y RN2
        List<EspacioDisponible> compatibles = espaciosClient.compatibles(inicio, fin, asistentes, actividad, equipamiento); // RN3 a RN5, RN8 a RN12
        Set<Long> ocupados = repository.espaciosOcupados(EstadoReserva.ACTIVOS, inicio, fin); // RN6
        return compatibles.stream()
                .filter(e -> !ocupados.contains(e.id()))
                .toList();
    }

    // ---------- Auxiliares ----------

    /** RN15: incrementa la versión de la agenda del espacio al hacer commit (la crea si es la primera reserva). */
    private void tomarAgenda(Long espacioId) {
        agendaRepository.findByEspacioId(espacioId)
                .orElseGet(() -> agendaRepository.saveAndFlush(new AgendaEspacio(espacioId)));
    }

    /** RN14: solo los cambios del diagrama de estados; cualquier otro es un 409. Devuelve el estado anterior. */
    private EstadoReserva cambiarEstado(Reserva r, EstadoReserva destino) {
        EstadoReserva antes = r.getEstado();
        if (!antes.puedePasarA(destino)) {
            throw new ConflictoException("Una reserva " + antes + " no puede pasar a " + destino);
        }
        r.setEstado(destino);
        return antes;
    }

    /** "15/11/2026 10:00 a 12:00" (o con la fecha completa en el fin si termina otro día), para los logs. */
    private static String franja(Reserva r) {
        boolean mismoDia = r.getInicio().toLocalDate().equals(r.getFin().toLocalDate());
        return r.getInicio().format(FECHA_HORA) + " a " + r.getFin().format(mismoDia ? HORA : FECHA_HORA);
    }

    private Reserva buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la reserva " + id));
    }

    private ReservaDto aDto(Reserva r) {
        return new ReservaDto(r.getId(), r.getEspacioId(), r.getEspacioNombre(), r.getUsuario(),
                r.getInicio(), r.getFin(), r.getAsistentes(), r.getActividad(), r.getEstado());
    }
}