package com.motorsport19.taller.servicio.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla de orden de trabajo: un servicio que el taller repite igual una y
 * otra vez.
 *
 * <p><b>Aqui no hay precios.</b> Una plantilla guarda que se hace y cuanto
 * (dos horas y media, un filtro, tres litros de aceite), nunca a cuanto. El
 * precio sale del catalogo y de la tarifa de la OT en el momento de volcarla,
 * exactamente igual que si se hubiera tecleado a mano. Guardarlo aqui seria
 * montar un segundo catalogo que envejece a espaldas del primero.
 *
 * <p><b>Volcarla no ata nada.</b> Una vez en la OT, las lineas son de la OT: se
 * editan, se borran y se cambian de cantidad como cualquier otra. Cambiar la
 * plantilla despues no toca ninguna orden ya montada, que es justo lo que se
 * quiere: la revision que se hizo en marzo se facturo con lo que se hizo en
 * marzo.
 */
@Entity
@Table(name = "servicio_tipo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServicioTipo extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "descripcion", length = 400)
    private String descripcion;

    /**
     * Las plantillas no se borran, se desactivan.
     *
     * <p>Una que se dejo de usar sigue explicando por que una OT de hace dos
     * anos tiene esas lineas. Desactivarla la saca del desplegable y la deja
     * donde estaba.
     */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /**
     * Las lineas viven y mueren con la plantilla: fuera de ella no significan
     * nada, de ahi el {@code orphanRemoval}.
     */
    @OneToMany(mappedBy = "servicioTipo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroLinea ASC")
    private List<LineaServicioTipo> lineas = new ArrayList<>();

    // ==================================================================
    // Fabrica
    // ==================================================================

    public static ServicioTipo crear(String nombre, String descripcion) {
        ServicioTipo servicio = new ServicioTipo();
        servicio.nombre = exigirNombre(nombre);
        servicio.descripcion = textoONulo(descripcion);
        servicio.activo = true;
        return servicio;
    }

    // ==================================================================
    // Modificacion
    // ==================================================================

    public void renombrar(String nombre, String descripcion) {
        this.nombre = exigirNombre(nombre);
        this.descripcion = textoONulo(descripcion);
    }

    public void activar() {
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    /**
     * Sustituye la lista de lineas entera.
     *
     * <p>Se reemplaza en bloque en vez de ir anadiendo y quitando una a una
     * porque la pantalla que las edita funciona asi: se monta la lista completa
     * y se guarda. Ir linea a linea obligaria a un endpoint por operacion para
     * no ganar nada.
     */
    public void reemplazarLineas(List<NuevaLinea> nuevas) {
        if (nuevas == null || nuevas.isEmpty()) {
            throw new ReglaNegocioException(
                    "Un servicio tipo sin lineas no ahorra nada: anade al menos una.");
        }

        lineas.clear();
        int numero = 1;
        for (NuevaLinea nueva : nuevas) {
            lineas.add(nueva.pieza() == null
                    ? LineaServicioTipo.manoDeObra(this, numero, nueva.descripcion(), nueva.cantidad())
                    : LineaServicioTipo.pieza(this, numero, nueva.pieza(), nueva.cantidad()));
            numero++;
        }
    }

    // ==================================================================
    // Consultas
    // ==================================================================

    /**
     * Horas de taller que suma la plantilla.
     *
     * <p>Es lo primero que se mira al elegir un servicio («esto son 2,5 h»), asi
     * que se calcula en vez de guardarse: un total guardado se queda desfasado
     * en cuanto alguien cambia una linea y nadie se entera.
     */
    public BigDecimal horasTotales() {
        return lineas.stream()
                .filter(LineaServicioTipo::esManoDeObra)
                .map(LineaServicioTipo::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long numeroDePiezas() {
        return lineas.stream().filter(l -> !l.esManoDeObra()).count();
    }

    // ==================================================================

    /** Lo que la capa de servicio pasa para montar una linea. */
    public record NuevaLinea(String descripcion, Pieza pieza, BigDecimal cantidad) {}

    private static String exigirNombre(String nombre) {
        String limpio = textoONulo(nombre);
        if (limpio == null) {
            throw new ReglaNegocioException("El servicio tipo necesita un nombre.");
        }
        return limpio;
    }

    private static String textoONulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
