# Plan — una sola regla de bono recurrente activa por funcionario y tipo

## Problema

`BonoService.saveConRecurrencia` crea una regla (`rrhh.bono_recurrente`) nueva cada vez que se
carga un bono **nuevo** con el toggle «Recurrente» encendido. No mira si el funcionario ya tiene
una regla activa del mismo tipo. El job del día 1 genera un bono por regla, así que el
funcionario cobra dos veces.

Caso real, bodega, 27/11/2026: 14 funcionarios tenían dos reglas activas de PRODUCTIVIDAD. Hubo
dos orígenes:

- el bono se cargó dos veces en minutos (121, 271, 460, 566);
- el 29/10 se anuló el bono viejo y se cargó otro con monto nuevo. Antes del #647, anular no
  apagaba la regla vieja (los otros 10).

Los datos se limpiaron por SQL el 27/11. El #647 tapa el segundo origen. Este plan tapa el primero
y cualquier otro camino que llegue a dos reglas activas.

## Decisión

Al guardar un bono con el toggle encendido se **rechaza**, con un mensaje que nombra la regla
existente y su monto, si el funcionario ya tiene **otra** regla activa del mismo tipo.

- Se descarta **reemplazar** en silencio la regla vieja: el bono que la regla vieja ya generó para
  el mes quedaría vivo y se cobraría igual el doble ese mes.
- Para cambiar el monto en adelante, se edita el bono del mes de esa regla (ya actualiza la
  plantilla) o se lo anula y se carga otro (el #647 apaga la regla al anular).
- Tipos distintos (por ejemplo PRODUCTIVIDAD y DESEMPENIO) siguen pudiendo convivir.
- «Otra» excluye la propia regla del bono que se edita. Así editar el bono del mes no se
  rechaza a sí mismo, y reactivar la regla apagada de ese bono se rechaza solo si mientras tanto
  nació otra.

## Fase única (central)

- `BonoService.saveConRecurrencia`: cuando el toggle viene encendido, **antes** de buscar o tocar la
  plantilla (para no dejar nada sucio en la sesión), consultar
  `plantillaRepository.findByFuncionarioIdAndActivoTrue(funcionarioId)` (ya existe) y lanzar
  `GraphQLException` si hay una del mismo tipo con id distinto al de la plantilla propia.
  - Se compara contra el funcionario y el tipo **nuevos** del bono (`entity`): `saveBono` los pisa
    con el input también al editar, y la plantilla se mueve con ellos.
  - Sin funcionario (o sin id) no se consulta: no hay contra quién comparar.
  - El mensaje nombra la regla y el monto, y dice la salida: cambiar el monto editando el bono del
    mes de esa regla, o cortar la regla apagando «Recurrente» en ese bono. **No** sugiere anular,
    porque `anular()` hoy no respeta `motivoNoEditable` y marcaría un bono ya pagado.
- Con dos reglas activas ya existentes (datos viejos), editar el bono con el toggle encendido se
  rechaza; con el toggle apagado sigue pasando y apaga la regla propia. Esa es la salida.
- Sin migración, sin cambios de schema GraphQL ni del desktop. El desktop ya muestra el texto de la
  `GraphQLException` de este mismo método, igual que el rechazo por funcionario inactivo.

### Tests (`BonoServiceRecurrenciaTest`)

1. Bono nuevo recurrente con otra regla activa del mismo tipo → rechaza y no guarda nada.
2. Bono nuevo recurrente con una regla activa de **otro** tipo → crea la plantilla.
3. Editar el bono de una regla activa (su propia regla aparece en la consulta) → no rechaza.
4. Bono nuevo **no** recurrente con una regla activa del mismo tipo → no rechaza (es un bono
   puntual).
5. Bono puntual existente que pasa a recurrente, con una regla activa del mismo tipo → rechaza.
6. Editar el bono de la regla R1 cambiándole el tipo al de otra regla activa R2 → rechaza.
7. Con dos reglas activas (datos viejos): editar el bono de una con el toggle encendido → rechaza;
   con el toggle apagado → no rechaza y apaga su regla.

Cada test de rechazo se corre también con el fix revertido, para confirmar que falla.

## Auditoría del plan (paso 5)

- **Eje A:** contrato sin cambios; solo el desktop llama a `saveBono` y ya muestra el texto de la
  excepción en un snackbar. Hallazgos aplicados: comparar contra los valores nuevos del bono (test
  6) y no sugerir anular en el mensaje. Queda fuera: del día en que se aprueba la liquidación al
  día 1 la regla no tiene bono editable; para cambiar el monto en ese tramo hay que esperar al bono
  del mes siguiente.
- **Eje B:** sin migración ni schema, rollback = jar anterior. Hallazgos aplicados: chequeo antes
  de tocar la plantilla, la salida con datos viejos (test 7), puntual a recurrente (test 5).
  Mayor y fuera del código: **farmacia no se revisó**. El job del 01/12 cobra doble en cualquier
  regla duplicada que ya exista, con o sin este fix. Chequeo de solo lectura en
  `dev-frc/notas-bonos-duplicados-2026-11-27/farmacia-chequeo.sh`.

## Datos nuevos

Ninguno.

## Sin verificar

- Si dos cargas llegan en el mismo milisegundo pasan las dos: no hay índice único. Los casos
  reales estaban separados por minutos. Un índice parcial único `(funcionario_id, tipo) WHERE
  activo` lo cerraría, pero exige migración y dry-run contra la base real: fuera de este fix.
- Farmacia no se revisó en datos.
