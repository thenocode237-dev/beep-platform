package io.beep.core.app.workspace;

import java.util.UUID;

/** Request-local selection; never persisted as a user's global preference. */
public final class WorkspaceScope {
  private static final ThreadLocal<UUID> CURRENT=new ThreadLocal<>();
  private WorkspaceScope(){}
  public static UUID selected(){return CURRENT.get();}
  public static AutoCloseable open(UUID id){UUID before=CURRENT.get();if(id==null)CURRENT.remove();else CURRENT.set(id);return ()->{if(before==null)CURRENT.remove();else CURRENT.set(before);};}
}
