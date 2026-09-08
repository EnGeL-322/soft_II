package org.ramiro.huata.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bitácora de auditoría: registro inmutable ("Fecha/hora, Rol, Acción, Entidad, Detalle" como en el prototipo). */
@Entity
@Table(name = "registros_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant fecha = Instant.now();

    @Column(length = 100)
    private String actor;

    @Column(nullable = false, length = 80)
    private String accion;

    @Column(length = 80)
    private String entidad;

    @Column(length = 500)
    private String detalle;
}
