package com.franco.dev.graphql.productos.input;

import com.franco.dev.domain.operaciones.dto.LucroPorProductosDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class LucroPorProductoResponse {
  private List<LucroPorProductosDto> content;
  private Long totalElements;
  private LucroPorProductoSummary summary;
}
