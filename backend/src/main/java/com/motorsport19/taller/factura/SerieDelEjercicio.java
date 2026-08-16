package com.motorsport19.taller.factura;

import com.motorsport19.taller.factura.domain.SerieFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.repository.SerieFacturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deja lista la serie de facturacion del ejercicio en curso.
 *
 * <p>Sin esto, un taller recien instalado no podia emitir <b>ninguna</b> factura:
 * las series no venian creadas, y hasta que alguien no encontraba la pantalla de
 * ajustes y creaba una a mano, el boton de facturar el primer trabajo del primer
 * dia fallaba. Mal momento para descubrir que falta configurar algo, con el
 * cliente delante esperando su factura.
 *
 * <p>Se crean las dos que hacen falta, porque una factura rectificativa no puede
 * ir en la serie ordinaria: la de facturar y la de corregir. La de corregir se
 * usa poco, y justamente por eso su ausencia se descubria en el peor momento —el
 * dia que hay que rectificar una factura ya emitida y cobrada—.
 *
 * <p>El mismo problema vuelve cada 1 de enero: las facturas se numeran por
 * ejercicio, asi que al cambiar de ano hace falta una serie nueva. Aqui se crea
 * al arrancar, y se hereda el codigo de la serie del ano anterior —si el taller
 * factura con la serie «F», la del ano nuevo tambien es «F»— para no partir en
 * dos una numeracion que la gestoria ya conoce.
 *
 * <p>No pisa nada: solo actua si no existe ya una serie ordinaria de este
 * ejercicio. Si el taller la creo a su gusto, o la desactivo a proposito, esta
 * clase no hace nada.
 */
@Component
public class SerieDelEjercicio implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SerieDelEjercicio.class);

    /** Codigo de la primera serie de un taller que empieza de cero. */
    private static final String CODIGO_POR_DEFECTO = "A";
    /** Las rectificativas van en su propia serie, con la R delante por convencion. */
    private static final String PREFIJO_RECTIFICATIVA = "R";

    private final SerieFacturaRepository series;

    public SerieDelEjercicio(SerieFacturaRepository series) {
        this.series = series;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        int ejercicio = LocalDate.now().getYear();
        List<SerieFactura> existentes = series.findAll();

        String ordinaria = crearSiFalta(existentes, ejercicio, TipoFactura.ORDINARIA,
                CODIGO_POR_DEFECTO, "Facturacion ordinaria %d".formatted(ejercicio));

        crearSiFalta(existentes, ejercicio, TipoFactura.RECTIFICATIVA,
                PREFIJO_RECTIFICATIVA + (ordinaria == null ? CODIGO_POR_DEFECTO : ordinaria),
                "Facturas rectificativas %d".formatted(ejercicio));
    }

    /**
     * Crea la serie del tipo pedido si el ejercicio no la tiene.
     *
     * @return el codigo con el que ha quedado la serie, la hubiera o no
     */
    private String crearSiFalta(List<SerieFactura> existentes, int ejercicio, TipoFactura tipo,
                                String codigoPorDefecto, String descripcion) {
        Optional<SerieFactura> yaEsta = existentes.stream()
                .filter(s -> s.getTipo() == tipo && s.getEjercicio() == ejercicio)
                .findFirst();
        if (yaEsta.isPresent()) {
            return yaEsta.get().getCodigo();
        }

        String codigo = codigoHeredado(existentes, ejercicio, tipo).orElse(codigoPorDefecto);
        series.save(SerieFactura.crear(codigo, ejercicio, descripcion, tipo, false));
        log.info("Creada la serie de facturacion {}/{} ({}) para el ejercicio en curso.",
                codigo, ejercicio, tipo);
        return codigo;
    }

    /** El codigo que el taller ya venia usando, para no cambiarlo de un ano a otro. */
    private Optional<String> codigoHeredado(List<SerieFactura> existentes, int ejercicio, TipoFactura tipo) {
        return existentes.stream()
                .filter(s -> s.getTipo() == tipo && s.getEjercicio() < ejercicio)
                .max(Comparator.comparingInt(SerieFactura::getEjercicio))
                .map(SerieFactura::getCodigo);
    }
}
