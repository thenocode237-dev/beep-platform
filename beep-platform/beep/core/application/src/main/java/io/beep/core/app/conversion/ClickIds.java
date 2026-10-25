package io.beep.core.app.conversion;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Signed, self-contained click receipts; forged or cross-workspace receipts cannot attribute sales. */
public final class ClickIds {
  public record Click(UUID workspace, UUID link, Instant at) {}
  private final byte[] secret;
  public ClickIds(String secret) {
    if(secret == null || secret.length() < 32) throw new IllegalArgumentException("conversion signing key requires 32 characters");
    this.secret=secret.getBytes(StandardCharsets.UTF_8);
  }
  public String issue(UUID workspace, UUID link, Instant now) {
    String body=Base64.getUrlEncoder().withoutPadding().encodeToString((workspace+"/"+link+"/"+now.toEpochMilli()+"/"+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    return body+"."+sign(body);
  }
  public String sign(String body) {
    try {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));}
    catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
  }
  public Optional<Click> read(String cid, UUID workspace, Instant now) {
    try {
      if(cid == null || cid.length()>512) return Optional.empty();
      String[] p=cid.split("\\.",-1);
      if(p.length!=2 || !MessageDigest.isEqual(sign(p[0]).getBytes(StandardCharsets.US_ASCII),p[1].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
      String[] f=new String(Base64.getUrlDecoder().decode(p[0]),StandardCharsets.UTF_8).split("/");
      var click=new Click(UUID.fromString(f[0]),UUID.fromString(f[1]),Instant.ofEpochMilli(Long.parseLong(f[2])));
      return click.workspace().equals(workspace) && !click.at().isAfter(now) && !click.at().isBefore(now.minus(Duration.ofDays(90))) ? Optional.of(click) : Optional.empty();
    } catch(RuntimeException e){return Optional.empty();}
  }
}
