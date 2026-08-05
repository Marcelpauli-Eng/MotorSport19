import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { MotoResumen } from '../../nucleo/modelos/taller';
import { MotosService } from '../../nucleo/servicios/motos.service';

@Component({
  selector: 'app-lista-motos',
  imports: [CommonModule, FormsModule, RouterLink, Cargando],
  template: `
    <div class="apilado">
      <h1>Motos</h1>

      <section class="tarjeta">
        <div class="fila">
          <div class="campo crece" style="margin: 0">
            <label for="buscar">Buscar</label>
            <input
              id="buscar"
              type="search"
              placeholder="Matrícula, marca, modelo o bastidor…"
              [ngModel]="texto()"
              (ngModelChange)="texto.set($event)"
              (keyup.enter)="cargar()"
            />
          </div>
          <button type="button" class="boton boton--principal" (click)="cargar()" style="margin-top: 1.2rem">
            Buscar
          </button>
        </div>
      </section>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><h2>{{ totalItems() }} moto(s)</h2></div>

        @if (cargando()) {
          <app-cargando mensaje="Cargando motos…" />
        } @else if (!filas().length) {
          <p class="vacio">No se ha encontrado ninguna moto.</p>
        } @else {
          <div class="tabla-envoltorio">
            <table>
              <thead>
                <tr>
                  <th>Matrícula</th>
                  <th>Moto</th>
                  <th>Año</th>
                  <th class="num">Kilómetros</th>
                </tr>
              </thead>
              <tbody>
                @for (m of filas(); track m.id) {
                  <tr>
                    <td>
                      <a [routerLink]="['/motos', m.id]" class="negrita">{{ m.matricula }}</a>
                      @if (!m.activo) {
                        <span class="etiqueta etiqueta--gris">De baja</span>
                      }
                    </td>
                    <td>{{ m.descripcion }}</td>
                    <td>{{ m.anio || '—' }}</td>
                    <td class="num">{{ m.kmActual | number: '1.0-0' : 'es' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (totalPaginas() > 1) {
            <div class="fila fila--fin" style="margin-top: 1rem">
              <button
                type="button"
                class="boton boton--pequeno"
                [disabled]="pagina() === 0"
                (click)="cargar(pagina() - 1)"
              >
                ← Anterior
              </button>
              <span class="pequeno silenciado">Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
              <button
                type="button"
                class="boton boton--pequeno"
                [disabled]="pagina() + 1 >= totalPaginas()"
                (click)="cargar(pagina() + 1)"
              >
                Siguiente →
              </button>
            </div>
          }
        }
      </section>
    </div>
  `,
})
export class ListaMotos {
  private readonly servicio = inject(MotosService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<MotoResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly texto = signal('');

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.servicio.buscar(this.texto(), true, pagina).subscribe({
      next: (p) => {
        this.filas.set(p.contenido);
        this.totalItems.set(p.totalItems);
        this.pagina.set(p.pagina);
        this.totalPaginas.set(p.totalPaginas);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
