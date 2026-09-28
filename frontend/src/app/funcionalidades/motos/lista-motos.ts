import { alCambiarDatos } from '../../nucleo/servicios/tiempo-real.service';
import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Cargando } from '../../compartido/cargando';
import { Icono } from '../../compartido/icono';
import { CampoImportable, Importar } from '../../compartido/importar';
import { FormularioMoto } from './formulario-moto';
import { SesionService } from '../../nucleo/servicios/sesion.service';
import { MotoResumen } from '../../nucleo/modelos/taller';
import { MotosService } from '../../nucleo/servicios/motos.service';

@Component({
  selector: 'app-lista-motos',
  imports: [CommonModule, FormsModule, RouterLink, Cargando, Icono, FormularioMoto, Importar],
  template: `
    @if (mostrarFormulario()) {
      <app-formulario-moto (cerrar)="mostrarFormulario.set(false)" (guardado)="trasGuardar()" />
    }

    <div class="pagina-cabecera">
      <div class="pagina-cabecera__texto">
        <h1>Motos</h1>
        <p class="pagina-cabecera__sub">
          {{ totalItems() }} {{ totalItems() === 1 ? 'moto registrada' : 'motos registradas' }}
        </p>
      </div>
      <div class="fila">
        @if (puedeImportar) {
          <app-importar
            ruta="motos"
            titulo="Importar motos"
            [campos]="camposImportacion"
            ayuda="En Cliente va el NIF del propietario o su nombre completo tal y como está en su ficha, así que importe antes los clientes. Si marca y modelo vienen juntos en una columna Denominación («Aprilia RS660»), la primera palabra se toma como marca."
            (importado)="cargar()"
          />
        }
        @if (puedeEditar) {
          <button type="button" class="boton boton--principal" (click)="mostrarFormulario.set(true)">
            <app-icono nombre="mas" [tamano]="16" />
            Nueva moto
          </button>
        }
      </div>
    </div>

    <div class="filtros">
      <div class="campo filtros__crece">
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
      <button type="button" class="boton" (click)="cargar()">
        <app-icono nombre="buscar" [tamano]="16" />
        Buscar
      </button>
    </div>

    <section class="tarjeta tarjeta--ajustada">
        @if (cargando()) {
          <app-cargando mensaje="Cargando motos…" />
        } @else if (!filas().length) {
          <div class="vacio">
            <app-icono class="vacio__icono" nombre="motos" [tamano]="30" />
            <span class="vacio__titulo">Ninguna moto encontrada</span>
            <span class="pequeno">Prueba con la matrícula, la marca o el bastidor.</span>
          </div>
        } @else {
          <div class="tabla-envoltorio">
            <table>
              <thead>
                <tr>
                  <th>Matrícula</th>
                  <th>Moto</th>
                  <th>Año</th>
                  <th class="num">Kilometraje</th>
                </tr>
              </thead>
              <tbody>
                @for (m of filas(); track m.id) {
                  <tr>
                    <td>
                      <div class="fila" style="gap: 6px; flex-wrap: nowrap">
                        <a [routerLink]="['/motos', m.id]" class="codigo">{{ m.matricula }}</a>
                        @if (!m.activo) {
                          <span class="etiqueta etiqueta--gris etiqueta--simple">De baja</span>
                        }
                      </div>
                    </td>
                    <td class="celda-doble__principal">{{ m.descripcion }}</td>
                    <td class="silenciado">{{ m.anio || '—' }}</td>
                    <td class="num importe">{{ m.kmActual | number: '1.0-0' : 'es' }} km</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          @if (totalPaginas() > 1) {
            <div class="paginacion">
              <span class="pequeno silenciado">Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
              <div class="fila">
                <button
                  type="button"
                  class="boton boton--pequeno"
                  [disabled]="pagina() === 0"
                  (click)="cargar(pagina() - 1)"
                >
                  <app-icono nombre="flecha-izquierda" [tamano]="14" /> Anterior
                </button>
                <button
                  type="button"
                  class="boton boton--pequeno"
                  [disabled]="pagina() + 1 >= totalPaginas()"
                  (click)="cargar(pagina() + 1)"
                >
                  Siguiente <app-icono nombre="flecha-derecha" [tamano]="14" />
                </button>
              </div>
            </div>
          }
        }
    </section>
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
  protected readonly mostrarFormulario = signal(false);
  protected readonly puedeEditar = inject(SesionService).tienePermiso('MOTOS_CREAR', 'MOTOS_EDITAR');
  protected readonly puedeImportar = inject(SesionService).tienePermiso('MOTOS_CREAR');

  protected readonly camposImportacion: CampoImportable[] = [
    { campo: 'matricula', etiqueta: 'Matrícula', obligatorio: true },
    { campo: 'cliente', etiqueta: 'Cliente', obligatorio: true, alias: ['propietario', 'titular'] },
    { campo: 'marca', etiqueta: 'Marca', obligatorio: true, oBien: 'denominacion' },
    { campo: 'modelo', etiqueta: 'Modelo', obligatorio: true, oBien: 'denominacion' },
    { campo: 'denominacion', etiqueta: 'Denominación', alias: ['denominacion comercial', 'marca y modelo'] },
    { campo: 'anio', etiqueta: 'Año', tipo: 'entero' },
    { campo: 'cilindrada', etiqueta: 'Cilindrada', alias: ['cc'], tipo: 'entero' },
    { campo: 'color', etiqueta: 'Color' },
    { campo: 'numeroBastidor', etiqueta: 'Bastidor', alias: ['numero de bastidor', 'vin', 'chasis'] },
    { campo: 'kmActual', etiqueta: 'Kilómetros', alias: ['km', 'kilometraje'], tipo: 'entero' },
    { campo: 'observaciones', etiqueta: 'Observaciones', alias: ['notas'] },
  ];

  protected trasGuardar(): void {
    this.mostrarFormulario.set(false);
    this.cargar();
  }

  constructor() {
    alCambiarDatos(() => this.cargar(this.pagina()));
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
