# Compras móvil: factura en papel leída por IA — diseño

Fecha: 2026-10-24. Repos: central (`franco-system-backend-servidor`), PWA (`frc-mobile-pwa`), desktop (`frc-sistemas-integrados-angular`).

## Intención

Hoy el celular carga compras solo con factura electrónica (QR del KuDE → SIFEN → `documento_recibido`). Las facturas en papel
se cargan a mano en el desktop. Se suma «Factura en papel»: el comprador saca fotos, OpenAI lee cabecera e ítems, y desde
ahí la factura sigue **exactamente el mismo flujo** que la electrónica (cruce de productos, lo aprendido, crear la compra,
nota, vínculo producto-proveedor, carga al finalizar la planificación).

Regla rectora: **no duplicar lógica**. La foto termina en `documento_recibido` + `documento_recibido_item`; todo lo que
viene después ya existe y no se entera del origen.

## Objetivos

1. Elección «Factura electrónica (QR)» / «Factura en papel (foto)» en Nueva compra y dentro de un pedido existente.
2. La IA lee: RUC y nombre del emisor, RUC del cliente, timbrado, número (establecimiento-punto-número), fecha, condición,
   moneda, total e ítems (código del proveedor si hay, código de barras si hay, descripción, unidad, cantidad, precio
   unitario con IVA, tasa de IVA, total del ítem) y qué campos le resultaron dudosos.
3. Paso de **confirmación de cabecera** (solo papel): foto + datos editables, dudosos marcados.
4. Lo aprendido también por **descripción** (proveedores sin código), en la tabla existente `proveedor_codigo_producto`.
5. Un solo método que vincula producto ↔ proveedor (hoy copiado en `ComprasMobileService`, y el finalizar del desktop no lo llama).
6. Pantalla de administración en el desktop para la clave, el modelo y el prompt adicional de OpenAI.

## No-objetivos

- Subir PDF o importar desde el desktop (lo hacía la rama vieja; queda afuera).
- Usar IA sobre la factura electrónica (el XML es exacto).
- Validar el timbrado contra la SET.
- Las tablas `alias_*_import`, `factura_proveedor_import`, el matcher y el confirm de la rama `feat/factura-import-ia`.

## Anti-objetivos (sería un fracaso aunque funcione)

- Que la IA vincule productos o cree la compra sin que el comprador vea la factura leída.
- Que la misma factura se cargue dos veces (papel+papel o papel+QR).
- Que la clave de OpenAI llegue al celular, al desktop o quede en el repo (es público).
- Un segundo camino de cruce/creación de compra paralelo al existente.

## Decisiones del usuario (2026-10-24)

- **Fotos en la base**: tabla `operaciones.documento_recibido_foto` (bytea).
- **RUC sin proveedor**: el comprador elige el proveedor a mano (no se bloquea).
- **Clave de OpenAI por pantalla**: tabla `empresarial.configuracion_sistema` con la clave cifrada, editable desde el desktop.

## Cómo (decidido)

### Central
- Migración única `V256.3__compras_factura_papel_ia.sql`:
  - `empresarial.configuracion_sistema(clave PK, valor TEXT, descripcion TEXT, encriptado BOOL, modificado_en)`;
    semillas `openai.api_key` (cifrada, null), `openai.modelo` = `gpt-4o`, `openai.prompt_adicional` = ''.
  - `documento_recibido`: `cdc` pasa a NULL; `origen VARCHAR(10) NOT NULL DEFAULT 'SIFEN'` (`SIFEN`|`FOTO`);
    `cabecera_confirmada BOOLEAN NOT NULL DEFAULT true`; `ia_modelo`, `ia_tokens_entrada`, `ia_tokens_salida`,
    `ia_respuesta TEXT`, `ia_dudosos TEXT`; índice único parcial
    `uk_documento_recibido_numero (ruc_emisor, COALESCE(tipo_documento,1), timbrado, establecimiento, punto_expedicion, numero)`
    `WHERE timbrado IS NOT NULL AND numero IS NOT NULL AND ruc_emisor IS NOT NULL`.
  - `documento_recibido_item.ia_dudosos TEXT`.
  - `documento_recibido_foto(id, documento_recibido_id FK ON DELETE CASCADE, orden, mime, imagen BYTEA, creado_en)`.
  - `proveedor_codigo_producto`: `codigo_proveedor` pasa a NULL; `descripcion_normalizada VARCHAR(500)`;
    `CHECK (codigo_proveedor IS NOT NULL OR descripcion_normalizada IS NOT NULL)`; índice único parcial
    `(proveedor_id, descripcion_normalizada) WHERE codigo_proveedor IS NULL`.
- Cifrado: AES/GCM, clave = SHA-256 de la variable de entorno `CONFIG_MASTER_KEY` (sin default; sin ella no se puede
  guardar ni leer la clave cifrada y se avisa claro). No se toca `application.properties` (es local).
- OpenAI: se trae `OpenAiVisionService` de la rama vieja (prompt paraguayo, `normalizarMontos`, una llamada por página +
  `mergeResultados`, corte por `finish_reason=length`), adaptado a *structured outputs* (`response_format` `json_schema`
  estricto) y a los campos nuevos. Clave/modelo/prompt desde `configuracion_sistema`.
- Servicio nuevo `FacturaFotoService` (no se agranda `ComprasMobileService`, 2.468 líneas): lee fotos → valida →
  crea el documento con origen FOTO y `cabecera_confirmada=false` → devuelve la cabecera leída. Confirmar la cabecera
  valida y delega en `ComprasMobileService` el mismo armado que usa el QR.
- `confirmarFactura` y `crearCompraDesdeFactura` rechazan un documento con `cabecera_confirmada=false`.
- Proveedor: en nueva compra, por RUC leído (`proveedorPorRuc`) y si no hay, el comprador elige. En un pedido existente,
  el proveedor es el del pedido; si el RUC leído es válido (DV ok) y es de **otro** proveedor cargado, se bloquea igual
  que la electrónica; si el DV no cierra, solo aviso (lectura dudosa).
- Duplicados: el índice único + `FacturaRepetida` contra las notas ya cargadas (misma regla que hoy).
- Validaciones: DV del RUC (módulo 11, base 11), número `\d{3}-\d{3}-\d{7}`, timbrado de 8 dígitos, fecha no futura,
  suma de ítems vs total (tolerancia 1 % o 1 Gs. por ítem: aviso, no bloqueo), RUC del cliente = empresa (`rucsPropios`).
- Seguridad: lectura de factura con `exigirCompras()`; pantalla de IA con rol `ADMIN` chequeado a mano por
  `RoleService` (`@AdminSecured` está roto, issue #177).
- `CONFIG_MASTER_KEY` es una variable de entorno nueva: se carga como secret del repo y `deploy.yml`/`deploy.sh` la
  escriben en el `.env` igual que los `GMAIL_*` (si falta el secret no se toca el `.env`). **Avisar al líder técnico
  antes del PR.** El central arranca igual sin ella.
- Tablas nuevas central-only: no se registran en `configuraciones.replication_table`.
- En GraphQL el origen viaja como `String` (sin enum nuevo: evita el desfase Java/schema).

### PWA
- «Nueva compra con factura» abre una hoja con las dos opciones; en el pedido, «Cargar factura» igual.
- Cámara con `<input type="file" accept="image/*" capture="environment" multiple>`, hasta 5 hojas, reducidas a
  2400 px / JPEG 0,85 (constante propia, no se cambia la de gastos).
- Página nueva `factura-foto` (cabecera editable) → al confirmar navega a las páginas existentes
  (`nueva/:documentoId` o `:pedidoId/factura/:documentoId`).
- `FacturaProveedorMobile.cdc` pasa a opcional; se muestra «Factura en papel» donde hoy va el CDC.

### Desktop
- Configuración → «Inteligencia artificial» (solo ADMIN): clave (se muestra solo si está cargada y sus últimos 4),
  modelo, prompt adicional, botón «Probar conexión».

## Lo que queda al implementador

Nombres internos, textos de error no fijados acá, la forma exacta del prompt adicional y el layout de las pantallas
siguiendo el design-system de cada repo.
