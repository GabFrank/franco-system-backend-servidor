# Guía de Implementación Frontend - ClienteResponse

## Resumen de Cambios

Se ha agregado un nuevo endpoint `clientePorPersonaDocumentoDetallado` que retorna un objeto `ClienteResponse` con información detallada, incluyendo warnings y errores. El endpoint original `clientePorPersonaDocumento` se mantiene para compatibilidad con el frontend legacy.

### Compatibilidad Legacy

- **`clientePorPersonaDocumento`** (legacy): Retorna `Cliente` directamente. Solo retorna cliente si tiene ID válido (fue guardado exitosamente). Retorna `null` si no se pudo guardar.
- **`clientePorPersonaDocumentoDetallado`** (nuevo): Retorna `ClienteResponse` con información completa, incluyendo datos básicos cuando no se puede guardar.

### ¿Cuándo usar cada uno?

- **Frontend Legacy**: Continúa usando `clientePorPersonaDocumento` - funcionará igual que antes
- **Frontend Nuevo**: Usa `clientePorPersonaDocumentoDetallado` para obtener información completa incluso cuando el servidor central está offline

## Estructura de la Respuesta

### Tipo ClienteResponse

```graphql
type ClienteResponse {
    cliente: Cliente              # Cliente completo (si fue guardado exitosamente)
    datosBasicos: ClienteDatosBasicos  # Datos básicos (si no se pudo guardar)
    warnings: [String]!           # Lista de advertencias
    errores: [String]!            # Lista de errores
    exito: Boolean                # Indica si la operación fue exitosa
}
```

### Tipo ClienteDatosBasicos

```graphql
type ClienteDatosBasicos {
    ruc: String
    razonSocial: String
    direccion: String
    estado: String
    estadoContribuyente: String
    tributa: Boolean
    tipoContribuyente: Int
    telefono: String
    nombreFantasia: String
    dv: String
}
```

## Queries GraphQL Disponibles

### Método Legacy (mantiene compatibilidad)

```graphql
query ClientePorDocumento($texto: String) {
  clientePorPersonaDocumento(texto: $texto) {
    id
    nombre
    documento
    persona {
      id
      nombre
      direccion
    }
    tributa
    verificadoSet
    tipoContribuyente
    # ... otros campos del cliente
  }
}
```

**Comportamiento**: 
- Retorna `Cliente` si fue guardado exitosamente (tiene ID)
- Retorna `null` si no se pudo guardar (servidor central offline)

### Método Nuevo (recomendado para nuevos desarrollos)

```graphql
query ClientePorDocumentoDetallado($texto: String) {
  clientePorPersonaDocumentoDetallado(texto: $texto) {
    exito
    warnings
    errores
    cliente {
      id
      nombre
      documento
      persona {
        id
        nombre
        direccion
      }
      tributa
      verificadoSet
      tipoContribuyente
      # ... otros campos del cliente
    }
    datosBasicos {
      ruc
      razonSocial
      direccion
      estado
      estadoContribuyente
      tributa
      tipoContribuyente
      telefono
      nombreFantasia
      dv
    }
  }
}
```

**Comportamiento**:
- Siempre retorna información útil
- Si el cliente fue guardado: `cliente` contiene los datos completos
- Si no se pudo guardar: `datosBasicos` contiene la información de SIFEN
- Incluye `warnings` y `errores` para mejor manejo

## Implementación en el Frontend

### 1. Actualizar el Tipo/Interfaz TypeScript

```typescript
interface ClienteDatosBasicos {
  ruc?: string | null;
  razonSocial?: string | null;
  direccion?: string | null;
  estado?: string | null;
  estadoContribuyente?: string | null;
  tributa?: boolean | null;
  tipoContribuyente?: number | null;
  telefono?: string | null;
  nombreFantasia?: string | null;
  dv?: string | null;
}

interface ClienteResponse {
  cliente?: Cliente | null;
  datosBasicos?: ClienteDatosBasicos | null;
  warnings: string[];
  errores: string[];
  exito?: boolean | null;
}
```

### 2. Función para Obtener Cliente (Método Nuevo)

```typescript
async function obtenerClientePorDocumentoDetallado(documento: string): Promise<ClienteResponse> {
  const query = gql`
    query ClientePorDocumentoDetallado($texto: String) {
      clientePorPersonaDocumentoDetallado(texto: $texto) {
        exito
        warnings
        errores
        cliente {
          id
          nombre
          documento
          persona {
            id
            nombre
            direccion
          }
          tributa
          verificadoSet
          tipoContribuyente
        }
        datosBasicos {
          ruc
          razonSocial
          direccion
          estado
          estadoContribuyente
          tributa
          tipoContribuyente
          telefono
          nombreFantasia
          dv
        }
      }
    }
  `;

  const result = await apolloClient.query({
    query,
    variables: { texto: documento }
  });

  return result.data.clientePorPersonaDocumentoDetallado;
}
```

### 2b. Función Legacy (para compatibilidad)

```typescript
async function obtenerClientePorDocumento(documento: string): Promise<Cliente | null> {
  const query = gql`
    query ClientePorDocumento($texto: String) {
      clientePorPersonaDocumento(texto: $texto) {
        id
        nombre
        documento
        persona {
          id
          nombre
          direccion
        }
        tributa
        verificadoSet
        tipoContribuyente
      }
    }
  `;

  const result = await apolloClient.query({
    query,
    variables: { texto: documento }
  });

  return result.data.clientePorPersonaDocumento;
}
```

### 3. Función Helper para Normalizar la Respuesta

```typescript
interface ClienteNormalizado {
  id?: string | null;
  nombre?: string | null;
  razonSocial?: string | null;
  documento?: string | null;
  ruc?: string | null;
  direccion?: string | null;
  tributa?: boolean | null;
  tipoContribuyente?: number | null;
  verificadoSet?: boolean | null;
  // ... otros campos necesarios
}

function normalizarClienteResponse(response: ClienteResponse): ClienteNormalizado | null {
  // Si tenemos el cliente completo, usarlo
  if (response.cliente) {
    return {
      id: response.cliente.id,
      nombre: response.cliente.persona?.nombre || response.cliente.nombre,
      razonSocial: response.cliente.persona?.nombre || response.cliente.nombre,
      documento: response.cliente.documento || response.cliente.persona?.documento,
      ruc: response.cliente.documento || response.cliente.persona?.documento,
      direccion: response.cliente.persona?.direccion,
      tributa: response.cliente.tributa,
      tipoContribuyente: response.cliente.tipoContribuyente,
      verificadoSet: response.cliente.verificadoSet,
    };
  }

  // Si no, usar datosBasicos
  if (response.datosBasicos) {
    return {
      id: null, // No tiene ID porque no se guardó
      nombre: response.datosBasicos.razonSocial,
      razonSocial: response.datosBasicos.razonSocial,
      documento: response.datosBasicos.ruc,
      ruc: response.datosBasicos.ruc,
      direccion: response.datosBasicos.direccion,
      tributa: response.datosBasicos.tributa,
      tipoContribuyente: response.datosBasicos.tipoContribuyente,
      verificadoSet: true, // Viene de SIFEN, está verificado
    };
  }

  return null;
}
```

### 4. Uso en el Componente (Método Nuevo)

```typescript
async function buscarCliente(documento: string) {
  try {
    const response = await obtenerClientePorDocumentoDetallado(documento);

    // Verificar si hay errores críticos
    if (response.errores && response.errores.length > 0) {
      // Mostrar errores al usuario
      mostrarErrores(response.errores);
      return null;
    }

    // Mostrar warnings si existen (informativos)
    if (response.warnings && response.warnings.length > 0) {
      mostrarWarnings(response.warnings);
    }

    // Normalizar la respuesta para usar en el componente
    const clienteNormalizado = normalizarClienteResponse(response);

    if (!clienteNormalizado) {
      mostrarError('No se pudo obtener información del cliente');
      return null;
    }

    // Verificar si el cliente tiene ID (fue guardado) o solo datos básicos
    if (!clienteNormalizado.id) {
      // Cliente sin ID - no se guardó en servidor central
      // Mostrar advertencia pero permitir continuar
      mostrarAdvertencia(
        'El cliente no se pudo guardar en el servidor central, ' +
        'pero puedes generar la factura con la información de SIFEN.'
      );
    }

    return clienteNormalizado;

  } catch (error) {
    console.error('Error al buscar cliente:', error);
    mostrarError('Error al buscar el cliente');
    return null;
  }
}
```

### 4b. Uso Legacy (para compatibilidad)

```typescript
async function buscarClienteLegacy(documento: string) {
  try {
    const cliente = await obtenerClientePorDocumento(documento);

    if (!cliente) {
      mostrarError('No se pudo obtener el cliente. El servidor central puede estar offline.');
      return null;
    }

    return cliente;

  } catch (error) {
    console.error('Error al buscar cliente:', error);
    mostrarError('Error al buscar el cliente');
    return null;
  }
}
```

### 5. Ejemplo Completo con React/Angular

#### React Example

```typescript
import { useState } from 'react';

function BuscarClienteComponent() {
  const [cliente, setCliente] = useState<ClienteNormalizado | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [errores, setErrores] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const buscarCliente = async (documento: string) => {
    setLoading(true);
    setWarnings([]);
    setErrores([]);

    try {
      const response = await obtenerClientePorDocumento(documento);

      if (response.errores && response.errores.length > 0) {
        setErrores(response.errores);
        setCliente(null);
        return;
      }

      if (response.warnings && response.warnings.length > 0) {
        setWarnings(response.warnings);
      }

      const clienteNormalizado = normalizarClienteResponse(response);
      setCliente(clienteNormalizado);

    } catch (error) {
      setErrores(['Error al buscar el cliente']);
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {/* Formulario de búsqueda */}
      <input 
        type="text" 
        placeholder="Ingrese RUC/Documento"
        onBlur={(e) => buscarCliente(e.target.value)}
      />

      {/* Mostrar errores */}
      {errores.length > 0 && (
        <div className="alert alert-danger">
          {errores.map((error, index) => (
            <div key={index}>{error}</div>
          ))}
        </div>
      )}

      {/* Mostrar warnings */}
      {warnings.length > 0 && (
        <div className="alert alert-warning">
          {warnings.map((warning, index) => (
            <div key={index}>{warning}</div>
          ))}
        </div>
      )}

      {/* Mostrar información del cliente */}
      {cliente && (
        <div>
          <h3>{cliente.razonSocial}</h3>
          <p>RUC: {cliente.ruc}</p>
          <p>Dirección: {cliente.direccion}</p>
          {!cliente.id && (
            <div className="alert alert-info">
              ⚠️ Cliente no guardado en servidor central. 
              Puedes generar la factura con esta información.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
```

#### Angular Example

```typescript
import { Component } from '@angular/core';

@Component({
  selector: 'app-buscar-cliente',
  template: `
    <div>
      <input 
        type="text" 
        placeholder="Ingrese RUC/Documento"
        (blur)="buscarCliente($event.target.value)"
      />

      <div *ngIf="errores.length > 0" class="alert alert-danger">
        <div *ngFor="let error of errores">{{ error }}</div>
      </div>

      <div *ngIf="warnings.length > 0" class="alert alert-warning">
        <div *ngFor="let warning of warnings">{{ warning }}</div>
      </div>

      <div *ngIf="cliente">
        <h3>{{ cliente.razonSocial }}</h3>
        <p>RUC: {{ cliente.ruc }}</p>
        <p>Dirección: {{ cliente.direccion }}</p>
        <div *ngIf="!cliente.id" class="alert alert-info">
          ⚠️ Cliente no guardado en servidor central. 
          Puedes generar la factura con esta información.
        </div>
      </div>
    </div>
  `
})
export class BuscarClienteComponent {
  cliente: ClienteNormalizado | null = null;
  warnings: string[] = [];
  errores: string[] = [];
  loading = false;

  async buscarCliente(documento: string) {
    this.loading = true;
    this.warnings = [];
    this.errores = [];

    try {
      const response = await this.obtenerClientePorDocumento(documento);

      if (response.errores && response.errores.length > 0) {
        this.errores = response.errores;
        this.cliente = null;
        return;
      }

      if (response.warnings && response.warnings.length > 0) {
        this.warnings = response.warnings;
      }

      this.cliente = this.normalizarClienteResponse(response);

    } catch (error) {
      this.errores = ['Error al buscar el cliente'];
      console.error(error);
    } finally {
      this.loading = false;
    }
  }

  // ... métodos obtenerClientePorDocumento y normalizarClienteResponse
}
```

## Casos de Uso

### Caso 1: Cliente guardado exitosamente
```json
{
  "exito": true,
  "cliente": {
    "id": "123",
    "nombre": "JUAN PEREZ",
    "documento": "1234567",
    // ... otros campos
  },
  "datosBasicos": null,
  "warnings": [],
  "errores": []
}
```

### Caso 2: Servidor central offline (solo datos básicos)
```json
{
  "exito": false,
  "cliente": null,
  "datosBasicos": {
    "ruc": "1234567",
    "razonSocial": "JUAN PEREZ",
    "direccion": "Av. Principal 123",
    "estado": "ACTIVO",
    "tributa": true,
    "tipoContribuyente": 1
  },
  "warnings": [
    "No se pudo guardar cliente en servidor central: Connection refused"
  ],
  "errores": []
}
```

### Caso 3: Error de validación
```json
{
  "exito": false,
  "cliente": null,
  "datosBasicos": null,
  "warnings": [],
  "errores": [
    "El cliente no es contribuyente de SET"
  ]
}
```

## Consideraciones Importantes

1. **Siempre verificar `exito`**: Indica si la operación fue exitosa
2. **Manejar `errores`**: Si hay errores, no se puede continuar
3. **Mostrar `warnings`**: Son informativos, pero se puede continuar
4. **Usar `cliente` si existe**: Tiene ID y está guardado
5. **Usar `datosBasicos` si no hay `cliente`**: No tiene ID pero tiene información válida de SIFEN
6. **Validar antes de generar factura**: Aunque no tenga ID, se puede generar la factura con los datos básicos

## Migración

### Para Frontend Legacy (sin cambios necesarios)

El frontend legacy puede continuar usando `clientePorPersonaDocumento` sin cambios. El comportamiento es el mismo:
- Retorna `Cliente` si fue guardado exitosamente
- Retorna `null` si no se pudo guardar

### Para Frontend Nuevo (migración opcional)

1. **Actualizar la query GraphQL**: Cambiar de `clientePorPersonaDocumento` a `clientePorPersonaDocumentoDetallado`
2. **Actualizar los tipos/interfaces TypeScript**: Agregar `ClienteResponse` y `ClienteDatosBasicos`
3. **Crear función helper**: Función para normalizar la respuesta
4. **Actualizar componentes**: Usar el nuevo método y manejar `ClienteResponse`
5. **Agregar manejo de warnings y errores**: Mostrar información adicional en la UI
6. **Probar casos**: Servidor central online y offline

### Estrategia de Migración Gradual

Puedes migrar gradualmente:
1. Mantener el código legacy funcionando
2. Agregar el nuevo método en paralelo
3. Migrar componentes uno por uno
4. Eventualmente deprecar el método legacy (si es necesario)

## Testing

### Método Legacy (`clientePorPersonaDocumento`)
- ✅ Cliente encontrado y guardado exitosamente → Retorna `Cliente`
- ✅ Cliente encontrado pero servidor central offline → Retorna `null`
- ✅ Cliente no encontrado en SIFEN → Retorna `null` o lanza excepción
- ✅ Cliente no es contribuyente → Lanza excepción GraphQL

### Método Nuevo (`clientePorPersonaDocumentoDetallado`)
- ✅ Cliente encontrado y guardado exitosamente → `cliente` con datos, `exito: true`
- ✅ Cliente encontrado pero servidor central offline → `datosBasicos` con información, `warnings` con mensaje, `exito: false`
- ✅ Cliente no encontrado en SIFEN → `errores` con mensaje, `exito: false`
- ✅ Cliente no es contribuyente → `errores` con mensaje, `exito: false`
- ✅ Mostrar warnings correctamente en la UI
- ✅ Generar factura con cliente sin ID (usando `datosBasicos`)

## Comparación de Métodos

| Característica | Legacy | Nuevo |
|---------------|--------|-------|
| Retorna cuando se guarda exitosamente | `Cliente` | `ClienteResponse.cliente` |
| Retorna cuando servidor offline | `null` | `ClienteResponse.datosBasicos` |
| Información de errores | Excepción GraphQL | `ClienteResponse.errores` |
| Información de warnings | No disponible | `ClienteResponse.warnings` |
| Compatibilidad | ✅ Mantiene comportamiento anterior | ⚠️ Requiere cambios en frontend |
| Recomendado para | Frontend existente | Nuevos desarrollos |

