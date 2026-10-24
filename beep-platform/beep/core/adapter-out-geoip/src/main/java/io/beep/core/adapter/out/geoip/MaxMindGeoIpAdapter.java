package io.beep.core.adapter.out.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import io.beep.core.app.port.GeoIpPort;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base pays MaxMind (.mmdb) lue localement : aucun appel externe sur le chemin chaud. Sans base configurée
 * (`GEOIP_DB_PATH`, licence GeoLite2 à obtenir), le pays reste inconnu (« ZZ » dans les agrégats) ; rien d'autre ne change.
 */
public class MaxMindGeoIpAdapter implements GeoIpPort {
  private static final Logger log = LoggerFactory.getLogger(MaxMindGeoIpAdapter.class);

  private final DatabaseReader reader;

  public MaxMindGeoIpAdapter(String databasePath) {
    DatabaseReader r = null;
    if (databasePath != null && !databasePath.isBlank()) {
      File f = new File(databasePath);
      if (f.isFile()) {
        try {
          r = new DatabaseReader.Builder(f).withCache(new CHMCache()).build();
          log.info("base GeoIP chargée : {} ({})", f.getName(), r.getMetadata().getDatabaseType());
        } catch (IOException e) {
          log.warn("base GeoIP illisible ({}) : pays inconnus", databasePath, e);
        }
      } else {
        log.warn("base GeoIP introuvable ({}) : pays inconnus", databasePath);
      }
    }
    this.reader = r;
  }

  public boolean enabled() {
    return reader != null;
  }

  @Override
  public String country(String ip) {
    if (reader == null || ip == null || ip.isBlank()) return null;
    try {
      // littéral IP uniquement : InetAddress.getByName ne résout pas de nom ici (pas de requête DNS)
      if (!ip.matches("[0-9a-fA-F:.]+")) return null;
      return reader.tryCountry(InetAddress.getByName(ip)).map(c -> c.getCountry().getIsoCode()).orElse(null);
    } catch (AddressNotFoundException e) {
      return null;
    } catch (Exception e) {
      return null; // une IP illisible ne doit jamais empêcher une redirection
    }
  }
}
