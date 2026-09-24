import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { CommonModule } from '@angular/common';
import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { ColorEstadoPipe } from '../../compartido/estado-ot.pipe';
import { Moto, OrdenTrabajoResumen } from '../../nucleo/modelos/taller';
import { FormularioMoto } from './formulario-moto';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { Icono } from '../../compartido/icono';
import { MotosService } from '../../nucleo/servicios/motos.service';
import { OrdenesService } from '../../nucleo/servicios/ordenes.service';
import { PdfService } from '../../nucleo/servicios/pdf.service';
import { environment } from '../../../environments/environment';

/** Ficha de la moto con su historial completo de intervenciones. */
@Component({
  selector: 'app-detalle-moto',
  imports: [CommonModule, RouterLink, Cargando, ColorEstadoPipe, FormularioMoto, Icono],
  template: `
    @if (cargando()) {
      <app-cargando mensaje="Cargando moto…" />
    } @else if (moto(); as m) {
      @if (mostrarFormulario()) {
        <app-formulario-moto [moto]="m" (cerrar)="mostrarFormulario.set(false)" (guardado)="trasGuardar($event)" />
      }

      <div class="apilado">
        <a routerLink="/motos" class="pequeno">← Motos</a>

        <div class="pagina-cabecera">
          <div class="pagina-cabecera__texto">
            <h1>{{ m.matricula }}</h1>
            <p class="silenciado pequeno">
              {{ m.descripcion }} · propietario:
              <a [routerLink]="['/clientes', m.clienteId]">{{ m.clienteNombre }}</a>
            </p>
          </div>
          <div class="fila" style="gap: var(--e2); flex-wrap: nowrap">
            <!--
              El historial se entrega en mano al cliente cuando vende la moto,
              así que el botón vive en la cabecera de la ficha y no escondido al
              final de la lista de intervenciones.
            -->
            <button type="button" class="boton" (click)="abrirHistorial()">
              <app-icono nombre="documento" [tamano]="16" />
              Historial en PDF
            </button>
            @if (puedeEditar) {
              <button type="button" class="boton boton--principal" (click)="mostrarFormulario.set(true)">
                Editar
              </button>
            }
          </div>
        </div>

        <section class="tarjeta">
          <div class="tarjeta__titulo"><h2>Datos</h2></div>
          <dl class="datos">
            <dt>Marca y modelo</dt>
            <dd>{{ m.marca }} {{ m.modelo }}</dd>
            <dt>Año</dt>
            <dd>{{ m.anio || '—' }}</dd>
            <dt>Cilindrada</dt>
            <dd>{{ m.cilindrada ? m.cilindrada + ' cc' : '—' }}</dd>
            <dt>Color</dt>
            <dd>{{ m.color || '—' }}</dd>
            <dt>Bastidor</dt>
            <dd class="mono">{{ m.numeroBastidor || '—' }}</dd>
            <dt>Kilometraje</dt>
            <dd>{{ m.kmActual | number: '1.0-0' : 'es' }} km</dd>
          </dl>
        </section>

        <section class="tarjeta">
          <div class="tarjeta__titulo">
            <h2>Historial de intervenciones</h2>
            <span class="pequeno silenciado">{{ historial().length }} orden(es)</span>
          </div>
          @if (!historial().length) {
            <p class="vacio pequeno">Esta moto todavía no ha pasado por el taller.</p>
          } @else {
            <div class="tabla-envoltorio">
              <table>
                <thead>
                  <tr>
                    <th>Orden</th>
                    <th>Estado</th>
                    <th>Entrada</th>
                    <th>Salida</th>
                    <th class="num">Km</th>
                    <th>Avería</th>
                  </tr>
                </thead>
                <tbody>
                  @for (o of historial(); track o.id) {
                    <tr>
                      <td><a [routerLink]="['/ordenes', o.id]" class="negrita">{{ o.codigo }}</a></td>
                      <td>
                        <span class="etiqueta etiqueta--{{ o.estado | colorEstado }}">
                          {{ o.estadoDescripcion }}
                        </span>
                      </td>
                      <td class="pequeno">{{ o.fechaEntrada | date: 'dd/MM/yy' }}</td>
                      <td class="pequeno">
                        {{ o.fechaRealSalida ? (o.fechaRealSalida | date: 'dd/MM/yy') : '—' }}
                      </td>
                      <td class="num pequeno">{{ o.kmEntrada | number: '1.0-0' : 'es' }}</td>
                      <td class="pequeno silenciado">{{ o.problemaReportado }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      </div>
    } @else {
      <!-- Sin esto, un enlace viejo o el servidor caído dejaban la página en blanco. -->
      <div class="vacio">
        <span class="vacio__titulo">No se ha podido abrir esta moto</span>
        <span class="pequeno">Puede que ya no exista o que ahora mismo no haya conexión con el servidor.</span>
        <a routerLink="/motos" class="boton boton--pequeno">Volver a motos</a>
      </div>
    }
  `,
  styles: [
    `
      .datos {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 0.35rem 1rem;
        margin: 0;
        font-size: 0.9rem;
      }
      .datos dt { color: var(--gris-500); white-space: nowrap; }
      .datos dd { margin: 0; font-weight: 500; }
    `,
  ],
})
export class DetalleMoto {
  private readonly motos = inject(MotosService);
  private readonly ordenes = inject(OrdenesService);
  private readonly pdf = inject(PdfService);

  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly moto = signal<Moto | null>(null);
  protected readonly historial = signal<OrdenTrabajoResumen[]>([]);
  
  protected readonly mostrarFormulario = signal(false);
  protected readonly puedeEditar = inject(SesionService).tienePermiso('MOTOS_EDITAR');

  protected trasGuardar(m: Moto): void {
    this.moto.set(m);
    this.mostrarFormulario.set(false);
  }

  /** Hoja de vida de la moto, para imprimir o mandar por correo. */
  protected abrirHistorial(): void {
    const m = this.moto();
    if (!m) return;
    this.pdf.abrir(
      `${environment.urlApi}/motos/${m.id}/historial/pdf`,
      `historial-${m.matricula}.pdf`,
    );
  }

  constructor() {
    // Con otro id en la misma ruta Angular reutiliza la pantalla: hay que volver a cargar.
    effect(() => {
      this.id();
      untracked(() => {
        this.moto.set(null);
        this.cargando.set(true);
        this.cargar();
      });
    });
    // Con el formulario abierto no: le cambiaría la moto que se está editando.
    alCambiarDatos(() => {
      if (!this.mostrarFormulario()) this.cargar();
    });
  }

  private cargar(): void {
    const id = Number(this.id());
    this.motos.obtener(id).subscribe({
      next: (m) => {
        this.moto.set(m);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
    this.ordenes.historialDeMoto(id).subscribe((h) => this.historial.set(h));
  }
}
