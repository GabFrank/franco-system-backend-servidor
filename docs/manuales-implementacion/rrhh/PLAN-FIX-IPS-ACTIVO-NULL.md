# Plan — fix: `saveFuncionario` deja en null los datos del legajo (issue #452)

Rama: `fix/rrhh-ips-activo-null` (central, desde `origin/develop` `fcf8c6af`).
Pieza: **solo central**. Estado: aprobado por Franco el 2026-10-20.

## Problema

`personas.funcionario.ips_activo` en `null` se lee distinto según quién lo lea (issue #452):
el legajo y el filtro «Cobra IPS» lo toman como «no inscripto», y `LiquidacionSueldoService` /
`LiquidacionFinalService` (y el diálogo de liquidación final del desktop) como «descuenta IPS».

El `null` lo escribe `FuncionarioGraphQL.saveFuncionario`, por dos caminos:

1. **Alta** (`m.map(input, Funcionario.class)`): el wizard del desktop no manda `ipsActivo`, y
   Hibernate inserta `NULL` explícito (el `DEFAULT false` de V154.0 no se aplica).
2. **Update** (`m.map(input, e)`): ModelMapper 2.3.0 copia los `null` del input sobre la entidad
   (verificado con el jar del repo: `true` → `null`). Todo guardado que no manda los campos del
   legajo los borra. Llamadores del desktop que lo hacen:
   - asignar horario en lote desde el listado (`list-funcioario.component.ts`
     `vincularMultiplesFuncionarios`): arma el input a mano, sin `ipsActivo`, `numeroIps`,
     `cobraBanco`, `cuentaBancaria`, `codigoInterno` ni contacto de emergencia;
   - el diálogo de edición abierto eligiendo una persona (`adicionar-funcionario-dialog` →
     `funcionarioPorPersonaQuery`, que no pide esos campos). Leído, no probado.

   Además, en un update sin `fechaIngresoIps` el resolver la pone en `null` a propósito.

Producción (bodega, 2026-10-20): 3 activos en `null`. Dos (556, 566) se liquidaron sin IPS en
agosto y septiembre y el borrador de octubre ya lo descuenta: pasaron de `false` a `null` entre
el 07/09 y el 17/10, consistente con el camino 2. RRHH corrige los datos a mano (decisión de
Franco); este plan solo arregla el código.

## Decisión

Seguir la convención que el resolver ya tiene para sueldo, crédito y horario, y que el legajo
documenta (`informacion-general.component.ts`: «el backend interpreta null como "no gestiono
este campo" y preserva el valor anterior»):

- **Update**, en `saveFuncionario`:
  - si `input.ipsActivo == null` → conservar `ipsActivo`, `numeroIps` y `fechaIngresoIps`. El
    legajo es el único que gestiona el bloque IPS y siempre manda `ipsActivo` como `true`/`false`
    (`FormControl(false)`); cuando lo apaga, manda `numeroIps`/`fechaIngresoIps` en `null` a
    propósito, y eso se sigue respetando porque en ese caso `ipsActivo` no es `null`.
  - si `input.cobraBanco == null` → conservar `cobraBanco` y `cuentaBancaria` (mismo patrón).
  - **el flag decide el bloque entero**: con `ipsActivo == null` se ignoran también un
    `numeroIps`/`fechaIngresoIps` que vengan con valor (hoy ningún llamador lo hace); con
    `cobraBanco == null`, una `cuentaBancaria` con valor. Regla explícita, con test.
  - si `codigoInterno`, `contactoEmergenciaNombre` o `contactoEmergenciaTelefono` vienen en
    `null` → conservar cada uno. El legajo no los manda en `null` para borrarlos: un campo que el
    usuario vació sale `''` por `up()`; uno que nunca tuvo valor sale `null`, y conservarlo deja el
    mismo vacío.
  - si `creadoEn` viene en `null` → conservar `creado_en`. Mismo mecanismo: en bodega 463 de 564
    funcionarios tienen `creado_en` vacío (2026-10-20). Lo perdido no se recupera; se corta el
    borrado.
  - el `fechaIngresoIps = null` forzado pasa a aplicarse solo cuando **el input** trae
    `ipsActivo` (no la entidad).
- **Alta**: `ipsActivo` y `cobraBanco` en `null` → `false` (lo que el `DEFAULT` de la columna
  quería y el legajo/filtro ya muestran). **Cambia la liquidación de las altas nuevas**: hoy un
  alta por el wizard queda en `null` y se le descuenta IPS; con el fix queda en `false` y no se
  le descuenta hasta que RRHH prenda «Inscripto en IPS» en el legajo. Activos en bodega: 116 en
  `false`, 36 en `true`. **Decidido por Franco (2026-10-20): el alta queda «no inscripto»**
(`false`); RRHH lo prende en el legajo cuando corresponde.

Comportamiento previo que el fix no cambia: el legajo muestra un `null` como «no inscripto»
(`!!f.ipsActivo`) y al guardarlo lo escribe `false`. Por eso RRHH corrige los 3 activos en `null`
antes de que alguien guarde su legajo, y antes del deploy se corre:

```sql
select id from personas.funcionario where activo and ips_activo is null;  -- tiene que dar 0
```

**No se cambia el criterio de los lectores** (liquidaciones: `null` = descuenta). Con el fix ya
no se escriben `null` nuevos y RRHH corrige los 3 activos; quedan 2 inactivos con finiquito
pagado (112, 684), sin efecto. Cambiar el criterio sin datos limpios le quitaría el descuento a
un inscripto que hoy esté en `null` (el caso de 513).

**No hay migración ni backfill**: el backfill de la issue (`null` → `false`) le sacaría el IPS a
513, que sí aporta. Los datos los corrige RRHH.

**No se toca el desktop**: con el central conservando lo que no recibe, sus llamadores parciales
dejan de borrar sin cambiar. Un desktop viejo sigue funcionando igual contra el central nuevo.

## Datos nuevos

Ninguno: no nace ningún campo, columna ni clave. Escritor de los campos tocados: el legajo
(`informacion-general.component.ts`); lectores: los listados en la issue. No cambian.

## Fases

### Fase 1 — fix en `saveFuncionario` + test (un commit `fix(rrhh): …`)

- `FuncionarioGraphQL.saveFuncionario`: lo descrito en «Decisión».
- Test nuevo `FuncionarioGraphQLDatosLegajoTest` (Mockito, `@InjectMocks` sobre el resolver,
  servicios mockeados, mismo patrón que `CajaVirtualConfiguracionGraphQLTest`):
  1. update como el de horario en lote (sin campos del legajo) sobre un funcionario con
     `ipsActivo=true`, `numeroIps`, `fechaIngresoIps`, `cobraBanco=true`, `cuentaBancaria`,
     `codigoInterno` y contacto → los conserva todos;
  2. update como el del legajo con `ipsActivo=false`, `numeroIps=null`, `fechaIngresoIps=null`,
     `cobraBanco=false`, `cuentaBancaria=null` → los borra (no se rompe el apagado);
  3. update del legajo con `ipsActivo=true` y número/fecha nuevos → los guarda;
  4. alta sin `ipsActivo` ni `cobraBanco` → `false` en los dos;
  5. update con `ipsActivo=null` y `numeroIps` con valor → conserva el bloque viejo entero;
  6. update mixto: `ipsActivo` presente y `cobraBanco` ausente → aplica IPS, conserva banco;
  7. update sin `creadoEn` → conserva `creado_en`.
  Revertir el fix y comprobar que 1, 4, 5, 6 y 7 fallan (2 y 3 son de no-regresión: pasan con
  los dos códigos). Mocks: `service.findById` → existente con persona (id y nombre),
  `service.save` → devuelve el argumento, `clienteService.findByPersonaId` y
  `usuarioService.findByPersonaId` → objetos existentes; en el alta, `personaService.findById`.
  ModelMapper real (lo crea el método), así que el test ejercita el mapeo de verdad.
- Build: `./mvnw clean verify -B -DskipFlyway=true`, leído del log.
- Commit + push de la rama.

## Prueba de runtime (paso 9)

Central local 8081 perfil `dev` (base `bodega@5551`) + desktop `ng serve -c web`, usuario MR:

1. Funcionario de prueba con IPS activo, número, fecha, cobra banco y cuenta cargados desde el
   legajo. **Asignar horario en lote** desde el listado → por SQL, los campos siguen iguales.
2. Mismo funcionario, editar desde el diálogo eligiendo la persona → siguen iguales.
3. Legajo: apagar IPS y cobra banco y guardar → `false` y número/cuenta/fecha en `null`.
4. Legajo: volver a prenderlos con datos → se guardan.
5. Alta por el wizard → `ips_activo = false`, `cobra_banco = false`, `creado_en` con fecha.
6. Filtro «Cobra IPS» y legajo muestran lo mismo que la base.
7. Legajo: vaciar código interno y contacto de emergencia y guardar → quedan vacíos.
8. Cualquier edición posterior → `creado_en` no cambia.

Datos de prueba creados/tocados se restauran al final.

### Resultado (2026-10-20, central local `c06c07be` + desktop `develop` `5ae0c215`, usuario MR)

Funcionario 611 de la base local; verificado por SQL tras cada guardado y restaurado al final.

| Caso | Resultado |
|---|---|
| 1. Asignar horario en lote (Marcaciones → Adicionar Horario → Asignar a otros) | OK: `horario_id` asignado, IPS/banco/código/contacto/`creado_en` intactos |
| 2. Diálogo de edición eligiendo persona | **N/A**: `adicionar-funcionario-dialog` no tiene llamadores en develop, beta ni master (solo uno comentado en `maletin.component.ts:51`) |
| 3. Legajo apaga IPS y banco | OK: `false`, número/fecha/cuenta en `null` |
| 4. Legajo prende IPS y banco con datos | OK: se guardan |
| 5. Alta por el wizard (Solicitudes de funcionarios → Registrar) | OK: `ips_activo=false`, `cobra_banco=false`, `creado_en` con fecha |
| 6. Filtro «Cobra IPS» | OK: Sí/No coinciden con la base en los dos estados |
| 7. Legajo vacía código interno y contacto | OK: quedan `''` |
| 8. `creado_en` tras las ediciones | OK: sin cambios |

Batería: `./mvnw clean verify -B -DskipFlyway=true` → 2151 tests, 0 fallos (sobre una copia fuera
del workspace: el servidor de lenguaje que indexa `/home/franco/dev-frc` reescribía `target/` y
la primera corrida dio `NoClassDefFound` en 404 tests ajenos). CI: solo corre en `pull_request`;
queda para el PR.

Hallazgo aparte (preexistente, desktop): el wizard guarda como sueldo el id de la sucursal
(se cargó 3.000.000 con «1 - Suc. Central» y quedó `sueldo = 1.0`).

## Despliegue

Solo central, sin migración ni variable nueva. Tras el merge, deploy manual (`gh workflow run
Deploy`) por canal. Reinicio: `frc-<instancia>.service`, lo hace el workflow. El filial no
cambia (`funcionario` es `MAIN_TO_ALL` y el desktop guarda siempre contra el central).

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| El alta en `false` deja de descontar IPS a los nuevos | B | Escrito en «Decisión»; lo decide Franco |
| Input con flag `null` y datos del bloque con valor: regla ambigua | A, B | Regla explícita + test 5 |
| `creadoEn` se borra por el mismo mecanismo | B | Verificado en bodega (463/564 vacíos); incluido + test 7 |
| El legajo guarda un `null` como `false` | A, B | Previo al fix; RRHH corrige y query de control antes del deploy |
| `up()` da `null` en un control sin valor | A | Sin pérdida de datos; caso 7 de la prueba de runtime |
| Faltan casos mixtos en el test | A | Test 6 |
| El update borraría `usuario_id` (legajo no lo manda) | A | **Descartado**: en bodega 0 de 155 activos sin `usuario_id` |
| El filial tiene el mismo `saveFuncionario` sin conservar | A | Nadie lo llama (el desktop usa `servidor=true`); no se toca |
| Mocks necesarios del test | B | Listados en la fase 1 |

## Fuera de alcance / sin verificar

- `saveFuncionario` del filial tiene el mismo patrón; hoy no lo llama ningún cliente.
- El wizard asigna `sueldo` desde el control de sucursal (`funcionario-wizard.component.ts`
  ~200). **Confirmado en runtime** (ver resultado del paso 9); issue aparte.
- `supervisadoPor` sigue el mismo mecanismo de borrado que este fix corrige (auditoría del diff,
  Fijo 2): el resolver lo pone en `null` y solo lo repone si el input trae `supervisadoPorId`.
  Bodega: 151 de 155 activos sin supervisor (no se sabe cuántos por borrado). Fuera de alcance.
- Huecos menores de test señalados en la auditoría del diff: alta con `fechaIngresoIps`, prender
  IPS sin fecha, banco apagado con IPS ausente. Cubiertos en runtime (casos 3-5), no en unit.
- `mobile` y `mobile-pwa` no llaman a `saveFuncionario` (grep en `develop`).
- Farmacia no se midió.
