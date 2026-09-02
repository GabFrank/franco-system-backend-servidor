-- Impresoras alojadas en una PC Windows y compartidas por SMB/CIFS.
--
-- El transporte ya lo resolvia CUPS (backend /usr/lib/cups/backend/smb -> smbspool): en produccion
-- la cola adm_ticket ya apuntaba a smb://100.64.0.10/adm_ticket, creada a mano. Lo que faltaba era
-- que la app pudiera registrarlas, detectarlas e instalarlas. Ver impresoras.md seccion SMB.
--
-- Estas columnas son informativas / para reinstalar la cola. La contrasena del share NO se guarda:
-- impresora se replica MAIN_TO_ALL a todas las filiales (V144.3), asi que no puede llevar secretos.
-- La credencial vive unicamente en el device-uri que CUPS persiste en /etc/cups/printers.conf
-- (root-only) del host donde esta instalada la cola.
ALTER TABLE empresarial.impresora
    ADD COLUMN IF NOT EXISTS smb_host VARCHAR(120),
    ADD COLUMN IF NOT EXISTS smb_recurso VARCHAR(255),
    ADD COLUMN IF NOT EXISTS smb_usuario VARCHAR(120),
    ADD COLUMN IF NOT EXISTS smb_dominio VARCHAR(120);
