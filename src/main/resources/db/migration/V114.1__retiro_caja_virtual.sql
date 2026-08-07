ALTER TABLE financiero.retiro 
ADD COLUMN caja_virtual_id BIGINT REFERENCES financiero.caja_virtual(id);
