package ar.edu.utn.frc.bda.campus.espacios.entities;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "espacios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Espacio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private TipoEspacio tipo;          // decide qué estrategia de validación se usa

    private int capacidad;

    private double latitud;            // se mandan a Open-Meteo (solo canchas)
    private double longitud;

    private LocalTime horaApertura;
    private LocalTime horaCierre;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "espacio_equipamiento", joinColumns = @JoinColumn(name = "espacio_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "equipamiento")
    @Builder.Default
    private Set<Equipamiento> equipamiento = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "espacio_actividades", joinColumns = @JoinColumn(name = "espacio_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actividad")
    @Builder.Default
    private Set<TipoActividad> actividadesPermitidas = new HashSet<>();
}