package ar.edu.utn.frc.bda.campus.reservas.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long espacioId;          // el espacio vive en otro servicio: solo el id
    private String espacioNombre;    // copia para poder listar aunque ms-espacios no responda (D11)
    private String usuario;          // viene del header X-Usuario

    private LocalDateTime inicio;
    private LocalDateTime fin;
    private int asistentes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private TipoActividad actividad;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private EstadoReserva estado;

    @Version
    private Long version;            // aprobar y cancelar a la vez: el segundo recibe 409
}