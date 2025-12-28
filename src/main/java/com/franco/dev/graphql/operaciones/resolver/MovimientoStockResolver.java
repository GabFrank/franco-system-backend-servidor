package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.MovimientoStock;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class MovimientoStockResolver implements GraphQLResolver<MovimientoStock> {

  public String creadoEnIso(MovimientoStock m) {
    if (m.getCreadoEn() != null) {
      return m.getCreadoEn().toString();
    }
    return null;
  }
}
