import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface NhtsaResponse<T> {
  Count: number;
  Message: string;
  SearchCriteria: string;
  Results: T[];
}

export interface NhtsaMake {
  MakeId: number;
  MakeName: string;
  VehicleTypeId: number;
  VehicleTypeName: string;
}

export interface NhtsaModel {
  Make_ID: number;
  Make_Name: string;
  Model_ID: number;
  Model_Name: string;
  VehicleTypeId: number;
  VehicleTypeName: string;
}

@Injectable({ providedIn: 'root' })
export class NhtsaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'https://vpic.nhtsa.dot.gov/api/vehicles';

  /** Obtiene todas las marcas que fabrican motocicletas. */
  obtenerMarcasMotos(): Observable<string[]> {
    return this.http
      .get<NhtsaResponse<NhtsaMake>>(`${this.baseUrl}/GetMakesForVehicleType/motorcycle?format=json`)
      .pipe(
        map((res) => {
          // Extraemos los nombres y los ordenamos alfabéticamente
          const nombres = res.Results.map((m) => m.MakeName.toUpperCase());
          return [...new Set(nombres)].sort();
        }),
      );
  }

  /**
   * Obtiene todos los modelos de una marca concreta.
   * Filtramos en el cliente porque el endpoint de modelos por marca
   * devuelve modelos de todos los tipos de vehículos que hace el fabricante
   * (por ejemplo, Honda hace coches y motos).
   */
  obtenerModelosMoto(marca: string): Observable<string[]> {
    const marcaCodificada = encodeURIComponent(marca.trim());
    return this.http
      .get<NhtsaResponse<NhtsaModel>>(
        `${this.baseUrl}/GetModelsForMakeYear/make/${marcaCodificada}/vehicletype/motorcycle?format=json`
      )
      .pipe(
        map((res) => {
          // Extraemos el nombre de modelo y ordenamos
          const modelosMoto = res.Results.map((m) => m.Model_Name.toUpperCase());
            
          return [...new Set(modelosMoto)].sort();
        }),
      );
  }
}
