package com.motorsport19.taller.support;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.domain.TipoDocumento;
import com.motorsport19.taller.factura.domain.DatosEmision;
import com.motorsport19.taller.factura.domain.DatosFiscales;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaAFacturar;
import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.domain.TipoRectificativa;
import com.motorsport19.taller.orden.domain.TipoLinea;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Construccion de facturas para los tests.
 *
 * <p>Los identificadores y los campos que rellena la base de datos se fijan aqui
 * por reflexion, y solo aqui: la entidad no expone ninguna forma de tocarlos
 * porque es justo la garantia que se quiere proteger.
 */
public final class FacturasDePrueba {

    public static final String NIF_TALLER = "B87654323";
    public static final BigDecimal IVA_GENERAL = new BigDecimal("21.00");

    private FacturasDePrueba() {
    }

    public static SerieFactura serie(Long id, String codigo, TipoFactura tipo, int ultimoNumero) {
        // El constructor sin argumentos es protegido a proposito: solo JPA
        // instancia entidades. En los tests se salta esa barrera de forma
        // explicita, que es distinto de abrirla en el codigo de produccion.
        SerieFactura serie = org.springframework.beans.BeanUtils.instantiateClass(SerieFactura.class);
        ReflectionTestUtils.setField(serie, "id", id);
        ReflectionTestUtils.setField(serie, "codigo", codigo);
        ReflectionTestUtils.setField(serie, "ejercicio", 2026);
        ReflectionTestUtils.setField(serie, "descripcion", "Serie de prueba " + codigo);
        ReflectionTestUtils.setField(serie, "tipo", tipo);
        ReflectionTestUtils.setField(serie, "ultimoNumero", ultimoNumero);
        ReflectionTestUtils.setField(serie, "activa", true);
        return serie;
    }

    public static Cliente clienteFacturable() {
        Cliente cliente = Cliente.registrar("Carlos", "Nunez Prieto", "600100101", null);
        cliente.asignarDatosFiscales(TipoDocumento.NIF, "12345678Z", "Calle de Alcala 145",
                "28009", "Madrid", "Madrid", "Espana");
        ReflectionTestUtils.setField(cliente, "id", 1L);
        return cliente;
    }

    public static DatosFiscales emisor() {
        return new DatosFiscales("MotorSport19 Taller S.L.", NIF_TALLER, "Calle del Motor 19",
                "28019", "Madrid", "Madrid", "Espana");
    }

    public static DatosFiscales receptor() {
        return new DatosFiscales("Carlos Nunez Prieto", "12345678Z", "Calle de Alcala 145",
                "28009", "Madrid", "Madrid", "Espana");
    }

    /** Linea de mano de obra: horas x tarifa, al 21%. */
    public static LineaAFacturar manoDeObra(String horas, String tarifa) {
        return new LineaAFacturar(TipoLinea.MANO_DE_OBRA, "Trabajo de taller", null,
                new BigDecimal(horas), new BigDecimal(tarifa), BigDecimal.ZERO, "GENERAL", IVA_GENERAL);
    }

    /** Linea de pieza, al 21%. */
    public static LineaAFacturar pieza(String sku, String cantidad, String precio) {
        return new LineaAFacturar(TipoLinea.PIEZA, "Pieza " + sku, sku,
                new BigDecimal(cantidad), new BigDecimal(precio), BigDecimal.ZERO, "GENERAL", IVA_GENERAL);
    }

    /** Linea con un tipo de IVA concreto, para probar el desglose. */
    public static LineaAFacturar conIva(String descripcion, String cantidad, String precio,
                                        String codigoIva, String porcentaje) {
        return new LineaAFacturar(TipoLinea.PIEZA, descripcion, null, new BigDecimal(cantidad),
                new BigDecimal(precio), BigDecimal.ZERO, codigoIva, new BigDecimal(porcentaje));
    }

    /** Emite una factura ordinaria encadenada a la huella que se indique. */
    public static Factura ordinaria(SerieFactura serie, int numero, long numeroRegistro,
                                    String huellaAnterior, List<LineaAFacturar> lineas) {
        return Factura.emitir(datos(serie, numero, numeroRegistro, TipoFactura.ORDINARIA,
                null, null, null), lineas, huellaAnterior);
    }

    /** Emite una rectificativa encadenada a la huella que se indique. */
    public static Factura rectificativa(SerieFactura serie, int numero, long numeroRegistro,
                                        String huellaAnterior, Factura original,
                                        TipoRectificativa tipo, String motivo,
                                        List<LineaAFacturar> lineas) {
        return Factura.emitir(datos(serie, numero, numeroRegistro, TipoFactura.RECTIFICATIVA,
                original, tipo, motivo), lineas, huellaAnterior);
    }

    public static DatosEmision datos(SerieFactura serie, int numero, long numeroRegistro, TipoFactura tipo,
                                     Factura rectificada, TipoRectificativa tipoRect, String motivo) {
        return new DatosEmision(
                serie, numero, numeroRegistro, tipo,
                null, rectificada, tipoRect, motivo,
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15),
                Instant.parse("2026-05-15T16:25:00Z"),
                emisor(), clienteFacturable(), receptor(),
                "1234 JKL", "Yamaha MT-07", "OT-2026-00001",
                "MotorSport19 Taller", "0.1.0", NIF_TALLER,
                "https://verifactu.motorsport19.example/verifica",
                1L);
    }

    /** Simula que la base de datos ha generado el numero completo tras el INSERT. */
    public static void simularNumeroCompleto(Factura factura) {
        ReflectionTestUtils.setField(factura, "numeroCompleto", factura.numeroVisible());
    }

    /** Manipula la huella guardada, como haria alguien tocando la base de datos. */
    public static void manipularHuella(Factura factura, String huellaFalsa) {
        ReflectionTestUtils.setField(factura, "huella", huellaFalsa);
    }

    /** Manipula un total sin recalcular la huella. */
    public static void manipularTotal(Factura factura, String nuevoTotal) {
        ReflectionTestUtils.setField(factura, "total", new BigDecimal(nuevoTotal));
    }
}
