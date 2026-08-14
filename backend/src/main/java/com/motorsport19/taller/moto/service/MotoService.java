package com.motorsport19.taller.moto.service;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.cliente.service.ClienteService;
import com.motorsport19.taller.common.error.ConflictoException;
import com.motorsport19.taller.common.error.RecursoNoEncontradoException;
import com.motorsport19.taller.common.util.Matriculas;
import com.motorsport19.taller.moto.domain.Moto;
import com.motorsport19.taller.moto.repository.MotoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MotoService {

    private final MotoRepository motoRepository;
    private final ClienteService clienteService;
    private final com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository;

    public MotoService(MotoRepository motoRepository, ClienteService clienteService,
                       com.motorsport19.taller.orden.repository.OrdenTrabajoRepository ordenRepository) {
        this.ordenRepository = ordenRepository;
        this.motoRepository = motoRepository;
        this.clienteService = clienteService;
    }

    @Transactional(readOnly = true)
    public Moto obtener(Long id) {
        // Con el propietario ya resuelto: la respuesta lo muestra y para entonces
        // la sesion de persistencia esta cerrada.
        return motoRepository.buscarConCliente(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la moto", id));
    }

    @Transactional(readOnly = true)
    public Moto obtenerPorMatricula(String matricula) {
        String normalizada = Matriculas.normalizar(matricula);
        return motoRepository.buscarPorMatricula(normalizada)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay ninguna moto con matricula %s.".formatted(normalizada)));
    }

    @Transactional(readOnly = true)
    public Page<Moto> buscar(String texto, boolean soloActivas, Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        // La misma busqueda, tambien en forma de matricula canonica: asi
        // «1234ABC» y «1234-abc» encuentran la moto fichada como «1234 ABC».
        String comoMatricula = Matriculas.normalizar(filtro);
        if (comoMatricula != null && comoMatricula.equalsIgnoreCase(filtro)) {
            comoMatricula = null;   // no aporta nada: ya lo cubre :texto
        }
        return motoRepository.buscar(filtro, comoMatricula, soloActivas, pageable);
    }

    @Transactional(readOnly = true)
    public List<Moto> buscarPorCliente(Long clienteId, boolean soloActivas) {
        clienteService.obtener(clienteId);
        return motoRepository.buscarPorCliente(clienteId, soloActivas);
    }

    @Transactional
    public Moto crear(Long clienteId, String matricula, String marca, String modelo, Integer anio,
                      Integer cilindrada, String color, String numeroBastidor, Integer kmActual,
                      String observaciones) {
        Cliente cliente = clienteService.obtener(clienteId);
        if (!cliente.isActivo()) {
            throw new ConflictoException(
                    "No se pueden dar de alta motos a '%s' porque el cliente esta dado de baja."
                            .formatted(cliente.nombreCompleto()));
        }

        String normalizada = Matriculas.normalizar(matricula);
        comprobarMatriculaLibre(normalizada, null);

        Moto moto = Moto.registrar(cliente, matricula, marca, modelo, anio, cilindrada, color,
                numeroBastidor, kmActual, observaciones);
        comprobarBastidorLibre(moto.getNumeroBastidor(), null);

        return motoRepository.save(moto);
    }

    @Transactional
    public Moto actualizar(Long id, String matricula, String marca, String modelo, Integer anio,
                           Integer cilindrada, String color, String numeroBastidor, String observaciones) {
        Moto moto = obtener(id);
        String normalizada = Matriculas.normalizar(matricula);
        comprobarMatriculaLibre(normalizada, id);

        moto.actualizarDatos(matricula, marca, modelo, anio, cilindrada, color, numeroBastidor, observaciones);
        comprobarBastidorLibre(moto.getNumeroBastidor(), id);

        return moto;
    }

    /**
     * Actualiza el kilometraje conocido. Lo llama tambien el alta de una OT, con
     * el kilometraje de entrada de la moto.
     */
    @Transactional
    public Moto registrarKilometraje(Long id, int km) {
        Moto moto = obtener(id);
        moto.registrarKilometraje(km);
        return moto;
    }

    /** Cambio de propietario: la moto se vende, el historial se queda con ella. */
    @Transactional
    public Moto cambiarPropietario(Long id, Long nuevoClienteId) {
        Moto moto = obtener(id);
        Cliente nuevo = clienteService.obtener(nuevoClienteId);
        if (!nuevo.isActivo()) {
            throw new ConflictoException(
                    "No se puede asignar la moto a '%s' porque el cliente esta dado de baja."
                            .formatted(nuevo.nombreCompleto()));
        }
        moto.cambiarPropietario(nuevo);
        return moto;
    }

    @Transactional
    public Moto darDeBaja(Long id) {
        Moto moto = obtener(id);

        // Con la moto en el taller, darla de baja la saca de los listados y de
        // los desplegables en mitad de la reparacion: quien la busca deja de
        // encontrarla y nadie le ha avisado de nada. Casi siempre es un despiste
        // ordenando el fichero, asi que se corta y se dice que falta por hacer.
        long abiertas = ordenRepository.contarAbiertasDeMoto(id);
        if (abiertas > 0) {
            throw new ConflictoException(
                    ("La moto %s tiene %d orden(es) de trabajo sin cerrar. Cierrelas o recha%celas "
                     + "antes de darla de baja.").formatted(moto.getMatricula(), abiertas, 'z'));
        }

        moto.darDeBaja();
        return moto;
    }

    @Transactional
    public Moto reactivar(Long id) {
        Moto moto = obtener(id);
        moto.reactivar();
        return moto;
    }

    // ------------------------------------------------------------------

    private void comprobarMatriculaLibre(String matricula, Long idExcluido) {
        boolean ocupada = idExcluido == null
                ? motoRepository.existeConMatricula(matricula)
                : motoRepository.existeOtraConMatricula(matricula, idExcluido);

        if (ocupada) {
            throw new ConflictoException(
                    "Ya hay una moto registrada con la matricula %s.".formatted(matricula));
        }
    }

    private void comprobarBastidorLibre(String bastidor, Long idExcluido) {
        if (bastidor == null) {
            return;
        }
        long id = idExcluido == null ? -1L : idExcluido;
        if (motoRepository.existeOtraConBastidor(bastidor, id)) {
            throw new ConflictoException(
                    "Ya hay una moto registrada con el numero de bastidor %s.".formatted(bastidor));
        }
    }
}
