package com.franco.dev.repository;

import com.franco.dev.domain.EmbebedPrimaryKey;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface HelperRepository<T, S> extends JpaRepository<T, S>, PagingAndSortingRepository<T, S> {

    List<T> findAllByOrderByIdAsc();

    List<T> findAllByOrderByIdAsc(Pageable pageable);

}
