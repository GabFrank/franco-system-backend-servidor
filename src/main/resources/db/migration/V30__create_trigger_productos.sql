CREATE TRIGGER producto_insert_trigger
AFTER INSERT OR UPDATE OR DELETE ON productos.producto
FOR EACH ROW
EXECUTE FUNCTION notify_error_event();