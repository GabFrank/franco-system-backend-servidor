package com.franco.dev.eventos.admin;

import lombok.Data;

import java.util.List;

/** Tipos del ABM de eventos por webhook (graphql/configuraciones/eventos.graphqls, srv #587). */
public final class EventosAdminDtos {

    private EventosAdminDtos() {
    }

    @Data
    public static class EventoCatalogoTipo {
        private String tipo;
        private Integer version;
        private String familia;
        private String nodo;
        private List<String> atributos;
        private List<EventoCatalogoCampo> campos;
    }

    @Data
    public static class EventoCatalogoCampo {
        private String ruta;
        private String clase;
    }

    @Data
    public static class EventosConsumidor {
        private Long id;
        private String nombre;
        private String contacto;
        private Boolean permitePersonales;
        private Boolean permiteSensibles;
        private Integer maxSuscripciones;
        private Boolean activo;
        private String creadoEn;
    }

    @Data
    public static class EventosConsumidorInput {
        private Long id;
        private String nombre;
        private String contacto;
        private Boolean permitePersonales;
        private Boolean permiteSensibles;
        private Integer maxSuscripciones;
        private Boolean activo;
    }

    @Data
    public static class WebhookSuscripcion {
        private Long id;
        private Long consumidorId;
        private String nombre;
        private String url;
        private List<String> eventos;
        private List<String> eventosPausados;
        private List<Long> sucursalIds;
        private String filtros;
        private String campos;
        private String umbrales;
        private String entrega;
        private Boolean datosPersonales;
        private Boolean datosSensibles;
        private String cabeceraNombre;
        private String secretoRef;
        private Boolean latido;
        private Boolean verificada;
        private Boolean activo;
        private Long configVersion;
        private String creadoEn;
        private String actualizadoEn;
    }

    @Data
    public static class WebhookSuscripcionInput {
        private Long id;
        private Long consumidorId;
        private String nombre;
        private String url;
        private List<String> eventos;
        private List<Long> sucursalIds;
        private String filtros;
        private String campos;
        private String umbrales;
        private String entrega;
        private Boolean datosPersonales;
        private Boolean datosSensibles;
        private String cabeceraNombre;
        private String secretoRef;
        private Boolean latido;
    }

    @Data
    public static class WebhookVerificacion {
        private Boolean verificada;
        private Integer statusHttp;
        private String mensaje;
    }
}
