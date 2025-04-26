package com.franco.dev.repository.productos;


import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubFamiliaRepository extends HelperRepository<Subfamilia, Long> {

    default Class<Subfamilia> getEntityClass() {
        return Subfamilia.class;
    }

    @Query("select f from Subfamilia f " +
            "join f.familia fam " +
            "where fam.id = ?1 " +
            "and (CAST(f.id as string) like %?2% " +
            "or UPPER(f.descripcion) like %?2% " +
            "or UPPER(f.nombre) like %?2%) " +
            "order by f.id asc")
    public Page<Subfamilia> findByDescripcion(Long familiaId, String texto, Pageable pageable);

    @Query("select f from Subfamilia f " +
            "where (CAST(f.id as string) like %?1% " +
            "or UPPER(f.descripcion) like %?1% " +
            "or UPPER(f.nombre) like %?1%) " +
            "order by f.id asc")
    public Page<Subfamilia> findByDescripcionSinFamilia(String texto, Pageable pageable);

    public List<Subfamilia> findByFamiliaDescripcion(String texto);

    public List<Subfamilia> findByFamiliaId(Long id);

    public List<Subfamilia> findBySubfamiliaId(Long id);

//    @Override
//    @Query(value = "select * from productos.subfamilia s " +
//            "where s.sub_familia_id is null", nativeQuery = true)
    public List<Subfamilia> findBySubfamiliaIsNull();

}
