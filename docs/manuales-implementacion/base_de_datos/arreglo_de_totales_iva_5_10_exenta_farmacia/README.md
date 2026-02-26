# Manual: Arreglo de Totales IVA (5%, 10%, Exenta) - Farmacia

## 📋 Descripción General

Este conjunto de scripts SQL está diseñado para corregir problemas de cálculo de IVA en las facturas legales del sistema. Los scripts solucionan dos problemas principales:

1. **Items de factura con IVA NULL**: Items que no tienen el campo `iva` poblado correctamente
2. **Totales parciales incorrectos**: Facturas donde los totales por categoría de IVA (exenta, 5%, 10%) no coinciden con la suma real de los items

## 📁 Archivos Incluidos

### 1. `update_factura_legal_item_iva.sql`
**Propósito**: Actualiza el campo `iva` de los items de factura legal que tienen valor `NULL`, basándose en el IVA del producto relacionado.

**Qué hace**:
- Busca items de factura legal con `iva = NULL`
- Determina el IVA correcto según la prioridad:
  1. Si el item ya tiene `iva`, lo mantiene (no actualiza)
  2. Si tiene `producto_id` directo, usa `producto.iva`
  3. Si tiene `presentacion_id`, usa `presentacion.producto.iva`
  4. Si tiene `venta_item_id`, usa `venta_item.producto.iva`
  5. Si no tiene ninguna relación, deja `NULL` (requiere atención manual)

### 2. `update_factura_legal_totales.sql`
**Propósito**: Recalcula y actualiza los totales parciales de IVA (`total_parcial_0`, `total_parcial_5`, `total_parcial_10`) y los IVAs parciales (`iva_parcial_5`, `iva_parcial_10`) en las facturas legales.

**Qué hace**:
- Suma los items por categoría de IVA (0%, 5%, 10%)
- Aplica el descuento proporcionalmente a cada categoría
- Calcula los IVAs sobre los montos con descuento aplicado
- Actualiza `total_final` como suma de los parciales

## 🔄 Orden de Ejecución

**IMPORTANTE**: Siempre ejecutar los scripts en este orden:

1. **Primero**: `update_factura_legal_item_iva.sql` (corrige los IVAs de los items)
2. **Segundo**: `update_factura_legal_totales.sql` (recalcula los totales de las facturas)

**Razón**: Los totales de las facturas dependen de que los items tengan el IVA correcto.

## 📝 Guía de Uso

### Paso 1: Preparación

Antes de ejecutar cualquier script, es recomendable:

1. **Hacer backup de la base de datos** o al menos de las tablas afectadas:
   - `financiero.factura_legal`
   - `financiero.factura_legal_item`

2. **Definir el rango de fechas** que se desea procesar

3. **Ejecutar primero las queries de análisis** para verificar qué se va a modificar

### Paso 2: Ejecutar `update_factura_legal_item_iva.sql`

#### 2.1. Configurar el rango de fechas

Editar las líneas 44-45 del script para definir el rango:

```sql
WHERE fl.fecha >= '2024-01-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-31 23:59:59'::timestamp;
```

**Alternativa**: Usar fecha de creación del item (`fli.creado_en`) en lugar de `fl.fecha` (comentar/descomentar según necesidad).

#### 2.2. Ejecutar PASO 0: Estadísticas

```sql
-- PASO 0: ESTADÍSTICAS GENERALES
```

Esto mostrará:
- Total de items en el rango
- Cantidad de items sin IVA (`iva IS NULL`)
- Distribución de items por IVA (0, 5, 10)
- Items sin relación (requieren atención manual)

#### 2.3. Ejecutar PASO 1: Verificar qué se actualizará

```sql
-- PASO 1: VERIFICAR QUÉ SE ACTUALIZARÁ
```

Esto mostrará una lista de items que se actualizarán, con:
- IVA actual
- IVA nuevo (calculado)
- Motivo de la actualización

**Revisar cuidadosamente** antes de proceder.

#### 2.4. Ejecutar PASO 2: UPDATE

```sql
-- PASO 2: ACTUALIZAR (ejecutar solo después de verificar)
```

**⚠️ ADVERTENCIA**: Este es el UPDATE real. Solo ejecutar después de verificar los pasos anteriores.

#### 2.5. Ejecutar PASO 3: Verificar resultados

```sql
-- PASO 3: VERIFICAR RESULTADOS
```

Verificar que:
- Los items con `iva = NULL` se redujeron significativamente
- La distribución de IVAs es correcta

#### 2.6. Ejecutar PASO 4: Identificar items sin relación

```sql
-- PASO 4: IDENTIFICAR ITEMS SIN RELACIÓN
```

Estos items requieren atención manual porque no tienen relación con productos.

### Paso 3: Ejecutar `update_factura_legal_totales.sql`

#### 3.1. Configurar parámetros de fecha

Este script usa parámetros con nombre. Al ejecutarlo, reemplazar `:fechaInicio` y `:fechaFin` con los valores reales:

```sql
-- Ejemplo:
-- :fechaInicio = '2025-12-01 00:00:00'
-- :fechaFin = '2025-12-02 00:00:00'
```

**Nota**: El script usa `creado_en` de la factura legal, no `fecha`.

#### 3.2. Ejecutar PASO 1: Estadísticas del rango

```sql
-- PASO 1: Estadísticas del Rango de Fechas
```

Muestra:
- Total de facturas en el rango
- Facturas con/sin descuento
- Rango de fechas procesado

#### 3.3. Ejecutar PASO 2: Preview de cálculos nuevos

```sql
-- PASO 2: Cálculo de Nuevos Valores (Preview)
```

Muestra las primeras 100 facturas con:
- Valores actuales
- Valores nuevos calculados
- Porcentaje de descuento aplicado

#### 3.4. Ejecutar PASO 3: Comparación actuales vs nuevos

```sql
-- PASO 3: Comparación Actuales vs Nuevos (Preview)
```

Muestra solo las facturas con diferencias, ordenadas por mayor diferencia.

#### 3.5. Ejecutar PASO 4: Resumen de errores

```sql
-- PASO 4: Resumen de Facturas con Errores
```

Muestra estadísticas de:
- Total de facturas
- Facturas con errores
- Errores por categoría (parcial_0, parcial_5, parcial_10)

#### 3.6. Ejecutar PASO 5: UPDATE

**⚠️ ADVERTENCIA**: Descomentar el bloque del UPDATE (líneas 222-276) solo después de verificar todos los pasos anteriores.

```sql
-- PASO 5: UPDATE (COMENTADO - Descomentar para ejecutar)
```

El UPDATE incluye una verificación post-actualización que muestra:
- Cantidad de facturas actualizadas
- Facturas con diferencias (debería ser 0)

## 🔍 Ejemplo de Uso Completo

### Escenario: Corregir facturas del 1 al 2 de diciembre de 2025

#### 1. Actualizar IVAs de items

```sql
-- En update_factura_legal_item_iva.sql, cambiar:
WHERE fl.fecha >= '2025-12-01 00:00:00'::timestamp
  AND fl.fecha <= '2025-12-02 23:59:59'::timestamp;

-- Ejecutar PASO 0, 1, 2, 3, 4 en orden
```

#### 2. Recalcular totales de facturas

```sql
-- En update_factura_legal_totales.sql, reemplazar:
-- :fechaInicio con '2025-12-01 00:00:00'
-- :fechaFin con '2025-12-02 23:59:59'

-- Ejecutar PASO 1, 2, 3, 4, 5 en orden
```

## ⚠️ Advertencias y Consideraciones

### Antes de Ejecutar

1. **Backup obligatorio**: Siempre hacer backup antes de ejecutar UPDATEs masivos
2. **Probar en rango pequeño**: Ejecutar primero en un rango de fechas pequeño (1-2 días) para verificar
3. **Horario de bajo tráfico**: Ejecutar en horarios de bajo uso del sistema
4. **Revisar resultados**: Siempre ejecutar los pasos de análisis antes del UPDATE

### Durante la Ejecución

1. **No interrumpir**: No cancelar la ejecución del UPDATE una vez iniciado
2. **Monitorear tiempo**: Los UPDATEs pueden tardar varios minutos según el volumen
3. **Verificar transacciones**: Asegurarse de que las transacciones se completen correctamente

### Después de Ejecutar

1. **Verificar coherencia**: Ejecutar queries de verificación para asegurar que:
   - `total_final = total_parcial_0 + total_parcial_5 + total_parcial_10`
   - `iva_parcial_5 = total_parcial_5 / 21`
   - `iva_parcial_10 = total_parcial_10 / 11`

2. **Revisar items sin relación**: Atender manualmente los items identificados en PASO 4 del primer script

## 📊 Lógica de Cálculo

### Cálculo de Totales Parciales

1. **Sumar items por categoría de IVA** (sin descuento):
   - `suma_items_iva_0`: Suma de items con `iva = 0`
   - `suma_items_iva_5`: Suma de items con `iva = 5`
   - `suma_items_iva_10`: Suma de items con `iva = 10`

2. **Calcular porcentaje de descuento**:
   ```
   porcentaje_descuento = descuento / suma_total_items
   ```

3. **Aplicar descuento proporcionalmente**:
   ```
   total_parcial_0 = suma_items_iva_0 * (1 - porcentaje_descuento)
   total_parcial_5 = suma_items_iva_5 * (1 - porcentaje_descuento)
   total_parcial_10 = suma_items_iva_10 * (1 - porcentaje_descuento)
   ```

4. **Calcular IVAs**:
   ```
   iva_parcial_5 = total_parcial_5 / 21.0
   iva_parcial_10 = total_parcial_10 / 11.0
   ```

5. **Calcular total final**:
   ```
   total_final = total_parcial_0 + total_parcial_5 + total_parcial_10
   ```

### Nota sobre Descuentos Negativos

Si una factura tiene `descuento < 0`, se trata como un recargo. El cálculo funciona igual, pero el resultado será un aumento en lugar de una disminución.

## 🐛 Troubleshooting

### Problema: Items sin relación después del UPDATE

**Solución**: Revisar manualmente estos items. Pueden ser:
- Items creados incorrectamente
- Productos eliminados
- Datos corruptos

### Problema: Totales no coinciden después del UPDATE

**Verificar**:
1. Que todos los items tengan `iva` correcto (ejecutar primer script)
2. Que el rango de fechas sea el mismo en ambos scripts
3. Que no haya items duplicados o corruptos

### Problema: UPDATE muy lento

**Soluciones**:
1. Reducir el rango de fechas
2. Ejecutar en horarios de bajo tráfico
3. Verificar índices en las tablas

## 📞 Soporte

Si encuentras problemas o necesitas ayuda:
1. Revisar los logs de ejecución
2. Verificar que los pasos de análisis muestren resultados esperados
3. Consultar con el equipo de desarrollo

## 📅 Historial de Cambios

- **2026-02-05**: Creación inicial de los scripts
- **2026-02-05**: Documentación completa del manual

---

**Última actualización**: 2026-02-05
