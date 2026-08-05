import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { ClienteResumen } from '../../nucleo/modelos/taller';
import { ClientesService } from '../../nucleo/servicios/clientes.service';

@Component({
  selector: 'app-lista-clientes',
  imports: [CommonModule, FormsModule, RouterLink, Cargando],
  template: `
    <div class="apilado">
      <h1>Clientes</h1>

      <section class="tarjeta">
        <div class="fila">
          <div class="campo crece" style="margin: 0">
            <label for="buscar">Buscar</label>
            <input
              id="buscar"
              type="search"
              placeholder="Nombre, apellidos, NIF, teléfono o email…"
              [ngModel]="texto()"
              (ngModelChange)="texto.set($event)"
              (keyup.enter)="cargar()"
            />
          </div>
          <button type="button" class="boton boton--principal" (click)="cargar()" style="margin-top: 1.2rem">
            Buscar
          </button>
        </div>
        <label class="fila pequeno" style="gap: 0.4rem">
          <input
            type="checkbox"
            style="width: auto; min-height: auto"
            [ngModel]="soloActivos()"
            (ngModelChange)="soloActivos.set($event); cargar()"
          />
          Ocultar los dados de baja
        </label>
      </section>

      <section class="tarjeta">
        <div class="tarjeta__titulo"><h2>{{ totalItems() }} cliente(s)</h2></div>

        @if (cargando()) {
          <app-cargando mensaje="Cargando clientes…" />
        } @else if (!filas().length) {
          <p class="vacio">No se ha encontrado ningún cliente.</p>
        } @else {
          <div class="tabla-envoltorio">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Documento</th>
                  <th>Teléfono</th>
                  <th>Email</th>
                  <th>Facturable</th>
                </tr>
              </thead>
              <tbody>
                @for (c of filas(); track c.id) {
                  <tr>
                    <td>
                      <a [routerLink]="['/clientes', c.id]" class="negrita">{{ c.nombreCompleto }}</a>
                      @if (!c.activo) {
                        <span class="etiqueta etiqueta--gris">De baja</span>
                      }
                    </td>
                    <td class="mono">{{ c.documento || '—' }}</td>
                    <td>{{ c.telefono || '—' }}</td>
                    <td class="pequeno">{{ c.email || '—' }}</td>
                    <td>
                      @if (c.facturable) {
                        <span class="etiqueta etiqueta--verde">Sí</span>
                      } @else {
                        <span class="etiqueta etiqueta--ambar" title="Faltan datos fiscales">
                          Faltan datos
                        </span>
                      }
                    </td>
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
export class ListaClientes {
  private readonly servicio = inject(ClientesService);

  protected readonly cargando = signal(true);
  protected readonly filas = signal<ClienteResumen[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly texto = signal('');
  protected readonly soloActivos = signal(true);

  constructor() {
    this.cargar();
  }

  protected cargar(pagina = 0): void {
    this.cargando.set(true);
    this.servicio.buscar(this.texto(), this.soloActivos(), pagina).subscribe({
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
