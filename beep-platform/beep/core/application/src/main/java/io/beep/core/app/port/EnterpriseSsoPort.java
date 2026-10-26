package io.beep.core.app.port;
import java.util.Map;
public interface EnterpriseSsoPort {
 record Identity(String issuer,String subject,String email,boolean verified){}
 void validate(Map<String,Object> config);
 String authorization(Map<String,Object> config,String state,String nonce,String challenge,String callback);
 Identity exchange(Map<String,Object> config,String secret,String code,String verifier,String nonce,String callback);
}
