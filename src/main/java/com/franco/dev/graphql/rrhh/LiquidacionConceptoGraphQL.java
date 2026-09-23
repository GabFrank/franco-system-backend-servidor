package com.franco.dev.graphql.rrhh;

import com.franco.dev.domain.rrhh.LiquidacionConcepto;
import com.franco.dev.graphql.rrhh.input.LiquidacionConceptoInput;
import com.franco.dev.repository.rrhh.LiquidacionItemRepository;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rrhh.LiquidacionConceptoService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LiquidacionConceptoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private LiquidacionConceptoService service;

    @Autowired
    private com.franco.dev.service.rrhh.RrhhSecurityService seg;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private LiquidacionItemRepository liquidacionItemRepository;

    public Optional<LiquidacionConcepto> liquidacionConcepto(Long id) {
        return service.findById(id);
    }

    /** Conceptos elegibles al agregar un item manual a una liquidacion. */
    public List<LiquidacionConcepto> liquidacionConceptosParaItemManual() {
        seg.requireVer();
        return service.findParaItemManual();
    }

    public Optional<LiquidacionConcepto> liquidacionConceptoPorCodigo(String codigo) {
        return service.findByCodigo(codigo);
    }

    public List<LiquidacionConcepto> liquidacionConceptos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public Long countLiquidacionConcepto() {
        return service.count();
    }

    public LiquidacionConcepto saveLiquidacionConcepto(LiquidacionConceptoInput input) throws GraphQLException {
        seg.requireAnyRole(seg.CONFIG, seg.GESTIONAR);
        validarCodigo(input);
        ModelMapper m = new ModelMapper();
        // Ver ConfiguracionRrhhGraphQL: en update, los campos null del input no deben
        // pisar valores existentes (ej. creadoEn) y disparar NOT NULL al guardar.
        m.getConfiguration().setSkipNullEnabled(true);
        LiquidacionConcepto e;
        if (input.getId() != null) {
            e = service.findById(input.getId()).orElse(new LiquidacionConcepto());
            e.setUsuario(null);
            m.map(input, e);
        } else {
            e = m.map(input, LiquidacionConcepto.class);
        }
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        } else if (input.getId() == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                usuarioService.findByNickname(authentication.getName()).ifPresent(e::setUsuario);
            }
        }
        return service.save(e);
    }

    /**
     * El codigo es la unica llave entre el catalogo y {@code liquidacion_item.codigo}, que
     * es un String suelto: si se duplica, findByCodigo empieza a resolver cualquiera de
     * los dos; si se renombra uno ya emitido, los items historicos quedan huerfanos igual
     * que si lo hubieran borrado. La base no protege ninguno de los dos casos.
     */
    private void validarCodigo(LiquidacionConceptoInput input) throws GraphQLException {
        if (input.getCodigo() == null) return;
        String codigo = input.getCodigo().toUpperCase();
        LiquidacionConcepto conMismoCodigo = service.findByCodigo(codigo).orElse(null);
        if (conMismoCodigo != null && !conMismoCodigo.getId().equals(input.getId())) {
            throw new GraphQLException("Ya existe un concepto con el codigo " + codigo);
        }
        if (input.getId() == null) return;
        LiquidacionConcepto actual = service.findById(input.getId()).orElse(null);
        if (actual == null || actual.getCodigo() == null || actual.getCodigo().equals(codigo)) return;
        Long items = liquidacionItemRepository.countByCodigo(actual.getCodigo());
        if (items != null && items > 0) {
            throw new GraphQLException(
                    "No se puede cambiar el codigo: " + actual.getCodigo() + " aparece en " + items
                            + " item(s) de liquidaciones ya emitidas");
        }
    }

    /**
     * Borrar un concepto ya usado deja recibos historicos sin etiqueta y sin forma de
     * saber si eran remunerativos: {@code liquidacion_item.codigo} es un String, no una
     * FK, asi que la base no lo impide. Para sacarlo de circulacion esta {@code activo =
     * false}, que lo saca del select de item manual sin tocar lo emitido.
     *
     * <p>Ademas CrudService.deleteById atrapa la excepcion y devuelve false, y el desktop
     * toma como exito cualquier respuesta sin errors: sin este chequeo la UI diria
     * "eliminado con exito" sin haber borrado nada. Mismo patron que
     * CargoGraphQL.deleteCargo.</p>
     */
    public Boolean deleteLiquidacionConcepto(Long id) throws GraphQLException {
        seg.requireAnyRole(seg.CONFIG, seg.GESTIONAR);
        LiquidacionConcepto concepto = service.findById(id).orElse(null);
        if (concepto == null) {
            throw new GraphQLException("No se puede eliminar: el concepto ya no existe");
        }
        if (concepto.getCodigo() != null) {
            Long items = liquidacionItemRepository.countByCodigo(concepto.getCodigo());
            if (items != null && items > 0) {
                throw new GraphQLException(
                        "No se puede eliminar: el concepto " + concepto.getCodigo() + " aparece en "
                                + items + " item(s) de liquidaciones ya emitidas. Desactivelo en vez de borrarlo");
            }
        }
        return service.deleteById(id);
    }
}
