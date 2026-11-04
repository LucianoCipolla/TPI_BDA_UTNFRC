package ar.edu.utn.frc.bda.campus.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(
	title = "ms-reservas", 
	version = "1.0", 
	description = "Reservas de espacios: creación con validaciones, estados y búsqueda de disponibles. CampusReservas, TPI Backend de Aplicaciones 2026."))
@SpringBootApplication(scanBasePackages = "ar.edu.utn.frc.bda.campus")
public class MsReservasApplication {
	public static void main(String[] args) {
		SpringApplication.run(MsReservasApplication.class, args);
	}
}