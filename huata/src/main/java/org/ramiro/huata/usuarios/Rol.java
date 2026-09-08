package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol de negocio. Se modela como tabla (no enum fijo) para poder ajustar responsabilidades sin
 * desplegar codigo nuevo; los nombres base sembrados en desarrollo son ADMINISTRADOR, ACOPIADOR y
 * CALIDAD (ver 01_CONTEXTO_COMPLETO_PARA_DESARROLLO.md).
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String ACOPIADOR = "ACOPIADOR";
    public static final String CALIDAD = "CALIDAD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    /** Lista de accesos separada por coma, para la tabla "Roles" de Configuración (rol / responsabilidad / acceso). */
    @Column(length = 200)
    private String accesos;

    public Rol(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Rol(String nombre, String descripcion, String accesos) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.accesos = accesos;
    }
}
