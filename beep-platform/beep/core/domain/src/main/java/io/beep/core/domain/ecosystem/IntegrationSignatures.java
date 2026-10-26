package io.beep.core.domain.ecosystem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;

public final class IntegrationSignatures {
  private IntegrationSignatures(){}
  public static byte[] hmac(String secret,byte[] bytes){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return m.doFinal(bytes);}catch(Exception e){throw new IllegalStateException(e);}}
  public static boolean verify(String provider,String secret,byte[] body,String signature,Instant now){
    try{
      if(signature==null||signature.length()>2048)return false;
      if(provider.equals("stripe")){
        String timestamp=null;var digests=new ArrayList<String>();
        for(String part:signature.split(",")){String[] kv=part.strip().split("=",2);if(kv.length==2){if(kv[0].equals("t"))timestamp=kv[1];if(kv[0].equals("v1"))digests.add(kv[1]);}}
        long t=Long.parseLong(timestamp);if(Math.abs(now.getEpochSecond()-t)>300)return false;
        byte[] prefix=(timestamp+".").getBytes(StandardCharsets.UTF_8),data=new byte[prefix.length+body.length];System.arraycopy(prefix,0,data,0,prefix.length);System.arraycopy(body,0,data,prefix.length,body.length);
        byte[] expected=hmac(secret,data);for(String s:digests)if(MessageDigest.isEqual(expected,HexFormat.of().parseHex(s)))return true;return false;
      }
      return Set.of("shopify","woocommerce").contains(provider)&&MessageDigest.isEqual(hmac(secret,body),Base64.getDecoder().decode(signature));
    }catch(IllegalArgumentException e){return false;}
  }
}
