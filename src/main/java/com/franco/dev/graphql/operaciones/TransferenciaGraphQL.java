package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.TransferenciaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class TransferenciaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TransferenciaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    public Optional<Transferencia> transferencia(Long id) {

        return service.findById(id);
    }

    public List<Transferencia> transferencias(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<Transferencia> transferenciaPorSucursalOrigenId(Long id) {
        return service.findBySucursalOrigenId(id);
    }

    public List<Transferencia> transferenciasPorUsuario(Long id) {
        return service.findByUsuario(id);
    }

    public List<Transferencia> transferenciaPorSucursalDesctinoId(Long id) {
        return service.findBySucursalDestinoId(id);
    }

    public List<Transferencia> transferenciaPorFecha(String start, String end) {
        if (end == null) {
            end = start;
        }
        return service.findByDate(start, end);
    }

    public Transferencia saveTransferencia(TransferenciaInput input) {
        ModelMapper m = new ModelMapper();
        Transferencia e = m.map(input, Transferencia.class);
        if (input.getUsuarioPreTransferenciaId() != null)
            e.setUsuarioPreTransferencia(usuarioService.findById(input.getUsuarioPreTransferenciaId()).orElse(null));
        if (input.getUsuarioPreparacionId() != null)
            e.setUsuarioPreparacion(usuarioService.findById(input.getUsuarioPreparacionId()).orElse(null));
        if (input.getUsuarioTransporteId() != null)
            e.setUsuarioTransporte(usuarioService.findById(input.getUsuarioTransporteId()).orElse(null));
        if (input.getUsuarioRecepcionId() != null)
            e.setUsuarioRecepcion(usuarioService.findById(input.getUsuarioRecepcionId()).orElse(null));
        e.setSucursalOrigen(sucursalService.findById(input.getSucursalOrigenId()).orElse(null));
        e.setSucursalDestino(sucursalService.findById(input.getSucursalDestinoId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteTransferencia(Long id) {
        Transferencia transferencia = service.findById(id).orElse(null);
        Boolean ok = service.deleteById(id);
        if (ok) {
            propagacionService.eliminarEntidad(transferencia.getId(), TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
            propagacionService.eliminarEntidad(transferencia.getId(), TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
        }
        return ok;
    }

    public Long countTransferencia() {
        return service.count();
    }

    public Boolean finalizarTransferencia(Long id, Long usuarioId) {
        Transferencia transferencia = service.findById(id).orElse(null);
        Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
        if (transferencia.getEstado() == TransferenciaEstado.ABIERTA) {
            transferencia.setEstado(TransferenciaEstado.EN_ORIGEN);
            transferencia.setEtapa(EtapaTransferencia.PRE_TRANSFERENCIA_ORIGEN);
            transferencia.setUsuarioPreTransferencia(usuario);
            transferencia = service.save(transferencia);
            transferencia.setIsDestino(false);
            transferencia.setIsOrigen(true);
            propagacionService.propagarTransferencia(transferencia, transferencia.getSucursalOrigen().getId());
            return true;
        } else {
            return false;
        }
    }

    public Boolean avanzarEtapaTransferencia(Long id, EtapaTransferencia etapa, Long usuarioId) {
        Boolean ok = false;
        Transferencia transferencia = transferencia(id).orElse(null);
        if (transferencia != null) {
            Usuario usuario = usuarioService.findById(usuarioId).orElse(null);
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(transferencia.getId());
            switch (etapa) {
                case PREPARACION_MERCADERIA:
                    transferencia.setUsuarioPreparacion(usuario);
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    break;
                case PREPARACION_MERCADERIA_CONCLUIDA:
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalOrigen().getId());
                    }
                    break;
                case TRANSPORTE_VERIFICACION:
                    transferencia.setUsuarioTransporte(usuario);
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    break;
                case TRANSPORTE_EN_CAMINO:
                    transferencia.setEstado(TransferenciaEstado.EN_TRANSITO);
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalOrigen().getId());
                        propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalDestino().getId());
                    }
                    break;
                case RECEPCION_EN_VERIFICACION:
                    transferencia.setUsuarioRecepcion(usuario);
                    transferencia.setEstado(TransferenciaEstado.EN_DESTINO);
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
                    break;
                case RECEPCION_CONCLUIDA:
                    transferencia.setEstado(TransferenciaEstado.CONLCUIDA);
                    transferencia.setEtapa(etapa);
                    for (TransferenciaItem ti : transferenciaItemList) {
                        propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalOrigen().getId());
                        propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalDestino().getId());
                    }
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
                    break;
            }
            service.save(transferencia);
            ok = true;
        }
        return ok;
    }

    public List<Transferencia> transferenciasWithFilters(Long sucursalOrigenId, Long sucursalDestinoId,
                                                         TransferenciaEstado estado, TipoTransferencia tipo,
                                                         EtapaTransferencia etapa, Boolean isOrigen, Boolean isDestino,
                                                         String creadoDesde, String creadoHasta, Integer page, Integer size) {
        if (page == null) page = 0;
        if (size == null) size = 20;
        Pageable pageable = PageRequest.of(page, size);
        return service.findByFilter(sucursalOrigenId, sucursalDestinoId, estado, tipo, etapa, isOrigen, isDestino, toDate(creadoDesde), toDate(creadoHasta), pageable);
    }

}
