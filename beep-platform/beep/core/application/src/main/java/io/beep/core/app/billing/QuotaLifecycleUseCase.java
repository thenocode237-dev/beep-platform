package io.beep.core.app.billing;
import io.beep.core.app.port.*;
import io.beep.core.domain.user.Email;
import java.time.Clock;
public class QuotaLifecycleUseCase {
  private final QuotaLifecycleRepository data;
  private final MailPort mail;
  private final TransactionPort tx;
  private final Clock clock;
  public QuotaLifecycleUseCase(QuotaLifecycleRepository data,MailPort mail,TransactionPort tx,Clock clock) {this.data=data;this.mail=mail;this.tx=tx;this.clock=clock;}
  public String run() {
    String result=tx.run(() -> data.apply(clock.instant()));
    for(var n:data.notices(clock.instant())) {
      mail.sendQuotaNotice(new Email(n.email()),n.locale(),n.links(),n.expiredAt());
      tx.run(() -> data.notified(n.workspaceId(),clock.instant()));
    }
    return result;
  }
}
