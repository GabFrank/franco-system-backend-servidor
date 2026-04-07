[sudo] password for franco: --
-- PostgreSQL database dump
--

-- Dumped from database version 16.1
-- Dumped by pg_dump version 16.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: administrativo; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA administrativo;


--
-- Name: configuraciones; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA configuraciones;


--
-- Name: empresarial; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA empresarial;


--
-- Name: equipos; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA equipos;


--
-- Name: financiero; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA financiero;


--
-- Name: general; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA general;


--
-- Name: media; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA media;


--
-- Name: operaciones; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA operaciones;


--
-- Name: personas; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA personas;


--
-- Name: productos; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA productos;


--
-- Name: utilitarios; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA utilitarios;


--
-- Name: vehiculos; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA vehiculos;


--
-- Name: dblink; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS dblink WITH SCHEMA public;


--
-- Name: estado_autorizacion; Type: TYPE; Schema: administrativo; Owner: -
--

CREATE TYPE administrativo.estado_autorizacion AS ENUM (
    'EN ESPERA',
    'CANCELADO',
    'AUTORIZADO',
    'NO_AUTORIZADO'
);


--
-- Name: tipo_autorizacion; Type: TYPE; Schema: administrativo; Owner: -
--

CREATE TYPE administrativo.tipo_autorizacion AS ENUM (
    'MARCACION'
);


--
-- Name: tipo_marcacion; Type: TYPE; Schema: administrativo; Owner: -
--

CREATE TYPE administrativo.tipo_marcacion AS ENUM (
    'ENTRADA',
    'SALIDA'
);


--
-- Name: nivel_actualizacion; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.nivel_actualizacion AS ENUM (
    'CRITICO',
    'MODERADO',
    'MANTENIMIENTO'
);


--
-- Name: nivel_error; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.nivel_error AS ENUM (
    'INFO',
    'ALERTA',
    'PELIGRO',
    'CRITICO'
);


--
-- Name: replication_direction; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.replication_direction AS ENUM (
    'MAIN_TO_ALL',
    'MAIN_TO_SPECIFIC',
    'BRANCH_TO_MAIN'
);


--
-- Name: tipo_actualizacion; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.tipo_actualizacion AS ENUM (
    'MOBILE',
    'DESKTOP',
    'SERVIDOR_FILIAL',
    'SERVIDOR_CENTRAL'
);


--
-- Name: tipo_dispositivo; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.tipo_dispositivo AS ENUM (
    'ANDROID',
    'IOS',
    'DESKTOP_WIN',
    'DESKTOP_LIN',
    'DESKTOP_MAC',
    'WEBWEB_MOBILE'
);


--
-- Name: tipo_error; Type: TYPE; Schema: configuraciones; Owner: -
--

CREATE TYPE configuraciones.tipo_error AS ENUM (
    'APLICACION',
    'BASE_DE_DATOS'
);


--
-- Name: estado_de_enum; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.estado_de_enum AS ENUM (
    'PENDIENTE',
    'EN_LOTE',
    'APROBADO',
    'RECHAZADO',
    'CANCELADO'
);


--
-- Name: estado_evento_enum; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.estado_evento_enum AS ENUM (
    'PENDIENTE',
    'APROBADO',
    'RECHAZADO',
    'ERROR_ENVIO'
);


--
-- Name: estado_lote_de_enum; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.estado_lote_de_enum AS ENUM (
    'PENDIENTE_ENVIO',
    'EN_PROCESO',
    'PROCESADO',
    'PROCESADO_CON_ERRORES',
    'ERROR_ENVIO',
    'ERROR_RED',
    'ERROR_PERMANENTE',
    'RECHAZADO'
);


--
-- Name: estado_retiro; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.estado_retiro AS ENUM (
    'EN_PROCESO',
    'CONCLUIDO',
    'NECESITA_VERIFICACION',
    'EN_VERIFICACION',
    'VERIFICADO_CONCLUIDO_SIN_PROBLEMA',
    'VERIFICADO_CONCLUIDO_CON_PROBLEMA'
);


--
-- Name: estado_venta_credito; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.estado_venta_credito AS ENUM (
    'ABIERTO',
    'FINALIZADO',
    'EN_MORA',
    'INCOBRABLE',
    'CANCELADO'
);


--
-- Name: pdv_caja_estado; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.pdv_caja_estado AS ENUM (
    'EN_PROCESO',
    'CONCLUIDO',
    'NECESITA_VERIFICACION',
    'EN_VERIFICACION',
    'VERIFICADO_CONCLUIDO_SIN_PROBLEMA',
    'VERIFICADO_CONCLUIDO_CON_PROBLEMA'
);


--
-- Name: pdv_caja_tipo_movimiento; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.pdv_caja_tipo_movimiento AS ENUM (
    'CAJA_INICIAL',
    'VENTA',
    'GASTO',
    'VALE',
    'RETIRO',
    'DEVOLUCION',
    'SALIDA_SENCILLO',
    'CAMBIO',
    'AJUSTE',
    'ENTRADA_SENCILLO',
    'CAJA_FINAL'
);


--
-- Name: tipo_confirmacion; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.tipo_confirmacion AS ENUM (
    'CONTRASENA',
    'PASSWORD',
    'QR',
    'LECTOR_HUELLAS',
    'FIRMA',
    'APP'
);


--
-- Name: tipo_cuenta; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.tipo_cuenta AS ENUM (
    'CUENTA_CORRIENTE',
    'CAJA_DE_AHORRO'
);


--
-- Name: tipo_movimiento_personas; Type: TYPE; Schema: financiero; Owner: -
--

CREATE TYPE financiero.tipo_movimiento_personas AS ENUM (
    'ANTICIPO',
    'AGUINALDO',
    'BONO',
    'VENTA_CREDITO',
    'MULTA',
    'PRESTAMO',
    'VACACIONES',
    'NO_DEVOLVIDOS',
    'COBRO',
    'SALARIO',
    'PAGO_SALARIO'
);


--
-- Name: dias_semana; Type: TYPE; Schema: general; Owner: -
--

CREATE TYPE general.dias_semana AS ENUM (
    'LUNES',
    'MARTES',
    'MIERCOLES',
    'JEUVES',
    'VIERNES',
    'SABADO',
    'DOMINGO'
);


--
-- Name: meses; Type: TYPE; Schema: general; Owner: -
--

CREATE TYPE general.meses AS ENUM (
    'ENERO',
    'FEBRERO',
    'MARZO',
    'ABRIL',
    'MAYO',
    'JUNIO',
    'JULIO',
    'AGOSTO',
    'SEMPTIEMBRE',
    'OCTUBRE',
    'NOVIEMBRE',
    'DICIEMBRE'
);


--
-- Name: cambio_precio_momento; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.cambio_precio_momento AS ENUM (
    'INMEDIATO',
    'EN_FECHA_INDICADA',
    'AL_RECIBIR_COMPRA',
    'AL_AUTORIZAR',
    'AL_ALCANZAR_CANTIDAD'
);


--
-- Name: compra_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.compra_estado AS ENUM (
    'ACTIVO',
    'CANCELADO',
    'DEVILVIDO',
    'EN_OBSERVACION',
    'IRREGULAR',
    'PRE_COMPRA'
);


--
-- Name: compra_item_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.compra_item_estado AS ENUM (
    'SIN_MODIFICACION',
    'MODIFICADO'
);


--
-- Name: compra_tipo_boleta; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.compra_tipo_boleta AS ENUM (
    'LEGAL',
    'COMUN'
);


--
-- Name: delivery_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.delivery_estado AS ENUM (
    'ABIERTO',
    'EN_CAMINO',
    'ENTREGADO',
    'CANCELADO',
    'DEVOLVIDO',
    'PARA_ENTREGA',
    'CONCLUIDO'
);


--
-- Name: devolucion_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.devolucion_estado AS ENUM (
    'PENDIENTE',
    'CONFIRMADA',
    'CANCELADA'
);


--
-- Name: estado_constancia; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.estado_constancia AS ENUM (
    'EMITIDA',
    'ANULADA'
);


--
-- Name: estado_verificacion; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.estado_verificacion AS ENUM (
    'PENDIENTE',
    'VERIFICADO',
    'VERIFICADO_CON_DIFERENCIA',
    'RECHAZADO'
);


--
-- Name: etapa_transferencia; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.etapa_transferencia AS ENUM (
    'PRE_TRANSFERENCIA_CREACION',
    'PRE_TRANSFERENCIA_ORIGEN',
    'PREPARACION_MERCADERIA',
    'PREPARACION_MERCADERIA_CONCLUIDA',
    'TRANSPORTE_VERIFICACION',
    'TRANSPORTE_EN_CAMINO',
    'TRANSPORTE_EN_DESTINO',
    'RECEPCION_EN_VERIFICACION',
    'RECEPCION_CONCLUIDA'
);


--
-- Name: inventario_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.inventario_estado AS ENUM (
    'ABIERTO',
    'CANCELADO',
    'CONCLUIDO'
);


--
-- Name: inventario_producto_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.inventario_producto_estado AS ENUM (
    'BUENO',
    'AVERIADO',
    'VENCIDO'
);


--
-- Name: metodo_verificacion; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.metodo_verificacion AS ENUM (
    'ESCANER',
    'MANUAL'
);


--
-- Name: motivo_rechazo_fisico; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.motivo_rechazo_fisico AS ENUM (
    'PRODUCTO_DANADO',
    'PRODUCTO_VENCIDO',
    'CANTIDAD_INCORRECTA',
    'PRODUCTO_DIFERENTE',
    'EMBALAJE_DANADO',
    'OTRO',
    'PRODUCTO_FALTANTE'
);


--
-- Name: motivo_verificacion_manual; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.motivo_verificacion_manual AS ENUM (
    'CODIGO_ILEGIBLE',
    'PRODUCTO_SIN_CODIGO'
);


--
-- Name: necesidad_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.necesidad_estado AS ENUM (
    'ACTIVO',
    'MODIFICADO',
    'CANCELADO',
    'EN_VERIFICACION',
    'EN_VERIFICACION_SOLICITUD_AUTORIZACION',
    'VERFICADO_SIN_MODIFICACION',
    'VERFICADO_CON_MODIFICACION',
    'CONCLUIDO'
);


--
-- Name: necesidad_item_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.necesidad_item_estado AS ENUM (
    'ACTIVO',
    'CANCELADO',
    'DEVOLUCION',
    'CONCLUIDO',
    'EN_FALTA'
);


--
-- Name: nota_recepcion_agrupada_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.nota_recepcion_agrupada_estado AS ENUM (
    'EN_RECEPCION',
    'CONCLUIDO',
    'CANCELADO'
);


--
-- Name: nota_recepcion_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.nota_recepcion_estado AS ENUM (
    'PENDIENTE_CONCILIACION',
    'CONCILIADA',
    'EN_RECEPCION',
    'RECEPCION_PARCIAL',
    'RECEPCION_COMPLETA',
    'CERRADA'
);


--
-- Name: nota_recepcion_item_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.nota_recepcion_item_estado AS ENUM (
    'PENDIENTE_CONCILIACION',
    'CONCILIADO',
    'RECHAZADO',
    'DISCREPANCIA'
);


--
-- Name: pago_detalle_cuota_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.pago_detalle_cuota_estado AS ENUM (
    'PENDIENTE',
    'PAGO_PARCIAL',
    'PAGADO',
    'CANCELADO'
);


--
-- Name: pago_detalle_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.pago_detalle_estado AS ENUM (
    'ABIERTO',
    'PENDIENTE',
    'PAGO_PARCIAL',
    'CONCLUIDO',
    'CANCELADO'
);


--
-- Name: pago_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.pago_estado AS ENUM (
    'ABIERTO',
    'PENDIENTE',
    'PARCIAL',
    'CONCLUIDO',
    'CANCELADO'
);


--
-- Name: pedido_forma_pago; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.pedido_forma_pago AS ENUM (
    'EFECTIVO',
    'TRANSFERENCIA',
    'CHEQUE',
    'CREDITO'
);


--
-- Name: pedido_item_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.pedido_item_estado AS ENUM (
    'ACTIVO',
    'CANCELADO',
    'DEVOLUCION',
    'CONCLUIDO',
    'EN_FALTA'
);


--
-- Name: proceso_etapa_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.proceso_etapa_estado AS ENUM (
    'PENDIENTE',
    'EN_PROCESO',
    'COMPLETADA',
    'OMITIDA',
    'CANCELADA'
);


--
-- Name: proceso_etapa_tipo; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.proceso_etapa_tipo AS ENUM (
    'CREACION',
    'RECEPCION_NOTA',
    'RECEPCION_MERCADERIA',
    'SOLICITUD_PAGO'
);


--
-- Name: recepcion_mercaderia_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.recepcion_mercaderia_estado AS ENUM (
    'PENDIENTE',
    'EN_PROCESO',
    'FINALIZADA',
    'CANCELADA'
);


--
-- Name: solicitud_pago_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.solicitud_pago_estado AS ENUM (
    'PENDIENTE',
    'PARCIAL',
    'CONCLUIDO',
    'CANCELADO'
);


--
-- Name: tipo_entrada; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_entrada AS ENUM (
    'COMPRA',
    'SUCURSAL',
    'AJUSTE'
);


--
-- Name: tipo_inventario; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_inventario AS ENUM (
    'ABC',
    'ZONA',
    'PRODUCTO',
    'CATEGORIA'
);


--
-- Name: tipo_movimiento; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_movimiento AS ENUM (
    'COMPRA',
    'VENTA',
    'DEVOLUCION',
    'DESCARTE',
    'AJUSTE',
    'TRANSFERENCIA',
    'CALCULO',
    'ENTRADA',
    'SALIDA'
);


--
-- Name: tipo_origen_vencimiento; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_origen_vencimiento AS ENUM (
    'RECEPCION_MERCADERIA',
    'AJUSTE_STOCK',
    'VENTA',
    'TRANSFERENCIA'
);


--
-- Name: tipo_salida; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_salida AS ENUM (
    'SUCURSAL',
    'VENCIDO',
    'DETERIORADO',
    'AJUSTE'
);


--
-- Name: tipo_solicitud_pago; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_solicitud_pago AS ENUM (
    'COMPRA',
    'GASTO',
    'RRHH'
);


--
-- Name: tipo_transferencia; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_transferencia AS ENUM (
    'MANUAL',
    'AUTOMATICA',
    'MIXTA'
);


--
-- Name: tipo_venta; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.tipo_venta AS ENUM (
    'EFECTIVO',
    'CREDITO',
    'TARJETA',
    'TRANSFERENCIA',
    'CONSIGNACION',
    'CORTESIA'
);


--
-- Name: transferencia_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.transferencia_estado AS ENUM (
    'ABIERTA',
    'EN_ORIGEN',
    'EN_TRANSITO',
    'EN_DESTINO',
    'FALTA_REVISION_EN_ORIGEN',
    'FALTA_REVISION_EN_DESTINO',
    'CONLCUIDA',
    'CANCELADA'
);


--
-- Name: transferencia_item_motivo_modificacion; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.transferencia_item_motivo_modificacion AS ENUM (
    'CANTIDAD_INCORRECTA',
    'VENCIMIENTO_INCORRECTO',
    'PRESENTACION_INCORRECTA'
);


--
-- Name: transferencia_item_motivo_rechazo; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.transferencia_item_motivo_rechazo AS ENUM (
    'FALTA_PRODUCTO',
    'PRODUCTO_AVERIADO',
    'PRODUCTO_VENCIDO',
    'PRODUCTO_EQUIVOCADO'
);


--
-- Name: venta_estado; Type: TYPE; Schema: operaciones; Owner: -
--

CREATE TYPE operaciones.venta_estado AS ENUM (
    'ABIERTA',
    'CONCLUIDA',
    'CANCELADA',
    'EN_VERIFICACION'
);


--
-- Name: tipo_cliente; Type: TYPE; Schema: personas; Owner: -
--

CREATE TYPE personas.tipo_cliente AS ENUM (
    'NORMAL',
    'ASOCIADO',
    'CONVENIADO',
    'FUNCIONARIO',
    'VIP'
);


--
-- Name: tipo_conservacion; Type: TYPE; Schema: productos; Owner: -
--

CREATE TYPE productos.tipo_conservacion AS ENUM (
    'ENFRIABLE',
    'NO_ENFRIABLE',
    'REFRIGERABLE',
    'CONGELABLE'
);


--
-- Name: unidad_medida; Type: TYPE; Schema: productos; Owner: -
--

CREATE TYPE productos.unidad_medida AS ENUM (
    'UNIDAD',
    'CAJA',
    'KILO',
    'LITROS'
);


--
-- Name: estado_vehiculo; Type: TYPE; Schema: vehiculos; Owner: -
--

CREATE TYPE vehiculos.estado_vehiculo AS ENUM (
    'FUNCIONANDO',
    'AVERIADO',
    'EN_REPARACION',
    'AGUARDANDO_REPARACION'
);


--
-- Name: delete_after_record_saved(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.delete_after_record_saved() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  -- Delete the record(s) after the new row has been committed
  DELETE FROM operaciones.stock_por_producto_sucursal WHERE producto_id = NEW.producto_id;
  RETURN NULL; -- With AFTER triggers, this doesn't prevent insertion (it's already done)
END;
$$;


--
-- Name: delete_new_record(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.delete_new_record() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  -- Simply delete the new row before it is committed
  DELETE FROM operaciones.stock_por_producto_sucursal WHERE producto_id = NEW.producto_id;
  RETURN NULL; -- Returning NULL prevents the row from being inserted
END;
$$;


--
-- Name: notify_error_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.notify_error_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM pg_notify(
        'error_event_channel',
        json_build_object(
            'table', TG_TABLE_NAME,
            'operation', TG_OP,
            'id', NEW.id,
            'message', 'A ' || TG_OP || ' operation occurred on table ' || TG_TABLE_NAME
        )::text
    );
    RETURN NEW;
END;
$$;


--
-- Name: reiniciartablas(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reiniciartablas(_username text, _schemaname text) RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
   execute (SELECT 'TRUNCATE '
       || string_agg(quote_ident(schemaname) || '.' || quote_ident(tablename), ', ')
       || ' RESTART IDENTITY CASCADE ;'
   FROM   pg_tables
   WHERE  tableowner = _username
   AND    schemaname = _schemaname
   );
END
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: autorizacion; Type: TABLE; Schema: administrativo; Owner: -
--

CREATE TABLE administrativo.autorizacion (
    id bigint NOT NULL,
    funcionario_id bigint,
    autorizador_id bigint,
    tipo_autorizacion administrativo.tipo_autorizacion,
    estado_autorizacion administrativo.estado_autorizacion,
    observacion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: autorizacion_id_seq; Type: SEQUENCE; Schema: administrativo; Owner: -
--

CREATE SEQUENCE administrativo.autorizacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: autorizacion_id_seq; Type: SEQUENCE OWNED BY; Schema: administrativo; Owner: -
--

ALTER SEQUENCE administrativo.autorizacion_id_seq OWNED BY administrativo.autorizacion.id;


--
-- Name: horario; Type: TABLE; Schema: administrativo; Owner: -
--

CREATE TABLE administrativo.horario (
    id bigint NOT NULL,
    descripcion character varying(255),
    hora_entrada time without time zone,
    hora_salida time without time zone,
    tolerancia_minutos integer DEFAULT 0,
    inicio_descanso time without time zone,
    fin_descanso time without time zone,
    usuario_id bigint,
    creado_en timestamp without time zone DEFAULT now(),
    turno character varying(20)
);


--
-- Name: horario_dias; Type: TABLE; Schema: administrativo; Owner: -
--

CREATE TABLE administrativo.horario_dias (
    horario_id bigint NOT NULL,
    dia character varying(20)
);


--
-- Name: horario_id_seq; Type: SEQUENCE; Schema: administrativo; Owner: -
--

CREATE SEQUENCE administrativo.horario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: horario_id_seq; Type: SEQUENCE OWNED BY; Schema: administrativo; Owner: -
--

ALTER SEQUENCE administrativo.horario_id_seq OWNED BY administrativo.horario.id;


--
-- Name: jornada; Type: TABLE; Schema: administrativo; Owner: -
--

CREATE TABLE administrativo.jornada (
    id bigint NOT NULL,
    usuario_id bigint,
    fecha date NOT NULL,
    entrada_id bigint,
    salida_id bigint,
    minutos_trabajados bigint DEFAULT 0,
    minutos_extras bigint DEFAULT 0,
    minutos_llegada_tardia bigint DEFAULT 0,
    estado character varying(30),
    observacion character varying(255),
    actualizado_en timestamp without time zone,
    marcacion_salida_almuerzo_id bigint,
    marcacion_entrada_almuerzo_id bigint,
    minutos_llegada_tardia_almuerzo bigint DEFAULT 0,
    hora_entrada_horario time without time zone,
    hora_salida_horario time without time zone,
    inicio_descanso_horario time without time zone,
    fin_descanso_horario time without time zone,
    tolerancia_minutos_horario integer,
    turno character varying(20),
    sucursal_id bigint NOT NULL,
    entrada_sucursal_id bigint,
    salida_sucursal_id bigint,
    marcacion_salida_almuerzo_suc_id bigint,
    marcacion_entrada_almuerzo_suc_id bigint
);

ALTER TABLE ONLY administrativo.jornada REPLICA IDENTITY FULL;


--
-- Name: marcacion; Type: TABLE; Schema: administrativo; Owner: -
--

CREATE TABLE administrativo.marcacion (
    id bigint NOT NULL,
    tipo_marcacion administrativo.tipo_marcacion,
    presencial boolean,
    autorizacion bigint,
    sucursal_entrada_id bigint NOT NULL,
    codigo character varying,
    usuario_id bigint,
    fecha_entrada timestamp with time zone DEFAULT now(),
    sucursal_salida_id bigint,
    fecha_salida timestamp without time zone,
    latitud numeric(10,7),
    longitud numeric(10,7),
    precision_gps real,
    distancia_sucursal integer,
    device_id character varying(255),
    device_info character varying(255),
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY administrativo.marcacion REPLICA IDENTITY FULL;


--
-- Name: actualizacion; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.actualizacion (
    id bigint NOT NULL,
    current_version character varying,
    enabled boolean,
    tipo configuraciones.tipo_actualizacion,
    nivel configuraciones.nivel_actualizacion,
    title character varying,
    msg character varying,
    btn character varying,
    usuario_id bigint,
    creado_en timestamp without time zone,
    sucursal_id bigint DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY configuraciones.actualizacion REPLICA IDENTITY FULL;


--
-- Name: actualizacion_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.actualizacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: actualizacion_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.actualizacion_id_seq OWNED BY configuraciones.actualizacion.id;


--
-- Name: error_log; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.error_log (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    tipo configuraciones.tipo_error NOT NULL,
    mensaje text NOT NULL,
    nivel configuraciones.nivel_error NOT NULL,
    fecha_primera_ocurrencia timestamp without time zone DEFAULT now() NOT NULL,
    fecha_ultima_ocurrencia timestamp without time zone,
    cantidad_ocurrencias integer DEFAULT 1
);


--
-- Name: error_log_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.error_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: error_log_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.error_log_id_seq OWNED BY configuraciones.error_log.id;


--
-- Name: inicio_sesion; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.inicio_sesion (
    id bigint NOT NULL,
    usuario_id bigint,
    id_dispositivo text,
    hora_inicio timestamp with time zone DEFAULT now(),
    hora_fin timestamp without time zone,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint NOT NULL,
    token text,
    tipo_dispositivo configuraciones.tipo_dispositivo
);

ALTER TABLE ONLY configuraciones.inicio_sesion REPLICA IDENTITY FULL;


--
-- Name: inicio_sesion_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.inicio_sesion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inicio_sesion_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.inicio_sesion_id_seq OWNED BY configuraciones.inicio_sesion.id;


--
-- Name: local; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.local (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    equipo_id bigint,
    is_servidor boolean,
    nombre_base_datos_master character varying(255),
    nombre_base_datos_filial character varying(255),
    ip_servidor_central character varying(255),
    puerto_servidor_central integer
);

ALTER TABLE ONLY configuraciones.local REPLICA IDENTITY FULL;


--
-- Name: local_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.local_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: local_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.local_id_seq OWNED BY configuraciones.local.id;


--
-- Name: modificacion_detalle; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.modificacion_detalle (
    id bigint NOT NULL,
    modificacion_registro_id bigint NOT NULL,
    campo_nombre character varying(100) NOT NULL,
    campo_tipo character varying(50),
    valor_anterior text,
    valor_nuevo text,
    valor_anterior_id bigint,
    valor_nuevo_id bigint,
    orden integer DEFAULT 1 NOT NULL,
    es_campo_sensible boolean DEFAULT false
);


--
-- Name: modificacion_detalle_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.modificacion_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modificacion_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.modificacion_detalle_id_seq OWNED BY configuraciones.modificacion_detalle.id;


--
-- Name: modificacion_registro; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.modificacion_registro (
    id bigint NOT NULL,
    tipo_entidad character varying(100) NOT NULL,
    entidad_id bigint NOT NULL,
    entidad_sucursal_id bigint,
    schema_nombre character varying(50) NOT NULL,
    tabla_nombre character varying(100) NOT NULL,
    tipo_operacion character varying(20) NOT NULL,
    usuario_id bigint,
    sucursal_id bigint,
    modificado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ip_address character varying(45),
    user_agent text,
    observacion text,
    activo boolean DEFAULT true
);


--
-- Name: modificacion_registro_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.modificacion_registro_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modificacion_registro_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.modificacion_registro_id_seq OWNED BY configuraciones.modificacion_registro.id;


--
-- Name: notificacion; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion (
    id bigint NOT NULL,
    titulo character varying(255) NOT NULL,
    mensaje text NOT NULL,
    tipo character varying(50) NOT NULL,
    data text,
    estado character varying(20) DEFAULT 'ACTIVA'::character varying NOT NULL,
    intentos_envio integer DEFAULT 0,
    ultimo_error text,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    estado_tablero character varying(50) DEFAULT 'POR_VERIFICAR'::character varying,
    verificado_por_usuario_id bigint,
    fecha_verificacion timestamp without time zone,
    usuario_creador_id bigint
);


--
-- Name: notificacion_comentario; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion_comentario (
    id bigint NOT NULL,
    notificacion_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    comentario text NOT NULL,
    comentario_padre_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    media_url text
);


--
-- Name: notificacion_comentario_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_comentario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_comentario_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_comentario_id_seq OWNED BY configuraciones.notificacion_comentario.id;


--
-- Name: notificacion_destinatario; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion_destinatario (
    id bigint NOT NULL,
    notificacion_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    leida boolean DEFAULT false,
    fecha_leida timestamp without time zone,
    fecha_entrega timestamp without time zone,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: notificacion_destinatario_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_destinatario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_destinatario_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_destinatario_id_seq OWNED BY configuraciones.notificacion_destinatario.id;


--
-- Name: notificacion_envio_log; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion_envio_log (
    id bigint NOT NULL,
    notificacion_id bigint NOT NULL,
    usuario_id bigint,
    token_fcm character varying(500),
    estado_envio character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    mensaje_error text,
    fecha_envio timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega timestamp without time zone
);


--
-- Name: notificacion_envio_log_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_envio_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_envio_log_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_envio_log_id_seq OWNED BY configuraciones.notificacion_envio_log.id;


--
-- Name: notificacion_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_id_seq OWNED BY configuraciones.notificacion.id;


--
-- Name: notificacion_preferencia_usuario; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion_preferencia_usuario (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    tipo_notificacion character varying(100) NOT NULL,
    habilitado boolean DEFAULT true NOT NULL,
    creado_en timestamp without time zone DEFAULT now(),
    actualizado_en timestamp without time zone DEFAULT now()
);


--
-- Name: notificacion_preferencia_usuario_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_preferencia_usuario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_preferencia_usuario_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_preferencia_usuario_id_seq OWNED BY configuraciones.notificacion_preferencia_usuario.id;


--
-- Name: notificacion_tipo_role; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.notificacion_tipo_role (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    tipo_notificacion character varying(100) NOT NULL,
    descripcion character varying(255),
    es_obligatorio boolean DEFAULT false NOT NULL,
    creado_en timestamp without time zone DEFAULT now()
);


--
-- Name: notificacion_tipo_role_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.notificacion_tipo_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificacion_tipo_role_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.notificacion_tipo_role_id_seq OWNED BY configuraciones.notificacion_tipo_role.id;


--
-- Name: rabbitmq_msg; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.rabbitmq_msg (
    id bigint NOT NULL,
    tipo_accion text,
    tipo_entidad text,
    entidad text,
    id_sucursal_origen numeric,
    data text,
    recibido_en_servidor boolean,
    recibido_en_filial boolean,
    exchange text,
    key text,
    class_type text
);


--
-- Name: rabbitmq_msg_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.rabbitmq_msg_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rabbitmq_msg_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.rabbitmq_msg_id_seq OWNED BY configuraciones.rabbitmq_msg.id;


--
-- Name: replication_table; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.replication_table (
    id integer NOT NULL,
    table_name character varying(255) NOT NULL,
    direction configuraciones.replication_direction NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(500),
    creado_en timestamp without time zone,
    usuario_id bigint,
    branch_ids bigint[],
    replicate_central_to_branch_with_filter boolean DEFAULT false NOT NULL
);


--
-- Name: replication_table_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.replication_table_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: replication_table_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.replication_table_id_seq OWNED BY configuraciones.replication_table.id;


--
-- Name: replication_test; Type: TABLE; Schema: configuraciones; Owner: -
--

CREATE TABLE configuraciones.replication_test (
    id bigint NOT NULL,
    test_uuid character varying(64) NOT NULL,
    source_db character varying(20) NOT NULL,
    sucursal_id bigint,
    created_at timestamp without time zone DEFAULT now()
);

ALTER TABLE ONLY configuraciones.replication_test REPLICA IDENTITY FULL;


--
-- Name: replication_test_id_seq; Type: SEQUENCE; Schema: configuraciones; Owner: -
--

CREATE SEQUENCE configuraciones.replication_test_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: replication_test_id_seq; Type: SEQUENCE OWNED BY; Schema: configuraciones; Owner: -
--

ALTER SEQUENCE configuraciones.replication_test_id_seq OWNED BY configuraciones.replication_test.id;


--
-- Name: cargo; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.cargo (
    id bigint NOT NULL,
    nombre character varying,
    descripcion character varying,
    supervisado_por_id bigint,
    sueldo_base numeric,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY empresarial.cargo REPLICA IDENTITY FULL;


--
-- Name: cargo_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.cargo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cargo_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.cargo_id_seq OWNED BY empresarial.cargo.id;


--
-- Name: configuracion_general; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.configuracion_general (
    id bigint NOT NULL,
    nombre_empresa character varying NOT NULL,
    razon_social character varying NOT NULL,
    ruc character varying,
    creado_en timestamp without time zone,
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY empresarial.configuracion_general REPLICA IDENTITY FULL;


--
-- Name: configuracion_general_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.configuracion_general_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: configuracion_general_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.configuracion_general_id_seq OWNED BY empresarial.configuracion_general.id;


--
-- Name: punto_de_venta; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.punto_de_venta (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    nombre character varying NOT NULL,
    nombre_impresora_ticket character varying,
    tamanho_impresora_ticket numeric,
    nombre_impresora_reportes character varying,
    creado_en timestamp without time zone,
    usuario_id bigint
);

ALTER TABLE ONLY empresarial.punto_de_venta REPLICA IDENTITY FULL;


--
-- Name: punto_de_venta_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.punto_de_venta_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: punto_de_venta_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.punto_de_venta_id_seq OWNED BY empresarial.punto_de_venta.id;


--
-- Name: sector; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.sector (
    id bigint NOT NULL,
    sucursal_id bigint,
    descripcion character varying,
    activo boolean,
    usuario_id bigint,
    creado_en timestamp without time zone
);

ALTER TABLE ONLY empresarial.sector REPLICA IDENTITY FULL;


--
-- Name: sector_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.sector_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sector_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.sector_id_seq OWNED BY empresarial.sector.id;


--
-- Name: sucursal; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.sucursal (
    id bigint NOT NULL,
    nombre character varying,
    localizacion character varying,
    ciudad_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    deposito boolean DEFAULT true,
    deposito_predeterminado boolean DEFAULT false,
    direccion character varying,
    nro_delivery character varying,
    is_configured boolean DEFAULT true,
    codigo_establecimiento_factura character varying,
    ip character varying,
    puerto integer,
    activo boolean DEFAULT true
);

ALTER TABLE ONLY empresarial.sucursal REPLICA IDENTITY FULL;


--
-- Name: sucursal_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sucursal_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.sucursal_id_seq OWNED BY empresarial.sucursal.id;


--
-- Name: zona; Type: TABLE; Schema: empresarial; Owner: -
--

CREATE TABLE empresarial.zona (
    id bigint NOT NULL,
    sector_id bigint,
    descripcion character varying,
    activo boolean,
    usuario_id bigint,
    creado_en timestamp without time zone
);

ALTER TABLE ONLY empresarial.zona REPLICA IDENTITY FULL;


--
-- Name: zona_id_seq; Type: SEQUENCE; Schema: empresarial; Owner: -
--

CREATE SEQUENCE empresarial.zona_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: zona_id_seq; Type: SEQUENCE OWNED BY; Schema: empresarial; Owner: -
--

ALTER SEQUENCE empresarial.zona_id_seq OWNED BY empresarial.zona.id;


--
-- Name: equipo; Type: TABLE; Schema: equipos; Owner: -
--

CREATE TABLE equipos.equipo (
    id bigint NOT NULL,
    marca character varying,
    modelo character varying,
    costo numeric,
    descripcion character varying,
    imagenes character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    tipo_equipo_id bigint,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY equipos.equipo REPLICA IDENTITY FULL;


--
-- Name: equipo_id_seq; Type: SEQUENCE; Schema: equipos; Owner: -
--

CREATE SEQUENCE equipos.equipo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: equipo_id_seq; Type: SEQUENCE OWNED BY; Schema: equipos; Owner: -
--

ALTER SEQUENCE equipos.equipo_id_seq OWNED BY equipos.equipo.id;


--
-- Name: tipo_equipo; Type: TABLE; Schema: equipos; Owner: -
--

CREATE TABLE equipos.tipo_equipo (
    id bigint NOT NULL,
    descripcion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY equipos.tipo_equipo REPLICA IDENTITY FULL;


--
-- Name: tipo_equipo_id_seq; Type: SEQUENCE; Schema: equipos; Owner: -
--

CREATE SEQUENCE equipos.tipo_equipo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_equipo_id_seq; Type: SEQUENCE OWNED BY; Schema: equipos; Owner: -
--

ALTER SEQUENCE equipos.tipo_equipo_id_seq OWNED BY equipos.tipo_equipo.id;


--
-- Name: banco; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.banco (
    id bigint NOT NULL,
    nombre character varying,
    codigo character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY financiero.banco REPLICA IDENTITY FULL;


--
-- Name: banco_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.banco_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: banco_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.banco_id_seq OWNED BY financiero.banco.id;


--
-- Name: caja_categoria_observacion; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.caja_categoria_observacion (
    id bigint NOT NULL,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: caja_categoria_observacion_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.caja_categoria_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: caja_categoria_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.caja_categoria_observacion_id_seq OWNED BY financiero.caja_categoria_observacion.id;


--
-- Name: caja_motivo_observacion; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.caja_motivo_observacion (
    id bigint NOT NULL,
    caja_subcategoria_id bigint,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: caja_motivo_observacion_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.caja_motivo_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: caja_motivo_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.caja_motivo_observacion_id_seq OWNED BY financiero.caja_motivo_observacion.id;


--
-- Name: caja_observacion; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.caja_observacion (
    id bigint NOT NULL,
    caja_motivo_id bigint,
    caja_id bigint,
    sucursal_id bigint,
    usuario_id bigint,
    descripcion character varying,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: caja_observacion_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.caja_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: caja_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.caja_observacion_id_seq OWNED BY financiero.caja_observacion.id;


--
-- Name: caja_subcategoria_observacion; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.caja_subcategoria_observacion (
    id bigint NOT NULL,
    caja_categoria_id bigint,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: caja_subcategoria_observacion_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.caja_subcategoria_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: caja_subcategoria_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.caja_subcategoria_observacion_id_seq OWNED BY financiero.caja_subcategoria_observacion.id;


--
-- Name: cambio; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.cambio (
    id bigint NOT NULL,
    moneda_id bigint,
    valor_en_gs numeric,
    activo boolean,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    valor_en_gs_cambio numeric
);

ALTER TABLE ONLY financiero.cambio REPLICA IDENTITY FULL;


--
-- Name: cambio_caja; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.cambio_caja (
    id bigint NOT NULL,
    cliente_id bigint,
    autorizado_por_id bigint,
    moneda_venta_id bigint,
    moneda_compra_id bigint,
    cotizacion numeric,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL,
    caja_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.cambio_caja REPLICA IDENTITY FULL;


--
-- Name: cambio_caja_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.cambio_caja_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cambio_caja_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.cambio_caja_id_seq OWNED BY financiero.cambio_caja.id;


--
-- Name: cambio_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.cambio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cambio_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.cambio_id_seq OWNED BY financiero.cambio.id;


--
-- Name: cheque; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.cheque (
    id bigint NOT NULL,
    chequera_id bigint NOT NULL,
    numero numeric NOT NULL,
    fecha_entrega timestamp without time zone,
    fecha_pago timestamp without time zone,
    orden text,
    concepto text,
    diferido boolean DEFAULT false,
    total numeric NOT NULL,
    firmante bigint,
    creado_en timestamp without time zone DEFAULT now(),
    usuario_id bigint,
    pago_detalle_cuota_id bigint
);


--
-- Name: cheque_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.cheque_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cheque_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.cheque_id_seq OWNED BY financiero.cheque.id;


--
-- Name: chequera; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.chequera (
    id bigint NOT NULL,
    cuenta_bancaria_id bigint NOT NULL,
    rango_desde numeric NOT NULL,
    rango_hasta numeric NOT NULL,
    fecha_retiro timestamp without time zone,
    creado_en timestamp without time zone DEFAULT now(),
    usuario_id bigint
);


--
-- Name: chequera_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.chequera_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chequera_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.chequera_id_seq OWNED BY financiero.chequera.id;


--
-- Name: conteo; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.conteo (
    id bigint NOT NULL,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.conteo REPLICA IDENTITY FULL;


--
-- Name: conteo_moneda; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.conteo_moneda (
    id bigint NOT NULL,
    conteo_id bigint,
    moneda_billetes_id bigint,
    cantidad numeric,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.conteo_moneda REPLICA IDENTITY FULL;


--
-- Name: conteo_moneda_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.conteo_moneda_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: conteo_moneda_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.conteo_moneda_id_seq OWNED BY financiero.conteo_moneda.id;


--
-- Name: cuenta_bancaria; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.cuenta_bancaria (
    id bigint NOT NULL,
    persona_id bigint,
    banco_id bigint,
    moneda_id bigint,
    numero character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    tipo_cuenta financiero.tipo_cuenta,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY financiero.cuenta_bancaria REPLICA IDENTITY FULL;


--
-- Name: cuenta_bancaria_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.cuenta_bancaria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cuenta_bancaria_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.cuenta_bancaria_id_seq OWNED BY financiero.cuenta_bancaria.id;


--
-- Name: documento; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.documento (
    id bigint NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp without time zone,
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY financiero.documento REPLICA IDENTITY FULL;


--
-- Name: documento_electronico; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.documento_electronico (
    id bigint NOT NULL,
    lote_de_id bigint,
    factura_legal_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    cdc character varying(44),
    mensaje_respuesta_sifen text,
    xml_firmado text,
    url_qr character varying(500),
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    codigo_respuesta_sifen character varying(10),
    xml_original text,
    numero_documento character varying(50),
    tipo_documento character varying(20),
    fecha_emision timestamp with time zone,
    fecha_recepcion_sifen timestamp with time zone,
    activo boolean DEFAULT true,
    actualizado_en timestamp with time zone DEFAULT now(),
    estado financiero.estado_de_enum DEFAULT 'PENDIENTE'::financiero.estado_de_enum
);

ALTER TABLE ONLY financiero.documento_electronico REPLICA IDENTITY FULL;


--
-- Name: documento_electronico_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.documento_electronico_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_electronico_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.documento_electronico_id_seq OWNED BY financiero.documento_electronico.id;


--
-- Name: documento_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.documento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.documento_id_seq OWNED BY financiero.documento.id;


--
-- Name: evento_cancelacion_de; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.evento_cancelacion_de (
    id bigint NOT NULL,
    documento_electronico_id bigint NOT NULL,
    evento_id character varying(255) NOT NULL,
    fecha_firma timestamp with time zone NOT NULL,
    cdc_documento character varying(44) NOT NULL,
    motivo_cancelacion text,
    xml_evento text,
    estado financiero.estado_evento_enum DEFAULT 'PENDIENTE'::financiero.estado_evento_enum NOT NULL,
    fecha_procesamiento timestamp with time zone,
    protocolo_autorizacion character varying(100),
    codigo_respuesta character varying(10),
    mensaje_respuesta text,
    respuesta_bruta text,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    actualizado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.evento_cancelacion_de REPLICA IDENTITY FULL;


--
-- Name: evento_cancelacion_de_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.evento_cancelacion_de_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_cancelacion_de_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.evento_cancelacion_de_id_seq OWNED BY financiero.evento_cancelacion_de.id;


--
-- Name: evento_inutilizacion_de; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.evento_inutilizacion_de (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    timbrado_id bigint NOT NULL,
    timbrado_detalle_id bigint,
    evento_id character varying(255) NOT NULL,
    fecha_firma timestamp with time zone NOT NULL,
    establecimiento character varying(10) NOT NULL,
    punto_expedicion character varying(10) NOT NULL,
    numero_inicio integer NOT NULL,
    numero_fin integer NOT NULL,
    tipo_de character varying(50) NOT NULL,
    motivo_inutilizacion text NOT NULL,
    estado financiero.estado_evento_enum DEFAULT 'PENDIENTE'::financiero.estado_evento_enum NOT NULL,
    fecha_procesamiento timestamp with time zone,
    protocolo_autorizacion character varying(100),
    codigo_respuesta character varying(10),
    mensaje_respuesta text,
    respuesta_bruta text,
    xml_evento text,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    actualizado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    usuario_id bigint,
    CONSTRAINT chk_evento_inutilizacion_numero_rango CHECK ((numero_inicio <= numero_fin))
);

ALTER TABLE ONLY financiero.evento_inutilizacion_de REPLICA IDENTITY FULL;


--
-- Name: evento_inutilizacion_de_documento_electronico; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.evento_inutilizacion_de_documento_electronico (
    id bigint NOT NULL,
    evento_inutilizacion_de_id bigint NOT NULL,
    evento_inutilizacion_de_sucursal_id bigint NOT NULL,
    documento_electronico_id bigint NOT NULL,
    documento_electronico_sucursal_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_evento_inutilizacion_de_doc_sucursal CHECK ((evento_inutilizacion_de_sucursal_id = documento_electronico_sucursal_id))
);

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico REPLICA IDENTITY FULL;


--
-- Name: evento_inutilizacion_de_documento_electronico_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.evento_inutilizacion_de_documento_electronico_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_inutilizacion_de_documento_electronico_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.evento_inutilizacion_de_documento_electronico_id_seq OWNED BY financiero.evento_inutilizacion_de_documento_electronico.id;


--
-- Name: evento_inutilizacion_de_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.evento_inutilizacion_de_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_inutilizacion_de_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.evento_inutilizacion_de_id_seq OWNED BY financiero.evento_inutilizacion_de.id;


--
-- Name: evento_nominacion_de; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.evento_nominacion_de (
    id bigint NOT NULL,
    documento_electronico_id bigint NOT NULL,
    evento_id character varying(50),
    fecha_firma timestamp with time zone,
    cdc_documento character varying(50),
    cliente_id bigint,
    nombre_receptor character varying(255),
    documento_receptor character varying(50),
    tipo_receptor character varying(50),
    total_factura numeric,
    fecha_emision timestamp with time zone,
    fecha_recepcion timestamp with time zone,
    xml_evento text,
    estado financiero.estado_evento_enum,
    fecha_procesamiento timestamp with time zone,
    protocolo_autorizacion character varying(50),
    codigo_respuesta character varying(10),
    mensaje_respuesta text,
    respuesta_bruta text,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    actualizado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.evento_nominacion_de REPLICA IDENTITY FULL;


--
-- Name: evento_nominacion_de_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.evento_nominacion_de_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_nominacion_de_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.evento_nominacion_de_id_seq OWNED BY financiero.evento_nominacion_de.id;


--
-- Name: factura_legal; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.factura_legal (
    id bigint NOT NULL,
    timbrado_detalle_id bigint NOT NULL,
    numero_factura numeric NOT NULL,
    autoimpreso boolean DEFAULT true,
    cliente_id bigint,
    venta_id integer,
    fecha timestamp without time zone,
    credito boolean,
    nombre character varying,
    ruc character varying,
    direccion character varying,
    iva_parcial_0 numeric,
    iva_parcial_5 numeric,
    iva_parcial_10 numeric,
    total_parcial_0 numeric,
    total_parcial_5 numeric,
    total_parcial_10 numeric,
    total_final numeric,
    activo boolean DEFAULT true,
    creado_en timestamp without time zone,
    usuario_id bigint,
    via_tributaria boolean DEFAULT false,
    caja_id bigint,
    sucursal_id bigint NOT NULL,
    descuento numeric DEFAULT 0,
    cdc character varying(255) DEFAULT NULL::character varying,
    moneda_extranjera character varying(3) DEFAULT NULL::character varying,
    tipo_cambio numeric(10,4) DEFAULT NULL::numeric
);

ALTER TABLE ONLY financiero.factura_legal REPLICA IDENTITY FULL;


--
-- Name: factura_legal_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.factura_legal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: factura_legal_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.factura_legal_id_seq OWNED BY financiero.factura_legal.id;


--
-- Name: factura_legal_item; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.factura_legal_item (
    id bigint NOT NULL,
    factura_legal_id bigint NOT NULL,
    venta_item_id integer,
    presentacion_id bigint,
    cantidad numeric,
    descripcion character varying,
    precio_unitario numeric,
    total numeric,
    creado_en timestamp without time zone,
    usuario_id bigint,
    sucursal_id bigint NOT NULL,
    producto_id bigint,
    unidad_medida character varying(50),
    iva integer
);

ALTER TABLE ONLY financiero.factura_legal_item REPLICA IDENTITY FULL;


--
-- Name: factura_legal_item_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.factura_legal_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: factura_legal_item_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.factura_legal_item_id_seq OWNED BY financiero.factura_legal_item.id;


--
-- Name: forma_pago; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.forma_pago (
    id bigint NOT NULL,
    descripcion character varying NOT NULL,
    activo boolean DEFAULT true,
    movimiento_caja boolean DEFAULT false,
    cuenta_bancaria_id bigint,
    autorizacion boolean DEFAULT false,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY financiero.forma_pago REPLICA IDENTITY FULL;


--
-- Name: forma_pago_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.forma_pago_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: forma_pago_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.forma_pago_id_seq OWNED BY financiero.forma_pago.id;


--
-- Name: gasto; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.gasto (
    id bigint NOT NULL,
    responsable_id bigint,
    tipo_gasto_id bigint,
    autorizado_por_id bigint,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    caja_id bigint,
    activo boolean DEFAULT true,
    finalizado boolean DEFAULT false,
    retiro_gs numeric DEFAULT 0,
    retiro_rs numeric DEFAULT 0,
    retiro_ds numeric DEFAULT 0,
    vuelto_gs numeric DEFAULT 0,
    vuelto_rs numeric DEFAULT 0,
    vuelto_ds numeric DEFAULT 0,
    sucursal_id bigint NOT NULL,
    sucursal_vuelto_id bigint
);

ALTER TABLE ONLY financiero.gasto REPLICA IDENTITY FULL;


--
-- Name: gasto_detalle; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.gasto_detalle (
    id bigint NOT NULL,
    gasto_id bigint,
    moneda_id bigint NOT NULL,
    cambio numeric(19,0),
    cantidad numeric NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    vuelto boolean DEFAULT false,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.gasto_detalle REPLICA IDENTITY FULL;


--
-- Name: gasto_detalle_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.gasto_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: gasto_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.gasto_detalle_id_seq OWNED BY financiero.gasto_detalle.id;


--
-- Name: lote_de_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.lote_de_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lote_de; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.lote_de (
    id bigint DEFAULT nextval('financiero.lote_de_id_seq'::regclass) NOT NULL,
    protocolo character varying(50),
    fecha_procesado timestamp with time zone,
    respuesta_sifen text,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    fecha_ultimo_intento timestamp with time zone,
    intentos integer DEFAULT 0,
    actualizado_en timestamp with time zone DEFAULT now(),
    estado financiero.estado_lote_de_enum,
    aprobado boolean,
    activo boolean DEFAULT true,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.lote_de REPLICA IDENTITY FULL;


--
-- Name: lote_dte_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.lote_dte_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lote_dte_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.lote_dte_id_seq OWNED BY financiero.lote_de.id;


--
-- Name: maletin_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.maletin_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maletin; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.maletin (
    id bigint DEFAULT nextval('financiero.maletin_id_seq'::regclass) NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    abierto boolean DEFAULT false,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY financiero.maletin REPLICA IDENTITY FULL;


--
-- Name: moneda; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.moneda (
    id bigint NOT NULL,
    denominacion character varying,
    simbolo character varying,
    pais_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY financiero.moneda REPLICA IDENTITY FULL;


--
-- Name: moneda_billetes; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.moneda_billetes (
    id bigint NOT NULL,
    moneda_id bigint NOT NULL,
    flotante boolean DEFAULT false,
    papel boolean DEFAULT true,
    valor numeric,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY financiero.moneda_billetes REPLICA IDENTITY FULL;


--
-- Name: moneda_billetes_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.moneda_billetes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: moneda_billetes_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.moneda_billetes_id_seq OWNED BY financiero.moneda_billetes.id;


--
-- Name: moneda_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.moneda_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: moneda_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.moneda_id_seq OWNED BY financiero.moneda.id;


--
-- Name: movimiento_caja; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.movimiento_caja (
    id bigint NOT NULL,
    caja_id bigint NOT NULL,
    moneda_id bigint NOT NULL,
    referencia_id bigint NOT NULL,
    cambio_id bigint,
    cantidad numeric NOT NULL,
    tipo_movimiento financiero.pdv_caja_tipo_movimiento NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    activo boolean DEFAULT true,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.movimiento_caja REPLICA IDENTITY FULL;


--
-- Name: movimiento_caja_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.movimiento_caja_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: movimiento_caja_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.movimiento_caja_id_seq OWNED BY financiero.movimiento_caja.id;


--
-- Name: movimiento_personas; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.movimiento_personas (
    id bigint NOT NULL,
    observacion character varying,
    persona_id bigint NOT NULL,
    tipo financiero.tipo_movimiento_personas,
    referencia_id bigint,
    valor_total numeric,
    activo boolean,
    vencimiento timestamp without time zone,
    usuario_id bigint,
    creado_en timestamp without time zone
);

ALTER TABLE ONLY financiero.movimiento_personas REPLICA IDENTITY FULL;


--
-- Name: movimiento_personas_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.movimiento_personas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: movimiento_personas_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.movimiento_personas_id_seq OWNED BY financiero.movimiento_personas.id;


--
-- Name: pdv_caja; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.pdv_caja (
    id bigint NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    estado financiero.pdv_caja_estado,
    tuvo_problema boolean DEFAULT false,
    fecha_apertura timestamp with time zone DEFAULT now(),
    fecha_cierre timestamp with time zone,
    observacion character varying,
    creado_en timestamp with time zone,
    maletin_id bigint,
    usuario_id bigint,
    conteo_apertura_id bigint,
    conteo_cierre_id bigint,
    sucursal_id bigint NOT NULL,
    verificado boolean DEFAULT false,
    verificado_por_id bigint
);

ALTER TABLE ONLY financiero.pdv_caja REPLICA IDENTITY FULL;


--
-- Name: retiro; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.retiro (
    id bigint NOT NULL,
    responsable_id bigint,
    estado financiero.estado_retiro,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    caja_salida_id bigint,
    caja_entrada_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.retiro REPLICA IDENTITY FULL;


--
-- Name: retiro_detalle; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.retiro_detalle (
    id bigint NOT NULL,
    retiro_id bigint NOT NULL,
    moneda_id bigint NOT NULL,
    cambio numeric(19,0),
    cantidad numeric NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.retiro_detalle REPLICA IDENTITY FULL;


--
-- Name: sencillo; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.sencillo (
    id bigint NOT NULL,
    caja_entrada_id bigint NOT NULL,
    caja_salida_id bigint NOT NULL,
    responsable_id bigint,
    entrada boolean,
    autorizado_por_id bigint,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.sencillo REPLICA IDENTITY FULL;


--
-- Name: sencillo_detalle; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.sencillo_detalle (
    id bigint NOT NULL,
    sencillo_id bigint,
    moneda_id bigint NOT NULL,
    cambio_id bigint,
    cantidad numeric NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY financiero.sencillo_detalle REPLICA IDENTITY FULL;


--
-- Name: sencillo_detalle_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.sencillo_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sencillo_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.sencillo_detalle_id_seq OWNED BY financiero.sencillo_detalle.id;


--
-- Name: sencillo_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.sencillo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sencillo_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.sencillo_id_seq OWNED BY financiero.sencillo.id;


--
-- Name: timbrado; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.timbrado (
    id bigint NOT NULL,
    razon_social character varying NOT NULL,
    ruc character varying NOT NULL,
    numero character varying NOT NULL,
    fecha_inicio timestamp without time zone,
    fecha_fin timestamp without time zone,
    activo boolean DEFAULT true,
    creado_en timestamp without time zone,
    usuario_id bigint,
    is_electronico boolean,
    csc character varying(255) DEFAULT NULL::character varying,
    email character varying(255) DEFAULT NULL::character varying,
    tipo_sociedad character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_departamento character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_ciudad character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_codigo_ciudad character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_localidad character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_barrio character varying(255) DEFAULT NULL::character varying,
    domicilio_fiscal_direccion character varying(255) DEFAULT NULL::character varying,
    telefono character varying(255) DEFAULT NULL::character varying,
    cod_actividad_economica_principal character varying(255) DEFAULT NULL::character varying,
    desc_actividad_economica_principal character varying(255) DEFAULT NULL::character varying,
    list_codigo_actividad_economica_secundaria text,
    list_descripcion_actividad_economica_secundaria text,
    csc_id character varying(10),
    CONSTRAINT check_fechas_requeridas_no_electronico CHECK (((is_electronico = true) OR ((is_electronico = false) AND (fecha_inicio IS NOT NULL) AND (fecha_fin IS NOT NULL)) OR ((is_electronico IS NULL) AND (fecha_inicio IS NOT NULL) AND (fecha_fin IS NOT NULL))))
);

ALTER TABLE ONLY financiero.timbrado REPLICA IDENTITY FULL;


--
-- Name: timbrado_detalle; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.timbrado_detalle (
    id bigint NOT NULL,
    timbrado_id bigint NOT NULL,
    punto_de_venta_id bigint,
    punto_expedicion character varying NOT NULL,
    cantidad numeric NOT NULL,
    rango_desde numeric NOT NULL,
    rango_hasta numeric NOT NULL,
    numero_actual numeric NOT NULL,
    activo boolean,
    creado_en timestamp without time zone,
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0 NOT NULL,
    departamento character varying(255) DEFAULT NULL::character varying,
    ciudad character varying(255) DEFAULT NULL::character varying,
    codigo_ciudad character varying(255) DEFAULT NULL::character varying,
    localidad character varying(255) DEFAULT NULL::character varying,
    barrio character varying(255) DEFAULT NULL::character varying,
    direccion character varying(255) DEFAULT NULL::character varying,
    telefono character varying(255) DEFAULT NULL::character varying
);

ALTER TABLE ONLY financiero.timbrado_detalle REPLICA IDENTITY FULL;


--
-- Name: timbrado_detalle_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.timbrado_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: timbrado_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.timbrado_detalle_id_seq OWNED BY financiero.timbrado_detalle.id;


--
-- Name: timbrado_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.timbrado_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: timbrado_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.timbrado_id_seq OWNED BY financiero.timbrado.id;


--
-- Name: tipo_gasto; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.tipo_gasto (
    id bigint NOT NULL,
    is_clasificacion boolean,
    clasificacion_gasto_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    autorizacion boolean,
    cargo_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY financiero.tipo_gasto REPLICA IDENTITY FULL;


--
-- Name: tipo_gasto_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.tipo_gasto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_gasto_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.tipo_gasto_id_seq OWNED BY financiero.tipo_gasto.id;


--
-- Name: venta_credito; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.venta_credito (
    id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    venta_id bigint,
    cliente_id bigint,
    tipo_confirmacion financiero.tipo_confirmacion,
    cantidad_cuotas integer,
    valor_total numeric,
    saldo_total numeric,
    plazo_en_dias numeric,
    interes_por_dia numeric,
    interes_mora_dia numeric,
    estado financiero.estado_venta_credito,
    usuario_id bigint,
    creado_en timestamp without time zone,
    sucursal_cobro_id bigint,
    cobro_id bigint,
    fecha_cobro timestamp without time zone
);

ALTER TABLE ONLY financiero.venta_credito REPLICA IDENTITY FULL;


--
-- Name: venta_credito_cuota; Type: TABLE; Schema: financiero; Owner: -
--

CREATE TABLE financiero.venta_credito_cuota (
    id bigint NOT NULL,
    venta_credito_id bigint NOT NULL,
    cobro_id bigint,
    valor numeric,
    parcial boolean,
    activo boolean,
    vencimiento timestamp without time zone,
    usuario_id bigint,
    creado_en timestamp without time zone,
    sucursal_id bigint NOT NULL,
    sucursal_cobro_id bigint
);

ALTER TABLE ONLY financiero.venta_credito_cuota REPLICA IDENTITY FULL;


--
-- Name: venta_credito_cuota_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.venta_credito_cuota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: venta_credito_cuota_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.venta_credito_cuota_id_seq OWNED BY financiero.venta_credito_cuota.id;


--
-- Name: venta_credito_id_seq; Type: SEQUENCE; Schema: financiero; Owner: -
--

CREATE SEQUENCE financiero.venta_credito_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: venta_credito_id_seq; Type: SEQUENCE OWNED BY; Schema: financiero; Owner: -
--

ALTER SEQUENCE financiero.venta_credito_id_seq OWNED BY financiero.venta_credito.id;


--
-- Name: barrio; Type: TABLE; Schema: general; Owner: -
--

CREATE TABLE general.barrio (
    id bigint NOT NULL,
    descripcion character varying,
    ciudad_id bigint,
    precio_delivery_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY general.barrio REPLICA IDENTITY FULL;


--
-- Name: barrio_id_seq; Type: SEQUENCE; Schema: general; Owner: -
--

CREATE SEQUENCE general.barrio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: barrio_id_seq; Type: SEQUENCE OWNED BY; Schema: general; Owner: -
--

ALTER SEQUENCE general.barrio_id_seq OWNED BY general.barrio.id;


--
-- Name: ciudad; Type: TABLE; Schema: general; Owner: -
--

CREATE TABLE general.ciudad (
    id bigint NOT NULL,
    descripcion character varying,
    pais_id bigint,
    codigo character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY general.ciudad REPLICA IDENTITY FULL;


--
-- Name: ciudad_id_seq; Type: SEQUENCE; Schema: general; Owner: -
--

CREATE SEQUENCE general.ciudad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ciudad_id_seq; Type: SEQUENCE OWNED BY; Schema: general; Owner: -
--

ALTER SEQUENCE general.ciudad_id_seq OWNED BY general.ciudad.id;


--
-- Name: contacto; Type: TABLE; Schema: general; Owner: -
--

CREATE TABLE general.contacto (
    id bigint NOT NULL,
    email character varying,
    telefono character varying,
    persona_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    redes_sociales character varying
);

ALTER TABLE ONLY general.contacto REPLICA IDENTITY FULL;


--
-- Name: contacto_id_seq; Type: SEQUENCE; Schema: general; Owner: -
--

CREATE SEQUENCE general.contacto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contacto_id_seq; Type: SEQUENCE OWNED BY; Schema: general; Owner: -
--

ALTER SEQUENCE general.contacto_id_seq OWNED BY general.contacto.id;


--
-- Name: pais; Type: TABLE; Schema: general; Owner: -
--

CREATE TABLE general.pais (
    id bigint NOT NULL,
    descripcion character varying,
    codigo character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY general.pais REPLICA IDENTITY FULL;


--
-- Name: pais_id_seq; Type: SEQUENCE; Schema: general; Owner: -
--

CREATE SEQUENCE general.pais_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pais_id_seq; Type: SEQUENCE OWNED BY; Schema: general; Owner: -
--

ALTER SEQUENCE general.pais_id_seq OWNED BY general.pais.id;


--
-- Name: imagen_master; Type: TABLE; Schema: media; Owner: -
--

CREATE TABLE media.imagen_master (
    id bigint NOT NULL,
    tipo_referencia character varying(50) NOT NULL,
    referencia_id bigint NOT NULL,
    nombre text,
    url text NOT NULL,
    principal boolean DEFAULT false,
    checksum text,
    extension character varying(10),
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);


--
-- Name: imagen_master_id_seq; Type: SEQUENCE; Schema: media; Owner: -
--

CREATE SEQUENCE media.imagen_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: imagen_master_id_seq; Type: SEQUENCE OWNED BY; Schema: media; Owner: -
--

ALTER SEQUENCE media.imagen_master_id_seq OWNED BY media.imagen_master.id;


--
-- Name: categoria_observacion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.categoria_observacion (
    id bigint NOT NULL,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: categoria_observacion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.categoria_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categoria_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.categoria_observacion_id_seq OWNED BY operaciones.categoria_observacion.id;


--
-- Name: cobro; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.cobro (
    id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    total_gs numeric,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY operaciones.cobro REPLICA IDENTITY FULL;


--
-- Name: cobro_detalle; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.cobro_detalle (
    id bigint NOT NULL,
    cobro_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    moneda_id bigint,
    forma_pago_id bigint,
    valor numeric,
    cambio numeric(19,0),
    vuelto boolean DEFAULT false,
    descuento boolean DEFAULT false,
    pago boolean DEFAULT true,
    aumento boolean DEFAULT false,
    sucursal_id bigint NOT NULL,
    identificador_transaccion character varying
);

ALTER TABLE ONLY operaciones.cobro_detalle REPLICA IDENTITY FULL;


--
-- Name: cobro_detalle_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.cobro_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cobro_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.cobro_detalle_id_seq OWNED BY operaciones.cobro_detalle.id;


--
-- Name: cobro_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.cobro_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cobro_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.cobro_id_seq OWNED BY operaciones.cobro.id;


--
-- Name: compra; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.compra (
    id bigint NOT NULL,
    pedido_id bigint,
    sucursal_id bigint,
    proveedor_id bigint,
    contacto_proveedor_id bigint,
    fecha timestamp with time zone DEFAULT now(),
    nro_nota character varying,
    fecha_de_entrega timestamp without time zone,
    moneda_id bigint,
    descuento numeric DEFAULT 0,
    estado operaciones.compra_estado,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    forma_pago_id bigint
);

ALTER TABLE ONLY operaciones.compra REPLICA IDENTITY FULL;


--
-- Name: compra_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.compra_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: compra_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.compra_id_seq OWNED BY operaciones.compra.id;


--
-- Name: compra_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.compra_item (
    id bigint NOT NULL,
    compra_id bigint,
    producto_id bigint,
    cantidad numeric,
    precio_unitario numeric,
    descuento_unitario numeric,
    bonificacion boolean,
    frio boolean,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    lote character varying,
    valor_total numeric,
    vencimiento timestamp(0) with time zone,
    presentacion_id bigint,
    pedido_item_id bigint,
    estado operaciones.compra_item_estado,
    verificado boolean DEFAULT false,
    programar_precio_id bigint
);

ALTER TABLE ONLY operaciones.compra_item REPLICA IDENTITY FULL;


--
-- Name: compra_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.compra_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: compra_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.compra_item_id_seq OWNED BY operaciones.compra_item.id;


--
-- Name: constancia_de_recepcion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.constancia_de_recepcion (
    id bigint NOT NULL,
    recepcion_mercaderia_id bigint NOT NULL,
    proveedor_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    fecha_emision timestamp without time zone NOT NULL,
    usuario_id bigint NOT NULL,
    codigo_verificacion character varying(255) NOT NULL,
    estado operaciones.estado_constancia NOT NULL
);


--
-- Name: constancia_de_recepcion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.constancia_de_recepcion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: constancia_de_recepcion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.constancia_de_recepcion_id_seq OWNED BY operaciones.constancia_de_recepcion.id;


--
-- Name: constancia_de_recepcion_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.constancia_de_recepcion_item (
    id bigint NOT NULL,
    constancia_de_recepcion_id bigint NOT NULL,
    producto_id bigint NOT NULL,
    presentacion_id bigint,
    cantidad_recibida double precision,
    cantidad_rechazada_fisico double precision
);


--
-- Name: constancia_de_recepcion_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.constancia_de_recepcion_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: constancia_de_recepcion_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.constancia_de_recepcion_item_id_seq OWNED BY operaciones.constancia_de_recepcion_item.id;


--
-- Name: delivery; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.delivery (
    id bigint NOT NULL,
    valor_en_gs numeric,
    precio_delivery_id bigint,
    entregador_id bigint,
    telefono character varying,
    direccion character varying,
    cliente_id bigint,
    forma_pago_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    estado operaciones.delivery_estado,
    barrio_id bigint,
    vehiculo_id bigint,
    vuelto_id bigint,
    sucursal_id bigint NOT NULL,
    fecha_concluido timestamp without time zone
);

ALTER TABLE ONLY operaciones.delivery REPLICA IDENTITY FULL;


--
-- Name: delivery_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.delivery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: delivery_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.delivery_id_seq OWNED BY operaciones.delivery.id;


--
-- Name: devolucion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.devolucion (
    id bigint NOT NULL,
    proveedor_id bigint NOT NULL,
    sucursal_origen_id bigint NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    motivo text,
    estado operaciones.devolucion_estado DEFAULT 'PENDIENTE'::operaciones.devolucion_estado NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: devolucion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.devolucion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: devolucion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.devolucion_id_seq OWNED BY operaciones.devolucion.id;


--
-- Name: devolucion_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.devolucion_item (
    id bigint NOT NULL,
    devolucion_id bigint NOT NULL,
    producto_id bigint NOT NULL,
    recepcion_mercaderia_item_id bigint,
    cantidad numeric(10,2) NOT NULL,
    motivo text,
    lote character varying(100)
);


--
-- Name: devolucion_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.devolucion_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: devolucion_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.devolucion_item_id_seq OWNED BY operaciones.devolucion_item.id;


--
-- Name: entrada; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.entrada (
    id bigint NOT NULL,
    responsable_carga_id bigint,
    tipo_entrada operaciones.tipo_entrada,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint,
    activo boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY operaciones.entrada REPLICA IDENTITY FULL;


--
-- Name: entrada_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.entrada_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: entrada_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.entrada_id_seq OWNED BY operaciones.entrada.id;


--
-- Name: entrada_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.entrada_item (
    id bigint NOT NULL,
    producto_id bigint NOT NULL,
    presentacion_id bigint NOT NULL,
    cantidad numeric NOT NULL,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    usuario_id bigint,
    entrada_id bigint NOT NULL,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY operaciones.entrada_item REPLICA IDENTITY FULL;


--
-- Name: entrada_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.entrada_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: entrada_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.entrada_item_id_seq OWNED BY operaciones.entrada_item.id;


--
-- Name: inventario; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.inventario (
    id bigint NOT NULL,
    id_central bigint,
    id_origen bigint,
    sucursal_id bigint,
    fecha_inicio timestamp without time zone,
    fecha_fin timestamp without time zone,
    abierto boolean,
    tipo operaciones.tipo_inventario,
    estado operaciones.inventario_estado,
    usuario_id bigint,
    observacion character varying
);

ALTER TABLE ONLY operaciones.inventario REPLICA IDENTITY FULL;


--
-- Name: inventario_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.inventario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventario_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.inventario_id_seq OWNED BY operaciones.inventario.id;


--
-- Name: inventario_producto; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.inventario_producto (
    id bigint NOT NULL,
    inventario_id bigint NOT NULL,
    zona_id bigint,
    concluido boolean,
    usuario_id bigint,
    creado_en timestamp without time zone
);

ALTER TABLE ONLY operaciones.inventario_producto REPLICA IDENTITY FULL;


--
-- Name: inventario_producto_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.inventario_producto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventario_producto_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.inventario_producto_id_seq OWNED BY operaciones.inventario_producto.id;


--
-- Name: inventario_producto_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.inventario_producto_item (
    id bigint NOT NULL,
    inventario_producto_id bigint NOT NULL,
    presentacion_id bigint NOT NULL,
    zona_id bigint,
    cantidad numeric,
    vencimiento timestamp without time zone,
    estado operaciones.inventario_producto_estado,
    usuario_id bigint,
    creado_en timestamp without time zone,
    cantidad_fisica numeric,
    cantidad_anterior numeric,
    fecha_verificado timestamp without time zone,
    verificado boolean DEFAULT false,
    revisado boolean DEFAULT false
);

ALTER TABLE ONLY operaciones.inventario_producto_item REPLICA IDENTITY FULL;


--
-- Name: inventario_producto_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.inventario_producto_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventario_producto_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.inventario_producto_item_id_seq OWNED BY operaciones.inventario_producto_item.id;


--
-- Name: motivo_diferencia_pedido; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.motivo_diferencia_pedido (
    id bigint NOT NULL,
    tipo character varying,
    descripcion character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY operaciones.motivo_diferencia_pedido REPLICA IDENTITY FULL;


--
-- Name: motivo_diferencia_pedido_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.motivo_diferencia_pedido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: motivo_diferencia_pedido_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.motivo_diferencia_pedido_id_seq OWNED BY operaciones.motivo_diferencia_pedido.id;


--
-- Name: motivo_observacion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.motivo_observacion (
    id bigint NOT NULL,
    subcategoria_id bigint,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: motivo_observacion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.motivo_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: motivo_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.motivo_observacion_id_seq OWNED BY operaciones.motivo_observacion.id;


--
-- Name: movimiento_stock; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.movimiento_stock (
    id bigint NOT NULL,
    producto_id bigint NOT NULL,
    tipo_movimiento operaciones.tipo_movimiento NOT NULL,
    referencia bigint NOT NULL,
    cantidad numeric DEFAULT 0 NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    estado boolean DEFAULT true,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY operaciones.movimiento_stock REPLICA IDENTITY FULL;


--
-- Name: movimiento_stock_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.movimiento_stock_id_seq
    START WITH 1
    INCREMENT BY 2
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: movimiento_stock_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.movimiento_stock_id_seq OWNED BY operaciones.movimiento_stock.id;


--
-- Name: necesidad; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.necesidad (
    id bigint NOT NULL,
    sucursal_id bigint,
    fecha timestamp with time zone DEFAULT now(),
    estado operaciones.necesidad_estado,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY operaciones.necesidad REPLICA IDENTITY FULL;


--
-- Name: necesidad_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.necesidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: necesidad_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.necesidad_id_seq OWNED BY operaciones.necesidad.id;


--
-- Name: necesidad_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.necesidad_item (
    id bigint NOT NULL,
    autogenerado boolean DEFAULT true,
    cantidad_sugerida numeric DEFAULT 0,
    modificado boolean DEFAULT false,
    necesidad_id bigint,
    producto_id bigint,
    cantidad numeric DEFAULT 0,
    observacion character varying,
    estado operaciones.necesidad_item_estado,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    frio boolean DEFAULT false,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY operaciones.necesidad_item REPLICA IDENTITY FULL;


--
-- Name: necesidad_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.necesidad_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: necesidad_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.necesidad_item_id_seq OWNED BY operaciones.necesidad_item.id;


--
-- Name: nota_pedido; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.nota_pedido (
    id bigint NOT NULL,
    pedido_id bigint,
    nro_nota character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY operaciones.nota_pedido REPLICA IDENTITY FULL;


--
-- Name: nota_pedido_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.nota_pedido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_pedido_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.nota_pedido_id_seq OWNED BY operaciones.nota_pedido.id;


--
-- Name: nota_recepcion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.nota_recepcion (
    id bigint NOT NULL,
    pedido_id bigint,
    compra_id bigint,
    documento_id bigint,
    valor numeric,
    descuento numeric,
    pagado boolean,
    numero numeric,
    timbrado numeric,
    creado_en timestamp without time zone,
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0,
    fecha timestamp without time zone,
    tipo_boleta character varying,
    nota_recepcion_agrupada_id bigint,
    moneda_id bigint NOT NULL,
    cotizacion double precision,
    estado operaciones.nota_recepcion_estado DEFAULT 'PENDIENTE_CONCILIACION'::operaciones.nota_recepcion_estado NOT NULL,
    es_nota_rechazo boolean DEFAULT false
);

ALTER TABLE ONLY operaciones.nota_recepcion REPLICA IDENTITY FULL;


--
-- Name: nota_recepcion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.nota_recepcion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_recepcion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.nota_recepcion_id_seq OWNED BY operaciones.nota_recepcion.id;


--
-- Name: nota_recepcion_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.nota_recepcion_item (
    id bigint NOT NULL,
    nota_recepcion_id bigint,
    pedido_item_id bigint,
    creado_en timestamp without time zone,
    usuario_id bigint,
    estado operaciones.nota_recepcion_item_estado DEFAULT 'PENDIENTE_CONCILIACION'::operaciones.nota_recepcion_item_estado,
    motivo_rechazo text,
    presentacion_en_nota_id bigint,
    cantidad_en_nota double precision,
    precio_unitario_en_nota double precision,
    producto_id bigint,
    es_bonificacion boolean DEFAULT false,
    vencimiento_en_nota date,
    observacion text
);

ALTER TABLE ONLY operaciones.nota_recepcion_item REPLICA IDENTITY FULL;


--
-- Name: nota_recepcion_item_distribucion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.nota_recepcion_item_distribucion (
    id bigint NOT NULL,
    nota_recepcion_item_id bigint NOT NULL,
    sucursal_entrega_id bigint NOT NULL,
    cantidad double precision NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    usuario_id bigint,
    sucursal_influencia_id bigint
);


--
-- Name: nota_recepcion_item_distribucion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.nota_recepcion_item_distribucion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_recepcion_item_distribucion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.nota_recepcion_item_distribucion_id_seq OWNED BY operaciones.nota_recepcion_item_distribucion.id;


--
-- Name: nota_recepcion_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.nota_recepcion_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_recepcion_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.nota_recepcion_item_id_seq OWNED BY operaciones.nota_recepcion_item.id;


--
-- Name: pago; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pago (
    id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    autorizado_por bigint,
    solicitud_pago_id bigint,
    estado operaciones.pago_estado NOT NULL,
    programado boolean NOT NULL
);


--
-- Name: pago_detalle; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pago_detalle (
    id bigint NOT NULL,
    pago_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    moneda_id bigint NOT NULL,
    forma_pago_id bigint NOT NULL,
    total numeric NOT NULL,
    sucursal_id bigint,
    caja_id bigint,
    activo boolean NOT NULL,
    fecha_programado timestamp without time zone,
    plazo boolean DEFAULT false,
    cuotas integer,
    estado operaciones.pago_detalle_estado DEFAULT 'ABIERTO'::operaciones.pago_detalle_estado NOT NULL
);


--
-- Name: pago_detalle_cuota; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pago_detalle_cuota (
    id bigint NOT NULL,
    pago_detalle_id bigint NOT NULL,
    referencia_id numeric,
    numero_cuota numeric NOT NULL,
    fecha_vencimiento timestamp without time zone,
    estado operaciones.pago_detalle_cuota_estado DEFAULT 'PENDIENTE'::operaciones.pago_detalle_cuota_estado NOT NULL,
    total_pagado numeric DEFAULT 0,
    total_final numeric NOT NULL,
    creado_en timestamp without time zone DEFAULT now(),
    usuario_id bigint
);


--
-- Name: pago_detalle_cuota_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pago_detalle_cuota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pago_detalle_cuota_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pago_detalle_cuota_id_seq OWNED BY operaciones.pago_detalle_cuota.id;


--
-- Name: pago_detalle_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pago_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pago_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pago_detalle_id_seq OWNED BY operaciones.pago_detalle.id;


--
-- Name: pago_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pago_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pago_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pago_id_seq OWNED BY operaciones.pago.id;


--
-- Name: pedido; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido (
    id bigint NOT NULL,
    proveedor_id bigint,
    vendedor_id bigint,
    plazo_credito integer,
    moneda_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    forma_pago_id bigint,
    tipo_boleta character varying,
    observacion_forma_pago text
);

ALTER TABLE ONLY operaciones.pedido REPLICA IDENTITY FULL;


--
-- Name: pedido_fecha_entrega; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido_fecha_entrega (
    id bigint NOT NULL,
    pedido_id bigint NOT NULL,
    fecha_entrega timestamp without time zone NOT NULL,
    creado_en timestamp without time zone,
    usuario_id bigint
);


--
-- Name: pedido_fecha_entrega_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_fecha_entrega_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_fecha_entrega_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_fecha_entrega_id_seq OWNED BY operaciones.pedido_fecha_entrega.id;


--
-- Name: pedido_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_id_seq OWNED BY operaciones.pedido.id;


--
-- Name: pedido_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido_item (
    id bigint NOT NULL,
    pedido_id bigint,
    producto_id bigint,
    precio_unitario_solicitado numeric DEFAULT 0,
    observacion character varying,
    estado operaciones.pedido_item_estado,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_creacion_id bigint,
    vencimiento_esperado timestamp(0) without time zone,
    presentacion_creacion_id bigint,
    cantidad_solicitada numeric,
    es_bonificacion boolean DEFAULT false
);

ALTER TABLE ONLY operaciones.pedido_item REPLICA IDENTITY FULL;


--
-- Name: pedido_item_distribucion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido_item_distribucion (
    id bigint NOT NULL,
    pedido_item_id bigint NOT NULL,
    sucursal_entrega_id bigint NOT NULL,
    cantidad_asignada numeric(10,2) NOT NULL,
    sucursal_influencia_id bigint NOT NULL
);


--
-- Name: pedido_item_distribucion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_item_distribucion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_item_distribucion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_item_distribucion_id_seq OWNED BY operaciones.pedido_item_distribucion.id;


--
-- Name: pedido_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_item_id_seq OWNED BY operaciones.pedido_item.id;


--
-- Name: pedido_sucursal_entrega; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido_sucursal_entrega (
    id bigint NOT NULL,
    pedido_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    creado_en timestamp without time zone,
    usuario_id bigint
);


--
-- Name: pedido_sucursal_entrega_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_sucursal_entrega_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_sucursal_entrega_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_sucursal_entrega_id_seq OWNED BY operaciones.pedido_sucursal_entrega.id;


--
-- Name: pedido_sucursal_influencia; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.pedido_sucursal_influencia (
    id bigint NOT NULL,
    pedido_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    creado_en timestamp without time zone,
    usuario_id bigint
);


--
-- Name: pedido_sucursal_influencia_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.pedido_sucursal_influencia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedido_sucursal_influencia_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.pedido_sucursal_influencia_id_seq OWNED BY operaciones.pedido_sucursal_influencia.id;


--
-- Name: precio_delivery; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.precio_delivery (
    id bigint NOT NULL,
    descripcion character varying,
    valor numeric,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint
);

ALTER TABLE ONLY operaciones.precio_delivery REPLICA IDENTITY FULL;


--
-- Name: precio_delivery_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.precio_delivery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: precio_delivery_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.precio_delivery_id_seq OWNED BY operaciones.precio_delivery.id;


--
-- Name: proceso_etapa; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.proceso_etapa (
    id bigint NOT NULL,
    pedido_id bigint NOT NULL,
    tipo_etapa operaciones.proceso_etapa_tipo NOT NULL,
    estado_etapa operaciones.proceso_etapa_estado DEFAULT 'PENDIENTE'::operaciones.proceso_etapa_estado NOT NULL,
    fecha_inicio timestamp without time zone,
    fecha_fin timestamp without time zone,
    usuario_inicio_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: proceso_etapa_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.proceso_etapa_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proceso_etapa_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.proceso_etapa_id_seq OWNED BY operaciones.proceso_etapa.id;


--
-- Name: producto_vencimiento; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.producto_vencimiento (
    id bigint NOT NULL,
    producto_id bigint NOT NULL,
    presentacion_id bigint,
    sucursal_id bigint NOT NULL,
    fecha_vencimiento date,
    cantidad double precision,
    tipo_origen operaciones.tipo_origen_vencimiento,
    origen_id bigint,
    usuario_id bigint NOT NULL,
    fecha_creacion timestamp without time zone
);


--
-- Name: producto_vencimiento_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.producto_vencimiento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_vencimiento_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.producto_vencimiento_id_seq OWNED BY operaciones.producto_vencimiento.id;


--
-- Name: programar_precio; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.programar_precio (
    id bigint NOT NULL,
    precio_id bigint NOT NULL,
    momento_cambio operaciones.cambio_precio_momento DEFAULT 'INMEDIATO'::operaciones.cambio_precio_momento,
    nuevo_precio numeric NOT NULL,
    fecha_cambio timestamp without time zone NOT NULL,
    cantidad numeric,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY operaciones.programar_precio REPLICA IDENTITY FULL;


--
-- Name: programar_precio_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.programar_precio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: programar_precio_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.programar_precio_id_seq OWNED BY operaciones.programar_precio.id;


--
-- Name: recepcion_costo_adicional; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.recepcion_costo_adicional (
    id bigint NOT NULL,
    recepcion_mercaderia_id bigint NOT NULL,
    descripcion character varying(255) NOT NULL,
    monto numeric(15,2) NOT NULL,
    moneda_id bigint NOT NULL
);


--
-- Name: recepcion_costo_adicional_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.recepcion_costo_adicional_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recepcion_costo_adicional_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.recepcion_costo_adicional_id_seq OWNED BY operaciones.recepcion_costo_adicional.id;


--
-- Name: recepcion_mercaderia; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.recepcion_mercaderia (
    id bigint NOT NULL,
    proveedor_id bigint NOT NULL,
    sucursal_recepcion_id bigint NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    moneda_id bigint NOT NULL,
    cotizacion numeric(10,4) DEFAULT 1.0,
    estado operaciones.recepcion_mercaderia_estado DEFAULT 'PENDIENTE'::operaciones.recepcion_mercaderia_estado NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: recepcion_mercaderia_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.recepcion_mercaderia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recepcion_mercaderia_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.recepcion_mercaderia_id_seq OWNED BY operaciones.recepcion_mercaderia.id;


--
-- Name: recepcion_mercaderia_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.recepcion_mercaderia_item (
    id bigint NOT NULL,
    recepcion_mercaderia_id bigint NOT NULL,
    nota_recepcion_item_id bigint,
    producto_id bigint NOT NULL,
    sucursal_entrega_id bigint NOT NULL,
    cantidad_recibida numeric(10,2) NOT NULL,
    vencimiento_recibido date,
    lote character varying(100),
    es_bonificacion boolean DEFAULT false,
    observaciones text,
    presentacion_recibida_id bigint,
    motivo_rechazo operaciones.motivo_rechazo_fisico,
    usuario_id bigint DEFAULT 1 NOT NULL,
    cantidad_rechazada numeric DEFAULT 0,
    nota_recepcion_item_distribucion_id bigint,
    metodo_verificacion operaciones.metodo_verificacion,
    motivo_verificacion_manual operaciones.motivo_verificacion_manual,
    estado_verificacion operaciones.estado_verificacion DEFAULT 'PENDIENTE'::operaciones.estado_verificacion NOT NULL
);


--
-- Name: recepcion_mercaderia_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.recepcion_mercaderia_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recepcion_mercaderia_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.recepcion_mercaderia_item_id_seq OWNED BY operaciones.recepcion_mercaderia_item.id;


--
-- Name: recepcion_mercaderia_item_variacion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.recepcion_mercaderia_item_variacion (
    id bigint NOT NULL,
    recepcion_mercaderia_item_id bigint NOT NULL,
    presentacion_id bigint,
    cantidad double precision,
    vencimiento timestamp without time zone,
    lote character varying(255),
    rechazado boolean DEFAULT false,
    motivo_rechazo character varying(255)
);


--
-- Name: recepcion_mercaderia_item_variacion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recepcion_mercaderia_item_variacion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.recepcion_mercaderia_item_variacion_id_seq OWNED BY operaciones.recepcion_mercaderia_item_variacion.id;


--
-- Name: recepcion_mercaderia_nota; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.recepcion_mercaderia_nota (
    id bigint NOT NULL,
    recepcion_mercaderia_id bigint NOT NULL,
    nota_recepcion_id bigint NOT NULL
);


--
-- Name: recepcion_mercaderia_nota_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.recepcion_mercaderia_nota_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recepcion_mercaderia_nota_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.recepcion_mercaderia_nota_id_seq OWNED BY operaciones.recepcion_mercaderia_nota.id;


--
-- Name: salida; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.salida (
    id bigint NOT NULL,
    responsable_carga_id bigint,
    tipo_salida operaciones.tipo_salida,
    sucursal_id bigint,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    usuario_id bigint,
    activo boolean DEFAULT false NOT NULL
);

ALTER TABLE ONLY operaciones.salida REPLICA IDENTITY FULL;


--
-- Name: salida_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.salida_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salida_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.salida_id_seq OWNED BY operaciones.salida.id;


--
-- Name: salida_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.salida_item (
    id bigint NOT NULL,
    producto_id bigint NOT NULL,
    presentacion_id bigint NOT NULL,
    cantidad numeric NOT NULL,
    observacion character varying,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    usuario_id bigint,
    salida_id bigint NOT NULL,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY operaciones.salida_item REPLICA IDENTITY FULL;


--
-- Name: salida_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.salida_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salida_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.salida_item_id_seq OWNED BY operaciones.salida_item.id;


--
-- Name: solicitud_pago; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.solicitud_pago (
    id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    estado operaciones.solicitud_pago_estado NOT NULL,
    pago_id bigint,
    numero_solicitud character varying(50) NOT NULL,
    fecha_solicitud timestamp without time zone NOT NULL,
    fecha_pago_propuesta date,
    observaciones text,
    forma_pago_id bigint,
    proveedor_id bigint NOT NULL,
    moneda_id bigint NOT NULL,
    monto_total numeric NOT NULL
);


--
-- Name: solicitud_pago_detalle; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.solicitud_pago_detalle (
    id bigint NOT NULL,
    solicitud_pago_id bigint NOT NULL,
    moneda_id bigint NOT NULL,
    forma_pago_id bigint NOT NULL,
    valor numeric(15,2) NOT NULL,
    fecha_pago date,
    observacion text,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    orden integer,
    cotizacion numeric(15,4),
    fecha_emision_cheque date,
    portador character varying(255),
    nominal boolean DEFAULT true,
    diferido boolean DEFAULT true
);


--
-- Name: solicitud_pago_detalle_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.solicitud_pago_detalle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_pago_detalle_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.solicitud_pago_detalle_id_seq OWNED BY operaciones.solicitud_pago_detalle.id;


--
-- Name: solicitud_pago_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.solicitud_pago_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_pago_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.solicitud_pago_id_seq OWNED BY operaciones.solicitud_pago.id;


--
-- Name: solicitud_pago_nota_recepcion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.solicitud_pago_nota_recepcion (
    id bigint NOT NULL,
    solicitud_pago_id bigint NOT NULL,
    nota_recepcion_id bigint NOT NULL,
    monto_incluido numeric(15,2) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: solicitud_pago_nota_recepcion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.solicitud_pago_nota_recepcion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_pago_nota_recepcion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.solicitud_pago_nota_recepcion_id_seq OWNED BY operaciones.solicitud_pago_nota_recepcion.id;


--
-- Name: stock_por_producto_sucursal; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.stock_por_producto_sucursal (
    producto_id bigint NOT NULL,
    last_movimiento_stock_id bigint NOT NULL,
    sucursal_id bigint NOT NULL,
    cantidad numeric DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY operaciones.stock_por_producto_sucursal REPLICA IDENTITY FULL;


--
-- Name: subcategoria_observacion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.subcategoria_observacion (
    id bigint NOT NULL,
    categoria_id bigint,
    usuario_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: subcategoria_observacion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.subcategoria_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subcategoria_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.subcategoria_observacion_id_seq OWNED BY operaciones.subcategoria_observacion.id;


--
-- Name: transferencia; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.transferencia (
    id bigint NOT NULL,
    sucursal_origen_id bigint NOT NULL,
    sucursal_destino_id bigint NOT NULL,
    estado operaciones.transferencia_estado,
    tipo operaciones.tipo_transferencia,
    etapa operaciones.etapa_transferencia,
    observacion text,
    is_origen boolean,
    is_destino boolean,
    usuario_pre_transferencia_id bigint NOT NULL,
    usuario_preparacion_id bigint,
    usuario_transporte_id bigint,
    usuario_recepcion_id bigint,
    creado_en timestamp without time zone
);

ALTER TABLE ONLY operaciones.transferencia REPLICA IDENTITY FULL;


--
-- Name: transferencia_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.transferencia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transferencia_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.transferencia_id_seq OWNED BY operaciones.transferencia.id;


--
-- Name: transferencia_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.transferencia_item (
    id bigint NOT NULL,
    transferencia_id bigint NOT NULL,
    presentacion_pre_transferencia_id bigint NOT NULL,
    presentacion_preparacion_id bigint,
    presentacion_transporte_id bigint,
    presentacion_recepcion_id bigint,
    cantidad_pre_transferencia numeric,
    cantidad_preparacion numeric,
    cantidad_transporte numeric,
    cantidad_recepcion numeric,
    observacion_pre_transferencia character varying,
    observacion_preparacion character varying,
    observacion_transporte character varying,
    observacion_recepcion character varying,
    vencimiento_pre_transferencia timestamp without time zone,
    vencimiento_preparacion timestamp without time zone,
    vencimiento_transporte timestamp without time zone,
    vencimiento_recepcion timestamp without time zone,
    motivo_modificacion_pre_transferencia operaciones.transferencia_item_motivo_modificacion,
    motivo_modificacion_preparacion operaciones.transferencia_item_motivo_modificacion,
    motivo_modificacion_transporte operaciones.transferencia_item_motivo_modificacion,
    motivo_modificacion_recepcion operaciones.transferencia_item_motivo_modificacion,
    motivo_rechazo_pre_transferencia operaciones.transferencia_item_motivo_rechazo,
    motivo_rechazo_preparacion operaciones.transferencia_item_motivo_rechazo,
    motivo_rechazo_transporte operaciones.transferencia_item_motivo_rechazo,
    motivo_rechazo_recepcion operaciones.transferencia_item_motivo_rechazo,
    activo boolean DEFAULT true,
    posee_vencimiento boolean DEFAULT true NOT NULL,
    usuario_id bigint NOT NULL,
    creado_en timestamp without time zone,
    vencimiento_verificado boolean DEFAULT false
);

ALTER TABLE ONLY operaciones.transferencia_item REPLICA IDENTITY FULL;


--
-- Name: transferencia_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.transferencia_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transferencia_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.transferencia_item_id_seq OWNED BY operaciones.transferencia_item.id;


--
-- Name: venta; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.venta (
    id bigint NOT NULL,
    cliente_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    estado operaciones.venta_estado,
    total_gs numeric,
    total_rs numeric,
    total_ds numeric,
    forma_pago_id bigint,
    cobro_id bigint,
    caja_id bigint,
    sucursal_id bigint NOT NULL,
    delivery_id bigint
);

ALTER TABLE ONLY operaciones.venta REPLICA IDENTITY FULL;


--
-- Name: venta_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.venta_item (
    id bigint NOT NULL,
    venta_id bigint,
    unidad_medida productos.unidad_medida,
    costo_unitario numeric,
    existencia numeric,
    producto_id bigint,
    cantidad numeric,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    descuento_unitario numeric DEFAULT 0,
    presentacion_id bigint,
    activo boolean DEFAULT true,
    id_central bigint,
    sucursal_id bigint NOT NULL,
    precio numeric,
    precio_id bigint
);

ALTER TABLE ONLY operaciones.venta_item REPLICA IDENTITY FULL;


--
-- Name: venta_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.venta_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: venta_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.venta_item_id_seq OWNED BY operaciones.venta_item.id;


--
-- Name: venta_observacion; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.venta_observacion (
    id bigint NOT NULL,
    motivo_id bigint,
    venta_id bigint,
    sucursal_id bigint,
    usuario_id bigint,
    descripcion character varying,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: venta_observacion_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.venta_observacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: venta_observacion_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.venta_observacion_id_seq OWNED BY operaciones.venta_observacion.id;


--
-- Name: vuelto; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.vuelto (
    id bigint NOT NULL,
    autorizado_por_id bigint,
    responsable_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    activo boolean DEFAULT true,
    sucursal_id bigint NOT NULL
);

ALTER TABLE ONLY operaciones.vuelto REPLICA IDENTITY FULL;


--
-- Name: vuelto_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.vuelto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vuelto_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.vuelto_id_seq OWNED BY operaciones.vuelto.id;


--
-- Name: vuelto_item; Type: TABLE; Schema: operaciones; Owner: -
--

CREATE TABLE operaciones.vuelto_item (
    id bigint NOT NULL,
    vuelto_id bigint,
    valor numeric,
    moneda_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    sucursal_id bigint
);

ALTER TABLE ONLY operaciones.vuelto_item REPLICA IDENTITY FULL;


--
-- Name: vuelto_item_id_seq; Type: SEQUENCE; Schema: operaciones; Owner: -
--

CREATE SEQUENCE operaciones.vuelto_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vuelto_item_id_seq; Type: SEQUENCE OWNED BY; Schema: operaciones; Owner: -
--

ALTER SEQUENCE operaciones.vuelto_item_id_seq OWNED BY operaciones.vuelto_item.id;


--
-- Name: cliente; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.cliente (
    id bigint NOT NULL,
    persona_id bigint,
    credito numeric DEFAULT 0,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    tipo personas.tipo_cliente DEFAULT 'NORMAL'::personas.tipo_cliente,
    codigo character varying,
    sucursal_id bigint DEFAULT 0,
    tributa boolean,
    verificado_set boolean DEFAULT false,
    activo boolean DEFAULT true,
    razon_social character varying,
    ruc character varying,
    tipo_contribuyente integer
);

ALTER TABLE ONLY personas.cliente REPLICA IDENTITY FULL;


--
-- Name: cliente_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.cliente_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cliente_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.cliente_id_seq OWNED BY personas.cliente.id;


--
-- Name: funcionario; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.funcionario (
    id bigint NOT NULL,
    persona_id bigint,
    cargo_id bigint,
    credito numeric DEFAULT 0,
    fecha_ingreso timestamp without time zone DEFAULT now(),
    sueldo numeric DEFAULT 0,
    sector bigint,
    supervisado_por_id bigint,
    sucursal_id bigint,
    fase_prueba boolean DEFAULT true,
    diarista boolean DEFAULT false,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    activo boolean DEFAULT true,
    horario_id bigint
);

ALTER TABLE ONLY personas.funcionario REPLICA IDENTITY FULL;


--
-- Name: funcionario_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.funcionario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: funcionario_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.funcionario_id_seq OWNED BY personas.funcionario.id;


--
-- Name: grupo_role; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.grupo_role (
    id bigint NOT NULL,
    descripcion character varying NOT NULL,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY personas.grupo_role REPLICA IDENTITY FULL;


--
-- Name: grupo_privilegio_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.grupo_privilegio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupo_privilegio_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.grupo_privilegio_id_seq OWNED BY personas.grupo_role.id;


--
-- Name: grupo_role_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.grupo_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: persona; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.persona (
    id bigint NOT NULL,
    nombre character varying,
    apodo character varying,
    documento character varying,
    nacimiento timestamp with time zone,
    sexo character varying,
    direccion character varying,
    ciudad_id bigint,
    telefono character varying,
    social_media character varying,
    imagenes character varying,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint,
    email character varying,
    activo boolean DEFAULT true,
    embedding text
);

ALTER TABLE ONLY personas.persona REPLICA IDENTITY FULL;


--
-- Name: persona_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.persona_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: persona_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.persona_id_seq OWNED BY personas.persona.id;


--
-- Name: pre_registro_funcionario; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.pre_registro_funcionario (
    id bigint NOT NULL,
    funcionario_id bigint,
    nombre_completo character varying,
    apodo character varying,
    documento character varying,
    telefono_personal character varying,
    telefono_emergencia character varying,
    nombre_contacto_emergencia character varying,
    email character varying,
    ciudad character varying,
    direccion character varying,
    sucursal character varying,
    fecha_nacimiento timestamp without time zone,
    fecha_ingreso timestamp without time zone,
    habilidades character varying,
    registro_conducir boolean,
    nivel_educacion character varying,
    observacion character varying,
    verificado boolean,
    creado_en timestamp without time zone
);


--
-- Name: pre_registro_funcionario_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.pre_registro_funcionario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pre_registro_funcionario_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.pre_registro_funcionario_id_seq OWNED BY personas.pre_registro_funcionario.id;


--
-- Name: proveedor; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.proveedor (
    id bigint NOT NULL,
    persona_id bigint,
    credito boolean DEFAULT false,
    tipo_credito character varying,
    cheque_dias numeric,
    datos_bancarios_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    funcionario_encargado_id bigint,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY personas.proveedor REPLICA IDENTITY FULL;


--
-- Name: proveedor_dias_visita; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.proveedor_dias_visita (
    id bigint NOT NULL,
    proveedor_id bigint,
    dia general.dias_semana,
    hora integer,
    observacion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY personas.proveedor_dias_visita REPLICA IDENTITY FULL;


--
-- Name: proveedor_dias_visita_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.proveedor_dias_visita_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proveedor_dias_visita_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.proveedor_dias_visita_id_seq OWNED BY personas.proveedor_dias_visita.id;


--
-- Name: proveedor_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.proveedor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proveedor_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.proveedor_id_seq OWNED BY personas.proveedor.id;


--
-- Name: role; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.role (
    id bigint NOT NULL,
    nombre character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    grupo_role_id bigint
);

ALTER TABLE ONLY personas.role REPLICA IDENTITY FULL;


--
-- Name: role_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.role_id_seq OWNED BY personas.role.id;


--
-- Name: usuario; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.usuario (
    id bigint NOT NULL,
    persona_id bigint,
    password character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    nickname character varying,
    email character varying,
    activo boolean DEFAULT true
);

ALTER TABLE ONLY personas.usuario REPLICA IDENTITY FULL;


--
-- Name: usuario_grupo; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.usuario_grupo (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    grupo_privilegio_id bigint NOT NULL,
    modificado boolean DEFAULT false,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY personas.usuario_grupo REPLICA IDENTITY FULL;


--
-- Name: usuario_grupo_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.usuario_grupo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_grupo_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.usuario_grupo_id_seq OWNED BY personas.usuario_grupo.id;


--
-- Name: usuario_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.usuario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.usuario_id_seq OWNED BY personas.usuario.id;


--
-- Name: usuario_role; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.usuario_role (
    id bigint NOT NULL,
    role_id bigint,
    user_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY personas.usuario_role REPLICA IDENTITY FULL;


--
-- Name: usuario_role_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.usuario_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_role_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.usuario_role_id_seq OWNED BY personas.usuario_role.id;


--
-- Name: vendedor; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.vendedor (
    id bigint NOT NULL,
    persona_id bigint,
    activo boolean DEFAULT true,
    observacion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY personas.vendedor REPLICA IDENTITY FULL;


--
-- Name: vendedor_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.vendedor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vendedor_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.vendedor_id_seq OWNED BY personas.vendedor.id;


--
-- Name: vendedor_proveedor; Type: TABLE; Schema: personas; Owner: -
--

CREATE TABLE personas.vendedor_proveedor (
    id bigint NOT NULL,
    vendedor_id bigint,
    proveedor_id bigint,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY personas.vendedor_proveedor REPLICA IDENTITY FULL;


--
-- Name: vendedor_proveedor_id_seq; Type: SEQUENCE; Schema: personas; Owner: -
--

CREATE SEQUENCE personas.vendedor_proveedor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vendedor_proveedor_id_seq; Type: SEQUENCE OWNED BY; Schema: personas; Owner: -
--

ALTER SEQUENCE personas.vendedor_proveedor_id_seq OWNED BY personas.vendedor_proveedor.id;


--
-- Name: codigo; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.codigo (
    id bigint NOT NULL,
    codigo character varying,
    principal boolean,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    activo boolean,
    presentacion_id bigint NOT NULL
);

ALTER TABLE ONLY productos.codigo REPLICA IDENTITY FULL;


--
-- Name: codigo_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.codigo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: codigo_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.codigo_id_seq OWNED BY productos.codigo.id;


--
-- Name: codigo_tipo_precio; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.codigo_tipo_precio (
    id bigint NOT NULL,
    codigo_id bigint NOT NULL,
    tipo_precio_id bigint NOT NULL,
    creado_en timestamp with time zone DEFAULT now(),
    usuario_id bigint NOT NULL,
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY productos.codigo_tipo_precio REPLICA IDENTITY FULL;


--
-- Name: codigo_tipo_precio_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.codigo_tipo_precio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: codigo_tipo_precio_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.codigo_tipo_precio_id_seq OWNED BY productos.codigo_tipo_precio.id;


--
-- Name: costo_por_producto; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.costo_por_producto (
    id bigint NOT NULL,
    producto_id bigint,
    sucursal_id bigint,
    ultimo_precio_compra numeric,
    ultimo_precio_venta numeric,
    costo_medio numeric,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    existencia numeric,
    movimiento_stock_id bigint,
    moneda_id bigint,
    cotizacion numeric
);

ALTER TABLE ONLY productos.costo_por_producto REPLICA IDENTITY FULL;


--
-- Name: costo_por_producto_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.costo_por_producto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: costo_por_producto_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.costo_por_producto_id_seq OWNED BY productos.costo_por_producto.id;


--
-- Name: familia; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.familia (
    id bigint NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    icono character varying,
    nombre character varying,
    posicion numeric
);

ALTER TABLE ONLY productos.familia REPLICA IDENTITY FULL;


--
-- Name: familia_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.familia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: familia_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.familia_id_seq OWNED BY productos.familia.id;


--
-- Name: producto_imagen; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.producto_imagen (
    id bigint NOT NULL,
    producto_id bigint,
    ruta character varying,
    principal boolean,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0
);

ALTER TABLE ONLY productos.producto_imagen REPLICA IDENTITY FULL;


--
-- Name: imagenes_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.imagenes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: imagenes_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.imagenes_id_seq OWNED BY productos.producto_imagen.id;


--
-- Name: pdv_categoria; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.pdv_categoria (
    id bigint NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    posicion numeric
);

ALTER TABLE ONLY productos.pdv_categoria REPLICA IDENTITY FULL;


--
-- Name: pdv_categoria_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.pdv_categoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pdv_categoria_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.pdv_categoria_id_seq OWNED BY productos.pdv_categoria.id;


--
-- Name: pdv_grupo; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.pdv_grupo (
    id bigint NOT NULL,
    descripcion character varying,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    categoria_id bigint
);

ALTER TABLE ONLY productos.pdv_grupo REPLICA IDENTITY FULL;


--
-- Name: pdv_grupo_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.pdv_grupo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pdv_grupo_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.pdv_grupo_id_seq OWNED BY productos.pdv_grupo.id;


--
-- Name: pdv_grupos_productos; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.pdv_grupos_productos (
    id bigint NOT NULL,
    producto_id bigint,
    grupo_id bigint,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp(0) without time zone DEFAULT now()
);

ALTER TABLE ONLY productos.pdv_grupos_productos REPLICA IDENTITY FULL;


--
-- Name: pdv_grupos_productos_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.pdv_grupos_productos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pdv_grupos_productos_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.pdv_grupos_productos_id_seq OWNED BY productos.pdv_grupos_productos.id;


--
-- Name: precio_por_sucursal; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.precio_por_sucursal (
    id bigint NOT NULL,
    sucursal_id bigint,
    precio numeric,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    presentacion_id bigint NOT NULL,
    tipo_precio_id bigint,
    principal boolean DEFAULT false,
    activo boolean DEFAULT true
);

ALTER TABLE ONLY productos.precio_por_sucursal REPLICA IDENTITY FULL;


--
-- Name: precio_por_sucursal_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.precio_por_sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: precio_por_sucursal_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.precio_por_sucursal_id_seq OWNED BY productos.precio_por_sucursal.id;


--
-- Name: presentacion; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.presentacion (
    id bigint NOT NULL,
    producto_id bigint NOT NULL,
    cantidad numeric NOT NULL,
    descripcion character varying,
    principal boolean DEFAULT false,
    activo boolean DEFAULT true,
    tipo_presentacion_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY productos.presentacion REPLICA IDENTITY FULL;


--
-- Name: presentacion_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.presentacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: presentacion_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.presentacion_id_seq OWNED BY productos.presentacion.id;


--
-- Name: producto; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.producto (
    id bigint NOT NULL,
    propagado boolean DEFAULT false,
    descripcion character varying,
    descripcion_factura character varying,
    iva character varying,
    unidad_por_caja numeric DEFAULT 1,
    balanza boolean DEFAULT false,
    combo boolean DEFAULT false,
    garantia boolean DEFAULT false,
    ingrediente boolean DEFAULT false,
    promocion boolean DEFAULT false,
    vencimiento boolean DEFAULT true,
    stock boolean DEFAULT true,
    usuario_id bigint,
    tipo_conservacion productos.tipo_conservacion DEFAULT 'ENFRIABLE'::productos.tipo_conservacion,
    creado_en timestamp with time zone DEFAULT now(),
    sub_familia_id bigint,
    observacion character varying,
    cambiable boolean DEFAULT false,
    es_alcoholico boolean DEFAULT false,
    unidad_por_caja_secundaria numeric DEFAULT 2,
    imagenes character varying,
    tiempo_garantia numeric,
    dias_vencimiento numeric,
    activo boolean DEFAULT true,
    is_envase boolean DEFAULT false,
    envase_id bigint,
    lote boolean DEFAULT false
);

ALTER TABLE ONLY productos.producto REPLICA IDENTITY FULL;


--
-- Name: producto_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.producto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.producto_id_seq OWNED BY productos.producto.id;


--
-- Name: producto_por_sucursal; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.producto_por_sucursal (
    id bigint NOT NULL,
    producto_id bigint,
    sucursal_id bigint,
    cant_minima numeric,
    cant_media numeric,
    cant_maxima numeric,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY productos.producto_por_sucursal REPLICA IDENTITY FULL;


--
-- Name: producto_proveedor; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.producto_proveedor (
    id bigint NOT NULL,
    producto_id bigint,
    proveedor_id bigint,
    pedido_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sucursal_id bigint DEFAULT 0,
    activo boolean DEFAULT true,
    motivo_desvinculacion character varying(255)
);

ALTER TABLE ONLY productos.producto_proveedor REPLICA IDENTITY FULL;


--
-- Name: producto_proveedor_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.producto_proveedor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_proveedor_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.producto_proveedor_id_seq OWNED BY productos.producto_proveedor.id;


--
-- Name: productos_por_sucursal_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.productos_por_sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: productos_por_sucursal_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.productos_por_sucursal_id_seq OWNED BY productos.producto_por_sucursal.id;


--
-- Name: subfamilia; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.subfamilia (
    id bigint NOT NULL,
    familia_id bigint,
    descripcion character varying,
    activo boolean DEFAULT true,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    sub_familia_id bigint,
    icono character varying,
    nombre character varying,
    posicion numeric
);

ALTER TABLE ONLY productos.subfamilia REPLICA IDENTITY FULL;


--
-- Name: subfamilia_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.subfamilia_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subfamilia_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.subfamilia_id_seq OWNED BY productos.subfamilia.id;


--
-- Name: tipo_precio; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.tipo_precio (
    id bigint NOT NULL,
    descripcion character varying,
    autorizacion boolean,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    activo boolean DEFAULT true
);

ALTER TABLE ONLY productos.tipo_precio REPLICA IDENTITY FULL;


--
-- Name: tipo_precio_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.tipo_precio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_precio_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.tipo_precio_id_seq OWNED BY productos.tipo_precio.id;


--
-- Name: tipo_presentacion; Type: TABLE; Schema: productos; Owner: -
--

CREATE TABLE productos.tipo_presentacion (
    id bigint NOT NULL,
    descripcion character varying NOT NULL,
    unico boolean DEFAULT false,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY productos.tipo_presentacion REPLICA IDENTITY FULL;


--
-- Name: tipo_presentacion_id_seq; Type: SEQUENCE; Schema: productos; Owner: -
--

CREATE SEQUENCE productos.tipo_presentacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_presentacion_id_seq; Type: SEQUENCE OWNED BY; Schema: productos; Owner: -
--

ALTER SEQUENCE productos.tipo_presentacion_id_seq OWNED BY productos.tipo_presentacion.id;


--
-- Name: producto_proveedor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.producto_proveedor (
    id bigint NOT NULL,
    producto_id bigint,
    proveedor_id bigint,
    pedido_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);


--
-- Name: producto_proveedor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.producto_proveedor_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: producto_proveedor_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.producto_proveedor_id_seq OWNED BY public.producto_proveedor.id;


--
-- Name: marca; Type: TABLE; Schema: vehiculos; Owner: -
--

CREATE TABLE vehiculos.marca (
    id bigint NOT NULL,
    descripcion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.marca REPLICA IDENTITY FULL;


--
-- Name: marca_id_seq; Type: SEQUENCE; Schema: vehiculos; Owner: -
--

CREATE SEQUENCE vehiculos.marca_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: marca_id_seq; Type: SEQUENCE OWNED BY; Schema: vehiculos; Owner: -
--

ALTER SEQUENCE vehiculos.marca_id_seq OWNED BY vehiculos.marca.id;


--
-- Name: modelo; Type: TABLE; Schema: vehiculos; Owner: -
--

CREATE TABLE vehiculos.modelo (
    id bigint NOT NULL,
    descripcion character varying,
    marca_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.modelo REPLICA IDENTITY FULL;


--
-- Name: modelo_id_seq; Type: SEQUENCE; Schema: vehiculos; Owner: -
--

CREATE SEQUENCE vehiculos.modelo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modelo_id_seq; Type: SEQUENCE OWNED BY; Schema: vehiculos; Owner: -
--

ALTER SEQUENCE vehiculos.modelo_id_seq OWNED BY vehiculos.modelo.id;


--
-- Name: tipo_vehiculo; Type: TABLE; Schema: vehiculos; Owner: -
--

CREATE TABLE vehiculos.tipo_vehiculo (
    id bigint NOT NULL,
    descripcion character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.tipo_vehiculo REPLICA IDENTITY FULL;


--
-- Name: tipo_vehiculo_id_seq; Type: SEQUENCE; Schema: vehiculos; Owner: -
--

CREATE SEQUENCE vehiculos.tipo_vehiculo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_vehiculo_id_seq; Type: SEQUENCE OWNED BY; Schema: vehiculos; Owner: -
--

ALTER SEQUENCE vehiculos.tipo_vehiculo_id_seq OWNED BY vehiculos.tipo_vehiculo.id;


--
-- Name: vehiculo; Type: TABLE; Schema: vehiculos; Owner: -
--

CREATE TABLE vehiculos.vehiculo (
    id bigint NOT NULL,
    color character varying,
    chapa character varying,
    documentacion boolean DEFAULT false,
    refrigerado boolean DEFAULT false,
    nuevo boolean DEFAULT true,
    fecha_adquisicion timestamp without time zone,
    primer_kilometraje numeric DEFAULT 0,
    tipo_vehiculo bigint,
    imagenes_documentos character varying,
    imagenes_vehiculo character varying,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now(),
    anho numeric,
    capacidad_kg numeric,
    capacidad_pasajeros numeric,
    modelo_id bigint
);

ALTER TABLE ONLY vehiculos.vehiculo REPLICA IDENTITY FULL;


--
-- Name: vehiculo_id_seq; Type: SEQUENCE; Schema: vehiculos; Owner: -
--

CREATE SEQUENCE vehiculos.vehiculo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehiculo_id_seq; Type: SEQUENCE OWNED BY; Schema: vehiculos; Owner: -
--

ALTER SEQUENCE vehiculos.vehiculo_id_seq OWNED BY vehiculos.vehiculo.id;


--
-- Name: vehiculo_sucursal; Type: TABLE; Schema: vehiculos; Owner: -
--

CREATE TABLE vehiculos.vehiculo_sucursal (
    id bigint NOT NULL,
    sucursal_id bigint,
    vehiculo_id bigint,
    responsable_id bigint,
    usuario_id bigint,
    creado_en timestamp with time zone DEFAULT now()
);

ALTER TABLE ONLY vehiculos.vehiculo_sucursal REPLICA IDENTITY FULL;


--
-- Name: vehiculo_sucursal_id_seq; Type: SEQUENCE; Schema: vehiculos; Owner: -
--

CREATE SEQUENCE vehiculos.vehiculo_sucursal_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehiculo_sucursal_id_seq; Type: SEQUENCE OWNED BY; Schema: vehiculos; Owner: -
--

ALTER SEQUENCE vehiculos.vehiculo_sucursal_id_seq OWNED BY vehiculos.vehiculo_sucursal.id;


--
-- Name: autorizacion id; Type: DEFAULT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.autorizacion ALTER COLUMN id SET DEFAULT nextval('administrativo.autorizacion_id_seq'::regclass);


--
-- Name: horario id; Type: DEFAULT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.horario ALTER COLUMN id SET DEFAULT nextval('administrativo.horario_id_seq'::regclass);


--
-- Name: actualizacion id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.actualizacion ALTER COLUMN id SET DEFAULT nextval('configuraciones.actualizacion_id_seq'::regclass);


--
-- Name: error_log id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.error_log ALTER COLUMN id SET DEFAULT nextval('configuraciones.error_log_id_seq'::regclass);


--
-- Name: inicio_sesion id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.inicio_sesion ALTER COLUMN id SET DEFAULT nextval('configuraciones.inicio_sesion_id_seq'::regclass);


--
-- Name: local id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.local ALTER COLUMN id SET DEFAULT nextval('configuraciones.local_id_seq'::regclass);


--
-- Name: modificacion_detalle id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_detalle ALTER COLUMN id SET DEFAULT nextval('configuraciones.modificacion_detalle_id_seq'::regclass);


--
-- Name: modificacion_registro id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_registro ALTER COLUMN id SET DEFAULT nextval('configuraciones.modificacion_registro_id_seq'::regclass);


--
-- Name: notificacion id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_id_seq'::regclass);


--
-- Name: notificacion_comentario id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_comentario ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_comentario_id_seq'::regclass);


--
-- Name: notificacion_destinatario id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_destinatario ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_destinatario_id_seq'::regclass);


--
-- Name: notificacion_envio_log id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_envio_log ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_envio_log_id_seq'::regclass);


--
-- Name: notificacion_preferencia_usuario id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_preferencia_usuario ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_preferencia_usuario_id_seq'::regclass);


--
-- Name: notificacion_tipo_role id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_tipo_role ALTER COLUMN id SET DEFAULT nextval('configuraciones.notificacion_tipo_role_id_seq'::regclass);


--
-- Name: rabbitmq_msg id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.rabbitmq_msg ALTER COLUMN id SET DEFAULT nextval('configuraciones.rabbitmq_msg_id_seq'::regclass);


--
-- Name: replication_table id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_table ALTER COLUMN id SET DEFAULT nextval('configuraciones.replication_table_id_seq'::regclass);


--
-- Name: replication_test id; Type: DEFAULT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_test ALTER COLUMN id SET DEFAULT nextval('configuraciones.replication_test_id_seq'::regclass);


--
-- Name: cargo id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.cargo ALTER COLUMN id SET DEFAULT nextval('empresarial.cargo_id_seq'::regclass);


--
-- Name: configuracion_general id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.configuracion_general ALTER COLUMN id SET DEFAULT nextval('empresarial.configuracion_general_id_seq'::regclass);


--
-- Name: punto_de_venta id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.punto_de_venta ALTER COLUMN id SET DEFAULT nextval('empresarial.punto_de_venta_id_seq'::regclass);


--
-- Name: sector id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sector ALTER COLUMN id SET DEFAULT nextval('empresarial.sector_id_seq'::regclass);


--
-- Name: sucursal id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sucursal ALTER COLUMN id SET DEFAULT nextval('empresarial.sucursal_id_seq'::regclass);


--
-- Name: zona id; Type: DEFAULT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.zona ALTER COLUMN id SET DEFAULT nextval('empresarial.zona_id_seq'::regclass);


--
-- Name: equipo id; Type: DEFAULT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.equipo ALTER COLUMN id SET DEFAULT nextval('equipos.equipo_id_seq'::regclass);


--
-- Name: tipo_equipo id; Type: DEFAULT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.tipo_equipo ALTER COLUMN id SET DEFAULT nextval('equipos.tipo_equipo_id_seq'::regclass);


--
-- Name: banco id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.banco ALTER COLUMN id SET DEFAULT nextval('financiero.banco_id_seq'::regclass);


--
-- Name: caja_categoria_observacion id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_categoria_observacion ALTER COLUMN id SET DEFAULT nextval('financiero.caja_categoria_observacion_id_seq'::regclass);


--
-- Name: caja_motivo_observacion id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_motivo_observacion ALTER COLUMN id SET DEFAULT nextval('financiero.caja_motivo_observacion_id_seq'::regclass);


--
-- Name: caja_observacion id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion ALTER COLUMN id SET DEFAULT nextval('financiero.caja_observacion_id_seq'::regclass);


--
-- Name: caja_subcategoria_observacion id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_subcategoria_observacion ALTER COLUMN id SET DEFAULT nextval('financiero.caja_subcategoria_observacion_id_seq'::regclass);


--
-- Name: cambio id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio ALTER COLUMN id SET DEFAULT nextval('financiero.cambio_id_seq'::regclass);


--
-- Name: cambio_caja id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja ALTER COLUMN id SET DEFAULT nextval('financiero.cambio_caja_id_seq'::regclass);


--
-- Name: cheque id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque ALTER COLUMN id SET DEFAULT nextval('financiero.cheque_id_seq'::regclass);


--
-- Name: chequera id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.chequera ALTER COLUMN id SET DEFAULT nextval('financiero.chequera_id_seq'::regclass);


--
-- Name: conteo_moneda id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo_moneda ALTER COLUMN id SET DEFAULT nextval('financiero.conteo_moneda_id_seq'::regclass);


--
-- Name: cuenta_bancaria id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria ALTER COLUMN id SET DEFAULT nextval('financiero.cuenta_bancaria_id_seq'::regclass);


--
-- Name: documento id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento ALTER COLUMN id SET DEFAULT nextval('financiero.documento_id_seq'::regclass);


--
-- Name: documento_electronico id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico ALTER COLUMN id SET DEFAULT nextval('financiero.documento_electronico_id_seq'::regclass);


--
-- Name: evento_cancelacion_de id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_cancelacion_de ALTER COLUMN id SET DEFAULT nextval('financiero.evento_cancelacion_de_id_seq'::regclass);


--
-- Name: evento_inutilizacion_de id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de ALTER COLUMN id SET DEFAULT nextval('financiero.evento_inutilizacion_de_id_seq'::regclass);


--
-- Name: evento_inutilizacion_de_documento_electronico id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico ALTER COLUMN id SET DEFAULT nextval('financiero.evento_inutilizacion_de_documento_electronico_id_seq'::regclass);


--
-- Name: evento_nominacion_de id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de ALTER COLUMN id SET DEFAULT nextval('financiero.evento_nominacion_de_id_seq'::regclass);


--
-- Name: factura_legal id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal ALTER COLUMN id SET DEFAULT nextval('financiero.factura_legal_id_seq'::regclass);


--
-- Name: factura_legal_item id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item ALTER COLUMN id SET DEFAULT nextval('financiero.factura_legal_item_id_seq'::regclass);


--
-- Name: forma_pago id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.forma_pago ALTER COLUMN id SET DEFAULT nextval('financiero.forma_pago_id_seq'::regclass);


--
-- Name: gasto_detalle id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto_detalle ALTER COLUMN id SET DEFAULT nextval('financiero.gasto_detalle_id_seq'::regclass);


--
-- Name: moneda id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda ALTER COLUMN id SET DEFAULT nextval('financiero.moneda_id_seq'::regclass);


--
-- Name: moneda_billetes id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda_billetes ALTER COLUMN id SET DEFAULT nextval('financiero.moneda_billetes_id_seq'::regclass);


--
-- Name: movimiento_caja id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja ALTER COLUMN id SET DEFAULT nextval('financiero.movimiento_caja_id_seq'::regclass);


--
-- Name: movimiento_personas id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_personas ALTER COLUMN id SET DEFAULT nextval('financiero.movimiento_personas_id_seq'::regclass);


--
-- Name: sencillo id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo ALTER COLUMN id SET DEFAULT nextval('financiero.sencillo_id_seq'::regclass);


--
-- Name: sencillo_detalle id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle ALTER COLUMN id SET DEFAULT nextval('financiero.sencillo_detalle_id_seq'::regclass);


--
-- Name: timbrado id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado ALTER COLUMN id SET DEFAULT nextval('financiero.timbrado_id_seq'::regclass);


--
-- Name: timbrado_detalle id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado_detalle ALTER COLUMN id SET DEFAULT nextval('financiero.timbrado_detalle_id_seq'::regclass);


--
-- Name: tipo_gasto id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.tipo_gasto ALTER COLUMN id SET DEFAULT nextval('financiero.tipo_gasto_id_seq'::regclass);


--
-- Name: venta_credito id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito ALTER COLUMN id SET DEFAULT nextval('financiero.venta_credito_id_seq'::regclass);


--
-- Name: venta_credito_cuota id; Type: DEFAULT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito_cuota ALTER COLUMN id SET DEFAULT nextval('financiero.venta_credito_cuota_id_seq'::regclass);


--
-- Name: barrio id; Type: DEFAULT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.barrio ALTER COLUMN id SET DEFAULT nextval('general.barrio_id_seq'::regclass);


--
-- Name: ciudad id; Type: DEFAULT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.ciudad ALTER COLUMN id SET DEFAULT nextval('general.ciudad_id_seq'::regclass);


--
-- Name: contacto id; Type: DEFAULT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.contacto ALTER COLUMN id SET DEFAULT nextval('general.contacto_id_seq'::regclass);


--
-- Name: pais id; Type: DEFAULT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.pais ALTER COLUMN id SET DEFAULT nextval('general.pais_id_seq'::regclass);


--
-- Name: imagen_master id; Type: DEFAULT; Schema: media; Owner: -
--

ALTER TABLE ONLY media.imagen_master ALTER COLUMN id SET DEFAULT nextval('media.imagen_master_id_seq'::regclass);


--
-- Name: categoria_observacion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.categoria_observacion ALTER COLUMN id SET DEFAULT nextval('operaciones.categoria_observacion_id_seq'::regclass);


--
-- Name: cobro id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro ALTER COLUMN id SET DEFAULT nextval('operaciones.cobro_id_seq'::regclass);


--
-- Name: cobro_detalle id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle ALTER COLUMN id SET DEFAULT nextval('operaciones.cobro_detalle_id_seq'::regclass);


--
-- Name: compra id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra ALTER COLUMN id SET DEFAULT nextval('operaciones.compra_id_seq'::regclass);


--
-- Name: compra_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item ALTER COLUMN id SET DEFAULT nextval('operaciones.compra_item_id_seq'::regclass);


--
-- Name: constancia_de_recepcion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion ALTER COLUMN id SET DEFAULT nextval('operaciones.constancia_de_recepcion_id_seq'::regclass);


--
-- Name: constancia_de_recepcion_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion_item ALTER COLUMN id SET DEFAULT nextval('operaciones.constancia_de_recepcion_item_id_seq'::regclass);


--
-- Name: delivery id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery ALTER COLUMN id SET DEFAULT nextval('operaciones.delivery_id_seq'::regclass);


--
-- Name: devolucion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion ALTER COLUMN id SET DEFAULT nextval('operaciones.devolucion_id_seq'::regclass);


--
-- Name: devolucion_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion_item ALTER COLUMN id SET DEFAULT nextval('operaciones.devolucion_item_id_seq'::regclass);


--
-- Name: entrada id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada ALTER COLUMN id SET DEFAULT nextval('operaciones.entrada_id_seq'::regclass);


--
-- Name: entrada_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada_item ALTER COLUMN id SET DEFAULT nextval('operaciones.entrada_item_id_seq'::regclass);


--
-- Name: inventario id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario ALTER COLUMN id SET DEFAULT nextval('operaciones.inventario_id_seq'::regclass);


--
-- Name: inventario_producto id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto ALTER COLUMN id SET DEFAULT nextval('operaciones.inventario_producto_id_seq'::regclass);


--
-- Name: inventario_producto_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item ALTER COLUMN id SET DEFAULT nextval('operaciones.inventario_producto_item_id_seq'::regclass);


--
-- Name: motivo_diferencia_pedido id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_diferencia_pedido ALTER COLUMN id SET DEFAULT nextval('operaciones.motivo_diferencia_pedido_id_seq'::regclass);


--
-- Name: motivo_observacion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_observacion ALTER COLUMN id SET DEFAULT nextval('operaciones.motivo_observacion_id_seq'::regclass);


--
-- Name: movimiento_stock id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.movimiento_stock ALTER COLUMN id SET DEFAULT nextval('operaciones.movimiento_stock_id_seq'::regclass);


--
-- Name: necesidad id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad ALTER COLUMN id SET DEFAULT nextval('operaciones.necesidad_id_seq'::regclass);


--
-- Name: necesidad_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad_item ALTER COLUMN id SET DEFAULT nextval('operaciones.necesidad_item_id_seq'::regclass);


--
-- Name: nota_pedido id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_pedido ALTER COLUMN id SET DEFAULT nextval('operaciones.nota_pedido_id_seq'::regclass);


--
-- Name: nota_recepcion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion ALTER COLUMN id SET DEFAULT nextval('operaciones.nota_recepcion_id_seq'::regclass);


--
-- Name: nota_recepcion_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item ALTER COLUMN id SET DEFAULT nextval('operaciones.nota_recepcion_item_id_seq'::regclass);


--
-- Name: nota_recepcion_item_distribucion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion ALTER COLUMN id SET DEFAULT nextval('operaciones.nota_recepcion_item_distribucion_id_seq'::regclass);


--
-- Name: pago id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago ALTER COLUMN id SET DEFAULT nextval('operaciones.pago_id_seq'::regclass);


--
-- Name: pago_detalle id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle ALTER COLUMN id SET DEFAULT nextval('operaciones.pago_detalle_id_seq'::regclass);


--
-- Name: pago_detalle_cuota id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle_cuota ALTER COLUMN id SET DEFAULT nextval('operaciones.pago_detalle_cuota_id_seq'::regclass);


--
-- Name: pedido id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_id_seq'::regclass);


--
-- Name: pedido_fecha_entrega id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_fecha_entrega ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_fecha_entrega_id_seq'::regclass);


--
-- Name: pedido_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_item_id_seq'::regclass);


--
-- Name: pedido_item_distribucion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_item_distribucion_id_seq'::regclass);


--
-- Name: pedido_sucursal_entrega id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_sucursal_entrega_id_seq'::regclass);


--
-- Name: pedido_sucursal_influencia id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia ALTER COLUMN id SET DEFAULT nextval('operaciones.pedido_sucursal_influencia_id_seq'::regclass);


--
-- Name: precio_delivery id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.precio_delivery ALTER COLUMN id SET DEFAULT nextval('operaciones.precio_delivery_id_seq'::regclass);


--
-- Name: proceso_etapa id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.proceso_etapa ALTER COLUMN id SET DEFAULT nextval('operaciones.proceso_etapa_id_seq'::regclass);


--
-- Name: producto_vencimiento id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento ALTER COLUMN id SET DEFAULT nextval('operaciones.producto_vencimiento_id_seq'::regclass);


--
-- Name: programar_precio id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.programar_precio ALTER COLUMN id SET DEFAULT nextval('operaciones.programar_precio_id_seq'::regclass);


--
-- Name: recepcion_costo_adicional id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_costo_adicional ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_costo_adicional_id_seq'::regclass);


--
-- Name: recepcion_mercaderia id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_id_seq'::regclass);


--
-- Name: recepcion_mercaderia_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_item_id_seq'::regclass);


--
-- Name: recepcion_mercaderia_item_variacion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item_variacion ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_item_variacion_id_seq'::regclass);


--
-- Name: recepcion_mercaderia_nota id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_nota ALTER COLUMN id SET DEFAULT nextval('operaciones.recepcion_mercaderia_nota_id_seq'::regclass);


--
-- Name: salida id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida ALTER COLUMN id SET DEFAULT nextval('operaciones.salida_id_seq'::regclass);


--
-- Name: salida_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida_item ALTER COLUMN id SET DEFAULT nextval('operaciones.salida_item_id_seq'::regclass);


--
-- Name: solicitud_pago id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago ALTER COLUMN id SET DEFAULT nextval('operaciones.solicitud_pago_id_seq'::regclass);


--
-- Name: solicitud_pago_detalle id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_detalle ALTER COLUMN id SET DEFAULT nextval('operaciones.solicitud_pago_detalle_id_seq'::regclass);


--
-- Name: solicitud_pago_nota_recepcion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_nota_recepcion ALTER COLUMN id SET DEFAULT nextval('operaciones.solicitud_pago_nota_recepcion_id_seq'::regclass);


--
-- Name: subcategoria_observacion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.subcategoria_observacion ALTER COLUMN id SET DEFAULT nextval('operaciones.subcategoria_observacion_id_seq'::regclass);


--
-- Name: transferencia id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia ALTER COLUMN id SET DEFAULT nextval('operaciones.transferencia_id_seq'::regclass);


--
-- Name: transferencia_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item ALTER COLUMN id SET DEFAULT nextval('operaciones.transferencia_item_id_seq'::regclass);


--
-- Name: venta_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item ALTER COLUMN id SET DEFAULT nextval('operaciones.venta_item_id_seq'::regclass);


--
-- Name: venta_observacion id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion ALTER COLUMN id SET DEFAULT nextval('operaciones.venta_observacion_id_seq'::regclass);


--
-- Name: vuelto id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto ALTER COLUMN id SET DEFAULT nextval('operaciones.vuelto_id_seq'::regclass);


--
-- Name: vuelto_item id; Type: DEFAULT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto_item ALTER COLUMN id SET DEFAULT nextval('operaciones.vuelto_item_id_seq'::regclass);


--
-- Name: cliente id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.cliente ALTER COLUMN id SET DEFAULT nextval('personas.cliente_id_seq'::regclass);


--
-- Name: funcionario id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario ALTER COLUMN id SET DEFAULT nextval('personas.funcionario_id_seq'::regclass);


--
-- Name: grupo_role id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.grupo_role ALTER COLUMN id SET DEFAULT nextval('personas.grupo_privilegio_id_seq'::regclass);


--
-- Name: persona id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.persona ALTER COLUMN id SET DEFAULT nextval('personas.persona_id_seq'::regclass);


--
-- Name: pre_registro_funcionario id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.pre_registro_funcionario ALTER COLUMN id SET DEFAULT nextval('personas.pre_registro_funcionario_id_seq'::regclass);


--
-- Name: proveedor id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor ALTER COLUMN id SET DEFAULT nextval('personas.proveedor_id_seq'::regclass);


--
-- Name: proveedor_dias_visita id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor_dias_visita ALTER COLUMN id SET DEFAULT nextval('personas.proveedor_dias_visita_id_seq'::regclass);


--
-- Name: role id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role ALTER COLUMN id SET DEFAULT nextval('personas.role_id_seq'::regclass);


--
-- Name: usuario id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario ALTER COLUMN id SET DEFAULT nextval('personas.usuario_id_seq'::regclass);


--
-- Name: usuario_grupo id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_grupo ALTER COLUMN id SET DEFAULT nextval('personas.usuario_grupo_id_seq'::regclass);


--
-- Name: usuario_role id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_role ALTER COLUMN id SET DEFAULT nextval('personas.usuario_role_id_seq'::regclass);


--
-- Name: vendedor id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor ALTER COLUMN id SET DEFAULT nextval('personas.vendedor_id_seq'::regclass);


--
-- Name: vendedor_proveedor id; Type: DEFAULT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor_proveedor ALTER COLUMN id SET DEFAULT nextval('personas.vendedor_proveedor_id_seq'::regclass);


--
-- Name: codigo id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo ALTER COLUMN id SET DEFAULT nextval('productos.codigo_id_seq'::regclass);


--
-- Name: codigo_tipo_precio id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo_tipo_precio ALTER COLUMN id SET DEFAULT nextval('productos.codigo_tipo_precio_id_seq'::regclass);


--
-- Name: costo_por_producto id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto ALTER COLUMN id SET DEFAULT nextval('productos.costo_por_producto_id_seq'::regclass);


--
-- Name: familia id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.familia ALTER COLUMN id SET DEFAULT nextval('productos.familia_id_seq'::regclass);


--
-- Name: pdv_categoria id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_categoria ALTER COLUMN id SET DEFAULT nextval('productos.pdv_categoria_id_seq'::regclass);


--
-- Name: pdv_grupo id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupo ALTER COLUMN id SET DEFAULT nextval('productos.pdv_grupo_id_seq'::regclass);


--
-- Name: pdv_grupos_productos id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupos_productos ALTER COLUMN id SET DEFAULT nextval('productos.pdv_grupos_productos_id_seq'::regclass);


--
-- Name: precio_por_sucursal id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal ALTER COLUMN id SET DEFAULT nextval('productos.precio_por_sucursal_id_seq'::regclass);


--
-- Name: presentacion id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.presentacion ALTER COLUMN id SET DEFAULT nextval('productos.presentacion_id_seq'::regclass);


--
-- Name: producto id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto ALTER COLUMN id SET DEFAULT nextval('productos.producto_id_seq'::regclass);


--
-- Name: producto_imagen id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_imagen ALTER COLUMN id SET DEFAULT nextval('productos.imagenes_id_seq'::regclass);


--
-- Name: producto_por_sucursal id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_por_sucursal ALTER COLUMN id SET DEFAULT nextval('productos.productos_por_sucursal_id_seq'::regclass);


--
-- Name: producto_proveedor id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_proveedor ALTER COLUMN id SET DEFAULT nextval('productos.producto_proveedor_id_seq'::regclass);


--
-- Name: subfamilia id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.subfamilia ALTER COLUMN id SET DEFAULT nextval('productos.subfamilia_id_seq'::regclass);


--
-- Name: tipo_precio id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_precio ALTER COLUMN id SET DEFAULT nextval('productos.tipo_precio_id_seq'::regclass);


--
-- Name: tipo_presentacion id; Type: DEFAULT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_presentacion ALTER COLUMN id SET DEFAULT nextval('productos.tipo_presentacion_id_seq'::regclass);


--
-- Name: producto_proveedor id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor ALTER COLUMN id SET DEFAULT nextval('public.producto_proveedor_id_seq'::regclass);


--
-- Name: marca id; Type: DEFAULT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.marca ALTER COLUMN id SET DEFAULT nextval('vehiculos.marca_id_seq'::regclass);


--
-- Name: modelo id; Type: DEFAULT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.modelo ALTER COLUMN id SET DEFAULT nextval('vehiculos.modelo_id_seq'::regclass);


--
-- Name: tipo_vehiculo id; Type: DEFAULT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.tipo_vehiculo ALTER COLUMN id SET DEFAULT nextval('vehiculos.tipo_vehiculo_id_seq'::regclass);


--
-- Name: vehiculo id; Type: DEFAULT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo ALTER COLUMN id SET DEFAULT nextval('vehiculos.vehiculo_id_seq'::regclass);


--
-- Name: vehiculo_sucursal id; Type: DEFAULT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo_sucursal ALTER COLUMN id SET DEFAULT nextval('vehiculos.vehiculo_sucursal_id_seq'::regclass);


--
-- Name: autorizacion autorizacion_pk; Type: CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.autorizacion
    ADD CONSTRAINT autorizacion_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: horario horario_pkey; Type: CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.horario
    ADD CONSTRAINT horario_pkey PRIMARY KEY (id);


--
-- Name: jornada pk_jornada; Type: CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT pk_jornada PRIMARY KEY (id, sucursal_id);


--
-- Name: marcacion pk_marcacion; Type: CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.marcacion
    ADD CONSTRAINT pk_marcacion PRIMARY KEY (id, sucursal_id);


--
-- Name: actualizacion actualizacion_current_version_key; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.actualizacion
    ADD CONSTRAINT actualizacion_current_version_key UNIQUE (current_version);


--
-- Name: actualizacion actualizacion_pk; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.actualizacion
    ADD CONSTRAINT actualizacion_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: error_log error_log_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.error_log
    ADD CONSTRAINT error_log_pkey PRIMARY KEY (id);


--
-- Name: inicio_sesion inicio_sesion_pk; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.inicio_sesion
    ADD CONSTRAINT inicio_sesion_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: local local_pk; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.local
    ADD CONSTRAINT local_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: local local_un; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.local
    ADD CONSTRAINT local_un UNIQUE (sucursal_id);


--
-- Name: modificacion_detalle modificacion_detalle_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_detalle
    ADD CONSTRAINT modificacion_detalle_pkey PRIMARY KEY (id);


--
-- Name: modificacion_registro modificacion_registro_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_registro
    ADD CONSTRAINT modificacion_registro_pkey PRIMARY KEY (id);


--
-- Name: notificacion_comentario notificacion_comentario_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_comentario
    ADD CONSTRAINT notificacion_comentario_pkey PRIMARY KEY (id);


--
-- Name: notificacion_destinatario notificacion_destinatario_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_destinatario
    ADD CONSTRAINT notificacion_destinatario_pkey PRIMARY KEY (id);


--
-- Name: notificacion_envio_log notificacion_envio_log_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_envio_log
    ADD CONSTRAINT notificacion_envio_log_pkey PRIMARY KEY (id);


--
-- Name: notificacion notificacion_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion
    ADD CONSTRAINT notificacion_pkey PRIMARY KEY (id);


--
-- Name: notificacion_preferencia_usuario notificacion_preferencia_usuario_pk; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_preferencia_usuario
    ADD CONSTRAINT notificacion_preferencia_usuario_pk PRIMARY KEY (id);


--
-- Name: notificacion_tipo_role notificacion_tipo_role_pk; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_tipo_role
    ADD CONSTRAINT notificacion_tipo_role_pk PRIMARY KEY (id);


--
-- Name: rabbitmq_msg rabbitmq_msg_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.rabbitmq_msg
    ADD CONSTRAINT rabbitmq_msg_pkey PRIMARY KEY (id);


--
-- Name: replication_table replication_table_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_table
    ADD CONSTRAINT replication_table_pkey PRIMARY KEY (id);


--
-- Name: replication_table replication_table_table_name_key; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_table
    ADD CONSTRAINT replication_table_table_name_key UNIQUE (table_name);


--
-- Name: replication_test replication_test_pkey; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_test
    ADD CONSTRAINT replication_test_pkey PRIMARY KEY (id);


--
-- Name: notificacion_preferencia_usuario uk_notif_pref_usu_usuario_tipo; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_preferencia_usuario
    ADD CONSTRAINT uk_notif_pref_usu_usuario_tipo UNIQUE (usuario_id, tipo_notificacion);


--
-- Name: notificacion_destinatario uk_notificacion_destinatario; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_destinatario
    ADD CONSTRAINT uk_notificacion_destinatario UNIQUE (notificacion_id, usuario_id);


--
-- Name: notificacion_tipo_role uk_notificacion_tipo_role; Type: CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_tipo_role
    ADD CONSTRAINT uk_notificacion_tipo_role UNIQUE (role_id, tipo_notificacion);


--
-- Name: cargo cargo_pk; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.cargo
    ADD CONSTRAINT cargo_pk PRIMARY KEY (id);


--
-- Name: configuracion_general configuracion_general_pk; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.configuracion_general
    ADD CONSTRAINT configuracion_general_pk PRIMARY KEY (id);


--
-- Name: punto_de_venta punto_de_venta_pk; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.punto_de_venta
    ADD CONSTRAINT punto_de_venta_pk PRIMARY KEY (id);


--
-- Name: sector sector_pk; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sector
    ADD CONSTRAINT sector_pk PRIMARY KEY (id);


--
-- Name: sector sector_unique; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sector
    ADD CONSTRAINT sector_unique UNIQUE (sucursal_id, descripcion);


--
-- Name: sucursal sucursal_pk; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sucursal
    ADD CONSTRAINT sucursal_pk PRIMARY KEY (id);


--
-- Name: zona zona_pkey; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.zona
    ADD CONSTRAINT zona_pkey PRIMARY KEY (id);


--
-- Name: zona zona_unique; Type: CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.zona
    ADD CONSTRAINT zona_unique UNIQUE (sector_id, descripcion);


--
-- Name: equipo equipo_pkey; Type: CONSTRAINT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.equipo
    ADD CONSTRAINT equipo_pkey PRIMARY KEY (id);


--
-- Name: tipo_equipo tipo_equipo_pkey; Type: CONSTRAINT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.tipo_equipo
    ADD CONSTRAINT tipo_equipo_pkey PRIMARY KEY (id);


--
-- Name: banco banco_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.banco
    ADD CONSTRAINT banco_pkey PRIMARY KEY (id);


--
-- Name: caja_categoria_observacion caja_categoria_observacion_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_categoria_observacion
    ADD CONSTRAINT caja_categoria_observacion_pk PRIMARY KEY (id);


--
-- Name: caja_categoria_observacion caja_categoria_observacion_unique; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_categoria_observacion
    ADD CONSTRAINT caja_categoria_observacion_unique UNIQUE (descripcion);


--
-- Name: caja_motivo_observacion caja_motivo_observacion_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_motivo_observacion
    ADD CONSTRAINT caja_motivo_observacion_pk PRIMARY KEY (id);


--
-- Name: caja_motivo_observacion caja_motivo_observacion_unique; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_motivo_observacion
    ADD CONSTRAINT caja_motivo_observacion_unique UNIQUE (descripcion);


--
-- Name: caja_observacion caja_observacion_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion
    ADD CONSTRAINT caja_observacion_pk PRIMARY KEY (id);


--
-- Name: caja_subcategoria_observacion caja_subcategoria_observacion_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_subcategoria_observacion
    ADD CONSTRAINT caja_subcategoria_observacion_pk PRIMARY KEY (id);


--
-- Name: caja_subcategoria_observacion caja_subcategoria_observacion_unique; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_subcategoria_observacion
    ADD CONSTRAINT caja_subcategoria_observacion_unique UNIQUE (descripcion);


--
-- Name: cambio_caja cambio_caja_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT cambio_caja_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: cambio cambio_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio
    ADD CONSTRAINT cambio_pkey PRIMARY KEY (id);


--
-- Name: cheque cheque_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque
    ADD CONSTRAINT cheque_pk PRIMARY KEY (id);


--
-- Name: chequera chequera_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.chequera
    ADD CONSTRAINT chequera_pk PRIMARY KEY (id);


--
-- Name: conteo_moneda conteo_moneda_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo_moneda
    ADD CONSTRAINT conteo_moneda_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: conteo conteo_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo
    ADD CONSTRAINT conteo_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: cuenta_bancaria cuenta_bancaria_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria
    ADD CONSTRAINT cuenta_bancaria_pkey PRIMARY KEY (id);


--
-- Name: documento_electronico documento_electronico_cdc_key; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT documento_electronico_cdc_key UNIQUE (cdc);


--
-- Name: documento_electronico documento_electronico_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT documento_electronico_pkey PRIMARY KEY (id, sucursal_id);


--
-- Name: documento documento_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento
    ADD CONSTRAINT documento_pkey PRIMARY KEY (id);


--
-- Name: documento documento_un; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento
    ADD CONSTRAINT documento_un UNIQUE (descripcion);


--
-- Name: evento_cancelacion_de evento_cancelacion_de_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_cancelacion_de
    ADD CONSTRAINT evento_cancelacion_de_pkey PRIMARY KEY (id, sucursal_id);


--
-- Name: evento_inutilizacion_de_documento_electronico evento_inutilizacion_de_documento_electronico_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico
    ADD CONSTRAINT evento_inutilizacion_de_documento_electronico_pkey PRIMARY KEY (id);


--
-- Name: evento_inutilizacion_de evento_inutilizacion_de_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de
    ADD CONSTRAINT evento_inutilizacion_de_pkey PRIMARY KEY (id, sucursal_id);


--
-- Name: evento_nominacion_de evento_nominacion_de_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de
    ADD CONSTRAINT evento_nominacion_de_pkey PRIMARY KEY (id, sucursal_id);


--
-- Name: factura_legal_item factura_legal_item_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT factura_legal_item_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: factura_legal factura_legal_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: forma_pago forma_pago_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.forma_pago
    ADD CONSTRAINT forma_pago_pkey PRIMARY KEY (id);


--
-- Name: gasto_detalle gasto_detalle_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto_detalle
    ADD CONSTRAINT gasto_detalle_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: gasto gasto_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: lote_de lote_de_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.lote_de
    ADD CONSTRAINT lote_de_pkey PRIMARY KEY (id, sucursal_id);


--
-- Name: maletin maletin_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.maletin
    ADD CONSTRAINT maletin_pkey PRIMARY KEY (id);


--
-- Name: maletin maletin_un_descripcion; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.maletin
    ADD CONSTRAINT maletin_un_descripcion UNIQUE (descripcion);


--
-- Name: moneda_billetes moneda_billetes_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda_billetes
    ADD CONSTRAINT moneda_billetes_pkey PRIMARY KEY (id);


--
-- Name: moneda moneda_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda
    ADD CONSTRAINT moneda_pkey PRIMARY KEY (id);


--
-- Name: movimiento_caja movimiento_caja_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja
    ADD CONSTRAINT movimiento_caja_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: movimiento_personas movimiento_personas_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_personas
    ADD CONSTRAINT movimiento_personas_pkey PRIMARY KEY (id);


--
-- Name: pdv_caja pdv_caja_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: retiro_detalle retiro_detalle_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro_detalle
    ADD CONSTRAINT retiro_detalle_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: retiro retiro_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro
    ADD CONSTRAINT retiro_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: sencillo_detalle sencillo_detalle_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle
    ADD CONSTRAINT sencillo_detalle_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: sencillo sencillo_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo
    ADD CONSTRAINT sencillo_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: timbrado_detalle timbrado_detalle_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado_detalle
    ADD CONSTRAINT timbrado_detalle_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: timbrado timbrado_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado
    ADD CONSTRAINT timbrado_pkey PRIMARY KEY (id);


--
-- Name: tipo_gasto tipo_gasto_pkey; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.tipo_gasto
    ADD CONSTRAINT tipo_gasto_pkey PRIMARY KEY (id);


--
-- Name: documento_electronico uk_documento_electronico_factura_legal; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT uk_documento_electronico_factura_legal UNIQUE (factura_legal_id, sucursal_id);


--
-- Name: evento_inutilizacion_de_documento_electronico uk_evento_inutilizacion_de_documento; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico
    ADD CONSTRAINT uk_evento_inutilizacion_de_documento UNIQUE (evento_inutilizacion_de_id, evento_inutilizacion_de_sucursal_id, documento_electronico_id, documento_electronico_sucursal_id);


--
-- Name: venta_credito_cuota venta_credito_cuota_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito_cuota
    ADD CONSTRAINT venta_credito_cuota_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: venta_credito venta_credito_pk; Type: CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: barrio barrio_pkey; Type: CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.barrio
    ADD CONSTRAINT barrio_pkey PRIMARY KEY (id);


--
-- Name: ciudad ciudad_pkey; Type: CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.ciudad
    ADD CONSTRAINT ciudad_pkey PRIMARY KEY (id);


--
-- Name: contacto contacto_pkey; Type: CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.contacto
    ADD CONSTRAINT contacto_pkey PRIMARY KEY (id);


--
-- Name: pais pais_pkey; Type: CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.pais
    ADD CONSTRAINT pais_pkey PRIMARY KEY (id);


--
-- Name: imagen_master imagen_master_pkey; Type: CONSTRAINT; Schema: media; Owner: -
--

ALTER TABLE ONLY media.imagen_master
    ADD CONSTRAINT imagen_master_pkey PRIMARY KEY (id);


--
-- Name: categoria_observacion categoria_observacion_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.categoria_observacion
    ADD CONSTRAINT categoria_observacion_pk PRIMARY KEY (id);


--
-- Name: categoria_observacion categoria_observacion_unique; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.categoria_observacion
    ADD CONSTRAINT categoria_observacion_unique UNIQUE (descripcion);


--
-- Name: cobro_detalle cobro_detalle_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: cobro cobro_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro
    ADD CONSTRAINT cobro_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: compra_item compra_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_pkey PRIMARY KEY (id);


--
-- Name: compra compra_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_pkey PRIMARY KEY (id);


--
-- Name: constancia_de_recepcion_item constancia_de_recepcion_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion_item
    ADD CONSTRAINT constancia_de_recepcion_item_pkey PRIMARY KEY (id);


--
-- Name: constancia_de_recepcion constancia_de_recepcion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion
    ADD CONSTRAINT constancia_de_recepcion_pkey PRIMARY KEY (id);


--
-- Name: delivery delivery_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: devolucion_item devolucion_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion_item
    ADD CONSTRAINT devolucion_item_pkey PRIMARY KEY (id);


--
-- Name: devolucion devolucion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion
    ADD CONSTRAINT devolucion_pkey PRIMARY KEY (id);


--
-- Name: entrada_item entrada_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada_item
    ADD CONSTRAINT entrada_item_pkey PRIMARY KEY (id);


--
-- Name: entrada entrada_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada
    ADD CONSTRAINT entrada_pkey PRIMARY KEY (id);


--
-- Name: inventario inventario_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario
    ADD CONSTRAINT inventario_pkey PRIMARY KEY (id);


--
-- Name: inventario_producto_item inventario_producto_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item
    ADD CONSTRAINT inventario_producto_item_pkey PRIMARY KEY (id);


--
-- Name: inventario_producto inventario_producto_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto
    ADD CONSTRAINT inventario_producto_pkey PRIMARY KEY (id);


--
-- Name: inventario_producto inventario_producto_un; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto
    ADD CONSTRAINT inventario_producto_un UNIQUE (inventario_id, zona_id);


--
-- Name: motivo_diferencia_pedido motivo_diferencia_pedido_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_diferencia_pedido
    ADD CONSTRAINT motivo_diferencia_pedido_pkey PRIMARY KEY (id);


--
-- Name: motivo_observacion motivo_observacion_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_observacion
    ADD CONSTRAINT motivo_observacion_pk PRIMARY KEY (id);


--
-- Name: motivo_observacion motivo_observacion_unique; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_observacion
    ADD CONSTRAINT motivo_observacion_unique UNIQUE (descripcion);


--
-- Name: movimiento_stock movimiento_stock_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.movimiento_stock
    ADD CONSTRAINT movimiento_stock_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: necesidad_item necesidad_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad_item
    ADD CONSTRAINT necesidad_item_pkey PRIMARY KEY (id);


--
-- Name: necesidad necesidad_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad
    ADD CONSTRAINT necesidad_pkey PRIMARY KEY (id);


--
-- Name: nota_pedido nota_pedido_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_pedido
    ADD CONSTRAINT nota_pedido_pkey PRIMARY KEY (id);


--
-- Name: nota_recepcion_item_distribucion nota_recepcion_item_distribucion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion
    ADD CONSTRAINT nota_recepcion_item_distribucion_pkey PRIMARY KEY (id);


--
-- Name: nota_recepcion_item nota_recepcion_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item
    ADD CONSTRAINT nota_recepcion_item_pkey PRIMARY KEY (id);


--
-- Name: nota_recepcion nota_recepcion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT nota_recepcion_pkey PRIMARY KEY (id);


--
-- Name: nota_recepcion nota_recepcion_unique; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT nota_recepcion_unique UNIQUE (pedido_id, numero);


--
-- Name: pago_detalle_cuota pago_detalle_cuota_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle_cuota
    ADD CONSTRAINT pago_detalle_cuota_pk PRIMARY KEY (id);


--
-- Name: pago_detalle pago_detalle_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_pk PRIMARY KEY (id);


--
-- Name: pago pago_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago
    ADD CONSTRAINT pago_pk PRIMARY KEY (id);


--
-- Name: pedido_fecha_entrega pedido_fecha_entrega_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_fecha_entrega
    ADD CONSTRAINT pedido_fecha_entrega_pkey PRIMARY KEY (id);


--
-- Name: pedido_fecha_entrega pedido_fecha_entrega_un; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_fecha_entrega
    ADD CONSTRAINT pedido_fecha_entrega_un UNIQUE (pedido_id, fecha_entrega);


--
-- Name: pedido_item_distribucion pedido_item_distribucion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion
    ADD CONSTRAINT pedido_item_distribucion_pkey PRIMARY KEY (id);


--
-- Name: pedido_item pedido_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item
    ADD CONSTRAINT pedido_item_pkey PRIMARY KEY (id);


--
-- Name: pedido pedido_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido
    ADD CONSTRAINT pedido_pkey PRIMARY KEY (id);


--
-- Name: pedido_sucursal_entrega pedido_sucursal_entrega_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega
    ADD CONSTRAINT pedido_sucursal_entrega_pkey PRIMARY KEY (id);


--
-- Name: pedido_sucursal_entrega pedido_sucursal_entrega_un; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega
    ADD CONSTRAINT pedido_sucursal_entrega_un UNIQUE (pedido_id, sucursal_id);


--
-- Name: pedido_sucursal_influencia pedido_sucursal_influencia_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia
    ADD CONSTRAINT pedido_sucursal_influencia_pkey PRIMARY KEY (id);


--
-- Name: pedido_sucursal_influencia pedido_sucursal_influencia_un; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia
    ADD CONSTRAINT pedido_sucursal_influencia_un UNIQUE (pedido_id, sucursal_id);


--
-- Name: precio_delivery precio_delivery_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.precio_delivery
    ADD CONSTRAINT precio_delivery_pkey PRIMARY KEY (id);


--
-- Name: proceso_etapa proceso_etapa_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.proceso_etapa
    ADD CONSTRAINT proceso_etapa_pkey PRIMARY KEY (id);


--
-- Name: producto_vencimiento producto_vencimiento_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento
    ADD CONSTRAINT producto_vencimiento_pkey PRIMARY KEY (id);


--
-- Name: programar_precio programar_precio_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.programar_precio
    ADD CONSTRAINT programar_precio_pkey PRIMARY KEY (id);


--
-- Name: recepcion_costo_adicional recepcion_costo_adicional_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_costo_adicional
    ADD CONSTRAINT recepcion_costo_adicional_pkey PRIMARY KEY (id);


--
-- Name: recepcion_mercaderia_item recepcion_mercaderia_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT recepcion_mercaderia_item_pkey PRIMARY KEY (id);


--
-- Name: recepcion_mercaderia_item_variacion recepcion_mercaderia_item_variacion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item_variacion
    ADD CONSTRAINT recepcion_mercaderia_item_variacion_pkey PRIMARY KEY (id);


--
-- Name: recepcion_mercaderia_nota recepcion_mercaderia_nota_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_nota
    ADD CONSTRAINT recepcion_mercaderia_nota_pkey PRIMARY KEY (id);


--
-- Name: recepcion_mercaderia recepcion_mercaderia_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia
    ADD CONSTRAINT recepcion_mercaderia_pkey PRIMARY KEY (id);


--
-- Name: salida_item salida_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida_item
    ADD CONSTRAINT salida_item_pkey PRIMARY KEY (id);


--
-- Name: salida salida_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida
    ADD CONSTRAINT salida_pkey PRIMARY KEY (id);


--
-- Name: solicitud_pago_detalle solicitud_pago_detalle_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_detalle
    ADD CONSTRAINT solicitud_pago_detalle_pkey PRIMARY KEY (id);


--
-- Name: solicitud_pago_nota_recepcion solicitud_pago_nota_recepcion_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_nota_recepcion
    ADD CONSTRAINT solicitud_pago_nota_recepcion_pkey PRIMARY KEY (id);


--
-- Name: solicitud_pago solicitud_pago_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_pk PRIMARY KEY (id);


--
-- Name: stock_por_producto_sucursal stock_por_producto_sucursal_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.stock_por_producto_sucursal
    ADD CONSTRAINT stock_por_producto_sucursal_pk PRIMARY KEY (producto_id, sucursal_id);


--
-- Name: subcategoria_observacion subcategoria_observacion_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.subcategoria_observacion
    ADD CONSTRAINT subcategoria_observacion_pk PRIMARY KEY (id);


--
-- Name: subcategoria_observacion subcategoria_observacion_unique; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.subcategoria_observacion
    ADD CONSTRAINT subcategoria_observacion_unique UNIQUE (descripcion);


--
-- Name: transferencia_item transferencia_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_pkey PRIMARY KEY (id);


--
-- Name: transferencia transferencia_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_pkey PRIMARY KEY (id);


--
-- Name: pedido_item_distribucion uk_distribucion_item_sucursales; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion
    ADD CONSTRAINT uk_distribucion_item_sucursales UNIQUE (pedido_item_id, sucursal_influencia_id, sucursal_entrega_id);


--
-- Name: proceso_etapa uk_proceso_etapa_pedido_tipo; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.proceso_etapa
    ADD CONSTRAINT uk_proceso_etapa_pedido_tipo UNIQUE (pedido_id, tipo_etapa);


--
-- Name: recepcion_mercaderia_nota uk_recepcion_nota; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_nota
    ADD CONSTRAINT uk_recepcion_nota UNIQUE (recepcion_mercaderia_id, nota_recepcion_id);


--
-- Name: solicitud_pago_nota_recepcion uk_solicitud_nota; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_nota_recepcion
    ADD CONSTRAINT uk_solicitud_nota UNIQUE (solicitud_pago_id, nota_recepcion_id);


--
-- Name: venta_item venta_item_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item
    ADD CONSTRAINT venta_item_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: venta_observacion venta_observacion_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion
    ADD CONSTRAINT venta_observacion_pk PRIMARY KEY (id);


--
-- Name: venta venta_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: vuelto_item vuelto_item_pkey; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto_item
    ADD CONSTRAINT vuelto_item_pkey PRIMARY KEY (id);


--
-- Name: vuelto vuelto_pk; Type: CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto
    ADD CONSTRAINT vuelto_pk PRIMARY KEY (id, sucursal_id);


--
-- Name: cliente cliente_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.cliente
    ADD CONSTRAINT cliente_pkey PRIMARY KEY (id);


--
-- Name: funcionario funcionario_pk; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_pk PRIMARY KEY (id);


--
-- Name: funcionario funcionario_un_persona; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_un_persona UNIQUE (persona_id);


--
-- Name: grupo_role grupo_privilegio_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.grupo_role
    ADD CONSTRAINT grupo_privilegio_pkey PRIMARY KEY (id);


--
-- Name: persona persona_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.persona
    ADD CONSTRAINT persona_pkey PRIMARY KEY (id);


--
-- Name: persona persona_un_documento; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.persona
    ADD CONSTRAINT persona_un_documento UNIQUE (documento);


--
-- Name: pre_registro_funcionario pre_registro_funcionario_documento_key; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.pre_registro_funcionario
    ADD CONSTRAINT pre_registro_funcionario_documento_key UNIQUE (documento);


--
-- Name: pre_registro_funcionario pre_registro_funcionario_email_key; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.pre_registro_funcionario
    ADD CONSTRAINT pre_registro_funcionario_email_key UNIQUE (email);


--
-- Name: pre_registro_funcionario pre_registro_funcionario_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.pre_registro_funcionario
    ADD CONSTRAINT pre_registro_funcionario_pkey PRIMARY KEY (id);


--
-- Name: proveedor_dias_visita proveedor_dias_visita_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor_dias_visita
    ADD CONSTRAINT proveedor_dias_visita_pkey PRIMARY KEY (id);


--
-- Name: proveedor proveedor_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor
    ADD CONSTRAINT proveedor_pkey PRIMARY KEY (id);


--
-- Name: proveedor proveedor_un; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor
    ADD CONSTRAINT proveedor_un UNIQUE (persona_id);


--
-- Name: role role_grupo_role_un; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role
    ADD CONSTRAINT role_grupo_role_un UNIQUE (id, grupo_role_id);


--
-- Name: role role_nombre_un; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role
    ADD CONSTRAINT role_nombre_un UNIQUE (nombre);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: usuario_grupo usuario_grupo_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_grupo
    ADD CONSTRAINT usuario_grupo_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_pk; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario
    ADD CONSTRAINT usuario_pk PRIMARY KEY (id);


--
-- Name: usuario_role usuario_role_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_role
    ADD CONSTRAINT usuario_role_pkey PRIMARY KEY (id);


--
-- Name: usuario usuario_un_nickname; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario
    ADD CONSTRAINT usuario_un_nickname UNIQUE (nickname);


--
-- Name: usuario usuario_un_persona; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario
    ADD CONSTRAINT usuario_un_persona UNIQUE (persona_id);


--
-- Name: vendedor vendedor_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor
    ADD CONSTRAINT vendedor_pkey PRIMARY KEY (id);


--
-- Name: vendedor_proveedor vendedor_proveedor_pkey; Type: CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor_proveedor
    ADD CONSTRAINT vendedor_proveedor_pkey PRIMARY KEY (id);


--
-- Name: codigo codigo_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo
    ADD CONSTRAINT codigo_pkey PRIMARY KEY (id);


--
-- Name: codigo_tipo_precio codigo_tipo_precio_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_pkey PRIMARY KEY (id);


--
-- Name: codigo codigo_un; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo
    ADD CONSTRAINT codigo_un UNIQUE (codigo);


--
-- Name: codigo codigo_un_presentacion_principal; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo
    ADD CONSTRAINT codigo_un_presentacion_principal UNIQUE (principal, presentacion_id);


--
-- Name: costo_por_producto costos_por_sucursal_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto
    ADD CONSTRAINT costos_por_sucursal_pkey PRIMARY KEY (id);


--
-- Name: familia familia_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.familia
    ADD CONSTRAINT familia_pkey PRIMARY KEY (id);


--
-- Name: familia familia_unique; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.familia
    ADD CONSTRAINT familia_unique UNIQUE (nombre);


--
-- Name: producto_imagen imagenes_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_imagen
    ADD CONSTRAINT imagenes_pkey PRIMARY KEY (id);


--
-- Name: pdv_categoria pdv_categoria_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_categoria
    ADD CONSTRAINT pdv_categoria_pkey PRIMARY KEY (id);


--
-- Name: pdv_grupo pdv_grupo_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupo
    ADD CONSTRAINT pdv_grupo_pkey PRIMARY KEY (id);


--
-- Name: pdv_grupos_productos pdv_grupos_productos_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupos_productos
    ADD CONSTRAINT pdv_grupos_productos_pkey PRIMARY KEY (id);


--
-- Name: precio_por_sucursal precio_por_sucursal_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal
    ADD CONSTRAINT precio_por_sucursal_pkey PRIMARY KEY (id);


--
-- Name: precio_por_sucursal precio_por_sucursal_un_presentacion_tipo_precio; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal
    ADD CONSTRAINT precio_por_sucursal_un_presentacion_tipo_precio UNIQUE (presentacion_id, tipo_precio_id);


--
-- Name: presentacion presentacion_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.presentacion
    ADD CONSTRAINT presentacion_pkey PRIMARY KEY (id);


--
-- Name: producto producto_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto
    ADD CONSTRAINT producto_pkey PRIMARY KEY (id);


--
-- Name: producto_por_sucursal producto_por_sucursal_pk; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_por_sucursal
    ADD CONSTRAINT producto_por_sucursal_pk PRIMARY KEY (id);


--
-- Name: producto_proveedor producto_proveedor_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_proveedor
    ADD CONSTRAINT producto_proveedor_pkey PRIMARY KEY (id);


--
-- Name: producto producto_un_producto; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto
    ADD CONSTRAINT producto_un_producto UNIQUE (descripcion);


--
-- Name: subfamilia subfamilia_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.subfamilia
    ADD CONSTRAINT subfamilia_pkey PRIMARY KEY (id);


--
-- Name: tipo_precio tipo_precio_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_precio
    ADD CONSTRAINT tipo_precio_pkey PRIMARY KEY (id);


--
-- Name: tipo_presentacion tipo_presentacion_pkey; Type: CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_presentacion
    ADD CONSTRAINT tipo_presentacion_pkey PRIMARY KEY (id);


--
-- Name: producto_proveedor producto_proveedor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.producto_proveedor
    ADD CONSTRAINT producto_proveedor_pkey PRIMARY KEY (id);


--
-- Name: marca marca_pkey; Type: CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.marca
    ADD CONSTRAINT marca_pkey PRIMARY KEY (id);


--
-- Name: modelo modelo_pkey; Type: CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.modelo
    ADD CONSTRAINT modelo_pkey PRIMARY KEY (id);


--
-- Name: tipo_vehiculo tipo_vehiculo_pkey; Type: CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.tipo_vehiculo
    ADD CONSTRAINT tipo_vehiculo_pkey PRIMARY KEY (id);


--
-- Name: vehiculo vehiculo_pkey; Type: CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo
    ADD CONSTRAINT vehiculo_pkey PRIMARY KEY (id);


--
-- Name: vehiculo_sucursal vehiculo_sucursal_pkey; Type: CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo_sucursal
    ADD CONSTRAINT vehiculo_sucursal_pkey PRIMARY KEY (id);


--
-- Name: idx_jornada_fecha; Type: INDEX; Schema: administrativo; Owner: -
--

CREATE INDEX idx_jornada_fecha ON administrativo.jornada USING btree (fecha);


--
-- Name: idx_jornada_usuario; Type: INDEX; Schema: administrativo; Owner: -
--

CREATE INDEX idx_jornada_usuario ON administrativo.jornada USING btree (usuario_id);


--
-- Name: idx_detalle_campo; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_detalle_campo ON configuraciones.modificacion_detalle USING btree (campo_nombre);


--
-- Name: idx_detalle_modificacion; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_detalle_modificacion ON configuraciones.modificacion_detalle USING btree (modificacion_registro_id);


--
-- Name: idx_detalle_sensible; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_detalle_sensible ON configuraciones.modificacion_detalle USING btree (es_campo_sensible) WHERE (es_campo_sensible = true);


--
-- Name: idx_modificacion_activo; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_activo ON configuraciones.modificacion_registro USING btree (activo) WHERE (activo = true);


--
-- Name: idx_modificacion_entidad_completa; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_entidad_completa ON configuraciones.modificacion_registro USING btree (tipo_entidad, entidad_id, entidad_sucursal_id);


--
-- Name: idx_modificacion_entidad_id; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_entidad_id ON configuraciones.modificacion_registro USING btree (entidad_id, tipo_entidad);


--
-- Name: idx_modificacion_fecha; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_fecha ON configuraciones.modificacion_registro USING btree (modificado_en);


--
-- Name: idx_modificacion_sucursal; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_sucursal ON configuraciones.modificacion_registro USING btree (sucursal_id);


--
-- Name: idx_modificacion_tipo_entidad; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_tipo_entidad ON configuraciones.modificacion_registro USING btree (tipo_entidad);


--
-- Name: idx_modificacion_tipo_operacion; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_tipo_operacion ON configuraciones.modificacion_registro USING btree (tipo_operacion);


--
-- Name: idx_modificacion_usuario; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_modificacion_usuario ON configuraciones.modificacion_registro USING btree (usuario_id);


--
-- Name: idx_notif_comentario_creado; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_comentario_creado ON configuraciones.notificacion_comentario USING btree (creado_en);


--
-- Name: idx_notif_comentario_notif_creado; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_comentario_notif_creado ON configuraciones.notificacion_comentario USING btree (notificacion_id, creado_en);


--
-- Name: idx_notif_comentario_notificacion; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_comentario_notificacion ON configuraciones.notificacion_comentario USING btree (notificacion_id);


--
-- Name: idx_notif_comentario_padre; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_comentario_padre ON configuraciones.notificacion_comentario USING btree (comentario_padre_id);


--
-- Name: idx_notif_comentario_usuario; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_comentario_usuario ON configuraciones.notificacion_comentario USING btree (usuario_id);


--
-- Name: idx_notif_dest_leida; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_dest_leida ON configuraciones.notificacion_destinatario USING btree (leida);


--
-- Name: idx_notif_dest_notificacion; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_dest_notificacion ON configuraciones.notificacion_destinatario USING btree (notificacion_id);


--
-- Name: idx_notif_dest_usuario; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_dest_usuario ON configuraciones.notificacion_destinatario USING btree (usuario_id);


--
-- Name: idx_notif_dest_usuario_leida; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_dest_usuario_leida ON configuraciones.notificacion_destinatario USING btree (usuario_id, leida);


--
-- Name: idx_notif_log_estado; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_log_estado ON configuraciones.notificacion_envio_log USING btree (estado_envio);


--
-- Name: idx_notif_log_notificacion; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_log_notificacion ON configuraciones.notificacion_envio_log USING btree (notificacion_id);


--
-- Name: idx_notif_log_usuario; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_log_usuario ON configuraciones.notificacion_envio_log USING btree (usuario_id);


--
-- Name: idx_notif_pref_usu_usuario_id; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_pref_usu_usuario_id ON configuraciones.notificacion_preferencia_usuario USING btree (usuario_id);


--
-- Name: idx_notif_tipo_role_role_id; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notif_tipo_role_role_id ON configuraciones.notificacion_tipo_role USING btree (role_id);


--
-- Name: idx_notificacion_creado_en; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notificacion_creado_en ON configuraciones.notificacion USING btree (creado_en);


--
-- Name: idx_notificacion_estado; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notificacion_estado ON configuraciones.notificacion USING btree (estado);


--
-- Name: idx_notificacion_estado_tablero; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notificacion_estado_tablero ON configuraciones.notificacion USING btree (estado_tablero);


--
-- Name: idx_notificacion_tipo; Type: INDEX; Schema: configuraciones; Owner: -
--

CREATE INDEX idx_notificacion_tipo ON configuraciones.notificacion USING btree (tipo);


--
-- Name: conteo_moneda_conteo_id_idx; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX conteo_moneda_conteo_id_idx ON financiero.conteo_moneda USING btree (conteo_id, sucursal_id);


--
-- Name: idx_documento_electronico_activo; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_documento_electronico_activo ON financiero.documento_electronico USING btree (activo);


--
-- Name: idx_documento_electronico_creado_en; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_documento_electronico_creado_en ON financiero.documento_electronico USING btree (creado_en);


--
-- Name: idx_documento_electronico_estado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_documento_electronico_estado ON financiero.documento_electronico USING btree (estado);


--
-- Name: idx_documento_electronico_fecha_emision; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_documento_electronico_fecha_emision ON financiero.documento_electronico USING btree (fecha_emision);


--
-- Name: idx_documento_electronico_lote_de_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_documento_electronico_lote_de_id ON financiero.documento_electronico USING btree (lote_de_id);


--
-- Name: idx_evento_cancelacion_cdc; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_cancelacion_cdc ON financiero.evento_cancelacion_de USING btree (cdc_documento);


--
-- Name: idx_evento_cancelacion_de_sucursal_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_cancelacion_de_sucursal_id ON financiero.evento_cancelacion_de USING btree (sucursal_id);


--
-- Name: idx_evento_cancelacion_documento; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_cancelacion_documento ON financiero.evento_cancelacion_de USING btree (documento_electronico_id);


--
-- Name: idx_evento_cancelacion_estado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_cancelacion_estado ON financiero.evento_cancelacion_de USING btree (estado);


--
-- Name: idx_evento_inutilizacion_de_creado_en; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_creado_en ON financiero.evento_inutilizacion_de USING btree (creado_en DESC);


--
-- Name: idx_evento_inutilizacion_de_doc_documento; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_doc_documento ON financiero.evento_inutilizacion_de_documento_electronico USING btree (documento_electronico_id, documento_electronico_sucursal_id);


--
-- Name: idx_evento_inutilizacion_de_doc_evento; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_doc_evento ON financiero.evento_inutilizacion_de_documento_electronico USING btree (evento_inutilizacion_de_id, evento_inutilizacion_de_sucursal_id);


--
-- Name: idx_evento_inutilizacion_de_estado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_estado ON financiero.evento_inutilizacion_de USING btree (estado);


--
-- Name: idx_evento_inutilizacion_de_evento_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_evento_id ON financiero.evento_inutilizacion_de USING btree (evento_id);


--
-- Name: idx_evento_inutilizacion_de_sucursal_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_sucursal_id ON financiero.evento_inutilizacion_de USING btree (sucursal_id);


--
-- Name: idx_evento_inutilizacion_de_timbrado_detalle_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_timbrado_detalle_id ON financiero.evento_inutilizacion_de USING btree (timbrado_detalle_id);


--
-- Name: idx_evento_inutilizacion_de_timbrado_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_inutilizacion_de_timbrado_id ON financiero.evento_inutilizacion_de USING btree (timbrado_id);


--
-- Name: idx_evento_nominacion_cdc; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_nominacion_cdc ON financiero.evento_nominacion_de USING btree (cdc_documento);


--
-- Name: idx_evento_nominacion_cliente; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_nominacion_cliente ON financiero.evento_nominacion_de USING btree (cliente_id);


--
-- Name: idx_evento_nominacion_de_sucursal_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_nominacion_de_sucursal_id ON financiero.evento_nominacion_de USING btree (sucursal_id);


--
-- Name: idx_evento_nominacion_documento; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_nominacion_documento ON financiero.evento_nominacion_de USING btree (documento_electronico_id);


--
-- Name: idx_evento_nominacion_estado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_evento_nominacion_estado ON financiero.evento_nominacion_de USING btree (estado);


--
-- Name: idx_factura_legal_item_producto_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_factura_legal_item_producto_id ON financiero.factura_legal_item USING btree (producto_id);


--
-- Name: idx_lote_de_estado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_lote_de_estado ON financiero.lote_de USING btree (estado);


--
-- Name: idx_lote_de_fecha_procesado; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_lote_de_fecha_procesado ON financiero.lote_de USING btree (fecha_procesado);


--
-- Name: idx_lote_de_intentos; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_lote_de_intentos ON financiero.lote_de USING btree (intentos);


--
-- Name: idx_lote_de_sucursal_id; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_lote_de_sucursal_id ON financiero.lote_de USING btree (sucursal_id);


--
-- Name: idx_lote_dte_creado_en; Type: INDEX; Schema: financiero; Owner: -
--

CREATE INDEX idx_lote_dte_creado_en ON financiero.lote_de USING btree (creado_en);


--
-- Name: idx_imagen_master_principal; Type: INDEX; Schema: media; Owner: -
--

CREATE INDEX idx_imagen_master_principal ON media.imagen_master USING btree (principal);


--
-- Name: idx_imagen_master_tipo_ref_id; Type: INDEX; Schema: media; Owner: -
--

CREATE INDEX idx_imagen_master_tipo_ref_id ON media.imagen_master USING btree (tipo_referencia, referencia_id);


--
-- Name: cobro_detalle_cobro_id_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX cobro_detalle_cobro_id_idx ON operaciones.cobro_detalle USING btree (cobro_id, sucursal_id);


--
-- Name: constancia_de_recepcion_codigo_verificacion_key; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE UNIQUE INDEX constancia_de_recepcion_codigo_verificacion_key ON operaciones.constancia_de_recepcion USING btree (codigo_verificacion);


--
-- Name: idx_const_item_const; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_const_item_const ON operaciones.constancia_de_recepcion_item USING btree (constancia_de_recepcion_id);


--
-- Name: idx_const_item_producto; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_const_item_producto ON operaciones.constancia_de_recepcion_item USING btree (producto_id);


--
-- Name: idx_const_recep; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_const_recep ON operaciones.constancia_de_recepcion USING btree (recepcion_mercaderia_id);


--
-- Name: idx_costo_adicional_recepcion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_costo_adicional_recepcion ON operaciones.recepcion_costo_adicional USING btree (recepcion_mercaderia_id);


--
-- Name: idx_devolucion_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_estado ON operaciones.devolucion USING btree (estado);


--
-- Name: idx_devolucion_fecha; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_fecha ON operaciones.devolucion USING btree (fecha);


--
-- Name: idx_devolucion_item_devolucion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_item_devolucion ON operaciones.devolucion_item USING btree (devolucion_id);


--
-- Name: idx_devolucion_item_producto; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_item_producto ON operaciones.devolucion_item USING btree (producto_id);


--
-- Name: idx_devolucion_proveedor; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_proveedor ON operaciones.devolucion USING btree (proveedor_id);


--
-- Name: idx_devolucion_sucursal; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_devolucion_sucursal ON operaciones.devolucion USING btree (sucursal_origen_id);


--
-- Name: idx_distribucion_pedido_item; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_distribucion_pedido_item ON operaciones.pedido_item_distribucion USING btree (pedido_item_id);


--
-- Name: idx_distribucion_sucursal; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_distribucion_sucursal ON operaciones.pedido_item_distribucion USING btree (sucursal_entrega_id);


--
-- Name: idx_distribucion_sucursal_influencia; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_distribucion_sucursal_influencia ON operaciones.pedido_item_distribucion USING btree (sucursal_influencia_id);


--
-- Name: idx_nota_recepcion_es_nota_rechazo; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_es_nota_rechazo ON operaciones.nota_recepcion USING btree (es_nota_rechazo);


--
-- Name: idx_nota_recepcion_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_estado ON operaciones.nota_recepcion USING btree (estado);


--
-- Name: idx_nota_recepcion_item_cantidad; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_cantidad ON operaciones.nota_recepcion_item USING btree (cantidad_en_nota);


--
-- Name: idx_nota_recepcion_item_distribucion_nota_recepcion_item; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_distribucion_nota_recepcion_item ON operaciones.nota_recepcion_item_distribucion USING btree (nota_recepcion_item_id);


--
-- Name: idx_nota_recepcion_item_distribucion_sucursal_entrega; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_distribucion_sucursal_entrega ON operaciones.nota_recepcion_item_distribucion USING btree (sucursal_entrega_id);


--
-- Name: idx_nota_recepcion_item_distribucion_sucursal_influencia; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_distribucion_sucursal_influencia ON operaciones.nota_recepcion_item_distribucion USING btree (sucursal_influencia_id);


--
-- Name: idx_nota_recepcion_item_distribucion_sucursal_influencia_entreg; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_distribucion_sucursal_influencia_entreg ON operaciones.nota_recepcion_item_distribucion USING btree (sucursal_influencia_id, sucursal_entrega_id);


--
-- Name: idx_nota_recepcion_item_distribucion_usuario; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_distribucion_usuario ON operaciones.nota_recepcion_item_distribucion USING btree (usuario_id);


--
-- Name: idx_nota_recepcion_item_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_estado ON operaciones.nota_recepcion_item USING btree (estado);


--
-- Name: idx_nota_recepcion_item_pedido_item_id; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_pedido_item_id ON operaciones.nota_recepcion_item USING btree (pedido_item_id);


--
-- Name: idx_nota_recepcion_item_presentacion_en_nota; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_presentacion_en_nota ON operaciones.nota_recepcion_item USING btree (presentacion_en_nota_id);


--
-- Name: idx_nota_recepcion_item_producto; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_producto ON operaciones.nota_recepcion_item USING btree (producto_id);


--
-- Name: idx_nota_recepcion_item_producto_id; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_item_producto_id ON operaciones.nota_recepcion_item USING btree (producto_id);


--
-- Name: idx_nota_recepcion_moneda; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_nota_recepcion_moneda ON operaciones.nota_recepcion USING btree (moneda_id);


--
-- Name: idx_pago_detalle_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_pago_detalle_estado ON operaciones.pago_detalle USING btree (estado);


--
-- Name: idx_pago_detalle_pago_id; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_pago_detalle_pago_id ON operaciones.pago_detalle USING btree (pago_id);


--
-- Name: idx_proceso_etapa_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_proceso_etapa_estado ON operaciones.proceso_etapa USING btree (estado_etapa);


--
-- Name: idx_proceso_etapa_pedido; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_proceso_etapa_pedido ON operaciones.proceso_etapa USING btree (pedido_id);


--
-- Name: idx_proceso_etapa_tipo; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_proceso_etapa_tipo ON operaciones.proceso_etapa USING btree (tipo_etapa);


--
-- Name: idx_prod_venc_fecha; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_prod_venc_fecha ON operaciones.producto_vencimiento USING btree (fecha_vencimiento);


--
-- Name: idx_prod_venc_producto; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_prod_venc_producto ON operaciones.producto_vencimiento USING btree (producto_id);


--
-- Name: idx_prod_venc_sucursal; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_prod_venc_sucursal ON operaciones.producto_vencimiento USING btree (sucursal_id);


--
-- Name: idx_recepcion_item_producto; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_item_producto ON operaciones.recepcion_mercaderia_item USING btree (producto_id);


--
-- Name: idx_recepcion_item_recepcion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_item_recepcion ON operaciones.recepcion_mercaderia_item USING btree (recepcion_mercaderia_id);


--
-- Name: idx_recepcion_item_sucursal; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_item_sucursal ON operaciones.recepcion_mercaderia_item USING btree (sucursal_entrega_id);


--
-- Name: idx_recepcion_mercaderia_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_estado ON operaciones.recepcion_mercaderia USING btree (estado);


--
-- Name: idx_recepcion_mercaderia_fecha; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_fecha ON operaciones.recepcion_mercaderia USING btree (fecha);


--
-- Name: idx_recepcion_mercaderia_item_estado_verificacion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_estado_verificacion ON operaciones.recepcion_mercaderia_item USING btree (estado_verificacion);


--
-- Name: idx_recepcion_mercaderia_item_nota_recepcion_item_distribucion_; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_nota_recepcion_item_distribucion_ ON operaciones.recepcion_mercaderia_item USING btree (nota_recepcion_item_distribucion_id);


--
-- Name: idx_recepcion_mercaderia_item_presentacion_recibida; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_presentacion_recibida ON operaciones.recepcion_mercaderia_item USING btree (presentacion_recibida_id);


--
-- Name: idx_recepcion_mercaderia_item_usuario_id; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_usuario_id ON operaciones.recepcion_mercaderia_item USING btree (usuario_id);


--
-- Name: idx_recepcion_mercaderia_item_variacion_presentacion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_variacion_presentacion ON operaciones.recepcion_mercaderia_item_variacion USING btree (presentacion_id);


--
-- Name: idx_recepcion_mercaderia_item_variacion_recepcion_item; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_item_variacion_recepcion_item ON operaciones.recepcion_mercaderia_item_variacion USING btree (recepcion_mercaderia_item_id);


--
-- Name: idx_recepcion_mercaderia_proveedor; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_proveedor ON operaciones.recepcion_mercaderia USING btree (proveedor_id);


--
-- Name: idx_recepcion_mercaderia_sucursal; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_mercaderia_sucursal ON operaciones.recepcion_mercaderia USING btree (sucursal_recepcion_id);


--
-- Name: idx_recepcion_nota_nota; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_nota_nota ON operaciones.recepcion_mercaderia_nota USING btree (nota_recepcion_id);


--
-- Name: idx_recepcion_nota_recepcion; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_recepcion_nota_recepcion ON operaciones.recepcion_mercaderia_nota USING btree (recepcion_mercaderia_id);


--
-- Name: idx_solicitud_nota_creado_en; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_nota_creado_en ON operaciones.solicitud_pago_nota_recepcion USING btree (creado_en);


--
-- Name: idx_solicitud_nota_nota; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_nota_nota ON operaciones.solicitud_pago_nota_recepcion USING btree (nota_recepcion_id);


--
-- Name: idx_solicitud_nota_solicitud; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_nota_solicitud ON operaciones.solicitud_pago_nota_recepcion USING btree (solicitud_pago_id);


--
-- Name: idx_solicitud_pago_detalle_forma_pago; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_detalle_forma_pago ON operaciones.solicitud_pago_detalle USING btree (forma_pago_id);


--
-- Name: idx_solicitud_pago_detalle_moneda; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_detalle_moneda ON operaciones.solicitud_pago_detalle USING btree (moneda_id);


--
-- Name: idx_solicitud_pago_detalle_solicitud; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_detalle_solicitud ON operaciones.solicitud_pago_detalle USING btree (solicitud_pago_id);


--
-- Name: idx_solicitud_pago_estado; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_estado ON operaciones.solicitud_pago USING btree (estado);


--
-- Name: idx_solicitud_pago_fecha; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_fecha ON operaciones.solicitud_pago USING btree (fecha_solicitud);


--
-- Name: idx_solicitud_pago_pago_id; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX idx_solicitud_pago_pago_id ON operaciones.solicitud_pago USING btree (pago_id);


--
-- Name: movimiento_stock_producto_id_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX movimiento_stock_producto_id_idx ON operaciones.movimiento_stock USING btree (producto_id, sucursal_id);


--
-- Name: movimiento_stock_sucursal_id_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX movimiento_stock_sucursal_id_idx ON operaciones.movimiento_stock USING btree (sucursal_id, id);


--
-- Name: movimiento_stock_tipo_ref_estado_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX movimiento_stock_tipo_ref_estado_idx ON operaciones.movimiento_stock USING btree (tipo_movimiento, referencia) WHERE (estado = true);


--
-- Name: transferencia_item_transferencia_id_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX transferencia_item_transferencia_id_idx ON operaciones.transferencia_item USING btree (transferencia_id);


--
-- Name: uk_solicitud_pago_numero; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE UNIQUE INDEX uk_solicitud_pago_numero ON operaciones.solicitud_pago USING btree (numero_solicitud);


--
-- Name: venta_caja_id_idx; Type: INDEX; Schema: operaciones; Owner: -
--

CREATE INDEX venta_caja_id_idx ON operaciones.venta USING btree (caja_id, sucursal_id);


--
-- Name: codigo_codigo_idx; Type: INDEX; Schema: productos; Owner: -
--

CREATE INDEX codigo_codigo_idx ON productos.codigo USING btree (codigo);


--
-- Name: costo_por_producto_producto_id_idx; Type: INDEX; Schema: productos; Owner: -
--

CREATE INDEX costo_por_producto_producto_id_idx ON productos.costo_por_producto USING btree (producto_id);


--
-- Name: precio_por_sucursal_presentacion_id_idx; Type: INDEX; Schema: productos; Owner: -
--

CREATE INDEX precio_por_sucursal_presentacion_id_idx ON productos.precio_por_sucursal USING btree (presentacion_id);


--
-- Name: presentacion_producto_id_idx; Type: INDEX; Schema: productos; Owner: -
--

CREATE INDEX presentacion_producto_id_idx ON productos.presentacion USING btree (producto_id);


--
-- Name: stock_por_producto_sucursal delete_after_save; Type: TRIGGER; Schema: operaciones; Owner: -
--

CREATE TRIGGER delete_after_save AFTER INSERT ON operaciones.stock_por_producto_sucursal FOR EACH ROW EXECUTE FUNCTION public.delete_after_record_saved();


--
-- Name: producto producto_insert_trigger; Type: TRIGGER; Schema: productos; Owner: -
--

CREATE TRIGGER producto_insert_trigger AFTER INSERT OR DELETE OR UPDATE ON productos.producto FOR EACH ROW EXECUTE FUNCTION public.notify_error_event();


--
-- Name: horario_dias fk_horario_dias_horario; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.horario_dias
    ADD CONSTRAINT fk_horario_dias_horario FOREIGN KEY (horario_id) REFERENCES administrativo.horario(id);


--
-- Name: jornada fk_jornada_entrada; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT fk_jornada_entrada FOREIGN KEY (entrada_id, entrada_sucursal_id) REFERENCES administrativo.marcacion(id, sucursal_id);


--
-- Name: jornada fk_jornada_entrada_almuerzo; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT fk_jornada_entrada_almuerzo FOREIGN KEY (marcacion_entrada_almuerzo_id, marcacion_entrada_almuerzo_suc_id) REFERENCES administrativo.marcacion(id, sucursal_id);


--
-- Name: jornada fk_jornada_salida; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT fk_jornada_salida FOREIGN KEY (salida_id, salida_sucursal_id) REFERENCES administrativo.marcacion(id, sucursal_id);


--
-- Name: jornada fk_jornada_salida_almuerzo; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT fk_jornada_salida_almuerzo FOREIGN KEY (marcacion_salida_almuerzo_id, marcacion_salida_almuerzo_suc_id) REFERENCES administrativo.marcacion(id, sucursal_id);


--
-- Name: jornada fk_jornada_usuario; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.jornada
    ADD CONSTRAINT fk_jornada_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: marcacion fk_marcacion_sucursal_salida; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.marcacion
    ADD CONSTRAINT fk_marcacion_sucursal_salida FOREIGN KEY (sucursal_salida_id) REFERENCES empresarial.sucursal(id);


--
-- Name: horario horario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: administrativo; Owner: -
--

ALTER TABLE ONLY administrativo.horario
    ADD CONSTRAINT horario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: notificacion_preferencia_usuario fk_notif_pref_usu_usuario; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_preferencia_usuario
    ADD CONSTRAINT fk_notif_pref_usu_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: notificacion_tipo_role fk_notificacion_tipo_role_role; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_tipo_role
    ADD CONSTRAINT fk_notificacion_tipo_role_role FOREIGN KEY (role_id) REFERENCES personas.role(id);


--
-- Name: replication_table fk_replication_table_usuario; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.replication_table
    ADD CONSTRAINT fk_replication_table_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: error_log fk_sucursal; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.error_log
    ADD CONSTRAINT fk_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);


--
-- Name: local local_equipo_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.local
    ADD CONSTRAINT local_equipo_id_fkey FOREIGN KEY (equipo_id) REFERENCES equipos.equipo(id);


--
-- Name: modificacion_detalle modificacion_detalle_modificacion_registro_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_detalle
    ADD CONSTRAINT modificacion_detalle_modificacion_registro_id_fkey FOREIGN KEY (modificacion_registro_id) REFERENCES configuraciones.modificacion_registro(id) ON DELETE CASCADE;


--
-- Name: modificacion_registro modificacion_registro_sucursal_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_registro
    ADD CONSTRAINT modificacion_registro_sucursal_id_fkey FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: modificacion_registro modificacion_registro_usuario_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.modificacion_registro
    ADD CONSTRAINT modificacion_registro_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: notificacion_comentario notificacion_comentario_comentario_padre_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_comentario
    ADD CONSTRAINT notificacion_comentario_comentario_padre_id_fkey FOREIGN KEY (comentario_padre_id) REFERENCES configuraciones.notificacion_comentario(id) ON DELETE CASCADE;


--
-- Name: notificacion_comentario notificacion_comentario_notificacion_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_comentario
    ADD CONSTRAINT notificacion_comentario_notificacion_id_fkey FOREIGN KEY (notificacion_id) REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE;


--
-- Name: notificacion_comentario notificacion_comentario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_comentario
    ADD CONSTRAINT notificacion_comentario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE CASCADE;


--
-- Name: notificacion_destinatario notificacion_destinatario_notificacion_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_destinatario
    ADD CONSTRAINT notificacion_destinatario_notificacion_id_fkey FOREIGN KEY (notificacion_id) REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE;


--
-- Name: notificacion_destinatario notificacion_destinatario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_destinatario
    ADD CONSTRAINT notificacion_destinatario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE CASCADE;


--
-- Name: notificacion_envio_log notificacion_envio_log_notificacion_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_envio_log
    ADD CONSTRAINT notificacion_envio_log_notificacion_id_fkey FOREIGN KEY (notificacion_id) REFERENCES configuraciones.notificacion(id) ON DELETE CASCADE;


--
-- Name: notificacion_envio_log notificacion_envio_log_usuario_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion_envio_log
    ADD CONSTRAINT notificacion_envio_log_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: notificacion notificacion_usuario_creador_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion
    ADD CONSTRAINT notificacion_usuario_creador_id_fkey FOREIGN KEY (usuario_creador_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: notificacion notificacion_verificado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: configuraciones; Owner: -
--

ALTER TABLE ONLY configuraciones.notificacion
    ADD CONSTRAINT notificacion_verificado_por_usuario_id_fkey FOREIGN KEY (verificado_por_usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cargo cargo_supervisado_por_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.cargo
    ADD CONSTRAINT cargo_supervisado_por_fk FOREIGN KEY (supervisado_por_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: cargo cargo_usuario_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.cargo
    ADD CONSTRAINT cargo_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: configuracion_general configuracion_general_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.configuracion_general
    ADD CONSTRAINT configuracion_general_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: punto_de_venta punto_de_venta_sucursal_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.punto_de_venta
    ADD CONSTRAINT punto_de_venta_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: punto_de_venta punto_de_venta_usuario_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.punto_de_venta
    ADD CONSTRAINT punto_de_venta_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: sector sector_sucursal_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sector
    ADD CONSTRAINT sector_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: sector sector_usuario_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sector
    ADD CONSTRAINT sector_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: sucursal sucursal_ciudad_id_fkey; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sucursal
    ADD CONSTRAINT sucursal_ciudad_id_fkey FOREIGN KEY (ciudad_id) REFERENCES general.ciudad(id);


--
-- Name: sucursal sucursal_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.sucursal
    ADD CONSTRAINT sucursal_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: zona zona_sector_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.zona
    ADD CONSTRAINT zona_sector_fk FOREIGN KEY (sector_id) REFERENCES empresarial.sector(id) ON DELETE CASCADE;


--
-- Name: zona zona_usuario_fk; Type: FK CONSTRAINT; Schema: empresarial; Owner: -
--

ALTER TABLE ONLY empresarial.zona
    ADD CONSTRAINT zona_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: equipo equipo_tipo_equipo_id_fkey; Type: FK CONSTRAINT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.equipo
    ADD CONSTRAINT equipo_tipo_equipo_id_fkey FOREIGN KEY (tipo_equipo_id) REFERENCES equipos.equipo(id);


--
-- Name: equipo equipo_tipo_equipo_id_fkey1; Type: FK CONSTRAINT; Schema: equipos; Owner: -
--

ALTER TABLE ONLY equipos.equipo
    ADD CONSTRAINT equipo_tipo_equipo_id_fkey1 FOREIGN KEY (tipo_equipo_id) REFERENCES equipos.tipo_equipo(id);


--
-- Name: banco banco_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.banco
    ADD CONSTRAINT banco_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: caja_categoria_observacion caja_categoria_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_categoria_observacion
    ADD CONSTRAINT caja_categoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: caja_motivo_observacion caja_motivo_observacion_subcategoria_observacion_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_motivo_observacion
    ADD CONSTRAINT caja_motivo_observacion_subcategoria_observacion_fk FOREIGN KEY (caja_subcategoria_id) REFERENCES financiero.caja_subcategoria_observacion(id) ON DELETE SET NULL;


--
-- Name: caja_motivo_observacion caja_motivo_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_motivo_observacion
    ADD CONSTRAINT caja_motivo_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: caja_observacion caja_observacion_caja_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion
    ADD CONSTRAINT caja_observacion_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: caja_observacion caja_observacion_caja_motivo_observacion_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion
    ADD CONSTRAINT caja_observacion_caja_motivo_observacion_fk FOREIGN KEY (caja_motivo_id) REFERENCES financiero.caja_motivo_observacion(id) ON DELETE SET NULL;


--
-- Name: caja_observacion caja_observacion_sucursal_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion
    ADD CONSTRAINT caja_observacion_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: caja_observacion caja_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_observacion
    ADD CONSTRAINT caja_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: caja_subcategoria_observacion caja_subcategoria_observacion_categoria_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_subcategoria_observacion
    ADD CONSTRAINT caja_subcategoria_observacion_categoria_fk FOREIGN KEY (caja_categoria_id) REFERENCES financiero.caja_categoria_observacion(id) ON DELETE SET NULL;


--
-- Name: caja_subcategoria_observacion caja_subcategoria_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.caja_subcategoria_observacion
    ADD CONSTRAINT caja_subcategoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cambio_caja cambio_caja_autorizado_por_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT cambio_caja_autorizado_por_fk FOREIGN KEY (autorizado_por_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: cambio_caja cambio_caja_fk_caja; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT cambio_caja_fk_caja FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: cambio_caja cambio_caja_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT cambio_caja_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cambio cambio_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio
    ADD CONSTRAINT cambio_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cambio cambio_moneda_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio
    ADD CONSTRAINT cambio_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: cheque cheque_chequera_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque
    ADD CONSTRAINT cheque_chequera_fk FOREIGN KEY (chequera_id) REFERENCES financiero.chequera(id) ON DELETE RESTRICT;


--
-- Name: cheque cheque_firmante_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque
    ADD CONSTRAINT cheque_firmante_fk FOREIGN KEY (firmante) REFERENCES personas.persona(id) ON DELETE SET NULL;


--
-- Name: cheque cheque_pago_detalle_cuota_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque
    ADD CONSTRAINT cheque_pago_detalle_cuota_fk FOREIGN KEY (pago_detalle_cuota_id) REFERENCES operaciones.pago_detalle_cuota(id) ON DELETE SET NULL;


--
-- Name: cheque cheque_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cheque
    ADD CONSTRAINT cheque_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: chequera chequera_cuenta_bancaria_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.chequera
    ADD CONSTRAINT chequera_cuenta_bancaria_fk FOREIGN KEY (cuenta_bancaria_id) REFERENCES financiero.cuenta_bancaria(id) ON DELETE RESTRICT;


--
-- Name: chequera chequera_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.chequera
    ADD CONSTRAINT chequera_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: conteo conteo_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo
    ADD CONSTRAINT conteo_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: conteo_moneda conteo_moneda_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo_moneda
    ADD CONSTRAINT conteo_moneda_fk FOREIGN KEY (conteo_id, sucursal_id) REFERENCES financiero.conteo(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: conteo_moneda conteo_moneda_moneda_billetes_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo_moneda
    ADD CONSTRAINT conteo_moneda_moneda_billetes_id_fkey FOREIGN KEY (moneda_billetes_id) REFERENCES financiero.moneda_billetes(id);


--
-- Name: conteo_moneda conteo_moneda_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.conteo_moneda
    ADD CONSTRAINT conteo_moneda_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cuenta_bancaria cuenta_bancaria_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria
    ADD CONSTRAINT cuenta_bancaria_fk FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: cuenta_bancaria cuenta_bancaria_fk__persona; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria
    ADD CONSTRAINT cuenta_bancaria_fk__persona FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: cuenta_bancaria cuenta_bancaria_fk_banco; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria
    ADD CONSTRAINT cuenta_bancaria_fk_banco FOREIGN KEY (banco_id) REFERENCES financiero.banco(id);


--
-- Name: cuenta_bancaria cuenta_bancaria_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cuenta_bancaria
    ADD CONSTRAINT cuenta_bancaria_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: documento_electronico documento_electronico_lote_de_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT documento_electronico_lote_de_fk FOREIGN KEY (lote_de_id, sucursal_id) REFERENCES financiero.lote_de(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: documento_electronico documento_electronico_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT documento_electronico_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: documento documento_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento
    ADD CONSTRAINT documento_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: factura_legal factura_legal__timbrado_detalle_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal__timbrado_detalle_fk FOREIGN KEY (timbrado_detalle_id, sucursal_id) REFERENCES financiero.timbrado_detalle(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: factura_legal factura_legal_caja_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: factura_legal factura_legal_cliente_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal_cliente_fk FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id);


--
-- Name: factura_legal_item factura_legal_item_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT factura_legal_item_fk FOREIGN KEY (factura_legal_id, sucursal_id) REFERENCES financiero.factura_legal(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: factura_legal_item factura_legal_item_presentacion_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT factura_legal_item_presentacion_fk FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id) ON DELETE SET NULL;


--
-- Name: factura_legal_item factura_legal_item_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT factura_legal_item_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: factura_legal_item factura_legal_item_venta_item_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT factura_legal_item_venta_item_fk FOREIGN KEY (venta_item_id, sucursal_id) REFERENCES operaciones.venta_item(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: factura_legal factura_legal_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: factura_legal factura_legal_venta_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal
    ADD CONSTRAINT factura_legal_venta_fk FOREIGN KEY (venta_id, sucursal_id) REFERENCES operaciones.venta(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: documento_electronico fk_documento_electronico_factura_legal; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.documento_electronico
    ADD CONSTRAINT fk_documento_electronico_factura_legal FOREIGN KEY (factura_legal_id, sucursal_id) REFERENCES financiero.factura_legal(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: evento_cancelacion_de fk_evento_cancelacion_de_sucursal; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_cancelacion_de
    ADD CONSTRAINT fk_evento_cancelacion_de_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: evento_cancelacion_de fk_evento_cancelacion_documento; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_cancelacion_de
    ADD CONSTRAINT fk_evento_cancelacion_documento FOREIGN KEY (documento_electronico_id, sucursal_id) REFERENCES financiero.documento_electronico(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: evento_cancelacion_de fk_evento_cancelacion_usuario; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_cancelacion_de
    ADD CONSTRAINT fk_evento_cancelacion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: evento_inutilizacion_de_documento_electronico fk_evento_inutilizacion_de_doc_documento; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico
    ADD CONSTRAINT fk_evento_inutilizacion_de_doc_documento FOREIGN KEY (documento_electronico_id, documento_electronico_sucursal_id) REFERENCES financiero.documento_electronico(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: evento_inutilizacion_de_documento_electronico fk_evento_inutilizacion_de_doc_evento; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico
    ADD CONSTRAINT fk_evento_inutilizacion_de_doc_evento FOREIGN KEY (evento_inutilizacion_de_id, evento_inutilizacion_de_sucursal_id) REFERENCES financiero.evento_inutilizacion_de(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: evento_inutilizacion_de fk_evento_inutilizacion_de_sucursal; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de
    ADD CONSTRAINT fk_evento_inutilizacion_de_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: evento_inutilizacion_de fk_evento_inutilizacion_de_timbrado; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de
    ADD CONSTRAINT fk_evento_inutilizacion_de_timbrado FOREIGN KEY (timbrado_id) REFERENCES financiero.timbrado(id) ON DELETE CASCADE;


--
-- Name: evento_inutilizacion_de fk_evento_inutilizacion_de_timbrado_detalle; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de
    ADD CONSTRAINT fk_evento_inutilizacion_de_timbrado_detalle FOREIGN KEY (timbrado_detalle_id, sucursal_id) REFERENCES financiero.timbrado_detalle(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: evento_inutilizacion_de fk_evento_inutilizacion_de_usuario; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_inutilizacion_de
    ADD CONSTRAINT fk_evento_inutilizacion_de_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: evento_nominacion_de fk_evento_nominacion_cliente; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de
    ADD CONSTRAINT fk_evento_nominacion_cliente FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id) ON DELETE SET NULL;


--
-- Name: evento_nominacion_de fk_evento_nominacion_de_sucursal; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de
    ADD CONSTRAINT fk_evento_nominacion_de_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: evento_nominacion_de fk_evento_nominacion_documento; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de
    ADD CONSTRAINT fk_evento_nominacion_documento FOREIGN KEY (documento_electronico_id, sucursal_id) REFERENCES financiero.documento_electronico(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: evento_nominacion_de fk_evento_nominacion_usuario; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.evento_nominacion_de
    ADD CONSTRAINT fk_evento_nominacion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: factura_legal_item fk_factura_legal_item_producto; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.factura_legal_item
    ADD CONSTRAINT fk_factura_legal_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: lote_de fk_lote_de_sucursal; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.lote_de
    ADD CONSTRAINT fk_lote_de_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: forma_pago forma_pago_cuenta_bancaria_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.forma_pago
    ADD CONSTRAINT forma_pago_cuenta_bancaria_id_fkey FOREIGN KEY (cuenta_bancaria_id) REFERENCES financiero.cuenta_bancaria(id);


--
-- Name: forma_pago forma_pago_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.forma_pago
    ADD CONSTRAINT forma_pago_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: gasto gasto_autorizado_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_autorizado_fk FOREIGN KEY (autorizado_por_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: gasto gasto_caja_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: gasto_detalle gasto_detalle_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto_detalle
    ADD CONSTRAINT gasto_detalle_fk FOREIGN KEY (gasto_id, sucursal_id) REFERENCES financiero.gasto(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: gasto_detalle gasto_detalle_moneda_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto_detalle
    ADD CONSTRAINT gasto_detalle_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: gasto_detalle gasto_detalle_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto_detalle
    ADD CONSTRAINT gasto_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: gasto gasto_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: gasto gasto_responsable_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_responsable_fk FOREIGN KEY (responsable_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: gasto gasto_tipo_gastofk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_tipo_gastofk FOREIGN KEY (tipo_gasto_id) REFERENCES financiero.tipo_gasto(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: gasto gasto_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.gasto
    ADD CONSTRAINT gasto_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: lote_de lote_dte_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.lote_de
    ADD CONSTRAINT lote_dte_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: maletin maletin_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.maletin
    ADD CONSTRAINT maletin_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: maletin maletin_sucursal_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.maletin
    ADD CONSTRAINT maletin_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: moneda_billetes moneda_billetes_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda_billetes
    ADD CONSTRAINT moneda_billetes_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: moneda_billetes moneda_detalle_moneda_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda_billetes
    ADD CONSTRAINT moneda_detalle_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE CASCADE;


--
-- Name: moneda moneda_pais_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda
    ADD CONSTRAINT moneda_pais_id_fkey FOREIGN KEY (pais_id) REFERENCES general.pais(id);


--
-- Name: moneda moneda_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.moneda
    ADD CONSTRAINT moneda_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cambio_caja mov_cambio_cliente_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT mov_cambio_cliente_id_fkey FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id);


--
-- Name: cambio_caja mov_cambio_moneda_compra_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT mov_cambio_moneda_compra_id_fkey FOREIGN KEY (moneda_compra_id) REFERENCES financiero.moneda(id);


--
-- Name: cambio_caja mov_cambio_moneda_venta_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.cambio_caja
    ADD CONSTRAINT mov_cambio_moneda_venta_id_fkey FOREIGN KEY (moneda_venta_id) REFERENCES financiero.moneda(id);


--
-- Name: movimiento_caja movimiento_caja_cambio_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja
    ADD CONSTRAINT movimiento_caja_cambio_id_fkey FOREIGN KEY (cambio_id) REFERENCES financiero.cambio(id);


--
-- Name: movimiento_caja movimiento_caja_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja
    ADD CONSTRAINT movimiento_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id);


--
-- Name: movimiento_caja movimiento_caja_moneda_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja
    ADD CONSTRAINT movimiento_caja_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: movimiento_caja movimiento_caja_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_caja
    ADD CONSTRAINT movimiento_caja_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: movimiento_personas movimiento_personas_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_personas
    ADD CONSTRAINT movimiento_personas_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: movimiento_personas movimiento_personas_persona_id_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.movimiento_personas
    ADD CONSTRAINT movimiento_personas_persona_id_fk FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: pdv_caja pdv_caja_fk_conteo_apertura; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_fk_conteo_apertura FOREIGN KEY (conteo_apertura_id, sucursal_id) REFERENCES financiero.conteo(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: pdv_caja pdv_caja_fk_conteo_cierre; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_fk_conteo_cierre FOREIGN KEY (conteo_cierre_id, sucursal_id) REFERENCES financiero.conteo(id, sucursal_id);


--
-- Name: pdv_caja pdv_caja_maletin_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_maletin_id_fkey FOREIGN KEY (maletin_id) REFERENCES financiero.maletin(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: pdv_caja pdv_caja_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pdv_caja pdv_caja_verificado_por_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.pdv_caja
    ADD CONSTRAINT pdv_caja_verificado_por_fk FOREIGN KEY (verificado_por_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: retiro_detalle retiro_detalle_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro_detalle
    ADD CONSTRAINT retiro_detalle_fk FOREIGN KEY (retiro_id, sucursal_id) REFERENCES financiero.retiro(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: retiro_detalle retiro_detalle_moneda_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro_detalle
    ADD CONSTRAINT retiro_detalle_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: retiro_detalle retiro_detalle_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro_detalle
    ADD CONSTRAINT retiro_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: retiro retiro_entrada_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro
    ADD CONSTRAINT retiro_entrada_fk FOREIGN KEY (caja_entrada_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: retiro retiro_respo_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro
    ADD CONSTRAINT retiro_respo_fk FOREIGN KEY (responsable_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: retiro retiro_salida_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro
    ADD CONSTRAINT retiro_salida_fk FOREIGN KEY (caja_salida_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id);


--
-- Name: retiro retiro_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.retiro
    ADD CONSTRAINT retiro_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: sencillo sencillo_caja_entrada_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo
    ADD CONSTRAINT sencillo_caja_entrada_fk FOREIGN KEY (caja_entrada_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: sencillo sencillo_caja_salida_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo
    ADD CONSTRAINT sencillo_caja_salida_fk FOREIGN KEY (caja_salida_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: sencillo_detalle sencillo_detalle_cambio_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle
    ADD CONSTRAINT sencillo_detalle_cambio_fk FOREIGN KEY (cambio_id) REFERENCES financiero.cambio(id) ON DELETE SET NULL;


--
-- Name: sencillo_detalle sencillo_detalle_moneda_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle
    ADD CONSTRAINT sencillo_detalle_moneda_fk FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE SET NULL;


--
-- Name: sencillo_detalle sencillo_detalle_sencillo_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle
    ADD CONSTRAINT sencillo_detalle_sencillo_fk FOREIGN KEY (sencillo_id, sucursal_id) REFERENCES financiero.sencillo(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: sencillo_detalle sencillo_detalle_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo_detalle
    ADD CONSTRAINT sencillo_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: sencillo sencillo_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo
    ADD CONSTRAINT sencillo_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: sencillo sencillo_respo_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.sencillo
    ADD CONSTRAINT sencillo_respo_fk FOREIGN KEY (responsable_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: timbrado_detalle timbrado_detalle_punto_de_venta_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado_detalle
    ADD CONSTRAINT timbrado_detalle_punto_de_venta_fk FOREIGN KEY (punto_de_venta_id) REFERENCES empresarial.punto_de_venta(id) ON DELETE SET NULL;


--
-- Name: timbrado_detalle timbrado_detalle_timbrado_id_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado_detalle
    ADD CONSTRAINT timbrado_detalle_timbrado_id_fk FOREIGN KEY (timbrado_id) REFERENCES financiero.timbrado(id) ON DELETE CASCADE;


--
-- Name: timbrado_detalle timbrado_detalle_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado_detalle
    ADD CONSTRAINT timbrado_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: timbrado timbrado_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.timbrado
    ADD CONSTRAINT timbrado_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: tipo_gasto tipo_gasto_cargo_s\fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.tipo_gasto
    ADD CONSTRAINT "tipo_gasto_cargo_s\fk" FOREIGN KEY (cargo_id) REFERENCES empresarial.cargo(id) ON DELETE SET NULL;


--
-- Name: tipo_gasto tipo_gasto_clasificacion_gasto_id_fkey; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.tipo_gasto
    ADD CONSTRAINT tipo_gasto_clasificacion_gasto_id_fkey FOREIGN KEY (clasificacion_gasto_id) REFERENCES financiero.tipo_gasto(id) ON DELETE CASCADE;


--
-- Name: tipo_gasto tipo_gasto_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.tipo_gasto
    ADD CONSTRAINT tipo_gasto_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: venta_credito venta_credito_cliente_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_cliente_fk FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id);


--
-- Name: venta_credito venta_credito_cobro_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_cobro_fk FOREIGN KEY (sucursal_cobro_id, cobro_id) REFERENCES operaciones.cobro(id, sucursal_id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: venta_credito_cuota venta_credito_cuota_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito_cuota
    ADD CONSTRAINT venta_credito_cuota_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: venta_credito_cuota venta_credito_cuota_sucursal_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito_cuota
    ADD CONSTRAINT venta_credito_cuota_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: venta_credito_cuota venta_credito_cuota_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito_cuota
    ADD CONSTRAINT venta_credito_cuota_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: venta_credito venta_credito_sucursal_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: venta_credito venta_credito_usuario_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: venta_credito venta_credito_venta_fk; Type: FK CONSTRAINT; Schema: financiero; Owner: -
--

ALTER TABLE ONLY financiero.venta_credito
    ADD CONSTRAINT venta_credito_venta_fk FOREIGN KEY (venta_id, sucursal_id) REFERENCES operaciones.venta(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: barrio barrio_ciudad_id_fkey; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.barrio
    ADD CONSTRAINT barrio_ciudad_id_fkey FOREIGN KEY (ciudad_id) REFERENCES general.ciudad(id);


--
-- Name: barrio barrio_precio_delivery_id_fkey; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.barrio
    ADD CONSTRAINT barrio_precio_delivery_id_fkey FOREIGN KEY (precio_delivery_id) REFERENCES operaciones.precio_delivery(id);


--
-- Name: barrio barrio_usuario_fk; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.barrio
    ADD CONSTRAINT barrio_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: ciudad ciudad_fk; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.ciudad
    ADD CONSTRAINT ciudad_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: ciudad ciudad_pais_id_fkey; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.ciudad
    ADD CONSTRAINT ciudad_pais_id_fkey FOREIGN KEY (pais_id) REFERENCES general.pais(id);


--
-- Name: contacto contacto_fk; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.contacto
    ADD CONSTRAINT contacto_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: contacto contacto_persona_id_fkey; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.contacto
    ADD CONSTRAINT contacto_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: pais pais_fk; Type: FK CONSTRAINT; Schema: general; Owner: -
--

ALTER TABLE ONLY general.pais
    ADD CONSTRAINT pais_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: imagen_master imagen_master_usuario_id_fkey; Type: FK CONSTRAINT; Schema: media; Owner: -
--

ALTER TABLE ONLY media.imagen_master
    ADD CONSTRAINT imagen_master_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: categoria_observacion categoria_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.categoria_observacion
    ADD CONSTRAINT categoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cobro_detalle cobro_detalle_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_fk FOREIGN KEY (cobro_id, sucursal_id) REFERENCES operaciones.cobro(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: cobro_detalle cobro_detalle_fk_forma_pago; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_fk_forma_pago FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id);


--
-- Name: cobro_detalle cobro_detalle_moneda_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: cobro_detalle cobro_detalle_sucursal_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: cobro_detalle cobro_detalle_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro_detalle
    ADD CONSTRAINT cobro_detalle_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cobro cobro_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.cobro
    ADD CONSTRAINT cobro_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: compra compra_contacto_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_contacto_proveedor_id_fkey FOREIGN KEY (contacto_proveedor_id) REFERENCES personas.persona(id);


--
-- Name: compra compra_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id);


--
-- Name: compra_item compra_item_compra_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_compra_id_fkey FOREIGN KEY (compra_id) REFERENCES operaciones.compra(id);


--
-- Name: compra_item compra_item_pedido_item_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_pedido_item_fk FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id);


--
-- Name: compra_item compra_item_presentacion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_presentacion_fk FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: compra_item compra_item_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: compra_item compra_item_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra_item
    ADD CONSTRAINT compra_item_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: compra compra_moneda_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: compra compra_pedido_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_pedido_id_fkey FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id);


--
-- Name: compra compra_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: compra compra_sucu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_sucu_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: compra compra_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.compra
    ADD CONSTRAINT compra_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: delivery delivery_cliente_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_cliente_id_fkey FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id);


--
-- Name: delivery delivery_entregador_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_entregador_fk FOREIGN KEY (entregador_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: delivery delivery_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_fk FOREIGN KEY (barrio_id) REFERENCES general.barrio(id);


--
-- Name: delivery delivery_forma_pago_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_forma_pago_id_fkey FOREIGN KEY (forma_pago_id) REFERENCES operaciones.precio_delivery(id);


--
-- Name: delivery delivery_forma_pago_id_fkey1; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_forma_pago_id_fkey1 FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id);


--
-- Name: delivery delivery_precio_delivery_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_precio_delivery_fk FOREIGN KEY (precio_delivery_id) REFERENCES operaciones.precio_delivery(id) ON DELETE SET NULL;


--
-- Name: delivery delivery_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: delivery delivery_vuelto_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.delivery
    ADD CONSTRAINT delivery_vuelto_fk FOREIGN KEY (vuelto_id, sucursal_id) REFERENCES operaciones.vuelto(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: entrada_item entrada_item_fk_entrada; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada_item
    ADD CONSTRAINT entrada_item_fk_entrada FOREIGN KEY (entrada_id) REFERENCES operaciones.entrada(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: entrada_item entrada_item_fk_presentacion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada_item
    ADD CONSTRAINT entrada_item_fk_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: entrada_item entrada_item_fk_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.entrada_item
    ADD CONSTRAINT entrada_item_fk_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: constancia_de_recepcion_item fk_const_item_const; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion_item
    ADD CONSTRAINT fk_const_item_const FOREIGN KEY (constancia_de_recepcion_id) REFERENCES operaciones.constancia_de_recepcion(id);


--
-- Name: constancia_de_recepcion_item fk_const_item_presentacion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion_item
    ADD CONSTRAINT fk_const_item_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: constancia_de_recepcion_item fk_const_item_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion_item
    ADD CONSTRAINT fk_const_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: constancia_de_recepcion fk_const_proveedor; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion
    ADD CONSTRAINT fk_const_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: constancia_de_recepcion fk_const_recep; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion
    ADD CONSTRAINT fk_const_recep FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id);


--
-- Name: constancia_de_recepcion fk_const_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion
    ADD CONSTRAINT fk_const_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);


--
-- Name: constancia_de_recepcion fk_const_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.constancia_de_recepcion
    ADD CONSTRAINT fk_const_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: recepcion_costo_adicional fk_costo_adicional_moneda; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_costo_adicional
    ADD CONSTRAINT fk_costo_adicional_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: recepcion_costo_adicional fk_costo_adicional_recepcion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_costo_adicional
    ADD CONSTRAINT fk_costo_adicional_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id);


--
-- Name: devolucion_item fk_devolucion_item_devolucion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion_item
    ADD CONSTRAINT fk_devolucion_item_devolucion FOREIGN KEY (devolucion_id) REFERENCES operaciones.devolucion(id);


--
-- Name: devolucion_item fk_devolucion_item_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion_item
    ADD CONSTRAINT fk_devolucion_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: devolucion_item fk_devolucion_item_recepcion_item; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion_item
    ADD CONSTRAINT fk_devolucion_item_recepcion_item FOREIGN KEY (recepcion_mercaderia_item_id) REFERENCES operaciones.recepcion_mercaderia_item(id);


--
-- Name: devolucion fk_devolucion_proveedor; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion
    ADD CONSTRAINT fk_devolucion_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: devolucion fk_devolucion_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion
    ADD CONSTRAINT fk_devolucion_sucursal FOREIGN KEY (sucursal_origen_id) REFERENCES empresarial.sucursal(id);


--
-- Name: devolucion fk_devolucion_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.devolucion
    ADD CONSTRAINT fk_devolucion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: pedido_item_distribucion fk_distribucion_pedido_item; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion
    ADD CONSTRAINT fk_distribucion_pedido_item FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id) ON DELETE CASCADE;


--
-- Name: pedido_item_distribucion fk_distribucion_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion
    ADD CONSTRAINT fk_distribucion_sucursal FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: pedido_item_distribucion fk_distribucion_sucursal_influencia; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item_distribucion
    ADD CONSTRAINT fk_distribucion_sucursal_influencia FOREIGN KEY (sucursal_influencia_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: nota_recepcion_item_distribucion fk_nota_recepcion_item_distribucion_nota_recepcion_item; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion
    ADD CONSTRAINT fk_nota_recepcion_item_distribucion_nota_recepcion_item FOREIGN KEY (nota_recepcion_item_id) REFERENCES operaciones.nota_recepcion_item(id) ON DELETE CASCADE;


--
-- Name: nota_recepcion_item_distribucion fk_nota_recepcion_item_distribucion_sucursal_entrega; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion
    ADD CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_entrega FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: nota_recepcion_item_distribucion fk_nota_recepcion_item_distribucion_sucursal_influencia; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion
    ADD CONSTRAINT fk_nota_recepcion_item_distribucion_sucursal_influencia FOREIGN KEY (sucursal_influencia_id) REFERENCES empresarial.sucursal(id);


--
-- Name: nota_recepcion_item_distribucion fk_nota_recepcion_item_distribucion_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item_distribucion
    ADD CONSTRAINT fk_nota_recepcion_item_distribucion_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: nota_recepcion_item fk_nota_recepcion_item_presentacion_en_nota; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item
    ADD CONSTRAINT fk_nota_recepcion_item_presentacion_en_nota FOREIGN KEY (presentacion_en_nota_id) REFERENCES productos.presentacion(id);


--
-- Name: nota_recepcion_item fk_nota_recepcion_item_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item
    ADD CONSTRAINT fk_nota_recepcion_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: nota_recepcion fk_nota_recepcion_moneda; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT fk_nota_recepcion_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: recepcion_mercaderia_item_variacion fk_presentacion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item_variacion
    ADD CONSTRAINT fk_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: proceso_etapa fk_proceso_etapa_pedido; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.proceso_etapa
    ADD CONSTRAINT fk_proceso_etapa_pedido FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id);


--
-- Name: proceso_etapa fk_proceso_etapa_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.proceso_etapa
    ADD CONSTRAINT fk_proceso_etapa_usuario FOREIGN KEY (usuario_inicio_id) REFERENCES personas.usuario(id);


--
-- Name: producto_vencimiento fk_prod_venc_presentacion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento
    ADD CONSTRAINT fk_prod_venc_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: producto_vencimiento fk_prod_venc_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento
    ADD CONSTRAINT fk_prod_venc_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: producto_vencimiento fk_prod_venc_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento
    ADD CONSTRAINT fk_prod_venc_sucursal FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id);


--
-- Name: producto_vencimiento fk_prod_venc_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.producto_vencimiento
    ADD CONSTRAINT fk_prod_venc_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_item_nota_item; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_item_nota_item FOREIGN KEY (nota_recepcion_item_id) REFERENCES operaciones.nota_recepcion_item(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_item_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_item_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_item_recepcion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_item_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id) ON DELETE CASCADE;


--
-- Name: recepcion_mercaderia_item fk_recepcion_item_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_item_sucursal FOREIGN KEY (sucursal_entrega_id) REFERENCES empresarial.sucursal(id);


--
-- Name: recepcion_mercaderia_item_variacion fk_recepcion_mercaderia_item; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item_variacion
    ADD CONSTRAINT fk_recepcion_mercaderia_item FOREIGN KEY (recepcion_mercaderia_item_id) REFERENCES operaciones.recepcion_mercaderia_item(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_mercaderia_item_nota_recepcion_item_distribucion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_mercaderia_item_nota_recepcion_item_distribucion FOREIGN KEY (nota_recepcion_item_distribucion_id) REFERENCES operaciones.nota_recepcion_item_distribucion(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_mercaderia_item_presentacion_recibida; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_mercaderia_item_presentacion_recibida FOREIGN KEY (presentacion_recibida_id) REFERENCES productos.presentacion(id);


--
-- Name: recepcion_mercaderia_item fk_recepcion_mercaderia_item_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_item
    ADD CONSTRAINT fk_recepcion_mercaderia_item_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: recepcion_mercaderia fk_recepcion_mercaderia_moneda; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia
    ADD CONSTRAINT fk_recepcion_mercaderia_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: recepcion_mercaderia fk_recepcion_mercaderia_proveedor; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia
    ADD CONSTRAINT fk_recepcion_mercaderia_proveedor FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: recepcion_mercaderia fk_recepcion_mercaderia_sucursal; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia
    ADD CONSTRAINT fk_recepcion_mercaderia_sucursal FOREIGN KEY (sucursal_recepcion_id) REFERENCES empresarial.sucursal(id);


--
-- Name: recepcion_mercaderia fk_recepcion_mercaderia_usuario; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia
    ADD CONSTRAINT fk_recepcion_mercaderia_usuario FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: recepcion_mercaderia_nota fk_recepcion_nota_nota; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_nota
    ADD CONSTRAINT fk_recepcion_nota_nota FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id);


--
-- Name: recepcion_mercaderia_nota fk_recepcion_nota_recepcion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.recepcion_mercaderia_nota
    ADD CONSTRAINT fk_recepcion_nota_recepcion FOREIGN KEY (recepcion_mercaderia_id) REFERENCES operaciones.recepcion_mercaderia(id);


--
-- Name: solicitud_pago_nota_recepcion fk_solicitud_nota_nota; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_nota_recepcion
    ADD CONSTRAINT fk_solicitud_nota_nota FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id) ON DELETE CASCADE;


--
-- Name: solicitud_pago_nota_recepcion fk_solicitud_nota_solicitud; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_nota_recepcion
    ADD CONSTRAINT fk_solicitud_nota_solicitud FOREIGN KEY (solicitud_pago_id) REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE;


--
-- Name: solicitud_pago_detalle fk_solicitud_pago_detalle_forma_pago; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_detalle
    ADD CONSTRAINT fk_solicitud_pago_detalle_forma_pago FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id) ON DELETE RESTRICT;


--
-- Name: solicitud_pago_detalle fk_solicitud_pago_detalle_moneda; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_detalle
    ADD CONSTRAINT fk_solicitud_pago_detalle_moneda FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE RESTRICT;


--
-- Name: solicitud_pago_detalle fk_solicitud_pago_detalle_solicitud; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago_detalle
    ADD CONSTRAINT fk_solicitud_pago_detalle_solicitud FOREIGN KEY (solicitud_pago_id) REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE;


--
-- Name: venta fk_venta_delivery; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT fk_venta_delivery FOREIGN KEY (delivery_id, sucursal_id) REFERENCES operaciones.delivery(id, sucursal_id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: inventario_producto inventario_producto_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto
    ADD CONSTRAINT inventario_producto_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: inventario_producto inventario_producto_inventario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto
    ADD CONSTRAINT inventario_producto_inventario_fk FOREIGN KEY (inventario_id) REFERENCES operaciones.inventario(id) ON DELETE CASCADE;


--
-- Name: inventario_producto_item inventario_producto_inventario_producto_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item
    ADD CONSTRAINT inventario_producto_inventario_producto_fk FOREIGN KEY (inventario_producto_id) REFERENCES operaciones.inventario_producto(id) ON DELETE CASCADE;


--
-- Name: inventario_producto_item inventario_producto_item_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item
    ADD CONSTRAINT inventario_producto_item_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: inventario_producto_item inventario_producto_presentacion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item
    ADD CONSTRAINT inventario_producto_presentacion_fk FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: inventario_producto_item inventario_producto_zona_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto_item
    ADD CONSTRAINT inventario_producto_zona_fk FOREIGN KEY (zona_id) REFERENCES empresarial.zona(id);


--
-- Name: inventario_producto inventario_producto_zona_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario_producto
    ADD CONSTRAINT inventario_producto_zona_fk FOREIGN KEY (zona_id) REFERENCES empresarial.zona(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: inventario inventario_suc_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario
    ADD CONSTRAINT inventario_suc_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: inventario inventario_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.inventario
    ADD CONSTRAINT inventario_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: motivo_diferencia_pedido motivo_diferencia_pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_diferencia_pedido
    ADD CONSTRAINT motivo_diferencia_pedido_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: motivo_observacion motivo_observacion_subcategoria_observacion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_observacion
    ADD CONSTRAINT motivo_observacion_subcategoria_observacion_fk FOREIGN KEY (subcategoria_id) REFERENCES operaciones.subcategoria_observacion(id) ON DELETE SET NULL;


--
-- Name: motivo_observacion motivo_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.motivo_observacion
    ADD CONSTRAINT motivo_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: movimiento_stock movimiento_stock_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.movimiento_stock
    ADD CONSTRAINT movimiento_stock_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: movimiento_stock movimientos_stock_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.movimiento_stock
    ADD CONSTRAINT movimientos_stock_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: necesidad_item necesidad_item_necesidad_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad_item
    ADD CONSTRAINT necesidad_item_necesidad_id_fkey FOREIGN KEY (necesidad_id) REFERENCES operaciones.necesidad(id);


--
-- Name: necesidad_item necesidad_item_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.necesidad_item
    ADD CONSTRAINT necesidad_item_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: nota_pedido nota_pedido_pedido_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_pedido
    ADD CONSTRAINT nota_pedido_pedido_id_fkey FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id);


--
-- Name: nota_recepcion nota_recepcion_compra_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT nota_recepcion_compra_fk FOREIGN KEY (compra_id) REFERENCES operaciones.nota_recepcion_item(id);


--
-- Name: nota_recepcion nota_recepcion_documento_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT nota_recepcion_documento_fk FOREIGN KEY (documento_id) REFERENCES financiero.documento(id);


--
-- Name: nota_recepcion_item nota_recepcion_item_nota_recepcion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item
    ADD CONSTRAINT nota_recepcion_item_nota_recepcion_fk FOREIGN KEY (nota_recepcion_id) REFERENCES operaciones.nota_recepcion(id) ON DELETE CASCADE;


--
-- Name: nota_recepcion_item nota_recepcion_item_pedido_item_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion_item
    ADD CONSTRAINT nota_recepcion_item_pedido_item_fk FOREIGN KEY (pedido_item_id) REFERENCES operaciones.pedido_item(id) ON DELETE CASCADE;


--
-- Name: nota_recepcion nota_recepcion_pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.nota_recepcion
    ADD CONSTRAINT nota_recepcion_pedido_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON DELETE CASCADE;


--
-- Name: pago pago_autorizado_por_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago
    ADD CONSTRAINT pago_autorizado_por_fk FOREIGN KEY (autorizado_por) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pago_detalle pago_detalle_caja_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_caja_fk FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE RESTRICT;


--
-- Name: pago_detalle_cuota pago_detalle_cuota_pago_detalle_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle_cuota
    ADD CONSTRAINT pago_detalle_cuota_pago_detalle_fk FOREIGN KEY (pago_detalle_id) REFERENCES operaciones.pago_detalle(id) ON DELETE CASCADE;


--
-- Name: pago_detalle_cuota pago_detalle_cuota_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle_cuota
    ADD CONSTRAINT pago_detalle_cuota_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pago_detalle pago_detalle_forma_pago_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_forma_pago_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id) ON DELETE RESTRICT;


--
-- Name: pago_detalle pago_detalle_moneda_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_moneda_fk FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE RESTRICT;


--
-- Name: pago_detalle pago_detalle_pago_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_pago_fk FOREIGN KEY (pago_id) REFERENCES operaciones.pago(id) ON DELETE CASCADE;


--
-- Name: pago_detalle pago_detalle_sucursal_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: pago_detalle pago_detalle_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago_detalle
    ADD CONSTRAINT pago_detalle_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pago pago_solicitud_pago_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago
    ADD CONSTRAINT pago_solicitud_pago_fk FOREIGN KEY (solicitud_pago_id) REFERENCES operaciones.solicitud_pago(id) ON DELETE CASCADE;


--
-- Name: pago pago_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pago
    ADD CONSTRAINT pago_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pedido_fecha_entrega pedido_fecha_entrega_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_fecha_entrega
    ADD CONSTRAINT pedido_fecha_entrega_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido_fecha_entrega pedido_fecha_entrega_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_fecha_entrega
    ADD CONSTRAINT pedido_fecha_entrega_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL ON DELETE SET NULL;


--
-- Name: pedido pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido
    ADD CONSTRAINT pedido_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id);


--
-- Name: pedido_item pedido_item_pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item
    ADD CONSTRAINT pedido_item_pedido_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido_item pedido_item_presentacion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item
    ADD CONSTRAINT pedido_item_presentacion_fk FOREIGN KEY (presentacion_creacion_id) REFERENCES productos.presentacion(id) ON UPDATE RESTRICT;


--
-- Name: pedido_item pedido_item_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_item
    ADD CONSTRAINT pedido_item_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON UPDATE RESTRICT;


--
-- Name: pedido pedido_moneda_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido
    ADD CONSTRAINT pedido_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: pedido pedido_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido
    ADD CONSTRAINT pedido_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: pedido_sucursal_entrega pedido_sucursal_entrega_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega
    ADD CONSTRAINT pedido_sucursal_entrega_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido_sucursal_entrega pedido_sucursal_entrega_pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega
    ADD CONSTRAINT pedido_sucursal_entrega_pedido_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido_sucursal_entrega pedido_sucursal_entrega_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_entrega
    ADD CONSTRAINT pedido_sucursal_entrega_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL ON DELETE SET NULL;


--
-- Name: pedido_sucursal_influencia pedido_sucursal_influencia_entrega_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia
    ADD CONSTRAINT pedido_sucursal_influencia_entrega_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE SET NULL ON DELETE SET NULL;


--
-- Name: pedido_sucursal_influencia pedido_sucursal_influencia_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia
    ADD CONSTRAINT pedido_sucursal_influencia_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido_sucursal_influencia pedido_sucursal_influencia_pedido_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido_sucursal_influencia
    ADD CONSTRAINT pedido_sucursal_influencia_pedido_fk FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: pedido pedido_vendedor_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.pedido
    ADD CONSTRAINT pedido_vendedor_fk FOREIGN KEY (vendedor_id) REFERENCES personas.vendedor(id);


--
-- Name: precio_delivery precio_delivery_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.precio_delivery
    ADD CONSTRAINT precio_delivery_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: programar_precio programar_precio_precio_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.programar_precio
    ADD CONSTRAINT programar_precio_precio_fk FOREIGN KEY (precio_id) REFERENCES productos.precio_por_sucursal(id) ON DELETE CASCADE;


--
-- Name: salida_item salida_item_fk_1_producto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida_item
    ADD CONSTRAINT salida_item_fk_1_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: salida_item salida_item_fk_presentacion; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida_item
    ADD CONSTRAINT salida_item_fk_presentacion FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id);


--
-- Name: salida_item salida_item_fk_salida; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.salida_item
    ADD CONSTRAINT salida_item_fk_salida FOREIGN KEY (salida_id) REFERENCES operaciones.salida(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: solicitud_pago solicitud_pago_forma_pago_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_forma_pago_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id) ON DELETE SET NULL;


--
-- Name: solicitud_pago solicitud_pago_moneda_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_moneda_fk FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id) ON DELETE RESTRICT;


--
-- Name: solicitud_pago solicitud_pago_pago_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_pago_fk FOREIGN KEY (pago_id) REFERENCES operaciones.pago(id) ON DELETE SET NULL;


--
-- Name: solicitud_pago solicitud_pago_proveedor_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_proveedor_fk FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id) ON DELETE RESTRICT;


--
-- Name: solicitud_pago solicitud_pago_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.solicitud_pago
    ADD CONSTRAINT solicitud_pago_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: stock_por_producto_sucursal stock_por_producto_sucursal_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.stock_por_producto_sucursal
    ADD CONSTRAINT stock_por_producto_sucursal_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON DELETE CASCADE;


--
-- Name: stock_por_producto_sucursal stock_por_producto_sucursal_sucursal_id_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.stock_por_producto_sucursal
    ADD CONSTRAINT stock_por_producto_sucursal_sucursal_id_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: subcategoria_observacion subcategoria_observacion_categoria_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.subcategoria_observacion
    ADD CONSTRAINT subcategoria_observacion_categoria_fk FOREIGN KEY (categoria_id) REFERENCES operaciones.categoria_observacion(id) ON DELETE SET NULL;


--
-- Name: subcategoria_observacion subcategoria_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.subcategoria_observacion
    ADD CONSTRAINT subcategoria_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: transferencia_item transferencia_item_presentacion_1_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_presentacion_1_fk FOREIGN KEY (presentacion_pre_transferencia_id) REFERENCES productos.presentacion(id);


--
-- Name: transferencia_item transferencia_item_presentacion_2_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_presentacion_2_fk FOREIGN KEY (presentacion_preparacion_id) REFERENCES productos.presentacion(id);


--
-- Name: transferencia_item transferencia_item_presentacion_3_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_presentacion_3_fk FOREIGN KEY (presentacion_transporte_id) REFERENCES productos.presentacion(id);


--
-- Name: transferencia_item transferencia_item_presentacion_4_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_presentacion_4_fk FOREIGN KEY (presentacion_recepcion_id) REFERENCES productos.presentacion(id);


--
-- Name: transferencia_item transferencia_item_transferencia_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_transferencia_fk FOREIGN KEY (transferencia_id) REFERENCES operaciones.transferencia(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: transferencia_item transferencia_item_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia_item
    ADD CONSTRAINT transferencia_item_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_suc_destino_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_suc_destino_fk FOREIGN KEY (sucursal_destino_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_suc_origen_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_suc_origen_fk FOREIGN KEY (sucursal_origen_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_usu_2_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_usu_2_fk FOREIGN KEY (usuario_preparacion_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_usu_3fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_usu_3fk FOREIGN KEY (usuario_transporte_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_usu_4_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_usu_4_fk FOREIGN KEY (usuario_recepcion_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: transferencia transferencia_usu_pre_trans_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.transferencia
    ADD CONSTRAINT transferencia_usu_pre_trans_fk FOREIGN KEY (usuario_pre_transferencia_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: venta venta_cliente_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_cliente_id_fkey FOREIGN KEY (cliente_id) REFERENCES personas.cliente(id);


--
-- Name: venta venta_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_fk FOREIGN KEY (forma_pago_id) REFERENCES financiero.forma_pago(id);


--
-- Name: venta venta_fk_caja; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_fk_caja FOREIGN KEY (caja_id, sucursal_id) REFERENCES financiero.pdv_caja(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: venta venta_fk_cobro; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_fk_cobro FOREIGN KEY (cobro_id, sucursal_id) REFERENCES operaciones.cobro(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: venta_item venta_item_fk_venta; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item
    ADD CONSTRAINT venta_item_fk_venta FOREIGN KEY (venta_id, sucursal_id) REFERENCES operaciones.venta(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: venta_item venta_item_precio_por_sucursal_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item
    ADD CONSTRAINT venta_item_precio_por_sucursal_fk FOREIGN KEY (precio_id) REFERENCES productos.precio_por_sucursal(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: venta_item venta_item_producto_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item
    ADD CONSTRAINT venta_item_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: venta_item venta_item_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_item
    ADD CONSTRAINT venta_item_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: venta_observacion venta_observacion_motivo_observacion_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion
    ADD CONSTRAINT venta_observacion_motivo_observacion_fk FOREIGN KEY (motivo_id) REFERENCES operaciones.motivo_observacion(id) ON DELETE SET NULL;


--
-- Name: venta_observacion venta_observacion_sucursal_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion
    ADD CONSTRAINT venta_observacion_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: venta_observacion venta_observacion_usuario_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion
    ADD CONSTRAINT venta_observacion_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: venta_observacion venta_observacion_venta_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta_observacion
    ADD CONSTRAINT venta_observacion_venta_fk FOREIGN KEY (venta_id, sucursal_id) REFERENCES operaciones.venta(id, sucursal_id) ON DELETE SET NULL;


--
-- Name: venta venta_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.venta
    ADD CONSTRAINT venta_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: vuelto vuelto_1_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto
    ADD CONSTRAINT vuelto_1_fk FOREIGN KEY (autorizado_por_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: vuelto vuelto_2_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto
    ADD CONSTRAINT vuelto_2_fk FOREIGN KEY (responsable_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: vuelto vuelto_3_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto
    ADD CONSTRAINT vuelto_3_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: vuelto_item vuelto_item_fk_vuelto; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto_item
    ADD CONSTRAINT vuelto_item_fk_vuelto FOREIGN KEY (vuelto_id, sucursal_id) REFERENCES operaciones.vuelto(id, sucursal_id) ON DELETE CASCADE;


--
-- Name: vuelto_item vuelto_item_moneda_id_fkey; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto_item
    ADD CONSTRAINT vuelto_item_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: vuelto_item vuelto_item_usu_fk; Type: FK CONSTRAINT; Schema: operaciones; Owner: -
--

ALTER TABLE ONLY operaciones.vuelto_item
    ADD CONSTRAINT vuelto_item_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: cliente cliente_persona_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.cliente
    ADD CONSTRAINT cliente_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: cliente cliente_suc_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.cliente
    ADD CONSTRAINT cliente_suc_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: cliente cliente_usu_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.cliente
    ADD CONSTRAINT cliente_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: funcionario funcionario_cargo_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_cargo_fk FOREIGN KEY (cargo_id) REFERENCES empresarial.cargo(id) ON DELETE SET NULL;


--
-- Name: funcionario funcionario_horario_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_horario_id_fkey FOREIGN KEY (horario_id) REFERENCES administrativo.horario(id);


--
-- Name: funcionario funcionario_persona_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: funcionario funcionario_suc_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_suc_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: funcionario funcionario_supervisado_por_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_supervisado_por_fk FOREIGN KEY (supervisado_por_id) REFERENCES personas.funcionario(id) ON DELETE SET NULL;


--
-- Name: funcionario funcionario_usuario_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.funcionario
    ADD CONSTRAINT funcionario_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id);


--
-- Name: usuario_grupo grupo_privilegio_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_grupo
    ADD CONSTRAINT grupo_privilegio_id_fkey FOREIGN KEY (grupo_privilegio_id) REFERENCES personas.grupo_role(id);


--
-- Name: grupo_role grupo_role_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.grupo_role
    ADD CONSTRAINT grupo_role_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: persona persona_ciudad_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.persona
    ADD CONSTRAINT persona_ciudad_id_fkey FOREIGN KEY (ciudad_id) REFERENCES general.ciudad(id);


--
-- Name: persona persona_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.persona
    ADD CONSTRAINT persona_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: proveedor_dias_visita proveedor_dias_visita_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor_dias_visita
    ADD CONSTRAINT proveedor_dias_visita_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: proveedor proveedor_persona_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.proveedor
    ADD CONSTRAINT proveedor_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: role role_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role
    ADD CONSTRAINT role_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: role role_grupo_role_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.role
    ADD CONSTRAINT role_grupo_role_fk FOREIGN KEY (grupo_role_id) REFERENCES personas.grupo_role(id) ON DELETE SET NULL;


--
-- Name: usuario usuario_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario
    ADD CONSTRAINT usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: usuario_grupo usuario_grupo_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_grupo
    ADD CONSTRAINT usuario_grupo_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: usuario usuario_persona_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario
    ADD CONSTRAINT usuario_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: usuario_role usuario_role_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_role
    ADD CONSTRAINT usuario_role_fk FOREIGN KEY (role_id) REFERENCES personas.role(id);


--
-- Name: usuario_role usuario_role_user_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_role
    ADD CONSTRAINT usuario_role_user_fk FOREIGN KEY (user_id) REFERENCES personas.usuario(id) ON DELETE CASCADE;


--
-- Name: usuario_role usuario_role_usuario_fk; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.usuario_role
    ADD CONSTRAINT usuario_role_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: vendedor vendedor_persona_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor
    ADD CONSTRAINT vendedor_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES personas.persona(id);


--
-- Name: vendedor_proveedor vendedor_proveedor_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor_proveedor
    ADD CONSTRAINT vendedor_proveedor_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id);


--
-- Name: vendedor_proveedor vendedor_proveedor_vendedor_id_fkey; Type: FK CONSTRAINT; Schema: personas; Owner: -
--

ALTER TABLE ONLY personas.vendedor_proveedor
    ADD CONSTRAINT vendedor_proveedor_vendedor_id_fkey FOREIGN KEY (vendedor_id) REFERENCES personas.vendedor(id);


--
-- Name: codigo codigo_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo
    ADD CONSTRAINT codigo_fk FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: codigo_tipo_precio codigo_tipo_precio_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_fk FOREIGN KEY (codigo_id) REFERENCES productos.codigo(id);


--
-- Name: codigo_tipo_precio codigo_tipo_precio_fk_1; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_fk_1 FOREIGN KEY (id) REFERENCES productos.tipo_precio(id);


--
-- Name: codigo_tipo_precio codigo_tipo_precio_fk_usu; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo_tipo_precio
    ADD CONSTRAINT codigo_tipo_precio_fk_usu FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: codigo codigo_usu_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.codigo
    ADD CONSTRAINT codigo_usu_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: costo_por_producto costo_por_producto_fk_suc; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto
    ADD CONSTRAINT costo_por_producto_fk_suc FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE CASCADE;


--
-- Name: costo_por_producto costo_por_producto_fk_usu; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto
    ADD CONSTRAINT costo_por_producto_fk_usu FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: costo_por_producto costos_por_sucursal_moneda_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto
    ADD CONSTRAINT costos_por_sucursal_moneda_id_fkey FOREIGN KEY (moneda_id) REFERENCES financiero.moneda(id);


--
-- Name: costo_por_producto costos_por_sucursal_producto_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.costo_por_producto
    ADD CONSTRAINT costos_por_sucursal_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: familia familia_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.familia
    ADD CONSTRAINT familia_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pdv_categoria pdv_categoria_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_categoria
    ADD CONSTRAINT pdv_categoria_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pdv_grupo pdv_grupo_categoria_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupo
    ADD CONSTRAINT pdv_grupo_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES productos.pdv_categoria(id);


--
-- Name: pdv_grupo pdv_grupo_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupo
    ADD CONSTRAINT pdv_grupo_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pdv_grupos_productos pdv_grupos_productos_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupos_productos
    ADD CONSTRAINT pdv_grupos_productos_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: pdv_grupos_productos pdv_grupos_productos_grupo_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupos_productos
    ADD CONSTRAINT pdv_grupos_productos_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES productos.pdv_grupo(id);


--
-- Name: pdv_grupos_productos pdv_grupos_productos_producto_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.pdv_grupos_productos
    ADD CONSTRAINT pdv_grupos_productos_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: precio_por_sucursal precio_por_sucursal_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal
    ADD CONSTRAINT precio_por_sucursal_fk FOREIGN KEY (presentacion_id) REFERENCES productos.presentacion(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: precio_por_sucursal precio_por_sucursal_fk_1; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal
    ADD CONSTRAINT precio_por_sucursal_fk_1 FOREIGN KEY (tipo_precio_id) REFERENCES productos.tipo_precio(id);


--
-- Name: precio_por_sucursal precio_por_sucursal_fk_usu; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.precio_por_sucursal
    ADD CONSTRAINT precio_por_sucursal_fk_usu FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: presentacion presentacion_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.presentacion
    ADD CONSTRAINT presentacion_fk FOREIGN KEY (tipo_presentacion_id) REFERENCES productos.tipo_presentacion(id);


--
-- Name: presentacion presentacion_fk_producto; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.presentacion
    ADD CONSTRAINT presentacion_fk_producto FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: presentacion presentacion_fk_usu; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.presentacion
    ADD CONSTRAINT presentacion_fk_usu FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: producto producto_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto
    ADD CONSTRAINT producto_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: producto_imagen producto_imagen_producto_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_imagen
    ADD CONSTRAINT producto_imagen_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: producto_proveedor producto_proveedor_pedido_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_proveedor
    ADD CONSTRAINT producto_proveedor_pedido_id_fkey FOREIGN KEY (pedido_id) REFERENCES operaciones.pedido(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: producto_proveedor producto_proveedor_producto_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_proveedor
    ADD CONSTRAINT producto_proveedor_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: producto_proveedor producto_proveedor_proveedor_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_proveedor
    ADD CONSTRAINT producto_proveedor_proveedor_id_fkey FOREIGN KEY (proveedor_id) REFERENCES personas.proveedor(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: producto producto_sub_familia_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto
    ADD CONSTRAINT producto_sub_familia_id_fkey FOREIGN KEY (sub_familia_id) REFERENCES productos.subfamilia(id);


--
-- Name: producto_por_sucursal productos_por_sucursal_producto_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.producto_por_sucursal
    ADD CONSTRAINT productos_por_sucursal_producto_id_fkey FOREIGN KEY (producto_id) REFERENCES productos.producto(id);


--
-- Name: subfamilia subfamilia_familia_id_fkey; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.subfamilia
    ADD CONSTRAINT subfamilia_familia_id_fkey FOREIGN KEY (familia_id) REFERENCES productos.familia(id);


--
-- Name: subfamilia subfamilia_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.subfamilia
    ADD CONSTRAINT subfamilia_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: subfamilia subfamilia_subfamiliafk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.subfamilia
    ADD CONSTRAINT subfamilia_subfamiliafk FOREIGN KEY (sub_familia_id) REFERENCES productos.subfamilia(id) ON DELETE CASCADE;


--
-- Name: tipo_precio tipo_precio_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_precio
    ADD CONSTRAINT tipo_precio_fk FOREIGN KEY (usuario_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL;


--
-- Name: tipo_presentacion tipo_presentacion_fk; Type: FK CONSTRAINT; Schema: productos; Owner: -
--

ALTER TABLE ONLY productos.tipo_presentacion
    ADD CONSTRAINT tipo_presentacion_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE SET NULL;


--
-- Name: modelo modelo_marca_id_fkey; Type: FK CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.modelo
    ADD CONSTRAINT modelo_marca_id_fkey FOREIGN KEY (marca_id) REFERENCES vehiculos.marca(id);


--
-- Name: vehiculo vehiculo_modelo_fk; Type: FK CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo
    ADD CONSTRAINT vehiculo_modelo_fk FOREIGN KEY (modelo_id) REFERENCES vehiculos.modelo(id);


--
-- Name: vehiculo vehiculo_tipo_vehiculo_fkey; Type: FK CONSTRAINT; Schema: vehiculos; Owner: -
--

ALTER TABLE ONLY vehiculos.vehiculo
    ADD CONSTRAINT vehiculo_tipo_vehiculo_fkey FOREIGN KEY (tipo_vehiculo) REFERENCES vehiculos.vehiculo(id);


--
-- Name: central_filial1_pub; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION central_filial1_pub WITH (publish = 'insert, update, delete, truncate');


--
-- Name: central_filial2_pub; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION central_filial2_pub WITH (publish = 'insert, update, delete, truncate');


--
-- Name: central_filial3_pub; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION central_filial3_pub WITH (publish = 'insert, update, delete, truncate');


--
-- Name: central_filial4_pub; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION central_filial4_pub WITH (publish = 'insert, update, delete, truncate');


--
-- Name: central_pub; Type: PUBLICATION; Schema: -; Owner: -
--

CREATE PUBLICATION central_pub WITH (publish = 'insert, update, delete, truncate');


--
-- Name: central_pub jornada; Type: PUBLICATION TABLE; Schema: administrativo; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY administrativo.jornada;


--
-- Name: central_filial1_pub marcacion; Type: PUBLICATION TABLE; Schema: administrativo; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY administrativo.marcacion WHERE ((sucursal_entrada_id = 1));


--
-- Name: central_filial2_pub marcacion; Type: PUBLICATION TABLE; Schema: administrativo; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY administrativo.marcacion WHERE ((sucursal_entrada_id = 2));


--
-- Name: central_filial3_pub marcacion; Type: PUBLICATION TABLE; Schema: administrativo; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY administrativo.marcacion WHERE ((sucursal_entrada_id = 3));


--
-- Name: central_filial4_pub marcacion; Type: PUBLICATION TABLE; Schema: administrativo; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY administrativo.marcacion WHERE ((sucursal_entrada_id = 4));


--
-- Name: central_pub actualizacion; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY configuraciones.actualizacion;


--
-- Name: central_filial1_pub inicio_sesion; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY configuraciones.inicio_sesion WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub inicio_sesion; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY configuraciones.inicio_sesion WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub inicio_sesion; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY configuraciones.inicio_sesion WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub inicio_sesion; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY configuraciones.inicio_sesion WHERE ((sucursal_id = 4));


--
-- Name: central_pub local; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY configuraciones.local;


--
-- Name: central_filial1_pub replication_test; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY configuraciones.replication_test WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub replication_test; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY configuraciones.replication_test WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub replication_test; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY configuraciones.replication_test WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub replication_test; Type: PUBLICATION TABLE; Schema: configuraciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY configuraciones.replication_test WHERE ((sucursal_id = 4));


--
-- Name: central_pub cargo; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.cargo;


--
-- Name: central_pub configuracion_general; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.configuracion_general;


--
-- Name: central_pub punto_de_venta; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.punto_de_venta;


--
-- Name: central_pub sector; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.sector;


--
-- Name: central_pub sucursal; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.sucursal;


--
-- Name: central_pub zona; Type: PUBLICATION TABLE; Schema: empresarial; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY empresarial.zona;


--
-- Name: central_pub equipo; Type: PUBLICATION TABLE; Schema: equipos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY equipos.equipo;


--
-- Name: central_pub tipo_equipo; Type: PUBLICATION TABLE; Schema: equipos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY equipos.tipo_equipo;


--
-- Name: central_pub banco; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.banco;


--
-- Name: central_pub cambio; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.cambio;


--
-- Name: central_filial1_pub cambio_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.cambio_caja WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub cambio_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.cambio_caja WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub cambio_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.cambio_caja WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub cambio_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.cambio_caja WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub conteo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.conteo WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub conteo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.conteo WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub conteo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.conteo WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub conteo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.conteo WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub conteo_moneda; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.conteo_moneda WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub conteo_moneda; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.conteo_moneda WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub conteo_moneda; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.conteo_moneda WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub conteo_moneda; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.conteo_moneda WHERE ((sucursal_id = 4));


--
-- Name: central_pub cuenta_bancaria; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.cuenta_bancaria;


--
-- Name: central_pub documento; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.documento;


--
-- Name: central_filial1_pub documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.documento_electronico WHERE ((sucursal_id = '1'::bigint));


--
-- Name: central_filial2_pub documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.documento_electronico WHERE ((sucursal_id = '2'::bigint));


--
-- Name: central_filial3_pub documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.documento_electronico WHERE ((sucursal_id = '3'::bigint));


--
-- Name: central_pub documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.documento_electronico;


--
-- Name: central_filial1_pub evento_cancelacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.evento_cancelacion_de WHERE ((sucursal_id = '1'::bigint));


--
-- Name: central_filial2_pub evento_cancelacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.evento_cancelacion_de WHERE ((sucursal_id = '2'::bigint));


--
-- Name: central_filial3_pub evento_cancelacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.evento_cancelacion_de WHERE ((sucursal_id = '3'::bigint));


--
-- Name: central_pub evento_cancelacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.evento_cancelacion_de;


--
-- Name: central_filial1_pub evento_inutilizacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.evento_inutilizacion_de WHERE ((sucursal_id = '1'::bigint));


--
-- Name: central_filial2_pub evento_inutilizacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.evento_inutilizacion_de WHERE ((sucursal_id = '2'::bigint));


--
-- Name: central_filial3_pub evento_inutilizacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.evento_inutilizacion_de WHERE ((sucursal_id = '3'::bigint));


--
-- Name: central_pub evento_inutilizacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.evento_inutilizacion_de;


--
-- Name: central_filial1_pub evento_inutilizacion_de_documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico WHERE (((evento_inutilizacion_de_sucursal_id = '1'::bigint) OR (documento_electronico_sucursal_id = '1'::bigint)));


--
-- Name: central_filial2_pub evento_inutilizacion_de_documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico WHERE (((evento_inutilizacion_de_sucursal_id = '2'::bigint) OR (documento_electronico_sucursal_id = '2'::bigint)));


--
-- Name: central_filial3_pub evento_inutilizacion_de_documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico WHERE (((evento_inutilizacion_de_sucursal_id = '3'::bigint) OR (documento_electronico_sucursal_id = '3'::bigint)));


--
-- Name: central_pub evento_inutilizacion_de_documento_electronico; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.evento_inutilizacion_de_documento_electronico;


--
-- Name: central_filial1_pub evento_nominacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.evento_nominacion_de WHERE ((sucursal_id = '1'::bigint));


--
-- Name: central_filial2_pub evento_nominacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.evento_nominacion_de WHERE ((sucursal_id = '2'::bigint));


--
-- Name: central_filial3_pub evento_nominacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.evento_nominacion_de WHERE ((sucursal_id = '3'::bigint));


--
-- Name: central_pub evento_nominacion_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.evento_nominacion_de;


--
-- Name: central_filial1_pub factura_legal; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.factura_legal WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub factura_legal; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.factura_legal WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub factura_legal; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.factura_legal WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub factura_legal; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.factura_legal WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub factura_legal_item; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.factura_legal_item WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub factura_legal_item; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.factura_legal_item WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub factura_legal_item; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.factura_legal_item WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub factura_legal_item; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.factura_legal_item WHERE ((sucursal_id = 4));


--
-- Name: central_pub forma_pago; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.forma_pago;


--
-- Name: central_filial1_pub gasto; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.gasto WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub gasto; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.gasto WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub gasto; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.gasto WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub gasto; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.gasto WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub gasto_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.gasto_detalle WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub gasto_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.gasto_detalle WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub gasto_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.gasto_detalle WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub gasto_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.gasto_detalle WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub lote_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.lote_de WHERE ((sucursal_id = '1'::bigint));


--
-- Name: central_filial2_pub lote_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.lote_de WHERE ((sucursal_id = '2'::bigint));


--
-- Name: central_filial3_pub lote_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.lote_de WHERE ((sucursal_id = '3'::bigint));


--
-- Name: central_pub lote_de; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.lote_de;


--
-- Name: central_filial1_pub maletin; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.maletin WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub maletin; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.maletin WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub maletin; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.maletin WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub maletin; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.maletin WHERE ((sucursal_id = 4));


--
-- Name: central_pub moneda; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.moneda;


--
-- Name: central_pub moneda_billetes; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.moneda_billetes;


--
-- Name: central_filial1_pub movimiento_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.movimiento_caja WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub movimiento_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.movimiento_caja WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub movimiento_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.movimiento_caja WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub movimiento_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.movimiento_caja WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub pdv_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.pdv_caja WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub pdv_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.pdv_caja WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub pdv_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.pdv_caja WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub pdv_caja; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.pdv_caja WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub retiro; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.retiro WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub retiro; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.retiro WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub retiro; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.retiro WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub retiro; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.retiro WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub retiro_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.retiro_detalle WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub retiro_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.retiro_detalle WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub retiro_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.retiro_detalle WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub retiro_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.retiro_detalle WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub sencillo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.sencillo WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub sencillo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.sencillo WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub sencillo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.sencillo WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub sencillo; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.sencillo WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub sencillo_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.sencillo_detalle WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub sencillo_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.sencillo_detalle WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub sencillo_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.sencillo_detalle WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub sencillo_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.sencillo_detalle WHERE ((sucursal_id = 4));


--
-- Name: central_pub timbrado; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.timbrado;


--
-- Name: central_pub timbrado_detalle; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.timbrado_detalle;


--
-- Name: central_pub tipo_gasto; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY financiero.tipo_gasto;


--
-- Name: central_filial1_pub venta_credito; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.venta_credito WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub venta_credito; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.venta_credito WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub venta_credito; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.venta_credito WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub venta_credito; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.venta_credito WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub venta_credito_cuota; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY financiero.venta_credito_cuota WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub venta_credito_cuota; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY financiero.venta_credito_cuota WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub venta_credito_cuota; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY financiero.venta_credito_cuota WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub venta_credito_cuota; Type: PUBLICATION TABLE; Schema: financiero; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY financiero.venta_credito_cuota WHERE ((sucursal_id = 4));


--
-- Name: central_pub barrio; Type: PUBLICATION TABLE; Schema: general; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY general.barrio;


--
-- Name: central_pub ciudad; Type: PUBLICATION TABLE; Schema: general; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY general.ciudad;


--
-- Name: central_pub contacto; Type: PUBLICATION TABLE; Schema: general; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY general.contacto;


--
-- Name: central_pub pais; Type: PUBLICATION TABLE; Schema: general; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY general.pais;


--
-- Name: central_filial1_pub cobro; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.cobro WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub cobro; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.cobro WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub cobro; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.cobro WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub cobro; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.cobro WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub cobro_detalle; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.cobro_detalle WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub cobro_detalle; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.cobro_detalle WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub cobro_detalle; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.cobro_detalle WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub cobro_detalle; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.cobro_detalle WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub delivery; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.delivery WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub delivery; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.delivery WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub delivery; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.delivery WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub delivery; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.delivery WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub movimiento_stock; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.movimiento_stock WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub movimiento_stock; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.movimiento_stock WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub movimiento_stock; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.movimiento_stock WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub movimiento_stock; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.movimiento_stock WHERE ((sucursal_id = 4));


--
-- Name: central_pub precio_delivery; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY operaciones.precio_delivery;


--
-- Name: central_filial1_pub stock_por_producto_sucursal; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.stock_por_producto_sucursal WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub stock_por_producto_sucursal; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.stock_por_producto_sucursal WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub stock_por_producto_sucursal; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.stock_por_producto_sucursal WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub stock_por_producto_sucursal; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.stock_por_producto_sucursal WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub venta; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.venta WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub venta; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.venta WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub venta; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.venta WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub venta; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.venta WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub venta_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.venta_item WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub venta_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.venta_item WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub venta_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.venta_item WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub venta_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.venta_item WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub vuelto; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.vuelto WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub vuelto; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.vuelto WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub vuelto; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.vuelto WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub vuelto; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.vuelto WHERE ((sucursal_id = 4));


--
-- Name: central_filial1_pub vuelto_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial1_pub ADD TABLE ONLY operaciones.vuelto_item WHERE ((sucursal_id = 1));


--
-- Name: central_filial2_pub vuelto_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial2_pub ADD TABLE ONLY operaciones.vuelto_item WHERE ((sucursal_id = 2));


--
-- Name: central_filial3_pub vuelto_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial3_pub ADD TABLE ONLY operaciones.vuelto_item WHERE ((sucursal_id = 3));


--
-- Name: central_filial4_pub vuelto_item; Type: PUBLICATION TABLE; Schema: operaciones; Owner: -
--

ALTER PUBLICATION central_filial4_pub ADD TABLE ONLY operaciones.vuelto_item WHERE ((sucursal_id = 4));


--
-- Name: central_pub cliente; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.cliente;


--
-- Name: central_pub funcionario; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.funcionario;


--
-- Name: central_pub grupo_role; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.grupo_role;


--
-- Name: central_pub persona; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.persona;


--
-- Name: central_pub role; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.role;


--
-- Name: central_pub usuario; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.usuario;


--
-- Name: central_pub usuario_grupo; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.usuario_grupo;


--
-- Name: central_pub usuario_role; Type: PUBLICATION TABLE; Schema: personas; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY personas.usuario_role;


--
-- Name: central_pub codigo; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.codigo;


--
-- Name: central_pub codigo_tipo_precio; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.codigo_tipo_precio;


--
-- Name: central_pub costo_por_producto; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.costo_por_producto;


--
-- Name: central_pub familia; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.familia;


--
-- Name: central_pub pdv_categoria; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.pdv_categoria;


--
-- Name: central_pub pdv_grupo; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.pdv_grupo;


--
-- Name: central_pub pdv_grupos_productos; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.pdv_grupos_productos;


--
-- Name: central_pub precio_por_sucursal; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.precio_por_sucursal;


--
-- Name: central_pub presentacion; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.presentacion;


--
-- Name: central_pub producto; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.producto;


--
-- Name: central_pub producto_imagen; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.producto_imagen;


--
-- Name: central_pub producto_por_sucursal; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.producto_por_sucursal;


--
-- Name: central_pub subfamilia; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.subfamilia;


--
-- Name: central_pub tipo_precio; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.tipo_precio;


--
-- Name: central_pub tipo_presentacion; Type: PUBLICATION TABLE; Schema: productos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY productos.tipo_presentacion;


--
-- Name: central_pub marca; Type: PUBLICATION TABLE; Schema: vehiculos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY vehiculos.marca;


--
-- Name: central_pub modelo; Type: PUBLICATION TABLE; Schema: vehiculos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY vehiculos.modelo;


--
-- Name: central_pub tipo_vehiculo; Type: PUBLICATION TABLE; Schema: vehiculos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY vehiculos.tipo_vehiculo;


--
-- Name: central_pub vehiculo; Type: PUBLICATION TABLE; Schema: vehiculos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY vehiculos.vehiculo;


--
-- Name: central_pub vehiculo_sucursal; Type: PUBLICATION TABLE; Schema: vehiculos; Owner: -
--

ALTER PUBLICATION central_pub ADD TABLE ONLY vehiculos.vehiculo_sucursal;


--
-- Name: filial_farmacia_1_sub; Type: SUBSCRIPTION; Schema: -; Owner: -
--

CREATE SUBSCRIPTION filial_farmacia_1_sub CONNECTION 'dbname=general host=172.25.3.1 user=franco password=franco port=5551' PUBLICATION filial1_pub WITH (connect = false, slot_name = 'filial_farmacia_1_sub', origin = none);


--
-- Name: filial_farmacia_2_sub; Type: SUBSCRIPTION; Schema: -; Owner: -
--

CREATE SUBSCRIPTION filial_farmacia_2_sub CONNECTION 'dbname=general host=172.25.3.2 user=franco password=franco port=5551' PUBLICATION filial2_pub WITH (connect = false, slot_name = 'filial_farmacia_2_sub', origin = none);


--
-- Name: filial_farmacia_3_sub; Type: SUBSCRIPTION; Schema: -; Owner: -
--

CREATE SUBSCRIPTION filial_farmacia_3_sub CONNECTION 'dbname=general host=172.25.3.3 user=franco password=franco port=5551' PUBLICATION filial3_pub WITH (connect = false, slot_name = 'filial_farmacia_3_sub', origin = none);


--
-- Name: filial_farmacia_4_sub; Type: SUBSCRIPTION; Schema: -; Owner: -
--

CREATE SUBSCRIPTION filial_farmacia_4_sub CONNECTION 'dbname=general host=172.25.3.4 user=franco password=franco port=5551' PUBLICATION filial4_pub WITH (connect = false, slot_name = 'filial_farmacia_4_sub', origin = none);


--
-- PostgreSQL database dump complete
--

