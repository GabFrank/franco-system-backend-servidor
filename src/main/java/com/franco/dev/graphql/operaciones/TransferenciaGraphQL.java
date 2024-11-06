package com.franco.dev.graphql.operaciones;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.domain.operaciones.enums.EtapaTransferencia;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.domain.operaciones.enums.TipoTransferencia;
import com.franco.dev.domain.operaciones.enums.TransferenciaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.operaciones.input.TransferenciaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import com.franco.dev.service.operaciones.TransferenciaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.GraphQLException;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.stringToDate;

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

    @Autowired
    private MultiTenantService multiTenantService;

    @Autowired
    private PlatformTransactionManager transactionManager;


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

    //    @MultiTenantTransactional
    public Transferencia saveTransferencia(TransferenciaInput input) {
        ModelMapper m = new ModelMapper();
        Transferencia e = m.map(input, Transferencia.class);
        Long oldSucursalOrigenId = null;
        Long oldSucursalDestinoId = null;
        Long newSucursalOrigenId = null;
        Long newSucursalDestinoId = null;
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
        if (input.getId() != null) {
            transferencia = service.findById(input.getId()).orElse(null);
            if (!transferencia.getSucursalOrigen().getId().equals(input.getSucursalOrigenId())) { // si no es igual
                newSucursalOrigenId = input.getSucursalOrigenId();
                oldSucursalOrigenId = transferencia.getSucursalOrigen().getId();
            }
            if (!transferencia.getSucursalDestino().getId().equals(input.getSucursalDestinoId())) { // si no es igual
                newSucursalDestinoId = input.getSucursalDestinoId();
                oldSucursalDestinoId = transferencia.getSucursalDestino().getId();
            }
        }
        if(input.getCreadoEn()!=null) e.setCreadoEn(stringToDate(input.getCreadoEn()));
        e = service.save(e);
        if (newSucursalOrigenId != null) {
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaId(transferencia.getId());
            for (TransferenciaItem ti : transferenciaItemList) {
                ti = transferenciaItemService.save(ti);
                movimientoStockService.createMovimientoFromTransferenciaItem(ti);
            }
//            deleteTransferenciaByTenant(e.getId(), oldSucursalOrigenId);
        }

        if (newSucursalDestinoId != null) {
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaId(transferencia.getId());
            for (TransferenciaItem ti : transferenciaItemList) {
                ti = transferenciaItemService.save(ti);
                movimientoStockService.createMovimientoFromTransferenciaItem(ti);
            }
//            multiTenantService.compartir("filial" + oldSucursalDestinoId + "_bkp", (params) -> deleteTransferenciaByTenant((Long) params[0], (String) params[1]), e.getId(), oldSucursalDestinoId);
        }

        return e;
    }

    public Boolean deleteTransferencia(Long id) {
        try {
            Transferencia transferencia = service.findById(id).orElse(null);
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaIdAndSucursalId(id);
            List<MovimientoStock> movimientoStockSalidaList = new ArrayList<>();
            List<MovimientoStock> movimientoStockEntradaList = new ArrayList<>();
            for (TransferenciaItem ti : transferenciaItemList) {
                MovimientoStock auxSalida = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalOrigen().getId(), ti.getPresentacionPreparacion().getProducto().getId());
                MovimientoStock auxEntrada = movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalDestino().getId(), ti.getPresentacionPreparacion().getProducto().getId());
                if (auxSalida != null) {
                    movimientoStockSalidaList.add(auxSalida);
                }
                if (auxEntrada != null) {
                    movimientoStockEntradaList.add(auxEntrada);
                }
            }

            for (MovimientoStock m : movimientoStockSalidaList) {
                movimientoStockService.delete(m);
            }

            for (MovimientoStock m : movimientoStockEntradaList) {
                movimientoStockService.delete(m);
            }

            Boolean ok = service.deleteById(id);
            return ok;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo eliminar la transferencia");
        }
    }

//    public Boolean deleteTransferenciaByTenant(Long id, String tenantId) {
//        Transferencia transferencia = service.findById(id).orElse(null);
//        List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaIdAndSucursalId(id);
//        List<MovimientoStock> movimientoStockSalidaList = new ArrayList<>();
//        List<MovimientoStock> movimientoStockEntradaList = new ArrayList<>();
////        Boolean ok = multiTenantService.compartir("default", (params) -> service.deleteById(id), id);
//        for (TransferenciaItem ti : transferenciaItemList) {
//            MovimientoStock auxSalida = multiTenantService.compartir("filial" + tenantId + "_bkp", (params) -> movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalOrigen().getId(), ti.getPresentacionPreparacion().getProducto().getId()), TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalOrigen().getId(), ti.getPresentacionPreparacion().getProducto().getId());
//            MovimientoStock auxEntrada = multiTenantService.compartir("filial" + tenantId + "_bkp", (params) -> movimientoStockService.findByTipoMovimientoAndReferenciaAndSucursalIdAndProductoId(TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalDestino().getId(), ti.getPresentacionPreparacion().getProducto().getId()), TipoMovimiento.TRANSFERENCIA, ti.getId(), ti.getTransferencia().getSucursalDestino().getId(), ti.getPresentacionPreparacion().getProducto().getId());
//            if (auxSalida != null) {
//                movimientoStockSalidaList.add(auxSalida);
//            }
//            if (auxEntrada != null) {
//                movimientoStockEntradaList.add(auxEntrada);
//            }
//        }
//
//        for (MovimientoStock m : movimientoStockSalidaList) {
//            multiTenantService.compartir("filial" + tenantId + "_bkp", (MovimientoStock s) -> movimientoStockService.delete(s), m);
//        }
//
//        for (MovimientoStock m : movimientoStockEntradaList) {
//            multiTenantService.compartir("filial" + tenantId + "_bkp", (MovimientoStock s) -> movimientoStockService.delete(s), m);
//        }
//
//        return multiTenantService.compartir("filial" + tenantId + "_bkp", (Transferencia s) -> service.delete(s), transferencia);
//    }


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
            List<TransferenciaItem> transferenciaItemList = transferenciaItemService.findByTransferenciaId(transferencia.getId());
            switch (etapa) {
                case PREPARACION_MERCADERIA:
                    transferencia.setUsuarioPreparacion(usuario);
                    transferencia.setEtapa(etapa);
                    for (TransferenciaItem ti : transferenciaItemList) {
                        if (ti.getVencimientoPreTransferencia() != null)
                            ti.setVencimientoPreparacion(ti.getVencimientoPreTransferencia());
                        ti.setPresentacionPreparacion(ti.getPresentacionPreTransferencia());
                        ti.setCantidadPreparacion(ti.getCantidadPreTransferencia());
                        transferenciaItemService.save(ti);
                        movimientoStockService.createMovimientoFromTransferenciaItem(ti);
                    }
                    break;
                case PREPARACION_MERCADERIA_CONCLUIDA:
                    transferencia.setEtapa(etapa);
//                    propagacionService.propagarEntidad(transferencia, TipoEntidad.TRANSFERENCIA, transferencia.getSucursalOrigen().getId());
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
                        ti = transferenciaItemService.save(ti);
                        movimientoStockService.createMovimientoFromTransferenciaItem(ti);
                    }
                    break;
                case TRANSPORTE_EN_CAMINO:
                    transferencia.setEstado(TransferenciaEstado.EN_TRANSITO);
                    transferencia.setEtapa(etapa);
                    break;
                case RECEPCION_EN_VERIFICACION:
                    transferencia.setUsuarioRecepcion(usuario);
                    transferencia.setEstado(TransferenciaEstado.EN_DESTINO);
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
                        ti = transferenciaItemService.save(ti);
                        movimientoStockService.createMovimientoFromTransferenciaItem(ti);
                    }
                    break;
                case RECEPCION_CONCLUIDA:
                    transferencia.setEstado(TransferenciaEstado.CONLCUIDA);
                    transferencia.setEtapa(etapa);
                    for (TransferenciaItem ti : transferenciaItemList) {
                        movimientoStockService.createMovimientoFromTransferenciaItem(ti);
                    }
                    break;
            }
            transferencia = service.save(transferencia);

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
        return service.findByFilter(sucursalOrigenId, sucursalDestinoId, estado, tipo, etapa, isOrigen, isDestino, stringToDate(creadoDesde), stringToDate(creadoHasta), pageable);
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
