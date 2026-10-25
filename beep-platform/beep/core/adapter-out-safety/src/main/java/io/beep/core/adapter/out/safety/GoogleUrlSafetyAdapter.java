package io.beep.core.adapter.out.safety;
import io.beep.core.app.port.UrlSafetyPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Web Risk for commercial use; Safe Browsing is an explicit noncommercial option. Worker-only. */
public class GoogleUrlSafetyAdapter implements UrlSafetyPort {
  private final String provider,key,base;
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper json=new ObjectMapper();
  public GoogleUrlSafetyAdapter(String provider,String key) {this(provider,key,"web_risk".equals(provider)?"https://webrisk.googleapis.com/v1/uris:search":"https://safebrowsing.googleapis.com/v4/threatMatches:find");}
  GoogleUrlSafetyAdapter(String provider,String key,String base) {this.provider=provider;this.key=key;this.base=base;}
  private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
  @Override public Verdict check(String url) {
    if(key==null || key.isBlank()) return Verdict.UNCONFIGURED;
    if(!Set.of("web_risk","safe_browsing").contains(provider)) return Verdict.UNAVAILABLE;
    try {
      HttpRequest req;
      if(provider.equals("web_risk")) {
        req=HttpRequest.newBuilder(URI.create(base+"?key="+enc(key)+"&uri="+enc(url)+"&threatTypes=MALWARE&threatTypes=SOCIAL_ENGINEERING&threatTypes=UNWANTED_SOFTWARE")).timeout(Duration.ofSeconds(5)).GET().build();
      } else {
        String body=json.writeValueAsString(Map.of("client",Map.of("clientId","beep","clientVersion","0.6.0"),"threatInfo",Map.of("threatTypes",List.of("MALWARE","SOCIAL_ENGINEERING","UNWANTED_SOFTWARE"),"platformTypes",List.of("ANY_PLATFORM"),"threatEntryTypes",List.of("URL"),"threatEntries",List.of(Map.of("url",url)))));
        req=HttpRequest.newBuilder(URI.create(base+"?key="+enc(key))).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
      }
      var res=client.send(req,HttpResponse.BodyHandlers.ofString());
      if(res.statusCode()!=200) return Verdict.UNAVAILABLE;
      var root=json.readTree(res.body());if(root==null||!root.isObject())return Verdict.UNAVAILABLE;
      if(provider.equals("web_risk")) return root.path("threat").path("threatTypes").isArray()&&!root.path("threat").path("threatTypes").isEmpty()?Verdict.UNSAFE:Verdict.SAFE;
      return root.path("matches").isArray()&&!root.path("matches").isEmpty()?Verdict.UNSAFE:Verdict.SAFE;
    } catch(InterruptedException e){Thread.currentThread().interrupt();return Verdict.UNAVAILABLE;}
      catch(Exception e){return Verdict.UNAVAILABLE;}
  }
}
