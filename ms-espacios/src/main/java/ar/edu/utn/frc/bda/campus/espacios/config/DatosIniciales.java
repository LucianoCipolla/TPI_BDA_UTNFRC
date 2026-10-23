package ar.edu.utn.frc.bda.campus.espacios.config;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;
import ar.edu.utn.frc.bda.campus.espacios.repositories.EspacioRepository;
import lombok.RequiredArgsConstructor;

/** Carga los espacios de prueba al arrancar, solo si la base está vacía. */
@Component
@RequiredArgsConstructor
public class DatosIniciales implements CommandLineRunner {

    private static final double LATITUD_UTN = -31.44;      // UTN FRC: se manda a Open-Meteo (solo canchas)
    private static final double LONGITUD_UTN = -64.19;

    private final EspacioRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;                                         // ya hay datos: no duplicar
        }
        LocalTime ocho = LocalTime.of(8, 0);
        LocalTime veintidos = LocalTime.of(22, 0);

        repository.saveAll(List.of(
                Espacio.builder().nombre("Aula 101").tipo(TipoEspacio.AULA).capacidad(40)
                        .latitud(LATITUD_UTN).longitud(LONGITUD_UTN).horaApertura(ocho).horaCierre(veintidos)
                        .equipamiento(equipos(Equipamiento.PROYECTOR, Equipamiento.PIZARRA))
                        .actividadesPermitidas(acts(TipoActividad.CLASE, TipoActividad.EXAMEN, TipoActividad.EVENTO))
                        .build(),
                Espacio.builder().nombre("Aula 102").tipo(TipoEspacio.AULA).capacidad(60)
                        .latitud(LATITUD_UTN).longitud(LONGITUD_UTN).horaApertura(ocho).horaCierre(veintidos)
                        .equipamiento(equipos(Equipamiento.PIZARRA))
                        .actividadesPermitidas(acts(TipoActividad.CLASE, TipoActividad.EXAMEN, TipoActividad.EVENTO))
                        .build(),
                Espacio.builder().nombre("Laboratorio 3").tipo(TipoEspacio.LABORATORIO).capacidad(25)
                        .latitud(LATITUD_UTN).longitud(LONGITUD_UTN).horaApertura(ocho).horaCierre(veintidos)
                        .equipamiento(equipos(Equipamiento.COMPUTADORAS, Equipamiento.PROYECTOR))
                        .actividadesPermitidas(acts(TipoActividad.CLASE, TipoActividad.EXAMEN))
                        .build(),
                Espacio.builder().nombre("Sala de reuniones").tipo(TipoEspacio.SALA).capacidad(12)
                        .latitud(LATITUD_UTN).longitud(LONGITUD_UTN)
                        .horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(18, 0))
                        .equipamiento(equipos(Equipamiento.PROYECTOR, Equipamiento.SONIDO))
                        .actividadesPermitidas(acts(TipoActividad.CLASE, TipoActividad.EVENTO))
                        .build(),
                Espacio.builder().nombre("Cancha de fútbol").tipo(TipoEspacio.CANCHA).capacidad(22)
                        .latitud(LATITUD_UTN).longitud(LONGITUD_UTN).horaApertura(ocho).horaCierre(LocalTime.of(21, 0))
                        .actividadesPermitidas(acts(TipoActividad.DEPORTE, TipoActividad.CLASE))
                        .build()));
    }

    private static Set<Equipamiento> equipos(Equipamiento... items) {
        return new HashSet<>(List.of(items));
    }

    private static Set<TipoActividad> acts(TipoActividad... items) {
        return new HashSet<>(List.of(items));
    }
}