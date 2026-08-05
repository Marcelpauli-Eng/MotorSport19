package com.motorsport19.taller.inventario.web;

import com.motorsport19.taller.common.web.PaginaResponse;
import com.motorsport19.taller.inventario.domain.MovimientoStock;
import com.motorsport19.taller.inventario.domain.TipoMovimiento;
import com.motorsport19.taller.inventario.service.InventarioService;
import com.motorsport19.taller.inventario.web.dto.AjusteStockRequest;
import com.motorsport19.taller.inventario.web.dto.AlertaStockResponse;
import com.motorsport19.taller.inventario.web.dto.EntradaStockRequest;
import com.motorsport19.taller.inventario.web.dto.MovimientoStockResponse;
import com.motorsport19.taller.inventario.web.dto.SalidaStockRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Movimientos de stock y alertas de reposicion.
 *
 * <p>No existe ningun endpoint para fijar el stock de una pieza directamente, y
 * es deliberado: las existencias solo cambian registrando movimientos.
 */
@RestController
@RequestMapping("/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    /** Piezas que han caido al minimo o por debajo. */
    @GetMapping("/alertas")
    public List<AlertaStockResponse> alertas() {
        return inventarioService.alertasDeStock().stream()
                .map(AlertaStockResponse::de)
                .toList();
    }

    /** Libro de movimientos, con filtros opcionales. */
    @GetMapping("/movimientos")
    public PaginaResponse<MovimientoStockResponse> movimientos(
            @RequestParam(required = false) Long piezaId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<MovimientoStock> pagina = inventarioService.consultarMovimientos(piezaId, tipo, desde, hasta,
                pageable);
        return PaginaResponse.de(pagina, MovimientoStockResponse::de);
    }

    /** Historial completo de una pieza concreta. */
    @GetMapping("/piezas/{piezaId}/movimientos")
    public PaginaResponse<MovimientoStockResponse> movimientosDePieza(
            @PathVariable Long piezaId,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<MovimientoStock> pagina = inventarioService.consultarMovimientosDePieza(piezaId, pageable);
        return PaginaResponse.de(pagina, MovimientoStockResponse::de);
    }

    @PostMapping("/piezas/{piezaId}/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStockResponse registrarEntrada(
            @PathVariable Long piezaId,
            @Valid @RequestBody EntradaStockRequest peticion,
            @RequestParam(required = false) Long usuarioId) {

        return MovimientoStockResponse.de(inventarioService.registrarEntrada(
                piezaId, peticion.cantidad(), peticion.documentoProveedor(),
                peticion.precioCosteUnitario(), peticion.motivo(), usuarioId));
    }

    @PostMapping("/piezas/{piezaId}/salidas")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStockResponse registrarSalida(
            @PathVariable Long piezaId,
            @Valid @RequestBody SalidaStockRequest peticion,
            @RequestParam(required = false) Long usuarioId) {

        return MovimientoStockResponse.de(inventarioService.registrarSalida(
                piezaId, peticion.cantidad(), peticion.motivo(), usuarioId));
    }

    @PostMapping("/piezas/{piezaId}/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoStockResponse registrarAjuste(
            @PathVariable Long piezaId,
            @Valid @RequestBody AjusteStockRequest peticion,
            @RequestParam(required = false) Long usuarioId) {

        return MovimientoStockResponse.de(inventarioService.registrarAjuste(
                piezaId, peticion.cantidad(), peticion.motivo(), usuarioId));
    }
}
