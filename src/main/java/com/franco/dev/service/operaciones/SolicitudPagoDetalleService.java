package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.SolicitudPago;
import com.franco.dev.domain.operaciones.SolicitudPagoDetalle;
import com.franco.dev.repository.operaciones.SolicitudPagoDetalleRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.franco.dev.utilitarios.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para detalles de solicitud de pago (formas de pago).
 * Usado en: Desktop (diálogo create/edit), reportes PDF/ticket, GraphQL resolver.
 */
@Service
@AllArgsConstructor
public class SolicitudPagoDetalleService extends CrudService<SolicitudPagoDetalle, SolicitudPagoDetalleRepository, Long> {

    private final SolicitudPagoDetalleRepository repository;
    private final MonedaService monedaService;
    private final FormaPagoService formaPagoService;

    @Override
    public SolicitudPagoDetalleRepository getRepository() {
        return repository;
    }

    @Override
    public SolicitudPagoDetalle save(SolicitudPagoDetalle entity) {
        if (entity.getId() == null) {
            entity.setCreadoEn(LocalDateTime.now());
        }
        return super.save(entity);
    }

    public List<SolicitudPagoDetalle> findBySolicitudPagoId(Long solicitudPagoId) {
        return repository.findBySolicitudPagoIdOrderByOrdenAscIdAsc(solicitudPagoId);
    }

    /**
     * Find detalles with moneda and formaPago eagerly loaded for printing (PDF/ticket).
     * Usado en: impresion PDF y ticket solicitud pago.
     */
    public List<SolicitudPagoDetalle> findBySolicitudPagoIdForPrint(Long solicitudPagoId) {
        return repository.findBySolicitudPagoIdOrderByOrdenAscIdAscWithMonedaAndFormaPago(solicitudPagoId);
    }

    /**
     * Agrega un detalle (forma de pago) a una solicitud existente.
     * Usado en: Desktop (diálogo adicionar forma de pago cuando se guarda en backend).
     */
    public SolicitudPagoDetalle agregarDetalle(SolicitudPago solicitud, DetalleInput in) {
        List<SolicitudPagoDetalle> existentes = repository.findBySolicitudPagoIdOrderByOrdenAscIdAsc(solicitud.getId());
        int orden = existentes.isEmpty() ? 0 : (existentes.get(existentes.size() - 1).getOrden() != null ? existentes.get(existentes.size() - 1).getOrden() + 1 : existentes.size());
        Moneda moneda = monedaService.findById(in.getMonedaId())
            .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + in.getMonedaId()));
        FormaPago formaPago = formaPagoService.findById(in.getFormaPagoId())
            .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + in.getFormaPagoId()));
        SolicitudPagoDetalle d = new SolicitudPagoDetalle();
        d.setSolicitudPago(solicitud);
        d.setMoneda(moneda);
        d.setFormaPago(formaPago);
        d.setValor(in.getValor());
        d.setFechaPago(parseLocalDate(in.getFechaPago()));
        d.setObservacion(in.getObservacion());
        d.setOrden(orden);
        d.setCotizacion(in.getCotizacion());
        d.setFechaEmisionCheque(parseLocalDate(in.getFechaEmisionCheque()));
        d.setPortador(in.getPortador());
        d.setNominal(in.getNominal() != null ? in.getNominal() : true);
        d.setDiferido(in.getDiferido() != null ? in.getDiferido() : true);
        return save(d);
    }

    /** Parsea string a LocalDate aceptando "yyyy-MM-dd" o "yyyy-MM-dd HH:mm" (p. ej. desde GraphQL). */
    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            LocalDateTime ldt = DateUtils.stringToDate(s.trim());
            return ldt != null ? ldt.toLocalDate() : null;
        } catch (Exception e) {
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
            throw e;
        }
    }

    /**
     * Sincroniza los detalles de una solicitud con la lista enviada: borra los actuales e inserta los nuevos.
     * Validación informativa: si la suma de valor no coincide con montoTotal de la solicitud no se bloquea.
     */
    @Transactional
    public void sincronizarDetalles(Long solicitudPagoId, SolicitudPago solicitud, List<DetalleInput> inputs) {
        repository.deleteBySolicitudPagoId(solicitudPagoId);
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        int orden = 0;
        for (DetalleInput in : inputs) {
            Moneda moneda = monedaService.findById(in.getMonedaId())
                .orElseThrow(() -> new IllegalArgumentException("Moneda no encontrada: " + in.getMonedaId()));
            FormaPago formaPago = formaPagoService.findById(in.getFormaPagoId())
                .orElseThrow(() -> new IllegalArgumentException("Forma de pago no encontrada: " + in.getFormaPagoId()));
            SolicitudPagoDetalle d = new SolicitudPagoDetalle();
            d.setSolicitudPago(solicitud);
            d.setMoneda(moneda);
            d.setFormaPago(formaPago);
            d.setValor(in.getValor());
            d.setFechaPago(parseLocalDate(in.getFechaPago()));
            d.setObservacion(in.getObservacion());
            d.setOrden(orden++);
            d.setCotizacion(in.getCotizacion());
            d.setFechaEmisionCheque(parseLocalDate(in.getFechaEmisionCheque()));
            d.setPortador(in.getPortador());
            d.setNominal(in.getNominal() != null ? in.getNominal() : true);
            d.setDiferido(in.getDiferido() != null ? in.getDiferido() : true);
            save(d);
        }
    }

    /**
     * DTO para sincronizar detalles (desde GraphQL input).
     */
    public static class DetalleInput {
        private Long monedaId;
        private Long formaPagoId;
        private Double valor;
        private String fechaPago;
        private String observacion;
        private Double cotizacion;
        private Integer orden;
        private String fechaEmisionCheque;
        private String portador;
        private Boolean nominal;
        private Boolean diferido;

        public Long getMonedaId() { return monedaId; }
        public void setMonedaId(Long monedaId) { this.monedaId = monedaId; }
        public Long getFormaPagoId() { return formaPagoId; }
        public void setFormaPagoId(Long formaPagoId) { this.formaPagoId = formaPagoId; }
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public String getFechaPago() { return fechaPago; }
        public void setFechaPago(String fechaPago) { this.fechaPago = fechaPago; }
        public String getObservacion() { return observacion; }
        public void setObservacion(String observacion) { this.observacion = observacion; }
        public Double getCotizacion() { return cotizacion; }
        public void setCotizacion(Double cotizacion) { this.cotizacion = cotizacion; }
        public Integer getOrden() { return orden; }
        public void setOrden(Integer orden) { this.orden = orden; }
        public String getFechaEmisionCheque() { return fechaEmisionCheque; }
        public void setFechaEmisionCheque(String fechaEmisionCheque) { this.fechaEmisionCheque = fechaEmisionCheque; }
        public String getPortador() { return portador; }
        public void setPortador(String portador) { this.portador = portador; }
        public Boolean getNominal() { return nominal; }
        public void setNominal(Boolean nominal) { this.nominal = nominal; }
        public Boolean getDiferido() { return diferido; }
        public void setDiferido(Boolean diferido) { this.diferido = diferido; }
    }
}
