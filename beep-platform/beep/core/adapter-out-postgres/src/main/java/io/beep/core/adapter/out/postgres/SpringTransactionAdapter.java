package io.beep.core.adapter.out.postgres;

import io.beep.core.app.port.TransactionPort;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SpringTransactionAdapter implements TransactionPort {
  private final TransactionTemplate template;

  public SpringTransactionAdapter(PlatformTransactionManager manager) {
    this.template = new TransactionTemplate(manager);
  }

  @Override
  public <T> T run(Supplier<T> work) {
    return template.execute(status -> work.get());
  }
}
