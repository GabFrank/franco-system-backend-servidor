# Guion de demo — Módulo RRHH (presentación al encargado)

Recorrido guiado de **punta a punta** del módulo de RRHH, desde el panel de control
hasta la **liquidación final (finiquito)**. Pensado para presentar el módulo al
encargado del sector.

**Convención de marcadores:**
- 🟢 **PRECARGADO** — ya está en la DB (semilla), sólo se muestra.
- 🔴 **EN VIVO** — se hace durante la demo (ahí está la gracia: el encargado ve el sistema funcionando).
- 💬 **Decir** — punto a resaltar mientras se muestra.
- ✅ **Resultado** — qué debería verse.

---

## 0. Preparación previa (antes de la reunión)

1. Backend `central` arriba (perfil `dev`, DB `bodega_producto_devoluciones`) y desktop (`npm start`) abierto y logueado como ADMIN.
2. Correr la **semilla**: [`demo-rrhh-seed.sql`](demo-rrhh-seed.sql) →
   ```bash
   PGPASSWORD=franco psql -h localhost -p 5551 -U franco -d bodega_producto_devoluciones -f demo-rrhh-seed.sql
   ```
   Deja dos funcionarios:
   - 🟢 **MARÍA FERNANDA GIMÉNEZ LÓPEZ** (doc DEMO-0001) — *la estrella*: completa, VENDEDOR, ingreso 2021-04-01 (~5 años → preaviso 60 días), nacida en julio (sale en "Cumpleaños del mes"). Sobre ella corremos la liquidación mensual y el finiquito.
   - 🟢 **JORGE DANIEL BENÍTEZ** (doc DEMO-0002) — *la incompleta*: sólo nombre + documento. Sale arriba en "Legajos por completar"; la completamos en vivo.
3. **Caja Mayor abierta** (necesaria para vales/adelantos y para pagar liquidaciones).
4. Período de trabajo de la demo: **julio 2026** (`2026-07`).

> Todo lo demás (justificativos, horas extra, penalizaciones, vales, préstamos, bonos, liquidaciones) se crea **en vivo**.

---

## 1. Apertura — el Dashboard RRHH (panel de control)

**Menú lateral → Dashboard RRHH.** Es la puerta de entrada al módulo.

- 💬 "Todo el módulo se resume en un panel: cuántos funcionarios activos, la nómina del mes, liquidaciones pendientes, vacaciones por vencer."
- Mostrar los **4 KPIs** arriba. Cambiar el **período** (selector arriba a la derecha) y **Aplicar** → los indicadores del mes se recalculan; el gráfico de **Nómina por mes (últimos 12)** es histórico, no depende del filtro.
- Columna derecha: **Top exposición financiera** (quién debe más entre vales + préstamos), **Top horas extra del mes**, **Cumpleaños del mes** (ahí aparece MARÍA FERNANDA, cumple en julio).
- Columna izquierda: **Legajos por completar** — 💬 "El sistema detecta funcionarios con datos faltantes y los rankea del peor al mejor. Un click abre el legajo para completarlo." (Lo usamos en el punto 3.)
- Accesos rápidos: **Liquidaciones · Vales · Préstamos · Reportes** (menú con los 5 reportes PDF).

✅ El encargado ve de un vistazo el estado del sector.

---

## 2. Configuración RRHH (las reglas del juego)

**Menú lateral → Configuración RRHH.**

- 💬 "Antes que nada, el módulo se parametriza. No es una lista técnica: son secciones curadas por tema."
- Recorrer las pestañas: IPS (% funcionario / patronal), **Preaviso** (días por antigüedad: hasta 1 año 30, 1-5 años 45, 5-10 años 60, +10 años 90), vacaciones (prescripción, aviso), aguinaldo, etc.
- 💬 "Estos valores alimentan automáticamente los cálculos de liquidación y finiquito que vamos a ver."

✅ Queda claro que los cálculos no son mágicos: salen de esta configuración.

---

## 3. Legajo por completar (🔴 EN VIVO)

Volver al **Dashboard → card "Legajos por completar"**.

- Buscar **JORGE DANIEL BENÍTEZ** (score 1/10, badge rojo). 💬 "Alguien lo dio de alta apurado y quedó sin cargo, sin salario, sin datos."
- 🔴 **Click en la fila** → abre el **legajo** de JORGE.
- 🔴 En **Información general**: completar datos personales (documento ya está; agregar nacimiento, sexo, dirección, teléfono, ciudad). Guardar.
- 🔴 En **Cargos**: asignar **VENDEDOR**. En **Salarios**: asignar un salario (ej. 2.800.000). Guardar.
- Volver al **Dashboard** y **Aplicar** → 💬 "JORGE ya no aparece (o subió el score). El panel se autocorrige."

✅ Se demostró el ciclo detectar → completar → verificar.

---

## 4. El legajo completo — tour (MARÍA FERNANDA)

Abrir el legajo de **MARÍA FERNANDA** (desde Funcionarios, o buscarla). 💬 "Así se ve un legajo bien cargado. Es un mini-dashboard del funcionario."

Recorrer las pestañas en orden:
1. **Información general** — persona + **foto de perfil** (🔴 opcional: botón cambiar foto → abre el buscador del SO directo → se ve en el avatar del header).
2. **Financiero** — 💬 "Todo lo que el funcionario debe o cobró: crédito por convenio, vales, préstamos, penalizaciones, y su exposición total." (Solo lectura.)
3. **Cargos** — 🔴 **cambio de cargo EN VIVO**: VENDEDOR → **ENCARGADO**. Mostrar que queda el **histórico** (fecha, cargo anterior/nuevo).
4. **Salarios** — 🔴 **cambio de salario EN VIVO**: 3.200.000 → **3.800.000**. Mostrar el **histórico** de salarios.
5. **Asistencia**, **Puntuaciones**, **Documentos** — mostrar rápido.

✅ El encargado ve que cargo y salario son **versionados** (nada se pierde, todo queda auditado).

---

## 5. Asistencia (🔴 EN VIVO, sobre MARÍA)

- 🔴 **Justificativo** (ex "novedad"): cargar una ausencia justificada (ej. permiso médico). 💬 "Impacta o no el sueldo según el tipo."
- **Marcaciones**: mostrar el historial de entradas/salidas.
- 🔴 **Hora extra**: cargar 1 HE en julio (ej. 4 hs). 💬 "El monto se calcula solo según el salario."
- 🔴 **Penalización**: cargar 1 (ej. llegada tarde, 80.000). 💬 "Se descuenta en la liquidación del mes."

✅ Estos ítems los vamos a ver **aparecer solos** en la liquidación mensual.

---

## 6. Anticipos vía Caja Mayor (🔴 EN VIVO)

- 🔴 **Vale / adelanto**: MARÍA pide un adelanto (ej. 400.000). 💬 "Sale de Caja Mayor, queda encadenado; al liquidar se descuenta y el vale pasa a DESCONTADO." (Requiere caja abierta.)
- 🔴 **Préstamo**: otorgar un préstamo con cuotas (ej. 1.200.000 en 3 cuotas). 💬 "Cada cuota vencida se cobra en la liquidación."

✅ Vale y préstamo quedan visibles también en la pestaña **Financiero** del legajo y en el **Top exposición** del dashboard.

---

## 7. Beneficios

- **Vacaciones**: mostrar los días generados por antigüedad. 💬 "Los días no gozados se pagan en el finiquito automáticamente."
- **Aguinaldo**: mostrar el acumulado del año. 💬 "Se puede **pagar por separado** (elige Caja Mayor y emite recibo) o dejar que se sume en la liquidación del mes. Si ya se pagó aparte, la liquidación no lo vuelve a sumar." (🔴 opcional: pagar el aguinaldo de MARÍA por separado e imprimir el recibo.)
- 🔴 **Bono**: cargar un bono (ej. 200.000 por productividad). 💬 "También entra en la liquidación."

---

## 8. Liquidación mensual — el integrador (🔴 EN VIVO)

**Liquidaciones → generar borrador de MARÍA, período 2026-07.**

- 💬 "Este es el corazón del módulo. Miren cómo **arrastra solo** todo lo que cargamos."
- ✅ El borrador trae: **salario base**, **hora extra**, **bono** (haberes) y **IPS**, **penalización**, **vale**, **cuota de préstamo** (descuentos). El **neto** es la suma.
- 🔴 **Editar un ítem** (todo es negociable): cambiar un monto → 💬 "Queda auditado quién y cuándo lo editó, y el total se recalcula."
- 🔴 **Aprobar → Pagar** (elegir Caja Mayor) → 💬 "En una sola transacción: egreso de caja por el neto, el vale pasa a DESCONTADO, la cuota a PAGADA."
- 🔴 **Imprimir recibo** → el diálogo pregunta **PDF (A4) o Ticket (58/80mm)**. 💬 "Mismo comprobante, dos salidas: el PDF para archivar/firmar en hoja, o el ticket térmico que sale directo en la impresora del cajero." El PDF abre en el visor integrado (razón social, guaraníes enteros, monto en letras, firma); el ticket se imprime al toque en la térmica local.

✅ Queda demostrado el flujo mensual completo, punta a punta.

---

## 9. Liquidación final / finiquito — el gran final (🔴 EN VIVO)

**Legajo de MARÍA → botón Liquidación final** (o desde Liquidaciones).

- Se abre el **diálogo de parámetros** (todo precargado y editable):
  - Motivo de egreso, fecha de egreso, **fecha de ingreso** (2021-04-01), **salario promedio** (formato Gs), **días trabajados del mes**.
  - **Preaviso**: días por antigüedad (60, tramo 5-10 años) + toggle "¿se otorgó?".
  - Vacaciones no gozadas, aguinaldo proporcional, base IPS.
  - Toggles: descontar IPS / cobrar vales / cobrar convenios / cobrar préstamos / descontar penalizaciones.
- 🔴 **Escenario A — Despido injustificado SIN preaviso**: generar. 💬 "La ley obliga a **pagar** el preaviso: aparece como HABER. Más indemnización por antigüedad, vacaciones no gozadas, aguinaldo proporcional y el salario de los días del mes. Menos IPS y las obligaciones pendientes (vale/cuota/penalización)."
- 🔴 **Editar ítems** si hace falta (negociable, con auditoría). El total se recalcula.
- 🔴 **Aprobar → Pagar** → 💬 "El funcionario queda **inactivo**; egreso de caja por el total."
- 🔴 **Ver Recibo** → diálogo **PDF o Ticket**. El PDF **"LIQUIDACIÓN FINAL DE HABERES"** abre en el visor (empresa, trabajador, entrada/salida, antigüedad, tabla de conceptos, total, monto en letras, firma con C.I.); el ticket sale en la térmica.
- (Opcional) 🔴 **Regenerar** con **Escenario B — Renuncia SIN preaviso**: 💬 "Al revés: el preaviso se **descuenta** (la mitad de los días), y no hay indemnización." Mostrar la diferencia en el total.

✅ El módulo cierra el ciclo de vida del funcionario, con la ley de por medio.

---

## 10. Reportes (cierre)

**Dashboard → Reportes (menú)** → generar **Nómina del mes** (2026-07) y **Resumen IPS**. 💬 "Todos en PDF, montos en guaraníes enteros, razón social real."

Volver al **Dashboard → Aplicar**: 💬 "Miren cómo cambió el panel: la nómina del mes, un funcionario menos activo (MARÍA egresó), la exposición financiera actualizada." Cierra el círculo con el que empezamos.

---

## Reset post-demo

Tras la presentación, para dejar la DB como estaba: descomentar y ejecutar el bloque **CLEANUP** al final de [`demo-rrhh-seed.sql`](demo-rrhh-seed.sql) (borra primero los eventos RRHH creados en vivo, luego funcionario y persona de DEMO-0001/DEMO-0002).

---

## Orden rápido (chuleta)

| # | Pantalla | Modo | Qué mostrar |
|---|---|---|---|
| 1 | Dashboard | 🟢 | KPIs, chart, rankings, legajos por completar |
| 2 | Configuración RRHH | 🟢 | Secciones curadas, preaviso, IPS |
| 3 | Dashboard → legajo JORGE | 🔴 | completar datos + cargo + salario |
| 4 | Legajo MARÍA | 🔴 | tour + cambio de cargo + cambio de salario |
| 5 | Asistencia | 🔴 | justificativo, HE, penalización |
| 6 | Caja Mayor | 🔴 | vale, préstamo |
| 7 | Beneficios | 🔴 | vacaciones, aguinaldo, bono |
| 8 | Liquidación mensual | 🔴 | arrastre de ítems, editar, aprobar, pagar, recibo |
| 9 | Liquidación final | 🔴 | preaviso, escenarios A/B, aprobar, pagar, recibo |
| 10 | Reportes + Dashboard | 🔴 | PDFs + panel actualizado |
