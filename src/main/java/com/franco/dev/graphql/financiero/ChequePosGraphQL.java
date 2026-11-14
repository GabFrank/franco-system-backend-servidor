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
    private final com.franco.dev.repository.personas.ProveedorRepository proveedorRepository;

    public Cheque emitirCheque(Long chequeraId, Double total, Boolean diferido, Long monedaId,
                               Long cuentaBancariaId, String fechaPago, String concepto,
                               String claveIdempotencia, Long proveedorId, String beneficiario, Boolean nominal,
                               String fechaEmision) {
        seg.requireGestionar();
        Cheque c = new Cheque();
        c.setChequera(chequeraRepository.findById(chequeraId).orElseThrow(() -> new GraphQLException("Chequera no encontrada")));
        c.setTotal(total);
        c.setDiferido(diferido);
        c.setConcepto(concepto);
        // Un id que no existe se rechaza: ignorarlo dejaba el cheque sin moneda o sin cuenta, y las
        // validaciones de la emisión no tenían contra qué comparar.
        if (monedaId != null) c.setMoneda(monedaService.findById(monedaId)
                .orElseThrow(() -> new GraphQLException("Moneda no encontrada: " + monedaId)));
        if (cuentaBancariaId != null) c.setCuentaBancaria(cuentaBancariaRepository.findById(cuentaBancariaId)
                .orElseThrow(() -> new GraphQLException("Cuenta bancaria no encontrada: " + cuentaBancariaId)));
        if (fechaPago != null) c.setFechaPago(stringToDate(fechaPago));
        c.setProveedor(proveedor(proveedorId));
        c.setBeneficiario(beneficiario != null && !beneficiario.trim().isEmpty() ? beneficiario.trim().toUpperCase() : null);
        c.setNominal(nominal);
        if (fechaEmision != null && !fechaEmision.isEmpty()) c.setFechaEntrega(stringToDate(fechaEmision));
        return chequeGestionService.emitir(c, seg.currentUsuario(), claveIdempotencia);
    }

    public java.util.List<Cheque> emitirCheques(Long chequeraId, java.util.List<CuotaChequeInput> cheques, Boolean diferido,
                                                Boolean nominal, String beneficiario, Long proveedorId, String fechaEmision,
                                                String concepto, String claveIdempotencia) {
        seg.requireGestionar();
        ChequeGestionService.DatosLote d = new ChequeGestionService.DatosLote();
        d.setChequeraId(chequeraId);
        d.setDiferido(diferido);
        d.setNominal(nominal);
        d.setBeneficiario(beneficiario != null && !beneficiario.trim().isEmpty() ? beneficiario.trim().toUpperCase() : null);
        d.setProveedor(proveedor(proveedorId));
        if (fechaEmision != null && !fechaEmision.isEmpty()) d.setFechaEmision(stringToDate(fechaEmision));
        d.setConcepto(concepto);
        java.util.List<ChequeGestionService.CuotaCheque> cuotas = new java.util.ArrayList<>();
        for (CuotaChequeInput c : (cheques != null ? cheques : java.util.Collections.<CuotaChequeInput>emptyList())) {
            cuotas.add(new ChequeGestionService.CuotaCheque(c.getTotal(),
                    c.getFechaPago() != null && !c.getFechaPago().isEmpty() ? stringToDate(c.getFechaPago()) : null));
        }
        return chequeGestionService.emitirLote(d, cuotas, seg.currentUsuario(), claveIdempotencia);
    }

    private com.franco.dev.domain.personas.Proveedor proveedor(Long proveedorId) {
        if (proveedorId == null) return null;
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new GraphQLException("Proveedor no encontrado: " + proveedorId));
    }

    @lombok.Data
    public static class CuotaChequeInput {
        private Double total;
        private String fechaPago;
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
