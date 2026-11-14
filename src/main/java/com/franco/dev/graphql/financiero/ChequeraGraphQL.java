package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Chequera;
import com.franco.dev.graphql.financiero.input.ChequeraInput;
import com.franco.dev.service.financiero.ChequeraService;
import com.franco.dev.service.financiero.TesoreriaSecurityService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChequeraGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ChequeraService service;

    @Autowired
    private TesoreriaSecurityService seg;

    @Autowired
    private com.franco.dev.service.financiero.ChequeraGestionService gestionService;

    public Optional<Chequera> chequera(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Chequera> chequeras(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    /**
     * Alta o edición. No se arma ni se carga la entidad acá: el servicio toma la chequera con lock y decide
     * sobre lo que hay en la base (issue #376). El usuario creador es el de la sesión.
     */
    public Chequera saveChequera(ChequeraInput input) {
        seg.requireGestionar();
        return gestionService.guardar(input, seg.currentUsuario());
    }

    public List<Chequera> chequerasSearch(String texto) {
        seg.requireVer();
        return service.findByAll(texto);
    }

    /** Chequeras de una cuenta bancaria (para ofrecer cheque como forma de pago). soloActivas por default. */
    public List<Chequera> chequerasPorCuenta(Long cuentaBancariaId, Boolean soloActivas) {
        seg.requireVer();
        if (Boolean.FALSE.equals(soloActivas)) {
            return service.getRepository().findByCuentaBancariaIdOrderByIdDesc(cuentaBancariaId);
        }
        return service.getRepository().findByCuentaBancariaIdAndEstadoOrderByIdDesc(
                cuentaBancariaId, com.franco.dev.domain.financiero.enums.EstadoChequera.ACTIVA);
    }

    public Boolean deleteChequera(Long id) {
        seg.requireGestionar();
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countChequera() {
        seg.requireVer();
        return service.count();
    }

    // ── Inutilizar números (issue #603) ── mismo rol que gestiona chequeras.

    @Autowired
    private com.franco.dev.service.financiero.ChequeGestionService chequeGestionService;

    @Autowired
    private com.franco.dev.repository.financiero.ChequeraNumeroInutilizadoRepository inutilizadoRepository;

    @Autowired
    private com.franco.dev.repository.financiero.ChequeraRepository chequeraRepository;

    public List<Double> proximosNumerosCheque(Long chequeraId, Integer cantidad) {
        seg.requireVer();
        Chequera ch = chequeraRepository.findById(chequeraId)
                .orElseThrow(() -> new graphql.GraphQLException("Chequera no encontrada"));
        int n = Math.max(1, Math.min(cantidad != null ? cantidad : 1, 50));
        List<Double> out = new java.util.ArrayList<>();
        for (Long numero : chequeGestionService.proximosLibres(ch, n)) out.add(numero.doubleValue());
        return out;
    }

    public List<com.franco.dev.domain.financiero.ChequeraNumeroInutilizado> numerosInutilizados(Long chequeraId) {
        seg.requireVer();
        return chequeraId != null ? inutilizadoRepository.findByChequeraIdOrderByIdDesc(chequeraId)
                : inutilizadoRepository.findByRevertidoEnIsNullOrderByIdDesc();
    }

    public com.franco.dev.domain.financiero.ChequeraNumeroInutilizado inutilizarNumeroCheque(Long chequeraId, Double numero,
            String motivo, com.franco.dev.domain.financiero.enums.AccionChequeInutilizado accionCheque, Double numeroNuevo) {
        seg.requireGestionar();
        return chequeGestionService.inutilizar(chequeraId, numero != null ? numero.longValue() : null, motivo, accionCheque,
                numeroNuevo != null ? numeroNuevo.longValue() : null, seg.currentUsuario());
    }

    public com.franco.dev.domain.financiero.ChequeraNumeroInutilizado revertirInutilizacion(Long id) {
        seg.requireGestionar();
        return chequeGestionService.revertirInutilizacion(id, seg.currentUsuario());
    }
}
