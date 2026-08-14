import { CommonModule } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Cliente, MotoResumen } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';
import { PdfService } from '../../nucleo/servicios/pdf.service';
import { environment } from '../../../environments/environment';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { FormularioCliente } from './formulario-cliente';

@Component({
  selector: 'app-detalle-cliente',
  imports: [CommonModule, RouterLink, Cargando, FormularioCliente],
  template: `
    @if (editando(); as c) {
      <app-formulario-cliente
        [cliente]="c"
        (cerrar)="editando.set(null)"
        (guardado)="trasEditar($event)"
      />
    }

    @if (cargando()) {
      <app-cargando mensaje="Cargando cliente…" />
    } @else if (cliente(); as c) {
      <div class="apilado">
        <a routerLink="/clientes" class="pequeno">← Clientes</a>

        <div class="fila">
          <div class="crece">
            <h1>
              {{ c.nombreCompleto }}
              @if (!c.activo) {
                <span class="etiqueta etiqueta--gris">De baja</span>
              }
            </h1>
          </div>
          <div class="fila" style="gap: var(--e2); flex-wrap: nowrap">
            <!--
              El historial se entrega en mano al cliente: lleva todas sus motos,
              cada una con lo que se le ha hecho.
            -->
            <button type="button" class="boton" (click)="abrirHistorial(c)">
              Historial en PDF
            </button>
            @if (puedeEditar) {
              <button type="button" class="boton" (click)="editando.set(c)">Editar</button>
            }
          </div>
        </div>

        @if (!c.facturable) {
          <section class="tarjeta" style="border-left: 4px solid var(--ambar); background: var(--ambar-claro)">
            <p class="negrita" style="margin: 0">Este cliente todavía no se puede facturar</p>
            <p class="pequeno" style="margin: 0.3rem 0 0">
              Faltan datos fiscales: hacen falta documento, dirección, código postal, ciudad y
              provincia. La factura los copia dentro y ya no se pueden cambiar después.
            </p>
          </section>
        }

        <div class="rejilla" style="grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem">
          <section class="tarjeta">
            <div class="tarjeta__titulo"><h2>Contacto</h2></div>
            <dl class="datos">
              <dt>Teléfono</dt>
              <dd>{{ c.telefono || '—' }}</dd>
              <dt>Email</dt>
              <dd>{{ c.email || '—' }}</dd>
              @if (c.observaciones) {
                <dt>Notas</dt>
                <dd>{{ c.observaciones }}</dd>
              }
            </dl>
          </section>

          <section class="tarjeta">
            <div class="tarjeta__titulo">
              <h2>Datos fiscales</h2>
              @if (c.facturable) {
                <span class="etiqueta etiqueta--verde">Completos</span>
              }
            </div>
            <dl class="datos">
              <dt>Documento</dt>
              <dd class="mono">{{ c.documento || '—' }} <span class="pequeno silenciado">{{ c.tipoDocumento || '' }}</span></dd>
              <dt>Dirección</dt>
              <dd>{{ c.direccion || '—' }}</dd>
              <dt>Población</dt>
              <dd>
                {{ c.codigoPostal || '' }} {{ c.ciudad || '—' }}
                @if (c.provincia) { ({{ c.provincia }}) }
              </dd>
              <dt>País</dt>
              <dd>{{ c.pais }}</dd>
            </dl>
          </section>
        </div>

        <section class="tarjeta">
          <div class="tarjeta__titulo"><h2>Motos</h2></div>
          @if (!motos().length) {
            <p class="vacio pequeno">Este cliente no tiene ninguna moto registrada.</p>
          } @else {
            <div class="tabla-envoltorio">
              <table>
                <thead>
                  <tr>
                    <th>Matrícula</th>
                    <th>Moto</th>
                    <th>Año</th>
                    <th class="num">Km</th>
                  </tr>
                </thead>
                <tbody>
                  @for (m of motos(); track m.id) {
                    <tr>
                      <td><a [routerLink]="['/motos', m.id]" class="negrita">{{ m.matricula }}</a></td>
                      <td>{{ m.descripcion }}</td>
                      <td>{{ m.anio || '—' }}</td>
                      <td class="num">{{ m.kmActual | number: '1.0-0' : 'es' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
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
export class DetalleCliente {
  private readonly servicio = inject(ClientesService);
  private readonly pdf = inject(PdfService);

  readonly id = input.required<string>();

  protected readonly cargando = signal(true);
  protected readonly cliente = signal<Cliente | null>(null);
  protected readonly motos = signal<MotoResumen[]>([]);

  /** Cliente abierto en el formulario. Sin él, la ficha es solo de lectura. */
  protected readonly editando = signal<Cliente | null>(null);

  /** Corregir la ficha es cosa de mostrador y dirección, igual que darla de alta. */
  protected readonly puedeEditar = inject(SesionService).puede('ADMIN', 'MOSTRADOR');

  /** Hoja de vida del cliente: todas sus motos con su historial. */
  protected abrirHistorial(cliente: Cliente): void {
    this.pdf.abrir(
      `${environment.urlApi}/clientes/${cliente.id}/historial/pdf`,
      `historial-${cliente.nombreCompleto}.pdf`,
    );
  }

  /** El formulario devuelve la ficha ya guardada: no hace falta volver a pedirla. */
  protected trasEditar(actualizado: Cliente): void {
    this.editando.set(null);
    this.cliente.set(actualizado);
  }

  constructor() {
    queueMicrotask(() => {
      const id = Number(this.id());
      this.servicio.obtener(id).subscribe({
        next: (c) => {
          this.cliente.set(c);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
      this.servicio.motosDe(id).subscribe((m) => this.motos.set(m));
    });
  }
}
