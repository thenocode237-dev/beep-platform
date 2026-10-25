package io.beep.core.adapter.out.security;
import io.beep.core.app.port.WebhookTransportPort;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** HTTPS only, pinned public DNS answer, certificate hostname verification, no redirect, bounded response. */
public class PinnedWebhookTransportAdapter implements WebhookTransportPort {
  public record Postback(String url,String body) {}
  /** Meta server events and Google GA4 server events. No IP, e-mail or user agent is forwarded. */
  public static Postback postbackPayload(String provider,String config,String payload)throws Exception{
    var json=new com.fasterxml.jackson.databind.ObjectMapper();var event=json.readTree(payload);var data=event.path("data");String[] c=config.split("\n",-1);String visitor=data.path("visitor").asText("");if(visitor.isBlank()||visitor.equals("null"))throw new IOException("consenting visitor required");
    String id=event.path("id").asText(),name=data.path("name").asText();long seconds=java.time.Instant.parse(data.path("at").asText()).getEpochSecond();String url;Object body;
    if(provider.equals("meta")){
      url="https://graph.facebook.com/"+c[2]+"/"+c[0]+"/events?access_token="+URLEncoder.encode(c[1],StandardCharsets.UTF_8);
      Map<String,Object> custom=new LinkedHashMap<>();if(!data.path("currency").isNull())custom.put("currency",data.path("currency").asText());custom.put("value",data.path("value").decimalValue());
      body=Map.of("data",List.of(Map.of("event_name",name.equals("sale")?"Purchase":"Lead","event_id",id,"event_time",seconds,"action_source","other","user_data",Map.of("external_id",List.of(visitor)),"custom_data",custom)));
    }else if(provider.equals("google")){
      url="https://www.google-analytics.com/mp/collect?measurement_id="+URLEncoder.encode(c[0],StandardCharsets.UTF_8)+"&api_secret="+URLEncoder.encode(c[1],StandardCharsets.UTF_8);
      var params=new LinkedHashMap<String,Object>();params.put("transaction_id",id);params.put("engagement_time_msec",1);params.put("value",data.path("value").decimalValue());if(!data.path("currency").isNull())params.put("currency",data.path("currency").asText());
      body=Map.of("client_id",visitor,"timestamp_micros",seconds*1000000,"events",List.of(Map.of("name",name.equals("sale")?"purchase":"generate_lead","params",params)));
    }else throw new IOException("unknown provider");
    return new Postback(url,json.writeValueAsString(body));
  }
  @Override public int postback(String provider,String config,String payload,String deliveryId)throws Exception{var p=postbackPayload(provider,config,payload);return deliver(p.url(),p.body(),"",deliveryId);}
  public static int statusLine(String raw)throws IOException{String status=raw.stripTrailing();if(!status.matches("HTTP/1\\.[01] [1-5][0-9][0-9].*"))throw new IOException("invalid response");return Integer.parseInt(status.substring(9,12));}
  public static boolean publicAddress(InetAddress a){
    if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
    byte[] b=a.getAddress();
    if(b.length==16)return (b[0]&0xe0)==0x20 && !(b[0]==0x20&&b[1]==1&&b[2]==0x0d&&(b[3]&255)==0xb8);
    int x=b[0]&255,y=b[1]&255;
    return x!=0&&x!=10&&x!=127&&x<224&&!(x==100&&y>=64&&y<=127)&&!(x==169&&y==254)&&!(x==172&&y>=16&&y<=31)&&!(x==192&&(y==168||y==0||y==2))&&!(x==198&&(y==18||y==19||y==51))&&!(x==203&&y==0);
  }
  @Override public int deliver(String url,String payload,String signature,String deliveryId)throws Exception{
    URI u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getPort()!=-1&&u.getPort()!=443)throw new IOException("unsafe webhook URL");
    var addresses=InetAddress.getAllByName(u.getHost());if(addresses.length==0||Arrays.stream(addresses).anyMatch(a->!publicAddress(a)))throw new IOException("non-public address");
    byte[] body=payload.getBytes(StandardCharsets.UTF_8);if(body.length>65536)throw new IOException("payload too large");
    try(Socket tcp=new Socket()){
      tcp.connect(new InetSocketAddress(addresses[0],443),2000);tcp.setSoTimeout(5000);
      try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,u.getHost(),443,true)){
        var params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");params.setServerNames(List.of(new SNIHostName(u.getHost())));tls.setSSLParameters(params);tls.setSoTimeout(5000);tls.startHandshake();
        String path=(u.getRawPath()==null||u.getRawPath().isEmpty()?"/":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        var out=tls.getOutputStream();out.write(("POST "+path+" HTTP/1.1\r\nHost: "+u.getHost()+"\r\nContent-Type: application/json\r\nUser-Agent: Beep-Webhooks/1\r\nBeep-Signature: "+signature+"\r\nBeep-Delivery: "+deliveryId+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(body);out.flush();
        var input=tls.getInputStream();var line=new ByteArrayOutputStream();for(int i=0;i<4096;i++){int ch=input.read();if(ch<0||ch=='\n')break;line.write(ch);}return statusLine(line.toString(StandardCharsets.US_ASCII));
      }
    }
  }
}
