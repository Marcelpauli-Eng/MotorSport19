package com.motorsport19.taller.configuracion.web;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.error.ReglaNegocioException;
import com.motorsport19.taller.inventario.domain.Pieza;
import com.motorsport19.taller.inventario.repository.PiezaRepository;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.domain.TipoIva;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.configuracion.repository.TipoIvaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos del taller: los que salen impresos en cada factura.
 *
 * <p>Cambiarlos no reescribe las facturas ya emitidas. Cada factura guarda
 * dentro una copia de como estaba el taller el dia que se emitio, de modo que
 * un cambio de domicilio no altera el historico. Es lo que exige la normativa y
 * ademas es lo que espera cualquiera que mire una factura de hace dos años.
 */
@RestController
@RequestMapping("/configuracion")
public class ConfiguracionController {

    private final ConfiguracionTallerRepository repositorio;
    private final TipoIvaRepository tiposIva;
    private final PiezaRepository piezas;

    public ConfiguracionController(ConfiguracionTallerRepository repositorio,
                                   TipoIvaRepository tiposIva, PiezaRepository piezas) {
        this.repositorio = repositorio;
        this.tiposIva = tiposIva;
        this.piezas = piezas;
    }

    /**
     * Los datos del taller, o el formulario en blanco si todavia no se han
     * puesto.
     *
     * <p>Un taller recien instalado no tiene fila de configuracion, y esto no es
     * un error que haya que devolver como tal: es justo la pantalla desde la que
     * se rellena. Ademas el catalogo de IVA viaja aqui, y sin el no se pueden
     * dar de alta ni piezas, asi que negarlo dejaria la instalacion bloqueada
     * sin manera de desbloquearla.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ConfiguracionResponse obtener() {
        List<TipoIva> tipos = tiposIva.findAll();
        return repositorio.findById(ConfiguracionTaller.ID_UNICO)
                .map(c -> ConfiguracionResponse.de(c, tipos))
                .orElseGet(() -> ConfiguracionResponse.sinConfigurar(tipos));
    }

    /** Guarda los datos del taller, creando la fila la primera vez. */
    @PutMapping
    @Transactional
    public ConfiguracionResponse actualizar(@Valid @RequestBody ActualizarConfiguracion peticion) {
        ConfiguracionTaller cfg = repositorio.findById(ConfiguracionTaller.ID_UNICO)
                .orElseGet(ConfiguracionTaller::sinRellenar);
        cfg.actualizar(
                peticion.razonSocial(), peticion.nif(), peticion.direccion(), peticion.codigoPostal(),
                peticion.ciudad(), peticion.provincia(), peticion.pais(), peticion.telefono(),
                peticion.email(), peticion.tarifaHoraDefecto(), peticion.tipoIvaDefecto(),
                peticion.capacidadDiariaHoras(),
                peticion.limiteFacturaSimplificada());
        return ConfiguracionResponse.de(repositorio.save(cfg), tiposIva.findAll());
    }

    /**
     * Configura la tasa de reciclaje de neumaticos, o la desactiva.
     *
     * <p>Endpoint propio y no un par de campos mas en el PUT de arriba: aquello
     * son los datos fiscales del taller y esto es una regla de cobro. Mezclarlas
     * obligaria a reenviar la razon social y el NIF para cambiar un desplegable.
     *
     * <p>Con cualquiera de los dos vacios queda desactivada, que es como sale de
     * fabrica y como se queda un taller que no vende neumaticos.
     */
    @PutMapping("/tasa-neumatico")
    @Transactional
    public ConfiguracionResponse configurarTasaNeumatico(@Valid @RequestBody TasaNeumaticoRequest peticion) {
        // Sin fila no se puede crear una a medias: la razon social y el NIF son
        // obligatorios en la base, y guardarla vacia reventaba con la sentencia SQL
        // entera como mensaje.
        ConfiguracionTaller cfg = repositorio.findById(ConfiguracionTaller.ID_UNICO)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Guarde primero los datos de la empresa; despues podra configurar la tasa de neumaticos."));

        Pieza tasa = peticion.piezaTasaId() == null ? null
                : piezas.findById(peticion.piezaTasaId()).orElseThrow(
                        () -> RecursoNoEncontradoException.de("la pieza de la tasa",
                                peticion.piezaTasaId()));

        cfg.configurarTasaNeumatico(peticion.familiaNeumaticos(), tasa);
        return ConfiguracionResponse.de(repositorio.save(cfg), tiposIva.findAll());
    }

    /** @param piezaTasaId nulo para desactivar la tasa */
    public record TasaNeumaticoRequest(
            @Size(max = 60, message = "La familia no puede superar los 60 caracteres") String familiaNeumaticos,
            Long piezaTasaId) {
    }

    /** Lo que se puede cambiar, mas el catalogo de IVA para el desplegable. */
    public record ConfiguracionResponse(
            // false mientras el taller no haya guardado sus datos ni una vez.
            boolean configurado,
            String razonSocial, String nif, String direccion, String codigoPostal,
            String ciudad, String provincia, String pais, String telefono, String email,
            BigDecimal tarifaHoraDefecto, String tipoIvaDefecto,
            BigDecimal capacidadDiariaHoras,
            BigDecimal limiteFacturaSimplificada,
            String familiaNeumaticos,
            Long piezaTasaNeumaticoId,
            String piezaTasaNeumaticoNombre,
            String softwareNombre, String softwareVersion,
            List<TipoIvaResponse> tiposIva
    ) {
        static ConfiguracionResponse de(ConfiguracionTaller c, List<TipoIva> tipos) {
            return new ConfiguracionResponse(
                    true,
                    c.getRazonSocial(), c.getNif(), c.getDireccion(), c.getCodigoPostal(),
                    c.getCiudad(), c.getProvincia(), c.getPais(), c.getTelefono(), c.getEmail(),
                    c.getTarifaHoraDefecto(), c.getTipoIvaDefecto(), c.getCapacidadDiariaHoras(),
                    c.getLimiteFacturaSimplificada(),
                    c.getFamiliaNeumaticos(),
                    c.getPiezaTasaNeumatico() == null ? null : c.getPiezaTasaNeumatico().getId(),
                    c.getPiezaTasaNeumatico() == null ? null
                            : c.getPiezaTasaNeumatico().getSku() + " · "
                              + c.getPiezaTasaNeumatico().getDescripcion(),
                    c.getSoftwareNombre(), c.getSoftwareVersion(),
                    tipos.stream().map(TipoIvaResponse::de).toList());
        }

        /** Taller sin estrenar: campos en blanco para que el administrador los rellene. */
        static ConfiguracionResponse sinConfigurar(List<TipoIva> tipos) {
            return new ConfiguracionResponse(
                    false,
                    null, null, null, null, null, null, "ES", null, null,
                    null, "GENERAL", null,
                    // El tope que trae la instalacion de serie; la gestoria lo confirma.
                    ConfiguracionTaller.LIMITE_SIMPLIFICADA_POR_DEFECTO,
                    // Sin estrenar no hay tasa de neumaticos: se configura si el taller los vende.
                    null, null, null,
                    ConfiguracionTaller.SOFTWARE_NOMBRE, ConfiguracionTaller.SOFTWARE_VERSION,
                    tipos.stream().map(TipoIvaResponse::de).toList());
        }
    }

    public record TipoIvaResponse(String codigo, String descripcion, BigDecimal porcentaje) {
        static TipoIvaResponse de(TipoIva t) {
            return new TipoIvaResponse(t.getCodigo(), t.getDescripcion(), t.getPorcentaje());
        }
    }

    public record ActualizarConfiguracion(
            @NotBlank(message = "La razon social es obligatoria")
            @Size(max = 150) String razonSocial,

            @NotBlank(message = "El NIF es obligatorio")
            @Size(max = 20) String nif,

            @NotBlank(message = "La direccion es obligatoria")
            @Size(max = 200) String direccion,

            @NotBlank(message = "El codigo postal es obligatorio")
            @Size(max = 10) String codigoPostal,

            @NotBlank(message = "La ciudad es obligatoria")
            @Size(max = 100) String ciudad,

            @Size(max = 100) String provincia,
            @Size(max = 2) String pais,
            @Size(max = 30) String telefono,
            @Size(max = 150) String email,

            @NotNull(message = "La tarifa por hora es obligatoria")
            @Positive(message = "La tarifa por hora tiene que ser mayor que cero")
            BigDecimal tarifaHoraDefecto,

            @NotBlank(message = "El tipo de IVA por defecto es obligatorio")
            String tipoIvaDefecto,

            @NotNull(message = "La capacidad diaria es obligatoria")
            @Positive(message = "La capacidad diaria tiene que ser mayor que cero")
            BigDecimal capacidadDiariaHoras,

            /**
             * Tope de la factura simplificada. Opcional: si no viene, se queda
             * el que hubiera, para que un cliente antiguo no lo borre sin querer.
             */
            @PositiveOrZero(message = "El limite de la factura simplificada no puede ser negativo")
            BigDecimal limiteFacturaSimplificada
    ) {
    }
}
