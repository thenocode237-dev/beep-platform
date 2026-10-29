package io.beep.core.adapter.in.http;

import com.fasterxml.jackson.databind.JsonNode;
import io.beep.core.app.conversion.DeveloperUseCase.EventCommand;
import io.beep.core.domain.DomainException;
import java.math.BigDecimal;
import java.util.*;

/** Normalizes only the payment amount and explicit BeepClick metadata, never customer details. */
final class CommercePayloads {
  record Sale(String externalId,EventCommand command){}
  static Optional<Sale> normalize(String provider,JsonNode root){
    try{
      JsonNode order=root;String id;BigDecimal value;String currency;Map<String,String> meta=new HashMap<>();
      if(provider.equals("stripe")){
        String type=root.path("type").asText();if(!Set.of("checkout.session.completed","checkout.session.async_payment_succeeded","payment_intent.succeeded").contains(type))return Optional.empty();order=root.path("data").path("object");
        if(type.startsWith("checkout.")&&!order.path("payment_status").asText().equals("paid"))return Optional.empty();
        currency=order.path("currency").asText().toUpperCase(Locale.ROOT);int decimals=Set.of("ISK","UGX").contains(currency)?2:currency.equals("MGA")?0:Currency.getInstance(currency).getDefaultFractionDigits();if(decimals<0||decimals>3)throw new IllegalArgumentException();
        value=new BigDecimal(order.path(type.startsWith("checkout.")?"amount_total":"amount_received").asText()).movePointLeft(decimals);
        id=order.path("payment_intent").isTextual()?order.path("payment_intent").asText():order.path("id").asText();
        order.path("metadata").fields().forEachRemaining(e->meta.put(e.getKey(),e.getValue().asText()));
      }else{
        boolean shop=provider.equals("shopify");String status=order.path(shop?"financial_status":"status").asText();if(shop?!status.equals("paid"):!Set.of("processing","completed").contains(status))return Optional.empty();
        id=order.path("id").asText();currency=order.path("currency").asText().toUpperCase(Locale.ROOT);value=new BigDecimal(order.path(shop?"total_price":"total").asText());
        for(JsonNode m:order.path(shop?"note_attributes":"meta_data"))meta.put(m.path(shop?"name":"key").asText(),m.path("value").asText());
      }
      if(!Set.of("true","1").contains(meta.getOrDefault("beep_consent","false")))return Optional.empty();
      if(id.isBlank()||id.length()>160||value.signum()<0||value.scale()>4||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException();
      String cid=meta.get("beep_cid"),visitor=meta.get("beep_visitor");if(cid!=null&&cid.length()>512||visitor!=null&&!visitor.matches("[A-Za-z0-9_-]{16,128}"))throw new IllegalArgumentException();
      return Optional.of(new Sale("sale:"+id,new EventCommand(UUID.randomUUID(),"sale",visitor,cid,value,currency,null,true,Map.of())));
    }catch(RuntimeException e){throw new DomainException("invalid_request");}
  }
}
