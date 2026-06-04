package com.franco.dev.graphql.operaciones.input;

import com.franco.dev.domain.operaciones.dto.LucroPorFuncionarioDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LucroPorFuncionarioResponse {
    private List<LucroPorFuncionarioDto> content;
    private Long totalElements;
    private LucroPorFuncionarioSummary summary;
}
