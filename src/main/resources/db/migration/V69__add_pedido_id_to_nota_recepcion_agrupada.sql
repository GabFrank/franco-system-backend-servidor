-- V69: Agregar pedido_id a nota_recepcion_agrupada para vincular directamente grupos con pedidos
-- Esto permite que los grupos mantengan su relación con el pedido incluso cuando no tienen notas asignadas

-- Paso 1: Agregar la columna pedido_id (nullable inicialmente)
ALTER TABLE operaciones.nota_recepcion_agrupada 
ADD COLUMN pedido_id BIGINT;

-- Paso 2: Agregar la foreign key constraint
ALTER TABLE operaciones.nota_recepcion_agrupada 
ADD CONSTRAINT fk_nota_recepcion_agrupada_pedido 
FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id);

-- Paso 3: Poblar la columna pedido_id basándose en las notas existentes
-- Para grupos que tienen notas, obtenemos el pedido_id de la primera nota
UPDATE operaciones.nota_recepcion_agrupada nra
SET pedido_id = (
    SELECT DISTINCT nr.pedido_id 
    FROM operaciones.nota_recepcion nr 
    WHERE nr.nota_recepcion_agrupada_id = nra.id 
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1 
    FROM operaciones.nota_recepcion nr 
    WHERE nr.nota_recepcion_agrupada_id = nra.id
);

-- Paso 4: Para grupos huérfanos (sin notas), los eliminaremos ya que no se pueden recuperar
-- Esto es una limpieza necesaria antes de hacer la columna NOT NULL
DELETE FROM operaciones.nota_recepcion_agrupada 
WHERE pedido_id IS NULL;

-- Paso 5: Hacer la columna pedido_id NOT NULL
ALTER TABLE operaciones.nota_recepcion_agrupada 
ALTER COLUMN pedido_id SET NOT NULL;

-- Paso 6: Crear índice para mejorar performance en consultas por pedido_id
CREATE INDEX idx_nota_recepcion_agrupada_pedido_id 
ON operaciones.nota_recepcion_agrupada(pedido_id);

-- Comentarios para documentación:
-- Esta migración establece una relación directa entre nota_recepcion_agrupada y pedido
-- Beneficios:
-- 1. Los grupos mantienen su vinculación con el pedido incluso sin notas
-- 2. Mejora la consulta de grupos por pedido
-- 3. Permite mostrar grupos vacíos en la UI
-- 4. Facilita la auditoría y trazabilidad 