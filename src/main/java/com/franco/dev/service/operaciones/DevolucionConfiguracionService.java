package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.DevolucionConfiguracion;
import com.franco.dev.repository.operaciones.DevolucionConfiguracionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso a la configuracion del modulo de devoluciones (fila unica id=1).
 * getConfiguracion() nunca devuelve null: si falta la fila, la crea con los
 * defaults (mismos que la migracion V148).
 */
@Service
public class DevolucionConfiguracionService {

    private static final Long CONFIG_ID = 1L;

    @Autowired
    private DevolucionConfiguracionRepository repository;

    @Transactional
    public DevolucionConfiguracion getConfiguracion() {
        return repository.findById(CONFIG_ID).orElseGet(() -> repository.save(porDefecto()));
    }

    @Transactional
    public DevolucionConfiguracion guardar(DevolucionConfiguracion input) {
        DevolucionConfiguracion actual = getConfiguracion();
        if (input.getDiasEstancada() != null) actual.setDiasEstancada(input.getDiasEstancada());
        if (input.getDiasEstancadaUrgente() != null) actual.setDiasEstancadaUrgente(input.getDiasEstancadaUrgente());
        if (input.getRankingTopN() != null) actual.setRankingTopN(input.getRankingTopN());
        if (input.getDashboardRangoDefault() != null) actual.setDashboardRangoDefault(input.getDashboardRangoDefault());
        if (input.getTicketAnchoMm() != null) actual.setTicketAnchoMm(input.getTicketAnchoMm());
        if (input.getImpresoraTicket() != null) actual.setImpresoraTicket(input.getImpresoraTicket());
        if (input.getComprobanteTitulo() != null) actual.setComprobanteTitulo(input.getComprobanteTitulo());
        if (input.getComprobanteTextoFirma() != null) actual.setComprobanteTextoFirma(input.getComprobanteTextoFirma());
        if (input.getMermaRespetaMotivo() != null) actual.setMermaRespetaMotivo(input.getMermaRespetaMotivo());
        if (input.getTipoGastoMerma() != null) actual.setTipoGastoMerma(input.getTipoGastoMerma());
        if (input.getAlertaIncluyeRetirado() != null) actual.setAlertaIncluyeRetirado(input.getAlertaIncluyeRetirado());
        if (input.getAlertaBloqueante() != null) actual.setAlertaBloqueante(input.getAlertaBloqueante());
        if (input.getPermitirStockNegativo() != null) actual.setPermitirStockNegativo(input.getPermitirStockNegativo());
        if (input.getRetiroPermitirSeleccionManual() != null) actual.setRetiroPermitirSeleccionManual(input.getRetiroPermitirSeleccionManual());
        return repository.save(actual);
    }

    private DevolucionConfiguracion porDefecto() {
        DevolucionConfiguracion c = new DevolucionConfiguracion();
        c.setId(CONFIG_ID);
        c.setDiasEstancada(30);
        c.setDiasEstancadaUrgente(60);
        c.setRankingTopN(5);
        c.setDashboardRangoDefault("MES_ACTUAL");
        c.setTicketAnchoMm(58);
        c.setImpresoraTicket("TICKET58");
        c.setComprobanteTitulo("COMPROBANTE DE RETIRO");
        c.setComprobanteTextoFirma("Firma del proveedor");
        c.setMermaRespetaMotivo(false);
        c.setTipoGastoMerma("MERMA/AVERIA DE PRODUCTO");
        c.setAlertaIncluyeRetirado(false);
        c.setAlertaBloqueante(true);
        c.setPermitirStockNegativo(true);
        c.setRetiroPermitirSeleccionManual(true);
        return c;
    }
}
