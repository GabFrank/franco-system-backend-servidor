package com.franco.dev.service.empresarial;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.repository.empresarial.CargoRepository;
import com.franco.dev.repository.general.CiudadRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class CargoService extends CrudService<Cargo, CargoRepository, Long> {

    /** Cuantos cargos dependen jerarquicamente de este. Ver CargoGraphQL.deleteCargo. */
    public Long countBySupervisadoPorId(Long cargoId) {
        return getRepository().countBySupervisadoPorId(cargoId);
    }


    private final CargoRepository repository;

    @Override
    public CargoRepository getRepository() {
        return repository;
    }

    public List<Cargo> findByDescripcion(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByDescripcion(texto);
    }

    public List<Cargo> findByAll(String texto){
        texto = texto.replace(' ', '%');
        return  repository.findByAll(texto.toUpperCase());
    }

    /** Sin texto filtra por "%%", o sea trae todos: una sola query para los dos casos. */
    public Page<Cargo> findByAllWithPage(String texto, Pageable pageable){
        texto = texto == null ? "" : texto.trim().replace(' ', '%');
        return repository.findByAllWithPage(texto.toUpperCase(), pageable);
    }

    @Override
    public Cargo save(Cargo entity) {
        Cargo e = super.save(entity);
//        personaPublisher.publish(p);
        return e;
    }
}