-- =====================================================
-- SCRIPT DE DATOS DE PRUEBA PARA SIFEN
-- Datos extraídos de la respuesta real de SIFEN (consulta_de_response.json)
-- =====================================================

-- =====================================================
-- PASO 1: Insertar Timbrado Electrónico
-- =====================================================
-- Datos extraídos del JSON:
-- Número de Timbrado: 18270044
-- RUC Emisor: 80099482-5
-- Razón Social: FRANCO AREVALOS S.A.
-- Email: francoarevalos05@gmail.com
-- Teléfono: 0982700027
-- Establecimiento: 001
-- Punto de Expedición: 001
-- Número de Documento: 0000031 (último emitido)
-- Departamento: CANINDEYU (código: 18)
-- Ciudad: SALTO DEL GUAIRA (código: 4738)
-- Dirección: CALLE, PY 03 GENERAL ELIZARDO AQUINO
-- Actividades Económicas:
--   Principal: 46304 - COMERCIO AL POR MAYOR DE BEBIDAS
--   Secundaria: 47112 - COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS

INSERT INTO financiero.timbrado (
    razon_social,
    ruc,
    numero,
    is_electronico,
    csc,
    email,
    tipo_sociedad,
    domicilio_fiscal_departamento,
    domicilio_fiscal_ciudad,
    domicilio_fiscal_codigo_ciudad,
    domicilio_fiscal_localidad,
    domicilio_fiscal_barrio,
    domicilio_fiscal_direccion,
    telefono,
    cod_actividad_economica_principal,
    desc_actividad_economica_principal,
    list_codigo_actividad_economica_secundaria,
    list_descripcion_actividad_economica_secundaria,
    fecha_inicio,
    fecha_fin,
    activo,
    creado_en,
    usuario_id
) VALUES (
    'FRANCO AREVALOS S.A.',
    '80099482-5',
    '18270044',
    true, -- Es timbrado electrónico
    'D37561586c1CAd69A2e7747E73f9F03B', -- CSC de prueba (ajustar según tu configuración)
    'francoarevalos05@gmail.com',
    'SOCIEDAD ANONIMA', -- Tipo de sociedad (inferido del nombre)
    'CANINDEYU',
    'SALTO DEL GUAIRA',
    '4738',
    NULL, -- Localidad no especificada en el JSON
    NULL, -- Barrio no especificado en el JSON
    'CALLE, PY 03 GENERAL ELIZARDO AQUINO',
    '0982700027',
    '46304',
    'COMERCIO AL POR MAYOR DE BEBIDAS',
    '47112', -- Código secundario
    'COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS', -- Descripción secundaria
    NULL, -- Para timbrados electrónicos, las fechas son opcionales
    NULL,
    true,
    CURRENT_TIMESTAMP,
    1 -- Usuario ID (ajustar según tu BD)
) ON CONFLICT (id) DO UPDATE SET
    razon_social = EXCLUDED.razon_social,
    ruc = EXCLUDED.ruc,
    numero = EXCLUDED.numero,
    is_electronico = EXCLUDED.is_electronico,
    email = EXCLUDED.email,
    telefono = EXCLUDED.telefono,
    activo = EXCLUDED.activo;

-- =====================================================
-- PASO 2: Insertar Timbrado Detalle (Punto de Expedición)
-- =====================================================
-- Datos extraídos del JSON:
-- Establecimiento: 001
-- Punto de Expedición: 001
-- Número Actual: 31 (último documento emitido: 0000031)
-- Departamento: CANINDEYU
-- Ciudad: SALTO DEL GUAIRA (código: 4738)
-- Dirección: CALLE, PY 03 GENERAL ELIZARDO AQUINO
-- Teléfono: 0982700027

INSERT INTO financiero.timbrado_detalle (
    timbrado_id,
    punto_de_venta_id,
    punto_expedicion,
    cantidad,
    rango_desde,
    rango_hasta,
    numero_actual,
    departamento,
    ciudad,
    codigo_ciudad,
    localidad,
    barrio,
    direccion,
    telefono,
    activo,
    creado_en,
    usuario_id,
    sucursal_id
) VALUES (
    null, -- ID temporal para pruebas (ajustar según la secuencia de tu BD)
    99999, -- Referencia al timbrado creado arriba
    1, -- Punto de venta ID (ajustar según tu BD - típicamente el PDV principal de la sucursal)
    '001',
    999999, -- Cantidad de documentos disponibles (número alto para electrónico)
    1, -- Rango desde
    999999, -- Rango hasta (número alto para electrónico)
    31, -- Número actual (último documento emitido: 0000031)
    'CANINDEYU',
    'SALTO DEL GUAIRA',
    '4738',
    NULL, -- Localidad no especificada
    NULL, -- Barrio no especificado
    'CALLE, PY 03 GENERAL ELIZARDO AQUINO',
    '0982700027',
    true,
    CURRENT_TIMESTAMP,
    1, -- Usuario ID (ajustar según tu BD)
    1 -- Sucursal ID (ajustar según tu BD - típicamente 1 para la sucursal principal)
) ON CONFLICT (id) DO UPDATE SET
    numero_actual = EXCLUDED.numero_actual,
    departamento = EXCLUDED.departamento,
    ciudad = EXCLUDED.ciudad,
    codigo_ciudad = EXCLUDED.codigo_ciudad,
    direccion = EXCLUDED.direccion,
    telefono = EXCLUDED.telefono,
    activo = EXCLUDED.activo;

-- =====================================================
-- PASO 3: Verificación de datos insertados
-- =====================================================
-- Para verificar que los datos se insertaron correctamente:
-- SELECT * FROM financiero.timbrado WHERE id = 99999;
-- SELECT * FROM financiero.timbrado_detalle WHERE id = 99999;

-- =====================================================
-- NOTAS IMPORTANTES
-- =====================================================
-- 1. Ajustar los IDs (99999) según la secuencia de tu base de datos
-- 2. Ajustar usuario_id (1) según el usuario de prueba en tu BD
-- 3. Ajustar sucursal_id (1) según la sucursal de prueba en tu BD
-- 4. Ajustar punto_de_venta_id (1) según el PDV de prueba en tu BD
-- 5. Este timbrado es ELECTRÓNICO (is_electronico = true)
-- 6. El establecimiento es "001" y punto de expedición "001"
-- 7. El último número emitido es 31 (documento 0000031 del JSON)
-- 8. Los datos de ubicación geográfica son de Salto del Guairá, Canindeyú
-- 9. Las actividades económicas cubren comercio al por mayor y menor
-- 10. El CSC debe coincidir con el configurado en application.properties

-- =====================================================
-- DATOS ADICIONALES DEL EMISOR (para referencia)
-- =====================================================
-- RUC Completo: 80099482-5
-- Razón Social: FRANCO AREVALOS S.A.
-- Tipo Contribuyente: PERSONA_JURIDICA (iTipCont = 2)
-- Email: francoarevalos05@gmail.com
-- Teléfono: 0982700027
-- Departamento: 18 - CANINDEYU
-- Ciudad: 4738 - SALTO DEL GUAIRA
-- Dirección: CALLE, PY 03 GENERAL ELIZARDO AQUINO
-- 
-- Actividades Económicas:
-- 1. 46304 - COMERCIO AL POR MAYOR DE BEBIDAS (Principal)
-- 2. 47112 - COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS (Secundaria)

-- =====================================================
-- EJEMPLO DE CDC GENERADO
-- =====================================================
-- CDC del documento de prueba: 01800994825001001000003122025100118428013857
-- Formato: 01 (Tipo DE) + 80099482 (RUC) + 5 (DV) + 001 (Est.) + 001 (Pto.Exp.) + 0000031 (Nro.Doc) + 2 (Tipo Doc) + 2025100118428013857 (Fecha/Seguridad)

