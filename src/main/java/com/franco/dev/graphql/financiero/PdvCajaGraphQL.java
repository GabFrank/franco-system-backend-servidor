package com.franco.dev.graphql.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.SolicitudAperturaCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import com.franco.dev.graphql.financiero.input.ConteoInput;
import com.franco.dev.graphql.financiero.input.ConteoMonedaInput;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.service.financiero.ConteoService;
import com.franco.dev.service.financiero.MaletinService;
import com.franco.dev.service.financiero.PdvCajaService;
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

import java.util.List;

@Component
public class PdvCajaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvCajaService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ConteoService conteoService;

    @Autowired
    private MaletinService maletinService;

    @Autowired
    private ConteoGraphQL conteoGraphQL;

    @Autowired
    private ConteoMonedaGraphQL conteoMonedaGraphQL;

    @Autowired
    private PropagacionService propagacionService;

    @Autowired
    private MultiTenantService multiTenantService;

    public PdvCaja pdvCaja(Long id, Long sucId) {
        PdvCaja aux = service.findById(id, sucId);
        return aux;
    }

    public List<PdvCaja> pdvCajas(int page, int size, Long sucId) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public List<PdvCaja> cajasPorFecha(String inicio, String fin, Long sucId) {
        return service.findByDate(inicio, fin, sucId);
    }

    public Page<PdvCaja> cajasWithFilters(Long cajaId, PdvCajaEstado estado, Long maletinId, Long cajeroId, String fechaInicio, String fechaFin, Long sucId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAllWithFilters(cajaId, estado, maletinId, cajeroId, fechaInicio, fechaFin, sucId, pageable);
    }

    public CajaBalance balancePorFecha(String inicio, String fin, Long sucId) {
        List<PdvCaja> pdvCajaList = service.findByDate(inicio, fin, sucId);
        Double totalGeneral = 0.0;
        for (PdvCaja c : pdvCajaList) {
            CajaBalance cb = service.getBalance(new EmbebedPrimaryKey(c.getId(), sucId));
            totalGeneral += cb.getTotalGeneral();
        }
        CajaBalance cajaBalance = new CajaBalance();
        cajaBalance.setTotalGeneral(totalGeneral);
        return cajaBalance;
    }


    public PdvCaja savePdvCaja(PdvCajaInput input) {
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getVerificadoPorId() != null) {
            e.setVerificadoPor(usuarioService.findById(input.getVerificadoPorId()).orElse(null));
        }
        if (input.getConteoAperturaId() != null)
            e.setConteoApertura(conteoService.findById(new EmbebedPrimaryKey(input.getConteoAperturaId(), input.getSucursalId())).orElse(null));
        if (input.getConteoCierreId() != null)
            e.setConteoCierre(conteoService.findById(new EmbebedPrimaryKey(input.getConteoCierreId(), input.getSucursalId())).orElse(null));
        if (input.getMaletinId() != null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        service.save(e);
        return e;
    }

    public PdvCaja savePdvCajaPorSucursal(PdvCajaInput input) {
        ModelMapper m = new ModelMapper();
        PdvCaja e = m.map(input, PdvCaja.class);
        if (input.getUsuarioId() != null) {
            e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        }
        if (input.getConteoAperturaId() != null)
            e.setConteoApertura(conteoService.findById(new EmbebedPrimaryKey(input.getConteoAperturaId(), input.getSucursalId())).orElse(null));
        if (input.getConteoCierreId() != null)
            e.setConteoCierre(conteoService.findById(new EmbebedPrimaryKey(input.getConteoCierreId(), input.getSucursalId())).orElse(null));
        if (input.getMaletinId() != null) e.setMaletin(maletinService.findById(input.getMaletinId()).orElse(null));
        service.save(e);
        return e;
    }

    //    public List<PdvCaja> pdvCajasSearch(String texto){
    //        return service.findByAll(texto);
    //    }

    public Long countPdvCaja() {
        return service.count();
    }

    public List<PdvCaja> cajaAbiertoPorUsuarioId(Long id) {
        return service.findByUsuarioIdAndAbierto(id);
    }

    public PdvCaja cajaAbiertoPorUsuarioIdPorSucursal(Long id, Long sucId) {
//        return propagacionService.buscarCajaAbiertaPorSucursal(id, sucId);
        return null;
    }

    public PdvCaja imprimirBalance(Long id, String printerName, String local, Long sucId) {
        return service.imprimirBalance(new EmbebedPrimaryKey(id, sucId), printerName, local);
    }

    public List<PdvCaja> cajasPorUsuarioId(Long id, Integer page, Integer size) {
        return service.findByUsuarioId(id, page, size);
    }

    public Boolean abrirCajaDesdeServidor(PdvCajaInput input, ConteoInput conteoInput, List<ConteoMonedaInput> conteoMonedaInputList) {
        try {
            SolicitudAperturaCaja solicitudAperturaCaja = new SolicitudAperturaCaja();
            solicitudAperturaCaja.setCajaInput(input);
            solicitudAperturaCaja.setConteoInput(conteoInput);
            solicitudAperturaCaja.setConteoMonedaInputList(conteoMonedaInputList);
//            propagacionService.propagarEntidad(solicitudAperturaCaja, TipoEntidad.SOLICITUD_APERTURA_CAJA, solicitudAperturaCaja.getCajaInput().getSucursalId());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
