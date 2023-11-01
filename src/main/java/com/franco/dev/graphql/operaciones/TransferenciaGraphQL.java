package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.TransferenciaInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private ImpresionService impresionService;

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    @Autowired
    private MovimientoStockService movimientoStockService;

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
        Long auxSucursalOrigenId = null;
        Long auxSucursalDestinoId = null;
        Transferencia transferencia = null;
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
        if(input.getId()!=null){
            transferencia = service.findById(input.getId()).orElse(null);
            auxSucursalOrigenId = transferencia.getSucursalOrigen().getId();
            auxSucursalDestinoId = transferencia.getSucursalDestino().getId();
        }
        e = service.save(e);
        propagacionService.propagarEntidad(e, TipoEntidad.TRANSFERENCIA, e.getSucursalOrigen().getId());
        propagacionService.propagarEntidad(e, TipoEntidad.TRANSFERENCIA, e.getSucursalDestino().getId());
        System.out.println(transferencia != null);
        System.out.println(input.getSucursalOrigenId() != auxSucursalOrigenId);
        System.out.println(input.getSucursalDestinoId() != auxSucursalDestinoId);
        if(transferencia != null && (!input.getSucursalOrigenId().equals(auxSucursalOrigenId) || !input.getSucursalDestinoId().equals(auxSucursalDestinoId))){
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemId(transferencia.getId());
            if(input.getSucursalOrigenId() != auxSucursalOrigenId){
                for (TransferenciaItem ti : transferenciaItemList) {
                    propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, e.getSucursalOrigen().getId());
                }
            }
            if(input.getSucursalDestinoId() != auxSucursalDestinoId){
                for (TransferenciaItem ti : transferenciaItemList) {
                    propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, e.getSucursalDestino().getId());
                }
            }
        }
        return e;
    }

    public Boolean deleteTransferencia(Long id) {
        Transferencia transferencia = service.findById(id).orElse(null);
        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaIdAndSucursalId(id);
        List<MovimientoStock> movimientoStockList = new ArrayList<>();
        for(TransferenciaItem ti: transferenciaItemList){
            MovimientoStock ms = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalId(TipoMovimiento.TRANSFERENCIA, ti.getId(), transferencia.getSucursalOrigen().getId());
            if(ms!=null){
                movimientoStockList.add(ms);
            }
        }
        Boolean ok = service.deleteById(id);
        if (ok) {
            for(MovimientoStock m: movimientoStockList){
                movimientoStockService.delete(m);
            }
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
                    for (TransferenciaItem ti : transferenciaItemList) {
                        if (ti.getVencimientoPreTransferencia() != null)
                            ti.setVencimientoPreparacion(ti.getVencimientoPreTransferencia());
                        ti.setPresentacionPreparacion(ti.getPresentacionPreTransferencia());
                        ti.setCantidadPreparacion(ti.getCantidadPreTransferencia());
                    }
                    break;
                case PREPARACION_MERCADERIA_CONCLUIDA:
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        if (ti.getVencimientoPreparacion() != null) {
                            ti.setVencimientoTransporte(ti.getVencimientoPreparacion());
                        }
                        if (ti.getMotivoRechazoPreparacion() != null) {
                            ti.setMotivoRechazoTransporte(ti.getMotivoRechazoPreparacion());
                        }
                        if (ti.getMotivoModificacionPreparacion() != null) {
                            ti.setMotivoModificacionTransporte(ti.getMotivoModificacionPreparacion());
                        }
                        ti.setPresentacionTransporte(ti.getPresentacionPreparacion());
                        ti.setCantidadTransporte(ti.getCantidadPreparacion());
                        }
                    break;
                case TRANSPORTE_VERIFICACION:
                    transferencia.setUsuarioTransporte(usuario);
                    transferencia.setEtapa(etapa);
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    for (TransferenciaItem ti : transferenciaItemList) {
                        if (ti.getVencimientoTransporte() != null)
                            ti.setVencimientoRecepcion(ti.getVencimientoTransporte());
                        if (ti.getMotivoRechazoTransporte() != null)
                            ti.setMotivoRechazoRecepcion(ti.getMotivoRechazoTransporte());
                        if (ti.getMotivoModificacionTransporte() != null)
                            ti.setMotivoModificacionRecepcion(ti.getMotivoModificacionTransporte());
                        ti.setPresentacionRecepcion(ti.getPresentacionTransporte());
                        ti.setCantidadRecepcion(ti.getCantidadTransporte());
                        }
                    break;
                case TRANSPORTE_EN_CAMINO:
                    transferencia.setEstado(TransferenciaEstado.EN_TRANSITO);
                    transferencia.setEtapa(etapa);
                    for (TransferenciaItem ti : transferenciaItemList) {
                        if (ti.getVencimientoTransporte() != null)
                            ti.setVencimientoRecepcion(ti.getVencimientoTransporte());
                        if (ti.getMotivoRechazoTransporte() != null)
                            ti.setMotivoRechazoRecepcion(ti.getMotivoRechazoTransporte());
                        if (ti.getMotivoModificacionTransporte() != null)
                            ti.setMotivoModificacionRecepcion(ti.getMotivoModificacionTransporte());
                        ti.setPresentacionRecepcion(ti.getPresentacionTransporte());
                        ti.setCantidadRecepcion(ti.getCantidadTransporte());
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
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
                    break;
            }
            transferencia = service.save(transferencia);
            propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
            propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalDestino().getId());
            for (TransferenciaItem ti : transferenciaItemList) {
                ti = transferenciaItemService.save(ti);
                propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalOrigen().getId());
                propagacionService.propagarEntidad(ti, TipoEntidad.TRANSFERENCIA_ITEM, transferencia.getSucursalDestino().getId());
            }
            ok = true;
        }
        return ok;
    }

    public Page<Transferencia> transferenciasWithFilters(Long sucursalOrigenId, Long sucursalDestinoId,
                                                         TransferenciaEstado estado, TipoTransferencia tipo,
                                                         EtapaTransferencia etapa, Boolean isOrigen, Boolean isDestino,
                                                         String creadoDesde, String creadoHasta, Integer page, Integer size) {
        if (page == null) page = 0;
        if (size == null) size = 20;
        Pageable pageable = PageRequest.of(page, size);
        return service.findByFilter(sucursalOrigenId, sucursalDestinoId, estado, tipo, etapa, isOrigen, isDestino, toDate(creadoDesde), toDate(creadoHasta), pageable);
    }

    public String imprimirTransferencia(Long id, Boolean ticket, String printerName) {
        Transferencia transferencia = service.findById(id).orElse(null);
        if (transferencia != null) {
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaItemIdAsc(id);
            return impresionService.imprimirTransferencia(transferencia, transferenciaItemList, ticket, printerName);
        } else {
            return "";
        }
    }

}
