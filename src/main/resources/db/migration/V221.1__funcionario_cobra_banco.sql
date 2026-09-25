-- =====================================================================
-- Funcionario — flag de forma de cobro (banco vs efectivo).
-- =====================================================================
-- Hasta ahora la unica pista de como cobra un funcionario era que tuviera
-- cargada `cuenta_bancaria`, un varchar libre que no distingue "cobra por
-- banco" de "tenemos el numero de cuenta anotado". Este flag lo hace
-- explicito, con el mismo patron que `ips_activo`: el toggle en el legajo
-- es el que habilita el campo de cuenta bancaria.
--
-- Aditiva: columna nullable con DEFAULT false. Idempotente.
-- =====================================================================

ALTER TABLE personas.funcionario
    ADD COLUMN IF NOT EXISTS cobra_banco boolean DEFAULT false;

-- Semilla: los funcionarios que ya tienen una cuenta bancaria cargada
-- arrancan clasificados como "cobra por banco". Es la mejor aproximacion
-- disponible al dato historico y se corrige uno por uno desde el legajo.
UPDATE personas.funcionario
   SET cobra_banco = true
 WHERE cobra_banco IS NOT DISTINCT FROM false
   AND cuenta_bancaria IS NOT NULL
   AND btrim(cuenta_bancaria) <> '';
