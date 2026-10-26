package io.beep.core.domain.team;

import io.beep.core.domain.DomainException;
import java.util.Locale;

/** Workspace privileges are independent of staff privileges and API scopes. */
public enum WorkspaceRole {
  OWNER, ADMIN, EDITOR, VIEWER;
  public boolean manages(){return this==OWNER||this==ADMIN;}
  public boolean writes(){return this!=VIEWER;}
  public String db(){return name().toLowerCase(Locale.ROOT);}
  public static WorkspaceRole parse(String raw){try{return valueOf(raw.toUpperCase(Locale.ROOT));}catch(Exception e){throw new DomainException("invalid_role");}}
  public void requireManage(WorkspaceRole target){if(!manages()||target==OWNER||this==ADMIN&&target==ADMIN)throw new DomainException("forbidden");}
}
