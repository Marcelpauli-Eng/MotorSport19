package com.motorsport19.taller.usuario.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Lo que se puede hacer en el programa, troceado para poder repartirlo.
 *
 * <p>El catalogo es <b>fijo</b>: el administrador combina estos permisos como
 * quiera para formar roles, pero no puede inventar permisos nuevos. Un permiso
 * que ningun sitio del codigo comprueba seria una casilla que no protege nada, y
 * eso es peor que no tenerla: quien la desmarca se queda creyendo que ha cerrado
 * una puerta.
 *
 * <p>Cada valor lleva el grupo con el que se agrupa en la pantalla de roles y el
 * texto que se le enseña a quien reparte permisos. Van aqui y no en el frontend
 * para que no puedan discrepar: el que manda es el que se comprueba.
 *
 * <p><b>Al añadir un permiso nuevo</b> hay que hacer dos cosas o no sirve de
 * nada: declararlo aqui y exigirlo en la ruta correspondiente de
 * {@code ConfiguracionSeguridad} (o donde toque en el servicio).
 */
public enum Permiso {

    // ------------------------------------------------------------------
    // Clientes
    // ------------------------------------------------------------------
    CLIENTES_VER(Grupo.CLIENTES, "Ver clientes"),
    CLIENTES_CREAR(Grupo.CLIENTES, "Crear clientes"),
    CLIENTES_EDITAR(Grupo.CLIENTES, "Editar clientes"),
    CLIENTES_DATOS_FISCALES(Grupo.CLIENTES, "Editar datos fiscales", "Documento y domicilio: sin esto no se le puede facturar"),
    CLIENTES_BAJA(Grupo.CLIENTES, "Dar de baja clientes"),

    // ------------------------------------------------------------------
    // Motos
    // ------------------------------------------------------------------
    MOTOS_VER(Grupo.MOTOS, "Ver motos"),
    MOTOS_CREAR(Grupo.MOTOS, "Crear motos"),
    MOTOS_EDITAR(Grupo.MOTOS, "Editar motos"),
    MOTOS_CAMBIAR_PROPIETARIO(Grupo.MOTOS, "Cambiar de propietario", "Cuando la moto se vende"),
    MOTOS_BAJA(Grupo.MOTOS, "Dar de baja motos"),

    // ------------------------------------------------------------------
    // Ordenes de trabajo
    // ------------------------------------------------------------------
    ORDENES_VER(Grupo.ORDENES, "Ver ordenes"),
    ORDENES_VER_TODAS(Grupo.ORDENES, "Ver las de todo el taller", "Sin esto solo ve las que tiene asignadas"),
    ORDENES_ABRIR(Grupo.ORDENES, "Abrir ordenes", "Entrar una moto en el taller"),
    ORDENES_DIAGNOSTICAR(Grupo.ORDENES, "Escribir el diagnostico"),
    ORDENES_LINEAS_MANO_OBRA(Grupo.ORDENES, "Apuntar mano de obra"),
    ORDENES_LINEAS_MATERIAL(Grupo.ORDENES, "Anadir material"),
    ORDENES_LINEAS_QUITAR(Grupo.ORDENES, "Quitar lineas del presupuesto"),
    ORDENES_ESTADO(Grupo.ORDENES, "Mover el estado", "Empezar la reparacion, marcarla lista..."),
    ORDENES_ASIGNAR_TECNICO(Grupo.ORDENES, "Repartir el trabajo", "Asignar la orden a un tecnico"),
    ORDENES_PREPARAR(Grupo.ORDENES, "Preparar trabajo cerrado", "Componerla entera y pasarsela a un tecnico, sin presupuesto ni aprobacion"),
    ORDENES_APROBAR(Grupo.ORDENES, "Aprobar o rechazar el presupuesto", "Lo que contesta el cliente"),
    ORDENES_ENTREGAR(Grupo.ORDENES, "Entregar la moto al cliente"),

    // ------------------------------------------------------------------
    // Dinero
    // ------------------------------------------------------------------
    IMPORTES_VER(Grupo.DINERO, "Ver importes y precios", "Sin esto no ve ni la tarifa, ni los totales, ni los precios del almacen"),
    PRECIOS_EDITAR(Grupo.DINERO, "Cambiar tarifas y precios", "El precio de la hora y el de una linea suelta"),
    DESCUENTOS_APLICAR(Grupo.DINERO, "Aplicar descuentos"),

    // ------------------------------------------------------------------
    // Facturacion
    // ------------------------------------------------------------------
    FACTURAS_VER(Grupo.FACTURACION, "Ver facturas"),
    FACTURAS_EMITIR(Grupo.FACTURACION, "Emitir facturas"),
    FACTURAS_RECTIFICAR(Grupo.FACTURACION, "Emitir rectificativas", "La unica forma de corregir una factura"),
    FACTURAS_SERIES(Grupo.FACTURACION, "Gestionar las series", "De donde sale el numero de cada factura"),
    FACTURACION_EXPORTAR(Grupo.FACTURACION, "Exportar para la gestoria"),
    INFORMES_VER(Grupo.FACTURACION, "Ver informes economicos", "Margenes, compras e IVA"),

    // ------------------------------------------------------------------
    // Almacen
    // ------------------------------------------------------------------
    ALMACEN_VER(Grupo.ALMACEN, "Consultar el almacen"),
    PIEZAS_CREAR(Grupo.ALMACEN, "Dar de alta piezas"),
    PIEZAS_EDITAR(Grupo.ALMACEN, "Editar piezas"),
    PIEZAS_PRECIOS(Grupo.ALMACEN, "Cambiar precios de piezas"),
    ALMACEN_MOVER(Grupo.ALMACEN, "Mover stock", "Entradas, salidas y ajustes de inventario"),
    PROVEEDORES_GESTIONAR(Grupo.ALMACEN, "Gestionar proveedores"),

    // ------------------------------------------------------------------
    // Agenda y plantillas
    // ------------------------------------------------------------------
    AGENDA_VER(Grupo.AGENDA, "Ver la agenda"),
    AGENDA_GESTIONAR(Grupo.AGENDA, "Dar y mover citas"),
    SERVICIOS_VER(Grupo.AGENDA, "Ver los servicios tipo"),
    SERVICIOS_GESTIONAR(Grupo.AGENDA, "Definir servicios tipo", "Las plantillas de trabajo y sus horas"),

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------
    AJUSTES_VER(Grupo.CONFIGURACION, "Ver los ajustes del taller"),
    AJUSTES_EDITAR(Grupo.CONFIGURACION, "Cambiar los ajustes del taller", "Datos fiscales, tarifa por hora"),
    USUARIOS_GESTIONAR(Grupo.CONFIGURACION, "Gestionar usuarios"),
    ROLES_GESTIONAR(Grupo.CONFIGURACION, "Gestionar roles y permisos");

    /** Bloques con los que se agrupan los permisos en la pantalla de roles. */
    public enum Grupo {
        CLIENTES("Clientes"),
        MOTOS("Motos"),
        ORDENES("Ordenes de trabajo"),
        DINERO("Dinero"),
        FACTURACION("Facturacion"),
        ALMACEN("Almacen"),
        AGENDA("Agenda y plantillas"),
        CONFIGURACION("Configuracion");

        private final String titulo;

        Grupo(String titulo) {
            this.titulo = titulo;
        }

        public String getTitulo() {
            return titulo;
        }
    }

    private final Grupo grupo;
    private final String descripcion;
    private final String detalle;

    Permiso(Grupo grupo, String descripcion) {
        this(grupo, descripcion, null);
    }

    Permiso(Grupo grupo, String descripcion, String detalle) {
        this.grupo = grupo;
        this.descripcion = descripcion;
        this.detalle = detalle;
    }

    public Grupo getGrupo() {
        return grupo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Aclaracion para los permisos cuyo nombre no basta. Puede ser nulo. */
    public String getDetalle() {
        return detalle;
    }

    public static List<Permiso> todos() {
        return Arrays.asList(values());
    }

    /**
     * Convierte el texto guardado en base de datos, ignorando lo que ya no
     * exista.
     *
     * <p>Un permiso retirado del catalogo no debe impedir que el rol cargue: se
     * ignora y punto. Lo contrario dejaria al taller sin poder entrar por una
     * fila huerfana.
     */
    public static Permiso deTextoONulo(String valor) {
        try {
            return valor == null ? null : Permiso.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
