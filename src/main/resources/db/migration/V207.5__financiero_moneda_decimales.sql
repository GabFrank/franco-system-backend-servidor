-- Pobla financiero.moneda.decimales, que quedo en 0 para TODAS las monedas desde que se creo
-- la columna. El 0 no es una decision: es el default que nunca se sobreescribio (regla_redondeo
-- y redondeo_multiplo tambien estan nulos en las 4 filas).
--
-- Es un dato equivocado, no una convencion: los saldos en real y dolar SI llevan centavos
-- (ej. 3.339,78 R$ en la caja mayor) y las denominaciones cargadas para el real bajan hasta
-- 0,05. Con decimales=0 la UI redondea y muestra "0 R$" donde hay 0,05.
--
-- El codigo que lee decimales usa el patron `decimales != null ? decimales : (GUARANI ? 0 : 2)`
-- (ver pagar-compras-dialog.component.ts), que nunca se dispara porque el valor es 0 y no null.
-- Esta migracion alinea el dato con lo que ese fallback ya asumia.
--
-- Aditiva: solo UPDATE de una columna existente, y solo sobre las filas que siguen en el
-- default. Si alguien ya configuro decimales a proposito (valor != 0), no se toca.
UPDATE financiero.moneda
SET decimales = 2
WHERE decimales = 0
  AND upper(denominacion) NOT LIKE '%GUARAN%';

-- El guarani no tiene fraccion en circulacion: se deja explicitamente en 0.
UPDATE financiero.moneda
SET decimales = 0
WHERE upper(denominacion) LIKE '%GUARAN%'
  AND decimales IS NULL;
