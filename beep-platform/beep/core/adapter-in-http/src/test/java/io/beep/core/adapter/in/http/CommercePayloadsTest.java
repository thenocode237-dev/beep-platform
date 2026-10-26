package io.beep.core.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class CommercePayloadsTest {
  final ObjectMapper json=new ObjectMapper();
  @Test void stripeCheckoutAndPaymentIntentShareOneBusinessKey()throws Exception{var sale=CommercePayloads.normalize("stripe",json.readTree("{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_1\",\"payment_intent\":\"pi_1\",\"payment_status\":\"paid\",\"amount_total\":4999,\"currency\":\"eur\",\"metadata\":{\"beep_consent\":\"true\"}}}}")).orElseThrow();assertThat(sale.externalId()).isEqualTo("sale:pi_1");assertThat(sale.command().value()).isEqualByComparingTo(new BigDecimal("49.99"));assertThat(sale.command().visitor()).isNull();}
  @Test void zeroDecimalCurrencyAndConsentAreRespected()throws Exception{String s="{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_1\",\"amount_received\":4999,\"currency\":\"xaf\",\"metadata\":{\"beep_consent\":\"true\"}}}}";assertThat(CommercePayloads.normalize("stripe",json.readTree(s)).orElseThrow().command().value()).isEqualByComparingTo("4999");assertThat(CommercePayloads.normalize("stripe",json.readTree(s.replace("true","false")))).isEmpty();}
  @Test void unpaidOrdersNeverBecomeSales()throws Exception{assertThat(CommercePayloads.normalize("woocommerce",json.readTree("{\"id\":12,\"status\":\"pending\",\"total\":\"40.00\",\"currency\":\"EUR\"}"))).isEmpty();assertThat(CommercePayloads.normalize("shopify",json.readTree("{\"id\":12,\"financial_status\":\"paid\",\"total_price\":\"40.00\",\"currency\":\"EUR\",\"note_attributes\":[{\"name\":\"beep_consent\",\"value\":\"true\"}]}"))).isPresent();}
  @Test void stripeSpecialCasesUseProviderUnitsRatherThanIsoDefaults()throws Exception{for(String currency:java.util.List.of("isk","ugx","mga")){String body="{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_1\",\"amount_received\":500,\"currency\":\""+currency+"\",\"metadata\":{\"beep_consent\":\"true\"}}}}";assertThat(CommercePayloads.normalize("stripe",json.readTree(body)).orElseThrow().command().value()).isEqualByComparingTo(currency.equals("mga")?"500":"5");}}
}
