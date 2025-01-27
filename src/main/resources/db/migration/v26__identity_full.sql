ALTER TABLE administrativo.marcacion REPLICA IDENTITY FULL;
ALTER TABLE configuraciones.inicio_sesion REPLICA IDENTITY FULL;
ALTER TABLE financiero.cambio_caja REPLICA IDENTITY FULL;
ALTER TABLE financiero.conteo REPLICA IDENTITY FULL;
ALTER TABLE financiero.conteo_moneda REPLICA IDENTITY FULL;
ALTER TABLE financiero.factura_legal REPLICA IDENTITY FULL;
ALTER TABLE financiero.factura_legal_item REPLICA IDENTITY FULL;
ALTER TABLE financiero.gasto REPLICA IDENTITY FULL;
ALTER TABLE financiero.gasto_detalle REPLICA IDENTITY FULL;
ALTER TABLE financiero.maletin REPLICA IDENTITY FULL;
ALTER TABLE financiero.movimiento_caja REPLICA IDENTITY FULL;
ALTER TABLE financiero.movimiento_personas REPLICA IDENTITY FULL;
ALTER TABLE financiero.pdv_caja REPLICA IDENTITY FULL;
ALTER TABLE financiero.retiro REPLICA IDENTITY FULL;
ALTER TABLE financiero.retiro_detalle REPLICA IDENTITY FULL;
ALTER TABLE financiero.sencillo REPLICA IDENTITY FULL;
ALTER TABLE financiero.sencillo_detalle REPLICA IDENTITY FULL;
ALTER TABLE financiero.venta_credito REPLICA IDENTITY FULL;
ALTER TABLE financiero.venta_credito_cuota REPLICA IDENTITY FULL;
ALTER TABLE operaciones.cobro REPLICA IDENTITY FULL;
ALTER TABLE operaciones.cobro_detalle REPLICA IDENTITY FULL;
ALTER TABLE operaciones.delivery REPLICA IDENTITY FULL;
ALTER TABLE operaciones.movimiento_stock REPLICA IDENTITY FULL;
ALTER TABLE operaciones.venta REPLICA IDENTITY FULL;
ALTER TABLE operaciones.venta_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.vuelto REPLICA IDENTITY FULL;
ALTER TABLE operaciones.vuelto_item REPLICA IDENTITY FULL;
ALTER TABLE configuraciones.actualizacion REPLICA IDENTITY FULL;
ALTER TABLE configuraciones.local REPLICA IDENTITY FULL;
ALTER TABLE empresarial.cargo REPLICA IDENTITY FULL;
ALTER TABLE empresarial.configuracion_general REPLICA IDENTITY FULL;
ALTER TABLE empresarial.punto_de_venta REPLICA IDENTITY FULL;
ALTER TABLE empresarial.sector REPLICA IDENTITY FULL;
ALTER TABLE empresarial.sucursal REPLICA IDENTITY FULL;
ALTER TABLE empresarial.zona REPLICA IDENTITY FULL;
ALTER TABLE equipos.equipo REPLICA IDENTITY FULL;
ALTER TABLE equipos.tipo_equipo REPLICA IDENTITY FULL;
ALTER TABLE financiero.banco REPLICA IDENTITY FULL;
ALTER TABLE financiero.cambio REPLICA IDENTITY FULL;
ALTER TABLE financiero.cuenta_bancaria REPLICA IDENTITY FULL;
ALTER TABLE financiero.documento REPLICA IDENTITY FULL;
ALTER TABLE financiero.forma_pago REPLICA IDENTITY FULL;
ALTER TABLE financiero.moneda REPLICA IDENTITY FULL;
ALTER TABLE financiero.moneda_billetes REPLICA IDENTITY FULL;
ALTER TABLE financiero.timbrado REPLICA IDENTITY FULL;
ALTER TABLE financiero.timbrado_detalle REPLICA IDENTITY FULL;
ALTER TABLE financiero.tipo_gasto REPLICA IDENTITY FULL;
ALTER TABLE general.barrio REPLICA IDENTITY FULL;
ALTER TABLE general.ciudad REPLICA IDENTITY FULL;
ALTER TABLE general.contacto REPLICA IDENTITY FULL;
ALTER TABLE general.pais REPLICA IDENTITY FULL;
ALTER TABLE operaciones.compra REPLICA IDENTITY FULL;
ALTER TABLE operaciones.compra_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.entrada REPLICA IDENTITY FULL;
ALTER TABLE operaciones.entrada_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.inventario REPLICA IDENTITY FULL;
ALTER TABLE operaciones.inventario_producto REPLICA IDENTITY FULL;
ALTER TABLE operaciones.inventario_producto_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.motivo_diferencia_pedido REPLICA IDENTITY FULL;
ALTER TABLE operaciones.necesidad REPLICA IDENTITY FULL;
ALTER TABLE operaciones.necesidad_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.nota_pedido REPLICA IDENTITY FULL;
ALTER TABLE operaciones.nota_recepcion REPLICA IDENTITY FULL;
ALTER TABLE operaciones.nota_recepcion_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.pedido REPLICA IDENTITY FULL;
ALTER TABLE operaciones.pedido_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.pedido_item_sucursal REPLICA IDENTITY FULL;
ALTER TABLE operaciones.precio_delivery REPLICA IDENTITY FULL;
ALTER TABLE operaciones.programar_precio REPLICA IDENTITY FULL;
ALTER TABLE operaciones.salida REPLICA IDENTITY FULL;
ALTER TABLE operaciones.salida_item REPLICA IDENTITY FULL;
ALTER TABLE operaciones.transferencia REPLICA IDENTITY FULL;
ALTER TABLE operaciones.transferencia_item REPLICA IDENTITY FULL;
ALTER TABLE personas.cliente REPLICA IDENTITY FULL;
ALTER TABLE personas.funcionario REPLICA IDENTITY FULL;
ALTER TABLE personas.grupo_role REPLICA IDENTITY FULL;
ALTER TABLE personas.persona REPLICA IDENTITY FULL;
ALTER TABLE personas.proveedor REPLICA IDENTITY FULL;
ALTER TABLE personas.proveedor_dias_visita REPLICA IDENTITY FULL;
ALTER TABLE personas.role REPLICA IDENTITY FULL;
ALTER TABLE personas.usuario REPLICA IDENTITY FULL;
ALTER TABLE personas.usuario_grupo REPLICA IDENTITY FULL;
ALTER TABLE personas.usuario_role REPLICA IDENTITY FULL;
ALTER TABLE personas.vendedor REPLICA IDENTITY FULL;
ALTER TABLE personas.vendedor_proveedor REPLICA IDENTITY FULL;
ALTER TABLE productos.codigo REPLICA IDENTITY FULL;
ALTER TABLE productos.codigo_tipo_precio REPLICA IDENTITY FULL;
ALTER TABLE productos.costo_por_producto REPLICA IDENTITY FULL;
ALTER TABLE productos.familia REPLICA IDENTITY FULL;
ALTER TABLE productos.pdv_categoria REPLICA IDENTITY FULL;
ALTER TABLE productos.pdv_grupo REPLICA IDENTITY FULL;
ALTER TABLE productos.pdv_grupos_productos REPLICA IDENTITY FULL;
ALTER TABLE productos.precio_por_sucursal REPLICA IDENTITY FULL;
ALTER TABLE productos.presentacion REPLICA IDENTITY FULL;
ALTER TABLE productos.producto REPLICA IDENTITY FULL;
ALTER TABLE productos.producto_imagen REPLICA IDENTITY FULL;
ALTER TABLE productos.producto_por_sucursal REPLICA IDENTITY FULL;
ALTER TABLE productos.producto_proveedor REPLICA IDENTITY FULL;
ALTER TABLE productos.subfamilia REPLICA IDENTITY FULL;
ALTER TABLE productos.tipo_precio REPLICA IDENTITY FULL;
ALTER TABLE productos.tipo_presentacion REPLICA IDENTITY FULL;
ALTER TABLE vehiculos.marca REPLICA IDENTITY FULL;
ALTER TABLE vehiculos.modelo REPLICA IDENTITY FULL;
ALTER TABLE vehiculos.tipo_vehiculo REPLICA IDENTITY FULL;
ALTER TABLE vehiculos.vehiculo REPLICA IDENTITY FULL;
ALTER TABLE vehiculos.vehiculo_sucursal REPLICA IDENTITY FULL;