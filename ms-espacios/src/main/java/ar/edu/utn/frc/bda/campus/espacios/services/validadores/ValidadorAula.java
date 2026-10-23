package ar.edu.utn.frc.bda.campus.espacios.services.validadores;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/** Aula: solo las reglas comunes. */
@Component
public class ValidadorAula extends ValidadorEspacioBase {

    @Override
    public TipoEspacio tipo() {
        return TipoEspacio.AULA;
    }
}