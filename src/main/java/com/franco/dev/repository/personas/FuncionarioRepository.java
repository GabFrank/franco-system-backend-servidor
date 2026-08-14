package com.franco.dev.repository.personas;

import com.franco.dev.domain.empresarial.Sucursal;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Vendedor;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import javax.persistence.QueryHint;
import java.util.List;

public interface FuncionarioRepository extends HelperRepository<Funcionario, Long> {

        default Class<Funcionario> getEntityClass() {
                return Funcionario.class;
        }

        public Funcionario findByPersonaId(Long id);

        @Query("select f from Funcionario f " +
                        "join f.persona p " +
                        "left join Usuario usr with usr.persona = p " +
                        "where CAST(f.id as text) like concat('%', ?1, '%') or " +
                        "UPPER(p.nombre) like UPPER(concat('%', ?1, '%')) or " +
                        "UPPER(usr.nickname) like UPPER(concat('%', ?1, '%'))")
        public List<Funcionario> findByIdOrPersonaNombre(String texto);

        public Funcionario findByUsuarioId(Long id);

        /**
         * Funcionarios activos con sueldo por debajo de un monto. Lo usa el flujo de
         * ajuste por cambio de SALARIO_MINIMO_LEGAL_PYG.
         * Se excluye sueldo null (no cargado) y 0 (diaristas/jornaleros, que no tienen
         * sueldo mensual y no aplican al minimo mensual).
         *
         * El filtro va por f.activo, NO por el activo del Usuario: el egreso marca
         * f.setActivo(false) (FuncionarioRrhhService/LiquidacionFinalService) pero la
         * cuenta de login puede quedar activa, o el funcionario puede no tener cuenta.
         * Filtrar por usuario dejaba entrar egresados a un flujo que sube sueldos.
         */
        @Query("select f from Funcionario f " +
                        "join f.persona p " +
                        "where f.sueldo is not null and f.sueldo > 0 and f.sueldo < ?1 " +
                        "and f.activo = true " +
                        "order by p.nombre")
        public List<Funcionario> findConSueldoMenorA(Float monto);
        // FlushMode COMMIT: en el path de update la entity está managed y sucia con el
        // nuevo 'activo'. Sin este hint Hibernate auto-flushea antes de la consulta (el
        // query space coincide con personas.funcionario) y devolvería el valor nuevo,
        // dejando sin efecto la comparación previa que dispara la cascada de estado.
        @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
        @Query("select f.activo from Funcionario f where f.id = :id")
        Boolean findActivoById(@Param("id") Long id);

        @Query("select u from Funcionario u " +
                        "join u.persona p " +
                        "left join u.sucursal s " +
                        "left join u.cargo c where " +
                        "(cast(:id as long) is null or u.id = :id) and " +
                        "(cast(:nombre as string) is null or upper(p.nombre) like concat('%', upper(cast(:nombre as string)), '%')) and " +
                        "(:sucursalList is null or s.id in :sucursalList) and " +
                        "(cast(:activo as boolean) is null or u.activo = :activo) and " +
                        "(cast(:cargoId as long) is null or c.id = :cargoId) and " +
                        "(cast(:diarista as boolean) is null or u.diarista = :diarista) and " +
                        "(cast(:fasePrueba as boolean) is null or u.fasePrueba = :fasePrueba) " +
                        "order by p.nombre")
        public Page<Funcionario> findAllWithFilterAndPage(
                        @Param("id") Long id,
                        @Param("nombre") String nombre,
                        @Param("sucursalList") List<Long> sucursalList,
                        @Param("activo") Boolean activo,
                        @Param("cargoId") Long cargoId,
                        @Param("diarista") Boolean diarista,
                        @Param("fasePrueba") Boolean fasePrueba,
                        Pageable pageable);
}
