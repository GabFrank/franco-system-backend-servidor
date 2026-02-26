# Pruebas: actualización de `nota_recepcion.valor`

Comprobar que el valor de la nota se actualiza al agregar/modificar/eliminar ítems y al registrar rechazos (total o parcial).

## Requisitos

- Backend compilado y en ejecución.
- Base de datos PostgreSQL accesible.
- (Opcional) Cliente GraphQL (Postman, Insomnia, o la app desktop/mobile).

---

## 1. Compilar el backend

En la máquina donde tengas Maven y las dependencias resueltas:

```bash
cd backend/central/frc-central-server
./mvnw compile
# o
mvn compile
```

---

## 2. Verificar en base de datos

### 2.1. Nota con ítems sin rechazos

```sql
-- Ver una nota y su valor actual
SELECT id, numero, valor, estado
FROM operaciones.nota_recepcion
WHERE id = :nota_id;

-- Ítems de la nota (suma = valor esperado)
SELECT nri.id, nri.cantidad_en_nota, nri.precio_unitario_en_nota,
       (nri.cantidad_en_nota * nri.precio_unitario_en_nota) AS subtotal,
       nri.estado
FROM operaciones.nota_recepcion_item nri
WHERE nri.nota_recepcion_id = :nota_id
  AND (nri.es_bonificacion IS NULL OR nri.es_bonificacion = false)
  AND (nri.estado != 'RECHAZADO' OR nri.estado IS NULL);
```

Comprobar que `nota_recepcion.valor` coincida con la suma de los subtotales de esos ítems.

### 2.2. Probar la query de valor con rechazos

```sql
-- Valor total descontando rechazos (misma lógica que valorTotalConRechazos)
SELECT COALESCE(SUM(
  GREATEST(0, (nri.cantidad_en_nota - COALESCE((
    SELECT SUM(rmi.cantidad_rechazada)
    FROM operaciones.recepcion_mercaderia_item rmi
    WHERE rmi.nota_recepcion_item_id = nri.id
  ), 0)) * nri.precio_unitario_en_nota)), 0.0) AS valor_con_rechazos
FROM operaciones.nota_recepcion_item nri
WHERE nri.nota_recepcion_id = :nota_id
  AND (nri.es_bonificacion IS NULL OR nri.es_bonificacion = false)
  AND (nri.estado != 'RECHAZADO' OR nri.estado IS NULL);
```

Sustituir `:nota_id` por un ID real (por ejemplo `3`).

---

## 3. Pruebas por flujo (GraphQL / app)

### 3.1. Actualización al guardar ítem de nota (desktop)

1. Abrir una nota de recepción que tenga ítems (o crear una y agregar ítems).
2. Antes: anotar `valor` de la nota (consulta `notaRecepcion(id)` con campo `valorTotal` o consultar la tabla).
3. Agregar un nuevo ítem a la nota (o editar cantidad/precio de uno existente) y guardar.
4. Después: volver a consultar la nota; `valorTotal` debe reflejar la nueva suma y `nota_recepcion.valor` en BD debe haber cambiado.

### 3.2. Actualización al eliminar ítem de nota

1. Nota con al menos 2 ítems; anotar valor actual.
2. Eliminar un ítem de la nota.
3. Comprobar que el valor de la nota se actualizó (menor que antes).

### 3.3. Rechazo desde mobile (saveRecepcionMercaderiaItem)

1. En mobile: recepción de mercadería, ítem con cantidad recibida y parte rechazada (`cantidadRechazada` o variaciones rechazadas).
2. Guardar el ítem de recepción.
3. En BD: `SELECT valor FROM operaciones.nota_recepcion WHERE id = :nota_id;`
4. El valor debe ser: suma de (cantidad_en_nota - cantidad_rechazada) * precio por ítem (rechazos descontados).

### 3.4. Rechazo con mutation rechazarItem

1. Ejecutar la mutación `rechazarItem` con un `notaRecepcionItemId` y los rechazos por sucursal.
2. Consultar de nuevo la nota: `notaRecepcion(id) { valorTotal }` o leer `nota_recepcion.valor`.
3. El valor debe haberse reducido según la cantidad rechazada.

### 3.5. Finalizar recepción con “rechazar pendientes”

1. Recepción con ítems pendientes; finalizar con `rechazoPendientes` (motivo de rechazo).
2. Verificar que las notas asociadas a esos ítems tengan `valor` actualizado (rechazos descontados).

---

## 4. Consultas GraphQL útiles

```graphql
# Obtener valor de una nota
query {
  notaRecepcion(id: 3) {
    id
    numero
    valorTotal
  }
}
```

Tras cada operación (agregar/editar/eliminar ítem, rechazar ítem, finalizar con rechazo), repetir esta consulta y/o revisar `operaciones.nota_recepcion.valor` en la BD para confirmar que el valor se actualiza como se espera.
