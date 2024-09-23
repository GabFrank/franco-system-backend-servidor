ALTER TABLE financiero.pdv_caja DROP CONSTRAINT pdv_caja_maletin_id_fkey;
ALTER TABLE financiero.pdv_caja ADD CONSTRAINT pdv_caja_maletin_id_fkey FOREIGN KEY (maletin_id) REFERENCES financiero.maletin(id) ON DELETE SET NULL ON UPDATE CASCADE;
