package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Anotacion del registro de eventos de facturacion.
 *
 * <p>Bitacora append-only de todo lo que le ocurre al subsistema de facturacion:
 * emisiones, rectificaciones, generacion de PDF, exportaciones, verificaciones de
 * la cadena e incidencias. Los triggers de la base de datos rechazan UPDATE y
 * DELETE, de forma que la bitacora no se puede maquillar a posteriori.
 */
@Entity
@Table(name = "evento_factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Vacio en eventos que no van referidos a una factura concreta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", updatable = false)
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, updatable = false, length = 30)
    private TipoEventoFactura tipoEvento;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "descripcion", nullable = false, updatable = false, length = 500)
    private String descripcion;

    /** Contexto adicional en JSON (parametros de la exportacion, error, etc.). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle", updatable = false)
    private String detalle;

    @Column(name = "ip_origen", updatable = false, length = 45)
    private String ipOrigen;

    public static EventoFactura registrar(Factura factura, TipoEventoFactura tipo, Usuario usuario,
                                          String descripcion, String detalleJson, String ipOrigen) {
        EventoFactura evento = new EventoFactura();
        evento.factura = factura;
        evento.tipoEvento = tipo;
        evento.usuario = usuario;
        evento.fecha = Instant.now();
        // La columna admite 500 caracteres; se recorta antes de llegar a la BD
        // para que un detalle largo no tumbe la operacion que se esta anotando.
        evento.descripcion = descripcion != null && descripcion.length() > 500
                ? descripcion.substring(0, 497) + "..."
                : descripcion;
        evento.detalle = detalleJson;
        evento.ipOrigen = ipOrigen;
        return evento;
    }
}
