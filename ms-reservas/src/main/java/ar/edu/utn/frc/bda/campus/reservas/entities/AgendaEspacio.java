package ar.edu.utn.frc.bda.campus.reservas.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Una fila por espacio. Su versión ordena las reservas simultáneas del mismo espacio (RN15). */
@Entity
@Table(name = "agenda_espacio")
@Getter
@NoArgsConstructor
public class AgendaEspacio {

    @Id
    private Long espacioId;

    @Version
    private Long version;

    public AgendaEspacio(Long espacioId) {
        this.espacioId = espacioId;
    }
}