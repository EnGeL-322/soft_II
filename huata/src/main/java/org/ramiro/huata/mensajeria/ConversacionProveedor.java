package org.ramiro.huata.mensajeria;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;

/** Hilo único y permanente entre Huata y un proveedor. */
@Entity
@Table(name = "conversaciones_proveedor")
@Getter @Setter @NoArgsConstructor
public class ConversacionProveedor extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id", nullable = false, unique = true)
    private Productor productor;
}
