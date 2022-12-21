package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.operaciones.VentaService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class PdvCajaService extends CrudService<PdvCaja, PdvCajaRepository, EmbebedPrimaryKey> {

    private final PdvCajaRepository repository;
    @Autowired
    private MaletinService maletinService;
    @Autowired
    private ConteoMonedaService conteoMonedaService;
    @Autowired
    private MovimientoCajaService movimientoCajaService;
    @Autowired
    private ImpresionService impresionService;
    @Autowired
    private CobroDetalleService cobroDetalleService;
    @Autowired
    private RetiroDetalleService retiroDetalleService;
    @Autowired
    private GastoService gastoService;
    @Autowired
    private VentaService ventaService;
    @Autowired
    private CobroService cobroService;
    @Autowired
    private ConteoService conteoService;
    @Autowired
    private SucursalService sucursalService;

    @Override
    public PdvCajaRepository getRepository() {
        return repository;
    }

    public Optional<PdvCaja> findById(Long id, Long sucId) {
        return repository.findById(new EmbebedPrimaryKey(id, sucId));
    }

    public List<PdvCaja> findByDate(String inicio, String fin, Long sucId) {
        if (sucId == null) return repository.findByCreadoEnBetween(toDate(inicio), toDate(fin));
        return repository.findBySucursalIdAndCreadoEnBetweenOrderByIdDesc(sucId, toDate(inicio), toDate(fin));
    }

    @Override
    public PdvCaja save(PdvCaja entity) {
        Maletin m = null;
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getMaletin() != null) {
            m = maletinService.findById(entity.getMaletin().getId()).orElse(null);
            if (entity.getActivo() == true) {
                if (m != null) {
                    m.setAbierto(true);
                }
            } else {
                if (m != null) {
                    m.setAbierto(false);
                }
            }
            maletinService.save(m);
        }

        PdvCaja e = super.save(entity);
        return e;
    }

    public List<PdvCaja> findByUsuarioIdAndAbierto(Long id) {
        return repository.findByUsuarioIdAndActivo(id, true);
    }

    @Override
    public Boolean deleteById(EmbebedPrimaryKey id) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        maletinService.cerrarMaletin(pdvCaja.getMaletin().getId());
        return super.deleteById(new EmbebedPrimaryKey(pdvCaja.getId(), pdvCaja.getSucursalId()));
    }

//    public PdvCajaBalanceDto generarBalance(PdvCaja pdvCaja){
//        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
//        if(pdvCaja!=null){
//            balance.setIdCaja(pdvCaja.getId());
//            List<ConteoMoneda> conteoMonedaAperList = conteoMonedaService.findByConteoId(pdvCaja.getConteoApertura().getId());
//            List<ConteoMoneda> conteoMonedaCierreList = conteoMonedaService.findByConteoId(pdvCaja.getConteoCierre().getId());
//            List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCajaId(pdvCaja.getId());
//            List<RetiroDetalle> retiroDetalleList = retiroDetalleService.findByCajId(pdvCaja.getId());
//            List<Gasto> gastoList = gastoService.findByCajaId(pdvCaja.getId());
//            if(!conteoMonedaAperList.isEmpty() && !conteoMonedaCierreList.isEmpty()){
//                Double totalGsAper = 0.0;
//                Double totalRsAper = 0.0;
//                Double totalDsAper = 0.0;
//                Double totalGsCierre = 0.0;
//                Double totalRsCierre = 0.0;
//                Double totalDsCierre = 0.0;
//                for(ConteoMoneda c: conteoMonedaAperList){
//                    if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")){
//                        totalGsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    } else if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")){
//                        totalRsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    } else if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")){
//                        totalDsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    }
//                }
//                for(ConteoMoneda c: conteoMonedaCierreList){
//                    if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")){
//                        totalGsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    } else if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")){
//                        totalRsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    } else if(c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")){
//                        totalDsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
//                    }
//                }
//                balance.setTotalGsAper(totalGsAper);
//                balance.setTotalRsAper(totalRsAper);
//                balance.setTotalDsAper(totalDsAper);
//                balance.setTotalGsCierre(totalGsCierre);
//                balance.setTotalRsCierre(totalRsCierre);
//                balance.setTotalDsCierre(totalDsCierre);
//
//            }
////            List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByPdvCajaId(pdvCaja.getId());
//            Double totalVentaGs = 0.0;
//            Double totalVentaRs = 0.0;
//            Double totalVentaDs = 0.0;
//            Double totalRetiroGs = 0.0;
//            Double totalRetiroRs = 0.0;
//            Double totalRetiroDs = 0.0;
//            Double totalTarjeta = 0.0;
//            Double totalGastoGs = 0.0;
//            Double totalGastoRs = 0.0;
//            Double totalGastoDs = 0.0;
//            for(RetiroDetalle retiroDetalle: retiroDetalleList){
//                if(retiroDetalle.getMoneda().getDenominacion().contains("GUARANI")){
//                    totalRetiroGs += retiroDetalle.getCantidad();
//                }
//                else if(retiroDetalle.getMoneda().getDenominacion().contains("REAL")){
//                    totalRetiroRs += retiroDetalle.getCantidad();
//
//                }
//                else if(retiroDetalle.getMoneda().getDenominacion().contains("DOLAR")){
//                    totalRetiroDs += retiroDetalle.getCantidad();
//                }
//            }
//            for(Gasto gasto: gastoList){
//                totalGastoGs += gasto.getRetiroGs();
//                totalGastoRs += gasto.getRetiroRs();
//                totalGastoDs += gasto.getRetiroDs();
//            }
//            for(CobroDetalle cobroDetalle: cobroDetalleList){
//                if(cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")){
//                    if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
//                        totalVentaGs += cobroDetalle.getValor();
//                    } else if(cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")){
//                        totalTarjeta += cobroDetalle.getValor();
//                    }
//                }
//                else if(cobroDetalle.getMoneda().getDenominacion().contains("REAL")){
//                    if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
//                        totalVentaRs += cobroDetalle.getValor();
//                    }
//                }
//                else if(cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")){
//                    if(cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")){
//                        totalVentaDs += cobroDetalle.getValor();
//                    }
//                }
//            }
//            balance.setTotalRetiroGs(totalRetiroGs);
//            balance.setTotalRetiroRs(totalRetiroRs);
//            balance.setTotalRetiroDs(totalRetiroDs);
//            balance.setTotalGastoGs(totalGastoGs);
//            balance.setTotalGastoRs(totalGastoRs);
//            balance.setTotalGastoDs(totalGastoDs);
//            balance.setTotalTarjeta(totalTarjeta);
//            balance.setTotalVentaGs(totalVentaGs);
//            balance.setTotalVentaRs(totalVentaRs);
//            balance.setTotalVentaDs(totalVentaDs);
//            balance.setUsuario(pdvCaja.getUsuario());
//            balance.setFechaApertura(pdvCaja.getFechaApertura());
//            balance.setFechaCierre(pdvCaja.getFechaCierre());
//            balance.setDiferenciaGs(balance.getTotalGsCierre() - balance.getTotalGsAper() + balance.getTotalRetiroGs() + balance.getTotalGastoGs() - balance.getTotalVentaGs());
//            balance.setDiferenciaRs(balance.getTotalRsCierre() - balance.getTotalRsAper() + balance.getTotalRetiroRs() + balance.getTotalGastoRs() - balance.getTotalVentaRs());
//            balance.setDiferenciaDs(balance.getTotalDsCierre() - balance.getTotalDsAper() + balance.getTotalRetiroDs() + balance.getTotalGastoDs() - balance.getTotalVentaDs());
//        }
//        return balance;
//    }

    public PdvCajaBalanceDto generarBalance(PdvCaja pdvCaja) {
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        if (pdvCaja != null && pdvCaja.getConteoApertura() != null) {
            balance.setIdCaja(pdvCaja.getId());
            List<ConteoMoneda> conteoMonedaAperList = conteoMonedaService.findByConteoId(pdvCaja.getConteoApertura().getId(), pdvCaja.getSucursalId());
            List<ConteoMoneda> conteoMonedaCierreList = conteoMonedaService.findByConteoId(pdvCaja.getConteoCierre().getId(), pdvCaja.getSucursalId());
            List<RetiroDetalle> retiroDetalleList = retiroDetalleService.findByCajId(pdvCaja.getId(), pdvCaja.getSucursalId());
            List<Gasto> gastoList = gastoService.findByCajaId(pdvCaja.getId(), pdvCaja.getSucursalId());
            List<Venta> ventaList = ventaService.findAllByCajaId(new EmbebedPrimaryKey(pdvCaja.getId(), pdvCaja.getSucursalId()));
            if (!conteoMonedaAperList.isEmpty() && !conteoMonedaCierreList.isEmpty()) {
                Double totalGsAper = 0.0;
                Double totalRsAper = 0.0;
                Double totalDsAper = 0.0;
                Double totalGsCierre = 0.0;
                Double totalRsCierre = 0.0;
                Double totalDsCierre = 0.0;


                for (ConteoMoneda c : conteoMonedaAperList) {
                    if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")) {
                        totalGsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
                    } else if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")) {
                        totalRsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
                    } else if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")) {
                        totalDsAper += c.getCantidad() * c.getMonedaBilletes().getValor();
                    }
                }
                for (ConteoMoneda c : conteoMonedaCierreList) {
                    if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("GUARANI")) {
                        totalGsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
                    } else if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("REAL")) {
                        totalRsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
                    } else if (c.getMonedaBilletes().getMoneda().getDenominacion().contains("DOLAR")) {
                        totalDsCierre += c.getCantidad() * c.getMonedaBilletes().getValor();
                    }
                }
                balance.setTotalGsAper(totalGsAper);
                balance.setTotalRsAper(totalRsAper);
                balance.setTotalDsAper(totalDsAper);
                balance.setTotalGsCierre(totalGsCierre);
                balance.setTotalRsCierre(totalRsCierre);
                balance.setTotalDsCierre(totalDsCierre);

            }
//            List<MovimientoCaja> movimientoCajaList = movimientoCajaService.findByPdvCajaId(pdvCaja.getId());
            Double totalGeneral = 0.0;
            Double totalVentaGs = 0.0;
            Double totalVentaRs = 0.0;
            Double totalVentaDs = 0.0;
            Double totalRetiroGs = 0.0;
            Double totalRetiroRs = 0.0;
            Double totalRetiroDs = 0.0;
            Double totalTarjeta = 0.0;
            Double totalConvenio = 0.0;
            Double totalGastoGs = 0.0;
            Double totalGastoRs = 0.0;
            Double totalGastoDs = 0.0;
            Double totalDescuento = 0.0;
            Double totalAumento = 0.0;
            Double totalCanceladasGs = 0.0;
            Double totalCanceladasRs = 0.0;
            Double totalCanceladasDs = 0.0;
            Double vueltoGs = 0.0;
            Double vueltoRs = 0.0;
            Double vueltoDs = 0.0;

            for (RetiroDetalle retiroDetalle : retiroDetalleList) {
                if (retiroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                    totalRetiroGs += retiroDetalle.getCantidad();
                } else if (retiroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                    totalRetiroRs += retiroDetalle.getCantidad();

                } else if (retiroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                    totalRetiroDs += retiroDetalle.getCantidad();
                }
            }
            for (Gasto gasto : gastoList) {
                totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs());
                totalGastoRs += (gasto.getRetiroRs() - gasto.getVueltoRs());
                totalGastoDs += (gasto.getRetiroDs() - gasto.getVueltoDs());
            }

            for (Venta venta : ventaList) {
                totalGeneral += venta.getTotalGs();

                Cobro cobro = cobroService.findById(venta.getCobro().getId(), venta.getSucursalId());
                if (cobro != null) {
                    List<CobroDetalle> cobroDetalleList = cobroDetalleService.findByCobroId(cobro.getId(), cobro.getSucursalId());
                    if (venta.getEstado() == VentaEstado.CONCLUIDA || venta.getEstado() == VentaEstado.EN_VERIFICACION) {
                        for (CobroDetalle cobroDetalle : cobroDetalleList) {
                            if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getDescuento()) {
                                        totalDescuento += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getVuelto()) {
                                        vueltoGs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago()) {
                                        totalVentaGs += cobroDetalle.getValor();
                                    }
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("TARJETA")) {
                                    totalTarjeta += cobroDetalle.getValor();
                                } else if (cobroDetalle.getFormaPago().getDescripcion().contains("CONVENIO")) {
                                    totalConvenio += cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor() * cobroDetalle.getCambio();
                                    } else if (cobroDetalle.getVuelto()) {
                                        vueltoRs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago()) {
                                        totalVentaRs += cobroDetalle.getValor();
                                    }
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                                if (cobroDetalle.getFormaPago().getDescripcion().contains("EFECTIVO")) {
                                    if (cobroDetalle.getAumento()) {
                                        totalAumento += cobroDetalle.getValor() * cobroDetalle.getCambio();
                                    } else if (cobroDetalle.getVuelto()) {
                                        vueltoDs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago()) {
                                        totalVentaDs += cobroDetalle.getValor();
                                    } else if (cobroDetalle.getPago()) {
                                        totalVentaDs += cobroDetalle.getValor();
                                    }
                                }
                            }
                        }
                    } else if (venta.getEstado() == VentaEstado.CANCELADA) {
                        for (CobroDetalle cobroDetalle : cobroDetalleList) {
                            if (cobroDetalle.getMoneda().getDenominacion().contains("GUARANI")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasGs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoGs -= cobroDetalle.getValor();
                                } else if (cobroDetalle.getDescuento()) {
                                    totalDescuento -= cobroDetalle.getValor();
                                } else if (cobroDetalle.getAumento()) {
                                    totalAumento -= cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("REAL")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasRs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoRs -= cobroDetalle.getValor();
                                }
                            } else if (cobroDetalle.getMoneda().getDenominacion().contains("DOLAR")) {
                                if (cobroDetalle.getPago()) {
                                    totalCanceladasDs += cobroDetalle.getValor();
                                } else if (cobroDetalle.getVuelto()) {
                                    vueltoDs -= cobroDetalle.getValor();
                                }
                            }
                        }
                    }
                }
            }
            balance.setTotalGeneral(totalGeneral - totalDescuento);
            balance.setVueltoGs(vueltoGs);
            balance.setVueltoRs(vueltoRs);
            balance.setVueltoDs(vueltoDs);
            balance.setTotalCanceladasGs(totalCanceladasGs);
            balance.setTotalCanceladasRs(totalCanceladasRs);
            balance.setTotalCanceladasDs(totalCanceladasDs);
            balance.setTotalDescuento(totalDescuento);
            balance.setTotalAumento(totalAumento);
            balance.setTotalRetiroGs(totalRetiroGs);
            balance.setTotalRetiroRs(totalRetiroRs);
            balance.setTotalRetiroDs(totalRetiroDs);
            balance.setTotalGastoGs(totalGastoGs);
            balance.setTotalGastoRs(totalGastoRs);
            balance.setTotalGastoDs(totalGastoDs);
            balance.setTotalTarjeta(totalTarjeta);
            balance.setTotalCredito(totalConvenio);
            balance.setTotalVentaGs(totalVentaGs);
            balance.setTotalVentaRs(totalVentaRs);
            balance.setTotalVentaDs(totalVentaDs);
            balance.setUsuario(pdvCaja.getUsuario());
            balance.setFechaApertura(pdvCaja.getFechaApertura());
            balance.setFechaCierre(pdvCaja.getFechaCierre());
            balance.setDiferenciaGs(balance.getTotalGsCierre() - balance.getTotalGsAper() - totalVentaGs - vueltoGs + totalRetiroGs + totalGastoGs + totalDescuento);
            balance.setDiferenciaRs(balance.getTotalRsCierre() - balance.getTotalRsAper() - totalVentaRs - vueltoRs + totalRetiroRs + totalGastoRs);
            balance.setDiferenciaDs(balance.getTotalDsCierre() - balance.getTotalDsAper() - totalVentaDs - vueltoDs + totalRetiroDs + totalGastoDs);
            balance.setSucursal((Sucursal) sucursalService.findById(pdvCaja.getSucursalId()).orElse(null));
        }
        return balance;
    }

    public PdvCaja imprimirBalance(EmbebedPrimaryKey id, String printerName, String local) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        if (pdvCaja != null) {
            PdvCajaBalanceDto balanceDto = generarBalance(pdvCaja);
            impresionService.printBalance(balanceDto, printerName, local);
        }
        return pdvCaja;
    }

    public CajaBalance getBalance(EmbebedPrimaryKey id) {
        PdvCaja pdvCaja = findById(id).orElse(null);
        CajaBalance balance = new CajaBalance();
        if (pdvCaja != null) {
            PdvCajaBalanceDto balanceDto = generarBalance(pdvCaja);
            balance.setCajaId(id.getId());
            balance.setTotalGeneral(balanceDto.getTotalGeneral());
            balance.setVueltoGs(balanceDto.getVueltoGs());
            balance.setVueltoRs(balanceDto.getVueltoRs());
            balance.setVueltoDs(balanceDto.getVueltoDs());
            balance.setTotalVentaGs(balanceDto.getTotalVentaGs());
            balance.setTotalVentaRs(balanceDto.getTotalVentaRs());
            balance.setTotalVentaDs(balanceDto.getTotalVentaDs());
            balance.setTotalTarjeta(balanceDto.getTotalTarjeta());
            balance.setTotalCredito(balanceDto.getTotalCredito());
            balance.setTotalRetiroGs(balanceDto.getTotalRetiroGs());
            balance.setTotalRetiroRs(balanceDto.getTotalRetiroRs());
            balance.setTotalRetiroDs(balanceDto.getTotalRetiroDs());
            balance.setTotalGastoGs(balanceDto.getTotalGastoGs());
            balance.setTotalGastoRs(balanceDto.getTotalGastoRs());
            balance.setTotalGastoDs(balanceDto.getTotalGastoDs());
            balance.setTotalAperGs(balanceDto.getTotalGsAper());
            balance.setTotalAperRs(balanceDto.getTotalRsAper());
            balance.setTotalAperDs(balanceDto.getTotalDsAper());
            balance.setTotalCierreGs(balanceDto.getTotalGsCierre());
            balance.setTotalCierreRs(balanceDto.getTotalRsCierre());
            balance.setTotalCierreDs(balanceDto.getTotalDsCierre());
            balance.setTotalDescuento(balanceDto.getTotalDescuento());
            balance.setTotalAumento(balanceDto.getTotalAumento());
            balance.setTotalCanceladasGs(balanceDto.getTotalCanceladasGs());
            balance.setTotalCanceladasRs(balanceDto.getTotalCanceladasRs());
            balance.setTotalCanceladasDs(balanceDto.getTotalCanceladasDs());
            balance.setDiferenciaGs(balanceDto.getDiferenciaGs());
            balance.setDiferenciaRs(balanceDto.getDiferenciaRs());
            balance.setDiferenciaDs(balanceDto.getDiferenciaDs());
        }
        return balance;
    }

    public PdvCaja findLastByMaletinId(Long id) {
        PdvCaja caja = repository.findFirstByMaletinIdOrderByCreadoEnDesc(id).orElse(null);
        return caja;
    }

    public List<PdvCaja> findByUsuarioId(Long id, Integer page, Integer size){
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByUsuarioIdOrderByIdDesc(id, pageable);
    }
}

