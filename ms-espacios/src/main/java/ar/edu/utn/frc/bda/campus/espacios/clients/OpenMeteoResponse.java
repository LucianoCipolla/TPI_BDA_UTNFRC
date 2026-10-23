package ar.edu.utn.frc.bda.campus.espacios.clients;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Solo los campos que usamos de la respuesta de Open-Meteo. */
public record OpenMeteoResponse(Hourly hourly) {

    public record Hourly(
        List<String> time,   // "2026-10-24T14:00"
        @JsonProperty("precipitation_probability") 
        List<Integer> lluvia) {
    }
}