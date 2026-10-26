package io.beep.core.bootstrap;
import io.beep.core.app.enterprise.EnterpriseUseCase;
import io.beep.core.app.port.*;
import io.beep.core.app.workspace.Workspaces;
import io.beep.core.app.auth.SessionIssuer;
import io.beep.core.app.common.PublicSite;
import io.beep.core.adapter.out.postgres.*;
import io.beep.core.adapter.out.oauth.OidcEnterpriseAdapter;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration @Profile("!migrate")
public class Phase5Wiring {
 @Bean EnterpriseRepository enterpriseRepository(DSLContext db){return new PostgresEnterpriseAdapter(db);}
 @Bean EnterpriseSsoPort enterpriseSso(@Value("${BEEP_SSO_ALLOWED_HOSTS:}")String hosts,Clock clock){return new OidcEnterpriseAdapter(hosts,clock);}
 @Bean EnterpriseUseCase enterprise(EnterpriseRepository data,EcosystemRepository teams,Workspaces rights,WorkspaceRepository ws,UserRepository users,TrustRepository audit,EnterpriseSsoPort sso,TokenPort tokens,SecretCipherPort cipher,TransactionPort tx,SessionIssuer sessions,Clock clock,PublicSite site){return new EnterpriseUseCase(data,teams,rights,ws,users,audit,sso,tokens,cipher,tx,sessions,clock,site);}
 @Bean JobLockPort jobLock(@Value("${BEEP_DATABASE_DIRECT_URL:${spring.datasource.url}}")String url,@Value("${spring.datasource.username}")String user,@Value("${spring.datasource.password}")String password){return new PostgresJobLockAdapter(url,user,password);}
}
