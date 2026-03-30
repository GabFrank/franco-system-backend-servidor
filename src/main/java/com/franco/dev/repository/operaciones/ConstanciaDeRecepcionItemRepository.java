package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.ConstanciaDeRecepcionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstanciaDeRecepcionItemRepository extends JpaRepository<ConstanciaDeRecepcionItem, Long> {
}

