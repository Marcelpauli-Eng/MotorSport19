package com.motorsport19.taller.estadisticas.web;

import com.motorsport19.taller.estadisticas.service.EstadisticasService;
import com.motorsport19.taller.estadisticas.service.FilaReparto;
import com.motorsport19.taller.estadisticas.service.ResumenMes;
import com.motorsport19.taller.estadisticas.service.TotalesEjercicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Informes del taller.
 *
 * <p>Son datos economicos, asi que la ruta queda reservada a direccion y
 * mostrador en {@code ConfiguracionSeguridad}: un tecnico no tiene por que ver
 * el margen del taller.
 */
@RestController
@RequestMapping("/estadisticas")
public class EstadisticasController {

    private final EstadisticasService servicio;

    public EstadisticasController(EstadisticasService servicio) {
        this.servicio = servicio;
    }

    /** Todo lo que necesita la pantalla de informes, en una sola llamada. */
    @GetMapping("/facturacion")
    public InformeFacturacionResponse facturacion(@RequestParam(required = false) Integer ejercicio) {
        int anio = ejercicio != null ? ejercicio : LocalDate.now().getYear();

        List<ResumenMes> meses = servicio.facturacionMensual(anio);
        TotalesEjercicio totales = servicio.totales(anio);

        return new InformeFacturacionResponse(
                anio,
                servicio.ejerciciosDisponibles(),
                totales,
                meses,
                servicio.mejoresClientes(anio, 5),
                servicio.piezasMasUsadas(anio, 5));
    }

    /** Serie mensual suelta, por si se quiere pintar en otro sitio. */
    @GetMapping("/facturacion/mensual")
    public List<ResumenMes> mensual(@RequestParam(required = false) Integer ejercicio) {
        return servicio.facturacionMensual(ejercicio != null ? ejercicio : LocalDate.now().getYear());
    }

    /** Ranking de clientes por importe facturado. */
    @GetMapping("/clientes")
    public List<FilaReparto> clientes(@RequestParam(required = false) Integer ejercicio,
                                      @RequestParam(defaultValue = "10") int limite) {
        return servicio.mejoresClientes(ejercicio != null ? ejercicio : LocalDate.now().getYear(), limite);
    }
}
