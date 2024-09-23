package com.franco.dev.service.financiero;

import com.franco.dev.config.multitenant.MultiTenantService;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.repository.financiero.MaletinRepository;
import com.franco.dev.service.CrudService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class MaletinService extends CrudService<Maletin, MaletinRepository, Long> {

    private final MaletinRepository repository;
    @Autowired
    private PropagacionService propagacionService;
    @Autowired
    private MultiTenantService multiTenantService;

    @Override
    public MaletinRepository getRepository() {
        return repository;
    }

//    public List<Maletin> findByDenominacion(String texto){
//        texto = texto.replace(' ', '%');
//        return  repository.findByDenominacionIgnoreCaseLike(texto);
//    }

//    public Page<Maletin> findAllWithPage(Pageable pageable){
//        Page<Maletin> maletinList = repository.findAll(pageable);
//        for(String key : TenantContext.getAllTenantKeys()){
//            for(Maletin maletin : maletinList){
//                Maletin foundMaletin = multiTenantService.compartir(key, (params) -> findById(maletin.getId()).orElse(null), maletin.getId());
//                if(foundMaletin.get)
//            }
//        }
//    }

    public Maletin findByDescripcion(String texto) {
        Maletin m = repository.findByDescripcionIgnoreCase(texto);
        return m;
    }

    public List<Maletin> searchByAll(String texto, Long sucId) {
        texto = texto != null ? texto.toUpperCase() : "";
        return repository.findByAll(texto, sucId);
    }

    @Override
    public Maletin save(Maletin entity) {
        if (entity.getId() == null) entity.setCreadoEn(LocalDateTime.now());
        if (entity.getCreadoEn() == null) entity.setCreadoEn(LocalDateTime.now());
        Maletin e = super.save(entity);
        return e;
    }

    public Maletin abrirMaletin(Long id) {
        Maletin m = findById(id).orElse(null);
        if (m != null) {
            m.setAbierto(true);
            return save(m);
        }
        return null;
    }

    public Maletin cerrarMaletin(Long id) {
        Maletin m = findById(id).orElse(null);
        if (m != null) {
            m.setAbierto(false);
            return save(m);
        }
        return null;
    }
}