package com.franco.dev.service.financiero;

import com.franco.dev.domain.EmbebedPrimaryKey;
import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.repository.financiero.BancoRepository;
import com.franco.dev.repository.financiero.GastoRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.personas.UsuarioService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.List;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Service
@AllArgsConstructor
public class GastoService extends CrudService<Gasto, GastoRepository, EmbebedPrimaryKey> {

    private final GastoRepository repository;

    public static final DecimalFormat df = new DecimalFormat("#,###.##");

    @Override
    public GastoRepository getRepository() {
        return repository;
    }

    @Autowired
    private MonedaService monedaService;

    @Autowired
    private MovimientoCajaService movimientoCajaService;

    @Autowired
    private CambioService cambioService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private InicioSesionService inicioSesionService;

    @Autowired
    private SucursalService sucursalService;

    @Autowired
    private PushNotificationService pushNotificationService;

//    public List<Gasto> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

    public List<Gasto> findByDate(String inicio, String fin, Long sucId){
        return repository.findBySucursalIdAndCreadoEnBetween(sucId, toDate(inicio), toDate(fin));
    }

    public List<Gasto> filterGastos(Long id, Long cajaId, Long sucId, Long responsableId, Pageable pageable){
        return repository.findByAll(id, cajaId, sucId, responsableId, pageable);
    }

    public List<Gasto> findByCajaId(Long id, Long sucId) {
        return repository.findByCajaIdAndSucursalId(id, sucId);
    }

    @Override
    public Gasto save(Gasto entity) {
        Gasto e = super.save(entity);
        Usuario usuario = usuarioService.findByPersonaId(entity.getResponsable().getPersona().getId());
        Page<InicioSesion> inicioSesionPage = inicioSesionService.findByUsuarioIdAndHoraFinIsNul(usuario.getId(), Long.valueOf(0), PageRequest.of(0, 1));
        for(InicioSesion inicioSesion : inicioSesionPage.getContent()){
            if(inicioSesion.getToken() != null){
                PushNotificationRequest pNr = new PushNotificationRequest();
                Sucursal sucursal = sucursalService.findById(entity.getSucursalId()).orElse(null);
                pNr.setTitle("Gasto realizado");
                StringBuilder stb = new StringBuilder();
                stb.append("Se ha detectado un gasto a tu nombre en la sucursal ");
                stb.append(sucursal.getNombre());
                stb.append(" por el valor de ");
                if(entity.getRetiroGs() > 0){
                    stb.append(df.format(entity.getRetiroGs()));
                    stb.append(" Gs. ");
                }
                if(entity.getRetiroRs() > 0){
                    stb.append(df.format(entity.getRetiroRs()));
                    stb.append(", Rs. ");
                }
                if(entity.getRetiroDs() > 0){
                    stb.append(df.format(entity.getRetiroDs()));
                    stb.append(", Ds. ");
                }
                stb.append("Si desconoce ésta acción contactar con el cajero ");
                stb.append(entity.getUsuario().getNickname().toUpperCase());
                stb.append(" al número ");
                stb.append(sucursal.getNroDelivery());
                pNr.setMessage(stb.toString());
                pNr.setToken(inicioSesion.getToken());
                pNr.setData("/");
                pushNotificationService.sendPushNotificationToToken(pNr);
            }
        }
        return e;
    }
}