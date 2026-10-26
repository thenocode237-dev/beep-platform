package io.beep.bo.app.port;
import java.util.*;
import java.time.Instant;
public interface EnterpriseConsoleRepository {void review(UUID staff,String decision,String reason,Instant now);List<Map<String,Object>> reviews();}
