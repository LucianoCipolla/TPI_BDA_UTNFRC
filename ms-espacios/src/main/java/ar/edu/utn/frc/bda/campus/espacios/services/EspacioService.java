package ar.edu.utn.frc.bda.campus.espacios.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.frc.bda.campus.commons.RecursoNoEncontradoException;
import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;
import ar.edu.utn.frc.bda.campus.commons.ServicioNoDisponibleException;
import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.EspacioDto;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.EspacioRequest;
import ar.edu.utn.frc.bda.campus.espacios.entities.models.ValidacionDto;
import ar.edu.utn.frc.bda.campus.espacios.repositories.EspacioRepository;
import ar.edu.utn.frc.bda.campus.espacios.services.validadores.ValidadorEspacioBase;

/** ABM de espacios, validación de una franja (para crear reservas) y búsqueda de compatibles. */
@Service
public class EspacioService {

    private final EspacioRepository repository;
    private final Map<TipoEspacio, ValidadorEspacioBase> validadores = new EnumMap<>(TipoEspacio.class);

    /** Spring inyecta todas las estrategias (@Component); se indexan por el tipo que atienden. */
    public EspacioService(EspacioRepository repository, List<ValidadorEspacioBase> estrategias) {
        this.repository = repository;
        estrategias.forEach(v -> validadores.put(v.tipo(), v));
    }

    // ---------- ABM ----------

    public List<EspacioDto> listar() {
        return repository.findAll().stream()
                .map(this::aDto)
                .toList();
    }

    public EspacioDto buscar(Long id) {
        return aDto(obtener(id));
    }

    @Transactional
    public EspacioDto crear(EspacioRequest req) {
        validarHorario(req);
        Espacio e = new Espacio();
        copiarDatos(req, e);
        return aDto(repository.save(e));
    }

    @Transactional
    public EspacioDto modificar(Long id, EspacioRequest req) {
        validarHorario(req);
        Espacio e = obtener(id);
        copiarDatos(req, e);
        return aDto(repository.save(e));
    }

    @Transactional
    public void borrar(Long id) {
        repository.delete(obtener(id));
    }

    // ---------- Validación de una franja ----------

    /** Para crear una reserva: ¿este espacio sirve en esta franja? */
    public ValidacionDto validar(Long id, LocalDateTime inicio, LocalDateTime fin, TipoActividad actividad, Set<Equipamiento> pedido) {
        validarFranja(inicio, fin);
        Espacio e = obtener(id);
        String motivo = motivoRechazo(e, inicio, fin, actividad, pedido);
        return new ValidacionDto(e.getId(), e.getNombre(), e.getCapacidad(), motivo == null, motivo);
    }

    /** Para la búsqueda: todos los espacios que sirven para la franja y el pedido. */
    public List<EspacioDto> compatibles(LocalDateTime inicio, LocalDateTime fin, int capacidad, TipoActividad actividad, Set<Equipamiento> pedido) {
        validarFranja(inicio, fin);
        List<EspacioDto> resultado = new ArrayList<>();
        for (Espacio e : repository.findByCapacidadGreaterThanEqual(capacidad)) {
            try {
                if (motivoRechazo(e, inicio, fin, actividad, pedido) == null) {
                    resultado.add(aDto(e));
                }
            } catch (ServicioNoDisponibleException ex) {
                // Sin pronóstico no se puede garantizar una cancha: se omite y se sigue con el resto
            }
        }
        return resultado;
    }

    // ---------- Auxiliares ----------

    /** Strategy: usa la estrategia del tipo del espacio, sin if ni switch. */
    private String motivoRechazo(Espacio e, LocalDateTime inicio, LocalDateTime fin, TipoActividad actividad, Set<Equipamiento> pedido) {
        return validadores.get(e.getTipo())
                .motivoRechazo(e, inicio, fin, actividad, pedido == null ? Set.of() : pedido);
    }

    private Espacio obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el espacio " + id));
    }

    private static void validarHorario(EspacioRequest req) {
        if (!req.horaApertura().isBefore(req.horaCierre())) {
            throw new ReglaNegocioException("La hora de apertura debe ser anterior a la de cierre");
        }
    }

    private static void validarFranja(LocalDateTime inicio, LocalDateTime fin) {
        if (!inicio.isBefore(fin) || !inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new ReglaNegocioException("La franja debe terminar después de empezar, en el mismo día");
        }
    }

    private static void copiarDatos(EspacioRequest req, Espacio e) {
        e.setNombre(req.nombre());
        e.setTipo(req.tipo());
        e.setCapacidad(req.capacidad());
        e.setLatitud(req.latitud());
        e.setLongitud(req.longitud());
        e.setHoraApertura(req.horaApertura());
        e.setHoraCierre(req.horaCierre());
        e.setActividadesPermitidas(new HashSet<>(req.actividadesPermitidas()));
        e.setEquipamiento(req.equipamiento() == null ? new HashSet<>() : new HashSet<>(req.equipamiento()));
    }

    private EspacioDto aDto(Espacio e) {
        return new EspacioDto(e.getId(), e.getNombre(), e.getTipo(), e.getCapacidad(),e.getHoraApertura(), e.getHoraCierre(), e.getLatitud(), e.getLongitud(), Set.copyOf(e.getActividadesPermitidas()), Set.copyOf(e.getEquipamiento()));
    }
}