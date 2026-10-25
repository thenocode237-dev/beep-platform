package io.beep.core.adapter.out.dns;

import io.beep.core.app.port.DnsVerifierPort;
import io.beep.core.domain.customdomain.CustomDomain;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

/**
 * Résolution via le fournisseur DNS du JDK (JNDI). Propriété : un TXT `_beep-verify.<hôte>` contient le jeton. Routage : l'hôte est un CNAME vers
 * `cnameTarget`, ou ses adresses A sont parmi `ips` ; si ni cible ni adresse n'est configurée (développement), le routage n'est pas contrôlé.
 * `resolver` : serveur DNS à interroger (vide = celui du système).
 */
public class JndiDnsVerifierAdapter implements DnsVerifierPort {
  private final String cnameTarget;
  private final Set<String> ips;
  private final String resolver;

  public JndiDnsVerifierAdapter(String cnameTarget, Set<String> ips, String resolver) {
    this.cnameTarget = cnameTarget == null ? "" : cnameTarget.strip().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    this.ips = Set.copyOf(ips);
    this.resolver = resolver == null ? "" : resolver.strip();
  }

  @Override
  public Result check(String hostname, String token) {
    try {
      boolean ownership = lookup(CustomDomain.TXT_PREFIX + hostname, "TXT").stream().anyMatch(v -> v.replace("\"", "").strip().equals(token));
      boolean routing = true;
      if (!cnameTarget.isEmpty() || !ips.isEmpty()) {
        routing = lookup(hostname, "CNAME").stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).replaceAll("\\.$", "").equals(cnameTarget))
            || lookup(hostname, "A").stream().anyMatch(ips::contains);
      }
      return new Result(ownership, routing, false);
    } catch (NamingException e) {
      return new Result(false, false, true);
    }
  }

  private List<String> lookup(String name, String type) throws NamingException {
    Hashtable<String, String> env = new Hashtable<>();
    env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
    env.put("java.naming.provider.url", resolver.isEmpty() ? "dns:" : "dns://" + resolver);
    env.put("com.sun.jndi.dns.timeout.initial", "2000");
    env.put("com.sun.jndi.dns.timeout.retries", "2");
    List<String> out = new ArrayList<>();
    InitialDirContext ctx = new InitialDirContext(env);
    try {
      Attributes attrs = ctx.getAttributes(name, new String[] {type});
      Attribute attr = attrs.get(type);
      if (attr != null) {
        NamingEnumeration<?> values = attr.getAll();
        while (values.hasMore()) out.add(String.valueOf(values.next()));
      }
    } catch (NameNotFoundException e) {
      // nom absent : aucune valeur (ce n'est pas une panne)
    } finally {
      ctx.close();
    }
    return out;
  }
}
