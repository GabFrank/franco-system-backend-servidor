package com.franco.dev.service.financiero;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.domain.financiero.CuentaBancaria;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.enums.EstadoCheque;
import com.franco.dev.repository.financiero.ChequeRepository;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.utilitarios.print.ChequeAnuladoTicketEscPos;
import graphql.GraphQLException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Comprobante térmico de un cheque anulado (desktop #807): se imprime al anular y se reimprime del dashboard. */
@Service
@AllArgsConstructor
public class ChequeAnuladoTicketService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ChequeRepository chequeRepository;
    private final ConfiguracionGeneralService configuracionGeneralService;

    @Transactional(readOnly = true)
    public String ticketBase64(Long chequeId, Integer anchoMm) {
        Cheque c = chequeRepository.findById(chequeId)
                .orElseThrow(() -> new GraphQLException("Cheque no encontrado: " + chequeId));
        if (c.getEstado() != EstadoCheque.ANULADO) {
            throw new GraphQLException("El cheque Nº " + numero(c) + " no está anulado.");
        }
        return ChequeAnuladoTicketEscPos.build(datos(c, empresa()), anchoMm);
    }

    static ChequeAnuladoTicketEscPos.Datos datos(Cheque c, String empresa) {
        ChequeAnuladoTicketEscPos.Datos d = new ChequeAnuladoTicketEscPos.Datos();
        d.empresa = empresa;
        d.numero = numero(c);
        CuentaBancaria cuenta = c.getCuentaBancaria();
        if (cuenta != null) {
            d.banco = cuenta.getBanco() != null ? cuenta.getBanco().getNombre() : null;
            d.cuenta = cuenta.getNumero();
        }
        d.beneficiario = c.getBeneficiario() != null ? c.getBeneficiario()
                : (c.getProveedor() != null && c.getProveedor().getPersona() != null ? c.getProveedor().getPersona().getNombre() : null);
        d.monto = monto(c.getTotal(), c.getMoneda());
        d.fechaEmision = fecha(c.getFechaEntrega(), DIA);
        d.fechaPago = fecha(c.getFechaPago(), DIA) + (Boolean.TRUE.equals(c.getDiferido()) && c.getFechaPago() != null ? " (diferido)" : "");
        // Anulados antes de #807: el motivo de la lista no existe, sale el texto que se guardaba.
        d.motivo = c.getMotivoAnulacionTipo() != null ? c.getMotivoAnulacionTipo().getEtiqueta() : c.getMotivoAnulacion();
        d.observacion = c.getObservacionAnulacion();
        d.fechaAnulacion = fecha(c.getFechaAnulacion(), DIA_HORA);
        d.usuarioAnulacion = c.getUsuarioAnulacion() != null ? c.getUsuarioAnulacion().getNickname() : null;
        // Anulado ya no dice el estado previo: con débito (cobrado) se revirtió el movimiento; sin él, era una reserva.
        d.efecto = c.getMovimientoBancarioId() != null ? "Movimiento bancario revertido" : "Reserva liberada";
        return d;
    }

    private static String numero(Cheque c) {
        return c.getNumero() != null ? String.valueOf(c.getNumero().longValue()) : String.valueOf(c.getId());
    }

    private static String fecha(LocalDateTime f, DateTimeFormatter fmt) {
        return f != null ? f.format(fmt) : null;
    }

    /** «Gs 1.500.000» / «US$ 1.250,50», con los decimales de la moneda. */
    static String monto(Double total, Moneda moneda) {
        if (total == null) return null;
        int dec = moneda != null && moneda.getDecimales() != null ? moneda.getDecimales() : 0;
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        StringBuilder patron = new StringBuilder("#,##0");
        if (dec > 0) { patron.append('.'); for (int i = 0; i < dec; i++) patron.append('0'); }
        String simbolo = moneda != null && moneda.getSimbolo() != null ? moneda.getSimbolo() + " " : "";
        return simbolo + new DecimalFormat(patron.toString(), s).format(total);
    }

    /** Razón social, como los recibos de RRHH. */
    private String empresa() {
        List<ConfiguracionGeneral> all = configuracionGeneralService.findAll2();
        ConfiguracionGeneral cg = all != null && !all.isEmpty() ? all.get(0) : null;
        if (cg == null) return null;
        return cg.getRazonSocial() != null ? cg.getRazonSocial() : cg.getNombreEmpresa();
    }
}
