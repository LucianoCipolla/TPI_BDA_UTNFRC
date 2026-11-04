package ar.edu.utn.frc.bda.campus.espacios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
	info = @Info(title = "ms-espacios", 
	version = "1.0", 
	description = "Catálogo de espacios y validación de franjas. CampusReservas, TPI Backend de Aplicaciones 2026."))
@SpringBootApplication(scanBasePackages = "ar.edu.utn.frc.bda.campus")
public class MsEspaciosApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsEspaciosApplication.class, args);
	}

}