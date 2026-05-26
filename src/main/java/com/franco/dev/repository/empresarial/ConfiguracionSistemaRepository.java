package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, String> {
}
