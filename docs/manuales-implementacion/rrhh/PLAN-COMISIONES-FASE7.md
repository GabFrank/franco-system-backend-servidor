# Plan — Fase 7: Comisiones (RRHH)

> Relevamiento + diseño. **No implementado todavía** (es el bloque de mayor
> riesgo; su validación necesita datos de ventas reales). Este documento define
> el diseño para decidirlo antes de codear.

## 1. Relevamiento del modelo de ventas real (confirmado)

La decisión abierta §20 #1 ("¿qué campo identifica al vendedor?") **queda
resuelta** tras inspeccionar el código:

| Entidad | Campo vendedor | Otros campos relevantes |
|---|---|---|
| `operaciones.Venta` | `usuario_id` → `Usuario usuario` | `estado` (ABIERTA/CONCLUIDA/CANCELADA/EN_VERIFICACION), `total_gs/rs/ds`, `caja`, `sucursal`, `cliente`, `creado_en` |
| `operaciones.VentaItem` | `usuario_id` → `Usuario usuario` (vendedor por ítem) | `producto`, `presentacion`, `cantidad`, `precio`, `precio_costo`, `valor_descuento`, `creado_en` |

- **Puente a funcionario**: `funcionario.usuario_id`. Un funcionario comisiona
  por las ventas de su usuario: `COALESCE(item.usuario_id, venta.usuario_id) = funcionario.usuario_id`.
- **Fecha del período**: `venta.creado_en` (LocalDateTime).
- **Estado que cuenta**: `venta.estado = CONCLUIDA`.
- **Finder ya disponible**: `VentaService.findByUsuarioIdAndCreadoEnBetweenOrderByIdDesc(usuarioId, inicio, fin)`
  y `findBySucursalIdAndCreadoEnBetween(...)`. Existe además el dominio
  `operaciones.VentaPorFuncionario` (base del reporte "lucro por funcionario").

## 2. Entidades a crear (schema `rrhh`, aditivas)

Espejo de Gourmet (§2.8 del PLAN-MODULO-RRHH.md), 7 entidades + enums:

1. **`regla_comision`** — nombre, descripcion, `tipo` (enum), `monto_base`,
   `porcentaje`, `meta_unidades`, `meta_monto_local`, `modo_validacion` (enum),
   `recurrencia` (enum), `fecha_inicio/fin`, `es_equipo`, `activo`.
2. **`regla_comision_producto`** (CASCADE) — regla, producto. **Vacío = todos.**
3. **`regla_comision_requisito`** (CASCADE) — regla, `tipo` (enum), `umbral`,
   `peso`, `descripcion`.
4. **`funcionario_regla_comision`** — funcionario, regla, `fecha_desde/hasta`, `activo`.
5. **`equipo_comision`** + **`equipo_comision_miembro`** (funcionario, `porcentaje_reparto`)
   + **`equipo_comision_regla`**.
6. **`liquidacion_comision`** — funcionario, `periodo`, `fecha_inicio/fin`,
   `total_calculado`, `estado` (BORRADOR/APROBADA/INTEGRADA/ANULADA), `aprobado_por`, `observacion`.
7. **`liquidacion_comision_item`** (CASCADE) — liquidacion, regla?, `concepto`,
   `monto`, `es_manual`, `observacion` (**snapshot de parámetros aplicados**, auditoría).

**Enums**: `TipoReglaComision` (META_UNIDADES, PORCENTAJE_VENTA, META_VENTA_LOCAL,
EXTRA_MANUAL, PENALIZACION_MANUAL, EQUIPO_PORCENTAJE), `ModoValidacionComision`
(TODO_O_NADA, PROPORCIONAL), `RecurrenciaComision` (UNICA, DEFINIDA, INDEFINIDA),
`TipoRequisitoComision` (TARDANZA_MAX, QUEJA_MAX, ASISTENCIA_MIN, CUSTOM),
`LiquidacionComisionEstado` (BORRADOR, APROBADA, INTEGRADA, ANULADA).

Migración: **V150.0** (aditiva).

## 3. Motor (`evaluarReglaParaFuncionario(regla, funcionario, periodo)`)

```
1. Ventas del período: VentaItem ⋈ Venta con
   venta.estado = CONCLUIDA,
   producto ∈ regla.productos (vacío = todos),
   COALESCE(item.usuario_id, venta.usuario_id) = funcionario.usuario_id,
   creado_en ∈ [inicio, fin]
2. Métricas: totalUnidades (Σ cantidad), totalMontoProductos (Σ precio×cant − desc),
   totalMontoVentaLocal (Σ total_gs de las ventas del vendedor)
3. Requisitos (regla_comision_requisito):
   TARDANZA_MAX:  Σ minutos_llegada_tardia (Jornada) ≤ umbral
   ASISTENCIA_MIN: COUNT jornadas del período ≥ umbral
   QUEJA_MAX:     COUNT Penalizacion tipo QUEJA_CLIENTE ≤ umbral
   No cumple → TODO_O_NADA: monto 0; PROPORCIONAL: descuento ponderado por peso
4. Monto según tipo:
   META_UNIDADES:    unidades ≥ meta ? monto_base : 0
   PORCENTAJE_VENTA: montoProductos × porcentaje/100
   META_VENTA_LOCAL: montoLocal ≥ meta ? monto_base : 0
   EXTRA_MANUAL:     +monto_base   |   PENALIZACION_MANUAL: −monto_base
5. monto −= Σ Penalizacion tipo COMISION_DESCUENTO del período
6. EQUIPO_PORCENTAJE: reparte monto × porcentaje_reparto/100 por miembro
7. Snapshot de parámetros en item.observacion (auditoría)
```

**Testabilidad**: extraer la aritmética a un `ComisionCalculator` puro
(`service/rrhh/builder`) — como las demás calculadoras — para tests JUnit sin
Spring (metas, porcentajes, requisitos TODO_O_NADA/PROPORCIONAL, reparto equipo).
La parte de query (ventas del período) queda en el service; el cálculo, puro.

**Ciclo**: `LiquidacionComision` por funcionario+período:
BORRADOR (regenerable) → APROBADA → INTEGRADA (cuando la liquidación de sueldo
que la incluye se paga) → ANULADA. Generación masiva mensual.

## 4. Integración con la liquidación de sueldo (hook ya existe)

El motor de Fase 5 (`LiquidacionSueldoService`) ya deja el punto de enganche:
un ítem HABER `COMISION` con `referenciaTipo = COMISION`. Falta:
- Que `generarBorrador` sume la `LiquidacionComision` APROBADA del período como
  HABER COMISION (hoy el bloque COMISION está previsto pero sin fuente).
- Que `pagar` marque la `LiquidacionComision` como INTEGRADA (efecto cruzado,
  análogo a VALE→DESCONTADO). Hoy el switch de efectos cruzados ya contempla
  `COMISION` en el diseño (§4.7) — conectar con el service de comisiones.

## 5. Pantallas

**Desktop**: Reglas (CRUD + productos + requisitos + asignar funcionarios),
Equipos (miembros con % de reparto + reglas), Liquidaciones de comisión
(generar individual/masivo, aprobar, item manual, ver snapshot).

**Mobile** (opcional): "Mis comisiones" — total del período + detalle por regla
(endpoint `misComisionesMobile`).

## 6. Riesgos y decisiones abiertas (a confirmar antes de codear)

1. **`VentaItem` no tiene campo `estado`** (solo `venta.estado`). El diseño de
   Gourmet filtra `item.estado != CANCELADO`; acá la cancelación es a nivel
   venta. → Filtrar solo por `venta.estado = CONCLUIDA` (confirmar que no hay
   anulación por ítem).
2. **Vendedor por ítem vs por venta**: ambos campos existen. ¿La comisión es
   por el vendedor del ítem (cuando difiere del de la venta) o siempre por el de
   la venta? El `COALESCE(item, venta)` cubre ambos, pero hay que confirmar la
   intención de negocio.
3. **Moneda**: las ventas tienen `total_gs/rs/ds`. Se asume comisión en
   guaraníes sobre `total_gs`. Confirmar.
4. **"META_VENTA_LOCAL"**: definir qué es "venta local" (¿`total_gs` de la
   sucursal del funcionario? ¿un subconjunto?). Aclarar la métrica.
5. **Fuente de "quejas"**: se asume `Penalizacion` tipo `QUEJA_CLIENTE`.
   Confirmar que es la fuente correcta para `QUEJA_MAX`.
6. **Performance**: la query de ventas por período puede ser pesada. Existe
   `VentaPorFuncionario` (con índices, migración V130.3) — evaluar reusarlo como
   base agregada en vez de recorrer `venta_item` crudo.
7. **Validación runtime**: el motor sobre ventas reales **necesita el ambiente
   dev con datos** para verificarse; los tests unitarios cubren la aritmética,
   no la query.

## 7. Esfuerzo estimado

XL (backend) + L (desktop) + S (mobile), riesgo **alto** (modelo de ventas +
transacciones + integración con la liquidación). Recomendación: implementar el
`ComisionCalculator` + entidades + motor con tests unitarios acá, pero **cerrar
las decisiones §6 y validar la query contra datos reales en dev** antes de
integrarlo al pago de la liquidación de sueldo.
