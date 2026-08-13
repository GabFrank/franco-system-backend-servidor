package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.repository.financiero.ChequeraRepository;
import com.franco.dev.repository.financiero.CuentaBancariaRepository;
import com.franco.dev.repository.financiero.TerminalPosRepository;
import com.franco.dev.service.financiero.*;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
@AllArgsConstructor
public class ChequePosGraphQL implements GraphQLMutationResolver {

    private final ChequeGestionService chequeGestionService;
    private final AcreditacionPosService acreditacionPosService;
    private final ChequeraRepository chequeraRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final TerminalPosRepository terminalPosRepository;
    private final MonedaService monedaService;
    private final TesoreriaSecurityService seg;

    public Cheque emitirCheque(Long chequeraId, Double total, Boolean diferido, Long monedaId,
                               Long cuentaBancariaId, String fechaPago, String concepto) {
        seg.requireGestionar();
        Cheque c = new Cheque();
        c.setChequera(chequeraRepository.findById(chequeraId).orElseThrow(() -> new GraphQLException("Chequera no encontrada")));
        c.setTotal(total);
        c.setDiferido(diferido);
        c.setConcepto(concepto);
        if (monedaId != null) c.setMoneda(monedaService.findById(monedaId).orElse(null));
        if (cuentaBancariaId != null) c.setCuentaBancaria(cuentaBancariaRepository.findById(cuentaBancariaId).orElse(null));
        if (fechaPago != null) c.setFechaPago(stringToDate(fechaPago));
        return chequeGestionService.emitir(c, seg.currentUsuario());
    }

    public Cheque cobrarCheque(Long chequeId) {
        seg.requireGestionar();
        return chequeGestionService.cobrar(chequeId, seg.currentUsuario());
    }

    public Cheque anularCheque(Long chequeId, String motivo) {
        seg.requireGestionar();
        return chequeGestionService.anular(chequeId, motivo, seg.currentUsuario());
    }

    public AcreditacionPos crearAcreditacionPos(Long terminalPosId, Double montoOriginal, Long ventaId) {
        seg.requireGestionar();
        TerminalPos terminal = terminalPosRepository.findById(terminalPosId)
                .orElseThrow(() -> new GraphQLException("Terminal POS no encontrada"));
        return acreditacionPosService.crear(terminal, terminal.getCuentaBancaria(),
                BigDecimal.valueOf(montoOriginal), ventaId, seg.currentUsuario());
    }

    public AcreditacionPos verificarAcreditacionPos(Long id, Double montoReal) {
        seg.requireGestionar();
        return acreditacionPosService.verificar(id, BigDecimal.valueOf(montoReal), seg.currentUsuario());
    }

    public Integer procesarAcreditacionesPos() {
        seg.requireGestionar();
        return acreditacionPosService.procesarPendientes(200);
    }
}
