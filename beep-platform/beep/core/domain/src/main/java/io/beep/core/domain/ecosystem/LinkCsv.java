package io.beep.core.domain.ecosystem;

import io.beep.core.domain.DomainException;
import java.util.*;

/** RFC4180 quoted fields, bounded imports and spreadsheet-formula-safe exports. */
public final class LinkCsv {
  private LinkCsv(){}
  public static List<List<String>> parse(String csv){
    if(csv==null||csv.length()>262144)throw new DomainException("invalid_csv");
    if(csv.startsWith("\ufeff"))csv=csv.substring(1);
    var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var field=new StringBuilder();boolean quoted=false,closed=false;
    for(int i=0;i<csv.length();i++){
      char c=csv.charAt(i);
      if(quoted){if(c=='"'){if(i+1<csv.length()&&csv.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(c);}
      else if(c=='"'){if(field.length()!=0||closed)throw new DomainException("invalid_csv");quoted=true;}
      else if(c==','||c=='\n'||c=='\r'){
        row.add(field.toString());field.setLength(0);closed=false;
        if(c!=','){if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;if(row.size()!=1||!row.getFirst().isBlank())rows.add(List.copyOf(row));row.clear();if(rows.size()>501)throw new DomainException("invalid_csv");}
      }else{if(closed)throw new DomainException("invalid_csv");field.append(c);}
    }
    if(quoted)throw new DomainException("invalid_csv");
    if(field.length()>0||!row.isEmpty()||closed){row.add(field.toString());rows.add(List.copyOf(row));}
    if(rows.isEmpty()||rows.size()>501||rows.getFirst().isEmpty()||!rows.getFirst().getFirst().equals("destination"))throw new DomainException("invalid_csv");
    if(new HashSet<>(rows.getFirst()).size()!=rows.getFirst().size()||!Set.of("destination","slug","title").containsAll(rows.getFirst()))throw new DomainException("invalid_csv");
    for(var r:rows)if(r.size()!=rows.getFirst().size())throw new DomainException("invalid_csv");
    return rows;
  }
  public static String cell(String value){String s=Objects.toString(value,"");if(s.matches("(?s)^[\\s]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
