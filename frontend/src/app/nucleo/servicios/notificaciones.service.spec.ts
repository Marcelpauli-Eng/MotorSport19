import { NotificacionesService } from './notificaciones.service';

describe('Avisos', () => {
  it('el mismo aviso a la vez no se apila: con el servidor caído salían cinco iguales', () => {
    const avisos = new NotificacionesService();

    for (let i = 0; i < 5; i++) {
      avisos.error('El servidor no está disponible ahora mismo.', [`502 · GET /peticion/${i}`]);
    }
    avisos.info('El servidor no está disponible ahora mismo.');

    // Un error y una información: mismo texto, pero no son el mismo aviso.
    expect(avisos.avisos()).toHaveLength(2);
  });

  it('cerrado el aviso, el mismo texto puede volver a salir', () => {
    const avisos = new NotificacionesService();
    avisos.exito('Guardado.');
    avisos.cerrar(avisos.avisos()[0].id);

    avisos.exito('Guardado.');

    expect(avisos.avisos()).toHaveLength(1);
  });
});
