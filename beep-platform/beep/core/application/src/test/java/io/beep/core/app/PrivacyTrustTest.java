package io.beep.core.app;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.auth.Fakes;
import io.beep.core.app.port.*;
import io.beep.core.app.privacy.PrivacyUseCase;
import io.beep.core.app.trust.TrustUseCase;
import io.beep.core.domain.user.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PrivacyTrustTest {
  static class Trust implements TrustRepository {
    List<Report> reports=new ArrayList<>();List<Flag> flags=new ArrayList<>(List.of(new Flag("links.rules",true,100,1,"test")));List<String> audit=new ArrayList<>();boolean blocked;
    public Optional<String> destinationOf(String url,String host){return Optional.empty();}
    public void report(Report r){reports.add(r);}public Reports reports(String status,int limit,int offset,Instant now){return new Reports(reports,reports.size(),0);}
    public Report resolve(UUID id,String decision,String reason,String staff,Instant now){throw new UnsupportedOperationException();}
    public List<BlockedHost> blocklist(){return List.of();}public void block(String host,boolean blocked,String reason,String staff,Instant now){this.blocked=blocked;}public boolean blocked(String host){return blocked;}
    public List<Flag> flags(){return flags;}public Flag publishFlag(String key,boolean enabled,int rollout,int version,String reason,String staff,Instant now){throw new UnsupportedOperationException();}
    public List<ScanTarget> scanTargets(int limit){return List.of(new ScanTarget(UUID.randomUUID(),"https://target.example",Instant.EPOCH,List.of("https://target.example")));}
    public boolean scanned(UUID id,String url,Instant update,Instant now){return true;}
    public void audit(String actor,String action,String target,String reason){audit.add(action);}
  }
  static class Data implements PrivacyRepository {List<UUID> erased=new ArrayList<>();public Map<String,Object> export(UUID id){return Map.of("user",id);}public void erase(UUID id){erased.add(id);}}
  static class Mail extends Fakes.Mail {public void sendAccountDeletion(Email to,String locale,String token){lastToken=token;}}
  @Test void selfErasureRequiresEmailProofAndTotpAndTokenIsConsumedOnce() {
    Platform p=new Platform();var data=new Data();var audit=new Trust();var mail=new Mail();
    var privacy=new PrivacyUseCase(data,audit,p.tokens,p.tokenGen,p.cipher,mail,p.limiter,p.tx,p.clock);
    var user=p.verifiedUser("erase@example.com").withTotp(p.cipher.encrypt(Totp.newSecret()),true);
    privacy.challenge(user);String token=mail.lastToken;
    assertThatThrownBy(()->privacy.eraseSelf(user,"bad",null)).hasMessageContaining("invalid_token");
    assertThatThrownBy(()->privacy.eraseSelf(user,token,"000000")).hasMessageContaining("invalid_totp");
    assertThat(data.erased).isEmpty();
    String code=Totp.code(p.cipher.decrypt(user.totpSecretCipher()),p.now);
    privacy.eraseSelf(user,token,code);assertThat(data.erased).containsExactly(user.id());
    assertThatThrownBy(()->privacy.eraseSelf(user,token,code)).hasMessageContaining("invalid_token");
    assertThat(audit.audit).containsExactly("privacy.erase");
  }
  @Test void anEmailTokenCannotEraseAnotherAccountAndExpiresAfterFifteenMinutes() {
    Platform p=new Platform();var data=new Data();var mail=new Mail();var privacy=new PrivacyUseCase(data,new Trust(),p.tokens,p.tokenGen,p.cipher,mail,p.limiter,p.tx,p.clock);
    var ada=p.verifiedUser("ada@example.com");var bob=p.verifiedUser("bob@example.com");privacy.challenge(ada);
    assertThatThrownBy(()->privacy.eraseSelf(bob,mail.lastToken,null)).hasMessageContaining("invalid_token");
    p.now=p.now.plus(Duration.ofMinutes(16));assertThatThrownBy(()->privacy.eraseSelf(ada,mail.lastToken,null)).hasMessageContaining("invalid_token");assertThat(data.erased).isEmpty();
  }
  @Test void flagsAndBlocklistAreEnforcedAndPublicReportingIsRateLimited() {
    Platform p=new Platform();var repo=new Trust();var trust=new TrustUseCase(repo,p.tx,p.limiter,p.clock);
    trust.requireFlag("links.rules",UUID.randomUUID());repo.flags=List.of(new TrustRepository.Flag("links.rules",false,100,2,"paused"));
    assertThatThrownBy(()->trust.requireFlag("links.rules",UUID.randomUUID())).hasMessageContaining("feature_unavailable");repo.blocked=true;
    assertThatThrownBy(()->trust.requireSafe("https://unsafe.example")).hasMessageContaining("destination_blocked");
    for(int i=0;i<5;i++)trust.report("https://unsafe.example","phishing","example","ip");
    assertThatThrownBy(()->trust.report("https://unsafe.example","phishing","example","ip")).hasMessageContaining("rate_limited");assertThat(repo.reports).hasSize(5);
  }
  @Test void unavailableSafetyProviderNeverMarksDestinationSafe() {
    Platform p=new Platform();var repo=new Trust();var trust=new TrustUseCase(repo,p.tx,p.limiter,p.clock);
    assertThat(trust.scan(url -> UrlSafetyPort.Verdict.UNCONFIGURED)).isEqualTo("unconfigured");
    assertThatThrownBy(()->trust.scan(url -> UrlSafetyPort.Verdict.UNAVAILABLE)).hasMessageContaining("url_safety_unavailable");assertThat(repo.blocked).isFalse();
    trust.scan(url -> UrlSafetyPort.Verdict.UNSAFE);assertThat(repo.blocked).isTrue();assertThat(repo.reports).hasSize(1);
  }
}
