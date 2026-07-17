-- "Compartir al central" (ver CupsAdminService/agregar-desde-sucursal-dialog) instala en el
-- central una cola CUPS proxy que reenvia por IPP a la sucursal dueña de la impresora. Antes,
-- el unico modo de marcar esto era pisar impresora.sucursal_id con el id del central (0), lo que
-- rompia PrintRouterService: el router cree que la impresora es local al central e imprime
-- contra la cola IPP-proxy (fragil, sin el proxy GraphQL confiable) en vez de hacer el proxy
-- GraphQL hacia la sucursal real.
--
-- compartida_en_central es un flag informativo, separado de sucursal_id: sucursal_id siempre
-- refleja el host real donde esta conectada la impresora (routing correcto via
-- PrintRouterService.proxyImprimir); este campo solo indica que ADEMAS existe una cola proxy
-- IPP instalada en el central (para uso fuera de la app, ej. impresion directa del SO en esa PC).
ALTER TABLE empresarial.impresora
    ADD COLUMN IF NOT EXISTS compartida_en_central BOOLEAN DEFAULT FALSE;
