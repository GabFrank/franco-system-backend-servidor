package com.franco.dev.repository;

import com.franco.dev.domain.EmbebedPrimaryKey;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface HelperRepositoryEmbeddedId<T, Long> extends JpaRepository<T, EmbebedPrimaryKey> {
    List<T> findAllByOrderByIdAsc();

    List<T> findAllByOrderByIdAsc(Pageable pageable);

}
