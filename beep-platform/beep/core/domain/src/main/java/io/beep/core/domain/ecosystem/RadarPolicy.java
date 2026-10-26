package io.beep.core.domain.ecosystem;

import io.beep.core.domain.DomainException;
import java.math.BigDecimal;

public final class RadarPolicy {
  private RadarPolicy(){}
  public static boolean fires(String kind,long threshold,long clicks,long leads,long sales,long failures){
    return switch(kind){
      case "click_spike" -> clicks>=threshold;
      case "conversion_drop" -> clicks>=20&&BigDecimal.valueOf(sales).multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf(clicks).multiply(BigDecimal.valueOf(threshold)))<0;
      case "broken_link","domain_error" -> failures>0;
      default -> throw new DomainException("invalid_request");
    };
  }
}
