import {
  CUSTOM_ELEMENTS_SCHEMA,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { Camara, CamarasService } from '../../nucleo/servicios/camaras.service';

/** Cuántas columnas tiene el mosaico. `auto` reparte según cuántas cámaras haya. */
type Rejilla = 'auto' | 1 | 2 | 3;

/**
 * Mosaico de cámaras del taller.
 *
 * Directo, sin grabación ni histórico: esto sirve para asomarse, no para
 * revisar lo que pasó anoche. Guardar vídeo es otro asunto —hace falta disco,
 * detección de movimiento y una política de borrado— y para eso está Frigate.
 *
 * La lista de cámaras la manda el servidor, no este fichero: sale de
 * `infra/camaras/go2rtc.yaml`. Añadir una cámara no toca código.
 *
 * `CUSTOM_ELEMENTS_SCHEMA` es por `<camara-video>`, que no es un componente de
 * Angular sino un elemento del navegador registrado a mano. Sin esto, Angular
 * no reconoce la etiqueta y no compila.
 */
@Component({
  selector: 'app-camaras',
  imports: [Cargando, Icono],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './camaras.html',
  styleUrl: './camaras.scss',
})
export class Camaras {
  private readonly servicio = inject(CamarasService);
  private readonly mosaico = viewChild<ElementRef<HTMLElement>>('mosaico');

  protected readonly camaras = signal<Camara[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly rejilla = signal<Rejilla>('auto');

  /** Cámara ampliada a pantalla completa dentro del mosaico, si hay alguna. */
  protected readonly ampliada = signal<string | null>(null);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      // El reproductor tiene que estar registrado antes de pintar ninguna
      // cámara, así que se esperan las dos cosas juntas y no se pinta nada
      // hasta que ambas están listas.
      const [camaras] = await Promise.all([
        this.servicio.listar(),
        this.servicio.cargarReproductor(),
      ]);
      this.camaras.set(camaras);
    } catch {
      // El motivo casi siempre es el mismo, y no merece la pena distinguirlo:
      // el servicio de cámaras no está levantado. El mensaje dice qué hacer.
      this.error.set(
        'No se ha podido contactar con el servicio de cámaras. Compruebe que está ' +
          'arrancado: docker compose --profile camaras up -d',
      );
    } finally {
      this.cargando.set(false);
    }
  }

  protected alternarAmpliada(nombre: string): void {
    this.ampliada.update((actual) => (actual === nombre ? null : nombre));
  }

  protected cambiarRejilla(r: Rejilla): void {
    this.rejilla.set(r);
    this.ampliada.set(null);
  }

  /**
   * Pantalla completa de verdad, la del navegador.
   *
   * En el mostrador la pantalla de cámaras se deja puesta todo el día, y con la
   * barra lateral y el menú alrededor se desaprovecha la mitad del monitor.
   */
  protected pantallaCompleta(): void {
    const elemento = this.mosaico()?.nativeElement;
    if (!elemento) return;

    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      // Si el navegador lo deniega no hay nada que hacer ni nada que avisar: la
      // pantalla se sigue viendo igual, solo que sin ocupar todo el monitor.
      void elemento.requestFullscreen().catch(() => {});
    }
  }
}
