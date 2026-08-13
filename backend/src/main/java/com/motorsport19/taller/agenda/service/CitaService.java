package com.motorsport19.taller.agenda.service;

import com.motorsport19.taller.agenda.domain.Cita;
import com.motorsport19.taller.agenda.domain.EstadoCita;
import com.motorsport19.taller.agenda.repository.CitaRepository;
import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.service.ClienteService;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.configuracion.domain.ConfiguracionTaller;
import com.motorsport19.taller.configuracion.repository.ConfiguracionTallerRepository;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.service.MotoService;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;
import com.motorsport19.taller.orden.service.OrdenTrabajoService;
import com.motorsport19.taller.usuario.domain.Rol;
import com.motorsport19.taller.usuario.domain.Usuario;
import com.motorsport19.taller.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agenda del taller.
 *
 * <p>Planifica lo que va a entrar. La orden de trabajo sigue naciendo cuando la
 * moto esta fisicamente dentro: {@link #atender} es el puente entre las dos
 * cosas, y deja la cita cerrada y enlazada con la OT que ha generado.
 */
@Service
public class CitaService {

    private static final Logger log = LoggerFactory.getLogger(CitaService.class);

    /**
     * Zona con la que se agrupan las citas por dia.
     *
     * <p>La del servidor, que en esta aplicacion se fija a la del taller (ver
     * {@code TZ} en el docker-compose). Agrupar en UTC partiria mal los dias:
     * una cita de las 09:00 del lunes en Madrid es domingo en UTC en invierno.
     */
    private static final ZoneId ZONA_TALLER = ZoneId.systemDefault();

    private final CitaRepository citaRepository;
    private final MotoService motoService;
    private final ClienteService clienteService;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionTallerRepository configuracionRepository;
    private final OrdenTrabajoService ordenService;

    public CitaService(CitaRepository citaRepository, MotoService motoService,
                       ClienteService clienteService, UsuarioRepository usuarioRepository,
                       ConfiguracionTallerRepository configuracionRepository,
                       OrdenTrabajoService ordenService) {
        this.citaRepository = citaRepository;
        this.motoService = motoService;
        this.clienteService = clienteService;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.ordenService = ordenService;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Cita obtener(Long id) {
        return citaRepository.buscarConDetalle(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la cita", id));
    }

    /** Citas de un rango de dias, ambos incluidos. Es lo que pinta el calendario. */
    @Transactional(readOnly = true)
    public List<Cita> agenda(LocalDate desde, LocalDate hasta) {
        return citaRepository.buscarEntre(inicioDe(desde), inicioDe(hasta.plusDays(1)));
    }

    @Transactional(readOnly = true)
    public List<Cita> historialDeMoto(Long motoId) {
        motoService.obtener(motoId);
        return citaRepository.buscarPorMoto(motoId);
    }

    /**
     * Carga comprometida dia a dia en el rango, incluidos los dias vacios.
     *
     * <p>Se devuelven tambien los dias sin nada a proposito: el calendario los
     * pinta igual, y un hueco libre es justo la informacion que se busca al
     * mirar «cuando puedo meter esta moto».
     */
    @Transactional(readOnly = true)
    public List<CargaDiaria> carga(LocalDate desde, LocalDate hasta) {
        BigDecimal capacidad = configuracion().getCapacidadDiariaHoras();

        Map<LocalDate, List<Cita>> porDia = citaRepository
                .buscarVivasEntre(inicioDe(desde), inicioDe(hasta.plusDays(1)))
                .stream()
                .collect(Collectors.groupingBy(cita -> diaDe(cita.getFechaHora())));

        List<CargaDiaria> carga = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            List<Cita> delDia = porDia.getOrDefault(dia, List.of());
            BigDecimal horas = delDia.stream()
                    .map(Cita::getDuracionEstimada)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            carga.add(CargaDiaria.de(dia, delDia.size(), horas, capacidad));
        }
        return carga;
    }

    /**
     * La semana repartida por tecnico, con lo que le queda libre a cada uno.
     *
     * <p>Solo cuentan las citas vivas: una cancelada dejo de ocupar el hueco y
     * sumarla diria que el taller esta lleno cuando no lo esta.
     *
     * <p>Las citas sin tecnico asignado no se reparten ni se esconden: van a su
     * propia fila. Son trabajo comprometido que todavia no tiene a quien lo
     * haga, y es lo primero que hay que colocar al planificar la semana.
     */
    @Transactional(readOnly = true)
    public AgendaSemanal semana(LocalDate desde, LocalDate hasta) {
        BigDecimal capacidad = configuracion().getCapacidadDiariaHoras();

        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            dias.add(dia);
        }

        List<Cita> citas = citaRepository.buscarVivasEntre(inicioDe(desde), inicioDe(hasta.plusDays(1)));

        // Toda la plantilla, tenga citas o no: el tecnico con la semana vacia es
        // justo el que se busca al mirar esta pantalla.
        Map<Long, String> tecnicos = new LinkedHashMap<>();
        for (Usuario t : usuarioRepository.buscarPorRol(Rol.TECNICO)) {
            tecnicos.put(t.getId(), t.getNombreCompleto());
        }
        // Y quien tenga citas aunque ya no sea tecnico: su trabajo sigue ahi.
        for (Cita c : citas) {
            if (c.getTecnico() != null) {
                tecnicos.putIfAbsent(c.getTecnico().getId(), c.getTecnico().getNombreCompleto());
            }
        }

        List<AgendaSemanal.ColumnaTecnico> columnas = new ArrayList<>();
        for (Map.Entry<Long, String> tecnico : tecnicos.entrySet()) {
            columnas.add(columnaDe(tecnico.getKey(), tecnico.getValue(), dias, citas, capacidad));
        }

        boolean haySinAsignar = citas.stream().anyMatch(c -> c.getTecnico() == null);
        if (haySinAsignar) {
            columnas.add(columnaDe(null, "Sin asignar", dias, citas, capacidad));
        }

        BigDecimal comprometidas = columnas.stream()
                .map(AgendaSemanal.ColumnaTecnico::horasComprometidas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal libres = columnas.stream()
                .filter(c -> c.tecnicoId() != null)
                .map(AgendaSemanal.ColumnaTecnico::horasLibres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AgendaSemanal(desde, hasta, capacidad, dias, columnas, comprometidas, libres);
    }

    /**
     * Los plantones de un periodo, con quien repite.
     *
     * <p>El porcentaje se calcula sobre las citas que llegaron a su dia
     * —atendidas mas ausencias—, no sobre todas. Meter en el divisor las que se
     * cancelaron con tiempo diluiria el dato: cancelar avisando libera el hueco
     * y no tiene nada que ver con no aparecer.
     */
    @Transactional(readOnly = true)
    public SeguimientoAusencias ausencias(LocalDate desde, LocalDate hasta) {
        Instant inicio = inicioDe(desde);
        Instant fin = inicioDe(hasta.plusDays(1));

        List<Cita> faltas = citaRepository.buscarAusenciasEntre(inicio, fin);
        List<Cita> delPeriodo = citaRepository.buscarEntre(inicio, fin);

        long cerradas = delPeriodo.stream()
                .filter(c -> c.getEstado() == EstadoCita.ATENDIDA
                        || c.getEstado() == EstadoCita.NO_PRESENTADO)
                .count();

        BigDecimal horas = faltas.stream()
                .map(Cita::getDuracionEstimada)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Se agrupa por el nombre con el que se apunto porque quien falta no
        // siempre tiene ficha: media agenda entra por telefono.
        Map<String, List<Cita>> porPersona = faltas.stream()
                .filter(c -> c.nombreDeContacto() != null)
                .collect(Collectors.groupingBy(Cita::nombreDeContacto, LinkedHashMap::new,
                        Collectors.toList()));

        List<SeguimientoAusencias.Reincidente> reincidentes = porPersona.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> new SeguimientoAusencias.Reincidente(
                        e.getKey(),
                        e.getValue().get(0).telefonoDeContacto(),
                        e.getValue().size()))
                .sorted(Comparator.comparingInt(SeguimientoAusencias.Reincidente::faltas).reversed())
                .toList();

        List<SeguimientoAusencias.Ausencia> ultimas = faltas.stream()
                .map(c -> new SeguimientoAusencias.Ausencia(
                        c.getId(), diaDe(c.getFechaHora()), c.nombreDeContacto(),
                        c.telefonoDeContacto(), c.moto(), c.getMotivo(), c.getDuracionEstimada(),
                        c.getTecnico() == null ? null : c.getTecnico().getNombreCompleto()))
                .toList();

        BigDecimal porcentaje = cerradas == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(faltas.size() * 100L)
                        .divide(BigDecimal.valueOf(cerradas), 1, RoundingMode.HALF_UP);

        return new SeguimientoAusencias(desde, hasta, faltas.size(), (int) cerradas, porcentaje,
                horas, reincidentes, ultimas);
    }

    // ------------------------------------------------------------------
    // Alta y modificacion
    // ------------------------------------------------------------------

    @Transactional
    public Cita agendar(Instant fechaHora, BigDecimal duracionEstimada, Long motoId, Long clienteId,
                        String contactoNombre, String contactoTelefono, String descripcionMoto,
                        String motivo, Long tecnicoId, String observaciones) {

        Moto moto = motoId == null ? null : motoService.obtener(motoId);
        avisarSiYaTieneCita(moto, fechaHora);

        Cita cita = Cita.agendar(fechaHora, duracionEstimada, moto, cargarCliente(clienteId),
                contactoNombre, contactoTelefono, descripcionMoto, motivo,
                cargarUsuario(tecnicoId), observaciones);

        Cita guardada = citaRepository.save(cita);
        log.info("Cita {} agendada para el {}", guardada.getId(), fechaHora);
        return guardada;
    }

    @Transactional
    public Cita actualizar(Long id, Instant fechaHora, BigDecimal duracionEstimada, Long motoId,
                           Long clienteId, String contactoNombre, String contactoTelefono,
                           String descripcionMoto, String motivo, Long tecnicoId,
                           String observaciones) {

        Cita cita = obtener(id);
        Moto moto = motoId == null ? null : motoService.obtener(motoId);
        cita.actualizar(fechaHora, duracionEstimada, moto, cargarCliente(clienteId), contactoNombre,
                contactoTelefono, descripcionMoto, motivo, cargarUsuario(tecnicoId), observaciones);
        return cita;
    }

    /** Mueve la cita de dia sin tocar el resto de la ficha. */
    @Transactional
    public Cita reprogramar(Long id, Instant nuevaFechaHora) {
        Cita cita = obtener(id);
        cita.reprogramar(nuevaFechaHora);
        return cita;
    }

    @Transactional
    public Cita confirmar(Long id) {
        Cita cita = obtener(id);
        cita.confirmar();
        return cita;
    }

    @Transactional
    public Cita cancelar(Long id, String motivo) {
        Cita cita = obtener(id);
        cita.cancelar(motivo);
        log.info("Cita {} cancelada", id);
        return cita;
    }

    @Transactional
    public Cita marcarNoPresentado(Long id, String motivo) {
        Cita cita = obtener(id);
        cita.marcarNoPresentado(motivo);
        return cita;
    }

    /**
     * La moto ha llegado: se abre su orden de trabajo y la cita queda cerrada.
     *
     * <p>Aqui se cruzan agenda y taller. La moto es obligatoria aunque la cita se
     * cogiera sin ella: para abrir una OT hay que saber de que moto se habla, asi
     * que si la cita venia de alguien nuevo, primero se da de alta y despues se
     * atiende. El problema reportado sale del motivo de la cita si no se indica
     * otro, que es lo que suele pasar: se apunto bien al cogerla.
     */
    @Transactional
    public Cita atender(Long id, Long motoId, int kmEntrada, String problemaReportado,
                        Long usuarioId) {
        Cita cita = obtener(id);

        Long motoDeLaOrden = motoId != null
                ? motoId
                : (cita.getMoto() == null ? null : cita.getMoto().getId());

        if (motoDeLaOrden == null) {
            throw new ConflictoException(
                    "Esta cita no tiene moto asociada. Dela de alta y vuelva a intentarlo.");
        }

        String problema = problemaReportado == null || problemaReportado.isBlank()
                ? cita.getMotivo()
                : problemaReportado;

        OrdenTrabajo orden = ordenService.abrir(
                motoDeLaOrden, problema, kmEntrada, null,
                cita.getTecnico() == null ? null : cita.getTecnico().getId(),
                cita.getObservaciones(), usuarioId);

        cita.atender(orden);
        log.info("Cita {} atendida: abierta la orden {}", id, orden.codigoVisible());
        return cita;
    }

    // ------------------------------------------------------------------

    /**
     * Avisa si esa moto ya tiene hueco apartado.
     *
     * <p>Dos citas vivas para la misma moto casi siempre son un despiste del
     * mostrador, igual que dos OT abiertas. Se corta aqui antes de que el taller
     * reserve dos huecos para el mismo trabajo.
     */
    private void avisarSiYaTieneCita(Moto moto, Instant fechaHora) {
        if (moto == null) {
            return;
        }
        List<Cita> vivas = citaRepository.buscarVivasDeMotoDesde(moto.getId(), Instant.now());
        if (!vivas.isEmpty()) {
            throw new ConflictoException(
                    ("La moto %s ya tiene una cita sin cerrar. Muevala de fecha o cancelela antes de "
                     + "dar otra.").formatted(moto.getMatricula()));
        }
    }

    /** Los siete días de un técnico, con lo comprometido y lo que le queda. */
    private AgendaSemanal.ColumnaTecnico columnaDe(Long tecnicoId, String nombre, List<LocalDate> dias,
                                                   List<Cita> citas, BigDecimal capacidad) {
        List<Cita> suyas = citas.stream()
                .filter(c -> tecnicoId == null
                        ? c.getTecnico() == null
                        : c.getTecnico() != null && tecnicoId.equals(c.getTecnico().getId()))
                .toList();

        Map<LocalDate, List<Cita>> porDia = suyas.stream()
                .collect(Collectors.groupingBy(c -> diaDe(c.getFechaHora())));

        List<AgendaSemanal.DiaTecnico> detalle = new ArrayList<>();
        BigDecimal comprometidas = BigDecimal.ZERO;
        BigDecimal libres = BigDecimal.ZERO;

        for (LocalDate dia : dias) {
            List<Cita> delDia = porDia.getOrDefault(dia, List.of());
            BigDecimal horas = delDia.stream()
                    .map(Cita::getDuracionEstimada)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // El hueco no baja de cero: un dia con mas trabajo del que cabe no
            // tiene «horas libres negativas», tiene un problema, y para eso esta
            // la marca de saturado.
            BigDecimal hueco = capacidad.subtract(horas).max(BigDecimal.ZERO);

            detalle.add(new AgendaSemanal.DiaTecnico(
                    dia,
                    delDia.stream().map(AgendaSemanal.CitaBreve::de).toList(),
                    horas, hueco, horas.compareTo(capacidad) > 0));

            comprometidas = comprometidas.add(horas);
            libres = libres.add(hueco);
        }

        // Lo que esta sin asignar no tiene jornada que llenar: sus «horas libres»
        // serian capacidad inventada.
        return new AgendaSemanal.ColumnaTecnico(tecnicoId, nombre, detalle, comprometidas,
                tecnicoId == null ? BigDecimal.ZERO : libres, suyas.size());
    }

    private ConfiguracionTaller configuracion() {
        return configuracionRepository.findById(ConfiguracionTaller.ID_UNICO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Faltan los datos del taller: rellenelos en Ajustes > Empresa y facturacion."));
    }

    private Cliente cargarCliente(Long clienteId) {
        return clienteId == null ? null : clienteService.obtener(clienteId);
    }

    private Usuario cargarUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", usuarioId));
    }

    private static Instant inicioDe(LocalDate dia) {
        return dia.atStartOfDay(ZONA_TALLER).toInstant();
    }

    private static LocalDate diaDe(Instant instante) {
        return instante.atZone(ZONA_TALLER).toLocalDate();
    }
}
