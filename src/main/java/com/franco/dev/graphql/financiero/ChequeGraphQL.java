package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Cheque;
import com.franco.dev.graphql.financiero.input.ChequeInput;
import com.franco.dev.service.financiero.ChequeService;
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
public class ChequeGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ChequeService service;

    
    
    @Autowired
    private TesoreriaSecurityService seg;

    public Optional<Cheque> cheque(Long id) {
        seg.requireVer();
        return service.findById(id);
    }

    public List<Cheque> cheques(int page, int size) {
        seg.requireVer();
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }
    
    public List<Cheque> chequesPorChequeraId(Long chequeraId) {
        seg.requireVer();
        return service.findByChequeraId(chequeraId);
    }
    
    public Cheque chequePorPagoDetalleCuotaId(Long pagoDetalleCuotaId) {
        seg.requireVer();
        return service.findByPagoDetalleCuotaId(pagoDetalleCuotaId);
    }

    private static final String NO_SE_EDITAN = "Los cheques se emiten, se cobran y se anulan con sus operaciones:"
            + " no se editan ni se borran.";

    /**
     * Rechazada (issue #376). Era un alta / edición plana de la tabla: dejaba escribir cualquier número,
     * pisar el estado de un cheque cobrado y dejarlo sin su movimiento bancario. Ninguna pantalla la usa;
     * sigue en el schema para no romper la validación de un cliente que la declare.
     */
    public Cheque saveCheque(ChequeInput input) {
        seg.requireGestionar();
        throw new graphql.GraphQLException(NO_SE_EDITAN);
    }

    public List<Cheque> chequesSearch(String texto) {
        seg.requireVer();
        return service.findByAll(texto);
    }

    /** Rechazada (issue #376): borrar un cheque emitido libera su número. Un cheque se anula. */
    public Boolean deleteCheque(Long id) {
        seg.requireGestionar();
        throw new graphql.GraphQLException(NO_SE_EDITAN);
    }

    public Long countCheque() {
        seg.requireVer();
        return service.count();
    }
} 