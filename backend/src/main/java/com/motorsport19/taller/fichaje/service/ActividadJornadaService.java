package com.motorsport19.taller.fichaje.service;

import com.motorsport19.taller.fichaje.domain.Fichaje;
import com.motorsport19.taller.fichaje.repository.ActividadJornadaRepository;
import com.motorsport19.taller.orden.domain.EstadoOT;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Lo que hizo una persona durante una jornada, en orden.
 *
 * <p>Sirve para lo que de verdad se pregunta al mirar las horas de alguien: no
 * cuantas fueron, sino que salio de ellas. Ocho horas fichadas y tres ordenes
 * entregadas se leen distinto que ocho horas fichadas y nada.
 *
 * <p>No es un sistema de vigilancia y por eso solo recoge <b>hechos del taller</b>
 * —una OT que cambia de estado, una factura emitida, un cliente dado de alta—,
 * nunca navegacion ni consultas. Lo que se ve aqui es exactamente lo mismo que
 * se veria repasando las ordenes una por una; esta pantalla solo ahorra el
 * repaso.
 */
@Service
public class ActividadJornadaService {

    private final ActividadJornadaRepository repositorio;

    public ActividadJornadaService(ActividadJornadaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public record Apunte(Instant momento, String tipo, String texto, String enlaceTipo, Long enlaceId) {
    }

    /**
     * Los apuntes de esa jornada.
     *
     * <p>El tramo se toma de las horas <b>corregidas</b> si las hubo: si el
     * administrador movio la entrada, lo que se hizo antes de esa hora ya no
     * cuenta como parte de la jornada.
     */
    @Transactional(readOnly = true)
    public List<Apunte> de(Fichaje jornada) {
        Instant hasta = jornada.finReal() != null ? jornada.finReal() : Instant.now();
        return repositorio.deLaJornada(jornada.getUsuario().getId(), jornada.inicioReal(), hasta)
                .stream()
                .map(ActividadJornadaService::aApunte)
                .toList();
    }

    private static Apunte aApunte(ActividadJornadaRepository.FilaActividad f) {
        // La bitacora ya trae el texto hecho y a donde lleva.
        if (f.getTipo().startsWith("BITACORA:")) {
            return new Apunte(f.getMomento(), f.getTipo().substring("BITACORA:".length()),
                    f.getTitulo(), f.getDetalle(), f.getDestinoId());
        }
        return switch (f.getTipo()) {
            case "OT_ESTADO" -> new Apunte(
                    f.getMomento(), "OT",
                    "%s · %s%s".formatted(f.getTitulo(), textoDeEstado(f.getDetalle().split("\\|", 2)[0]),
                            f.getDetalle().contains("|") ? " (" + f.getDetalle().split("\\|", 2)[1] + ")" : ""),
                    "orden", f.getDestinoId());

            case "CITA" -> new Apunte(
                    f.getMomento(), "AGENDA",
                    "%s%s".formatted(textoDeCita(f.getDetalle()), coleta(f.getTitulo())),
                    "agenda", f.getDestinoId());

            case "FACTURA" -> new Apunte(
                    f.getMomento(), "FACTURA",
                    "%s%s".formatted(textoDeFactura(f.getDetalle()), coleta(f.getTitulo())),
                    "factura", f.getDestinoId());

            case "PIEZA" -> new Apunte(
                    f.getMomento(), "ALMACEN",
                    "Alta de pieza: %s".formatted(f.getTitulo()),
                    "inventario", f.getDestinoId());

            case "EDICION" -> new Apunte(
                    f.getMomento(), "EDICION",
                    "Editó %s: %s".formatted(nombreDeFicha(f.getDetalle()), f.getTitulo()),
                    f.getDetalle(), f.getDestinoId());

            case "CLIENTE" -> new Apunte(
                    f.getMomento(), "CLIENTE",
                    "Alta de cliente: %s".formatted(f.getTitulo()),
                    "cliente", f.getDestinoId());

            case "MOTO" -> new Apunte(
                    f.getMomento(), "MOTO",
                    "Alta de moto: %s".formatted(f.getTitulo()),
                    "moto", f.getDestinoId());

            case "STOCK" -> new Apunte(
                    f.getMomento(), "ALMACEN",
                    "ENTRADA".equals(f.getDetalle())
                            ? "Entrada de almacén: %s".formatted(f.getTitulo())
                            : "Ajuste de inventario: %s".formatted(f.getTitulo()),
                    "inventario", f.getDestinoId());

            default -> new Apunte(f.getMomento(), f.getTipo(), f.getTitulo(), null, null);
        };
    }

    /**
     * Lo que se le hizo a la cita.
     *
     * <p>Una reprogramacion no cambia el estado —la cita sigue pendiente o
     * confirmada—, asi que la consulta la marca aparte con «|MOVIDA»; sin eso
     * aqui saldria «la confirmó» dos veces y una seria mentira.
     */
    private static String textoDeCita(String detalle) {
        if (detalle != null && detalle.endsWith("|MOVIDA")) {
            return "Movió una cita de día";
        }
        return switch (detalle == null ? "" : detalle) {
            case "PENDIENTE" -> "Dio cita";
            case "CONFIRMADA" -> "Confirmó la cita";
            case "ATENDIDA" -> "La moto entró: cita atendida";
            case "CANCELADA" -> "Canceló la cita";
            case "NO_PRESENTADO" -> "Anotó que el cliente no vino";
            default -> "Movimiento en la agenda";
        };
    }

    private static String textoDeFactura(String tipoEvento) {
        return switch (tipoEvento == null ? "" : tipoEvento) {
            case "EMISION" -> "Emitió la factura";
            case "RECTIFICACION" -> "Emitió una rectificativa";
            case "GENERACION_PDF" -> "Sacó el PDF de la factura";
            case "EXPORTACION" -> "Exportó el registro de facturación";
            case "VERIFICACION_CADENA" -> "Verificó la cadena de facturas";
            case "INCIDENCIA" -> "Incidencia de facturación";
            default -> "Movimiento de facturación";
        };
    }

    private static String nombreDeFicha(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "cliente" -> "el cliente";
            case "moto" -> "la moto";
            case "inventario" -> "la pieza";
            default -> "una ficha";
        };
    }

    /**
     * «PRESUPUESTADA» se lee fatal en una lista. Se traduce a lo que la persona
     * hizo, que es lo que se esta consultando.
     */
    private static String textoDeEstado(String estado) {
        return switch (estado) {
            case "RECIBIDA" -> "abrió la orden";
            case "PREPARADA" -> "la dejó preparada";
            case "EN_DIAGNOSTICO" -> "empezó el diagnóstico";
            case "PRESUPUESTADA" -> "hizo el presupuesto";
            case "APROBADA" -> "registró la aprobación del cliente";
            case "EN_REPARACION" -> "la puso en reparación";
            case "ESPERANDO_PIEZAS" -> "la dejó esperando piezas";
            case "LISTA" -> "la dejó lista para entregar";
            case "ENTREGADA" -> "la entregó";
            case "RECHAZADA" -> "registró el rechazo del presupuesto";
            default -> descripcionOTal(estado);
        };
    }

    /** Un estado nuevo que aqui no se conozca sale con su descripcion, no en bruto. */
    private static String descripcionOTal(String estado) {
        try {
            return EstadoOT.valueOf(estado).getDescripcion();
        } catch (IllegalArgumentException e) {
            return estado;
        }
    }

    private static String coleta(String texto) {
        return texto == null || texto.isBlank() ? "" : " · " + texto;
    }
}
