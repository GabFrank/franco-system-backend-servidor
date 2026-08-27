-- Amonestaciones (PenalizacionTipo.ADVERTENCIA).
--
-- Reusan rrhh.penalizacion en vez de una entidad nueva: comparten funcionario, fecha,
-- descripcion, anulacion y auditoria, y asi heredan el CRUD, el paginado con filtro por
-- tipo y el gating de rol que ya existen. Lo que NO comparten es la plata: los tres
-- lugares que suman penalizaciones (liquidacion mensual, finiquito y KPI del dashboard)
-- las excluyen explicitamente por tipo.
--
-- El tipo no necesita migracion: PenalizacionTipo se persiste con @Enumerated(STRING)
-- sobre un varchar(30), no sobre un enum nativo de Postgres. Verificado en V155.0.
--
-- Columnas nuevas, todas nullable y solo usadas por las advertencias:
--   numero_advertencia  la 1a, 2a, 3a... del funcionario. Lo calcula el backend al crear.
--   firmada             si el funcionario firmo el acta. Negarse a firmar es un dato.
--   fecha_hecho         cuando ocurrio, si no es el dia en que se registro.

ALTER TABLE rrhh.penalizacion ADD COLUMN IF NOT EXISTS numero_advertencia INTEGER;
ALTER TABLE rrhh.penalizacion ADD COLUMN IF NOT EXISTS firmada BOOLEAN;
ALTER TABLE rrhh.penalizacion ADD COLUMN IF NOT EXISTS fecha_hecho DATE;
