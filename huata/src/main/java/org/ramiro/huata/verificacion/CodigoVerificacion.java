package org.ramiro.huata.verificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Codigo de un solo uso enviado por correo para confirmar un cambio sensible.
 *
 * <p>Se guarda el hash, nunca los seis digitos: mientras esta vigente, el codigo en claro vale lo
 * mismo que el permiso que protege.
 */
@Entity
@Table(name = "codigos_verificacion")
@Getter
@Setter
@NoArgsConstructor
public class CodigoVerificacion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PropositoVerificacion proposito;

    /** Usuario que lo pidio; es el unico que puede usarlo. */
    @Column(nullable = false, length = 120)
    private String solicitante;

    /** Correo al que se envio, guardado para poder decir despues a donde fue. */
    @Column(nullable = false, length = 150)
    private String destinatario;

    @Column(name = "codigo_hash", nullable = false, length = 120)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean usado = false;

    /** Intentos fallidos. Al llegar al maximo el codigo se quema. */
    @Column(nullable = false)
    private int intentos = 0;

    public boolean vigente(Instant ahora) {
        return !usado && isActivo() && expiraEn.isAfter(ahora);
    }
}
