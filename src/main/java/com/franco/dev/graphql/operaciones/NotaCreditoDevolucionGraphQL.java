package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NotaCreditoDevolucion;
import com.franco.dev.domain.operaciones.dto.AcreditacionPreviewDto;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.NotaCreditoDevolucionItemInput;
import com.franco.dev.service.operaciones.NotaCreditoDevolucionService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

@Component
public class NotaCreditoDevolucionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotaCreditoDevolucionService service;
    @Autowired
    private UsuarioService usuarioService;

    // ===================== Queries =====================

    /** Preview consolidado para acreditar un retiro (por producto, a costo medio). */
    public AcreditacionPreviewDto acreditacionPreview(Long retiroId) {
        if (retiroId == null) throw new GraphQLException("retiroId es requerido");
        return service.previewAcreditacion(retiroId);
    }

    /** Nota de credito ya registrada de un retiro (null si no acreditado). */
    public NotaCreditoDevolucion notaCreditoPorRetiro(Long retiroId) {
        if (retiroId == null) throw new GraphQLException("retiroId es requerido");
        return service.findByRetiroId(retiroId).orElse(null);
    }

    public NotaCreditoDevolucion notaCreditoDevolucion(Long id) {
        if (id == null) throw new GraphQLException("id es requerido");
        return service.findById(id).orElse(null);
    }

    // ===================== Mutations =====================

    @Transactional
    public NotaCreditoDevolucion acreditarRetiro(Long retiroId, String nroNotaCredito, String fecha,
                                                 List<NotaCreditoDevolucionItemInput> items, Long usuarioId) {
        if (retiroId == null) throw new GraphQLException("retiroId es requerido");
        // nroNotaCredito es OPCIONAL: muchas veces la nota se solicita con este
        // documento y el numero llega despues.
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        LocalDateTime fechaLdt = fecha != null ? stringToDate(fecha) : null;
        try {
            return service.acreditarRetiro(retiroId, nroNotaCredito, fechaLdt, items, usuario);
        } catch (GraphQLException e) {
            throw e;
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }

    @Transactional
    public Boolean revertirNotaCreditoDevolucion(Long id, Long usuarioId) {
        if (id == null) throw new GraphQLException("id es requerido");
        Usuario usuario = usuarioId != null ? usuarioService.findById(usuarioId).orElse(null) : null;
        try {
            return service.revertir(id, usuario);
        } catch (GraphQLException e) {
            throw e;
        } catch (Exception e) {
            throw new GraphQLException(e.getMessage());
        }
    }
}
