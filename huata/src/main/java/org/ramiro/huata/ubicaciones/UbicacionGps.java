package org.ramiro.huata.ubicaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;

/**
 * Posición GPS real reportada por el navegador del acopiador (Geolocation API), no un esquema
 * simulado. Cada fila es un "punto" de su recorrido; la mas reciente por acopiador es su posición
 * actual en "Seguimiento de producción".
 */
@Entity
@Table(name = "ubicaciones_gps")
@Getter
@Setter
@NoArgsConstructor
public class UbicacionGps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;

    /** Precisión en metros que reporta el navegador (Geolocation API), si está disponible. */
    private Double precisionMetros;

    /** Velocidad en m/s que reporta el navegador (GeolocationCoordinates.speed), si está disponible. */
    private Double velocidadMs;

    @Column(nullable = false)
    private Instant fecha = Instant.now();
}
