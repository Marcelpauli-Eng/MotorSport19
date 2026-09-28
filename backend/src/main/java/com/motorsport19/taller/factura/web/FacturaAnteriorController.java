package com.motorsport19.taller.factura.web;

import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.factura.domain.FacturaAnterior;
import com.motorsport19.taller.factura.domain.LineaFacturaAnterior;
import com.motorsport19.taller.factura.repository.FacturaAnteriorRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Facturas del programa anterior, para verlas desde la ficha del cliente y la
 * de la moto. Solo lectura: se cargan una vez con el script de migracion.
 */
@RestController
public class FacturaAnteriorController {

    private final FacturaAnteriorRepository facturas;

    public FacturaAnteriorController(FacturaAnteriorRepository facturas) {
        this.facturas = facturas;
    }

    /**
     * @param conceptos lo que se cobro, en el orden de la factura: basta para
     *                  reconocerla en un listado; el detalle esta en el PDF
     */
    public record FacturaAnteriorResponse(Long id, String origen, String numero, LocalDate fecha,
                                          Long motoId, String matricula, List<String> conceptos,
                                          BigDecimal total) {

        static FacturaAnteriorResponse de(FacturaAnterior f) {
            return new FacturaAnteriorResponse(
                    f.getId(), f.getOrigen(), f.getNumero(), f.getFecha(),
                    f.getMoto() == null ? null : f.getMoto().getId(),
                    f.getMoto() == null ? null : f.getMoto().getMatricula(),
                    f.getLineas().stream().map(LineaFacturaAnterior::getDescripcion).toList(),
                    f.getTotal());
        }
    }

    @GetMapping("/clientes/{id}/facturas-anteriores")
    public List<FacturaAnteriorResponse> delCliente(@PathVariable Long id) {
        return facturas.findByClienteIdOrderByFechaDescNumeroDesc(id).stream()
                .map(FacturaAnteriorResponse::de)
                .toList();
    }

    @GetMapping("/motos/{id}/facturas-anteriores")
    public List<FacturaAnteriorResponse> deLaMoto(@PathVariable Long id) {
        return facturas.findByMotoIdOrderByFechaDescNumeroDesc(id).stream()
                .map(FacturaAnteriorResponse::de)
                .toList();
    }

    /** La factura original, tal cual la emitio el otro programa: la que vale. */
    @GetMapping(value = "/facturas-anteriores/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> pdf(@PathVariable Long id) {
        byte[] pdf = facturas.pdfDe(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la factura anterior", id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"factura-anterior-%d.pdf\"".formatted(id))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdf));
    }
}
