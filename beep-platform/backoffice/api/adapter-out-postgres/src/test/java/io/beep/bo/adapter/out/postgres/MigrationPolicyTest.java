package io.beep.bo.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Politique « forward-only / expand-contract » (identique à beep-core) : voir le test du core pour le raisonnement. */
class MigrationPolicyTest {
  private static final Path DIR = Path.of("src/main/resources/db/migration");
  private static final Pattern NAME = Pattern.compile("V\\d+__[a-z0-9_]+\\.sql");
  private static final Pattern DESTRUCTIVE = Pattern.compile("(?i)\\b(DROP\\s+(TABLE|COLUMN|SCHEMA|INDEX|CONSTRAINT)|TRUNCATE|ALTER\\s+TABLE\\s+\\S+\\s+RENAME|ALTER\\s+COLUMN\\s+\\S+\\s+TYPE)\\b");

  private static List<Path> migrations() throws IOException {
    try (Stream<Path> s = Files.list(DIR)) {
      return s.sorted().toList();
    }
  }

  @Test
  void migrationsFollowTheNamingConventionAndAreContiguous() throws IOException {
    List<Path> all = migrations();
    assertThat(all).isNotEmpty().allSatisfy(p -> assertThat(p.getFileName().toString()).matches(NAME));
    for (int i = 0; i < all.size(); i++) assertThat(Integer.parseInt(all.get(i).getFileName().toString().substring(1).split("__")[0])).isEqualTo(i + 1);
  }

  @Test
  void destructiveStatementsAreOnlyAllowedInContractMigrations() throws IOException {
    for (Path p : migrations()) {
      if (p.getFileName().toString().contains("__contract_")) continue;
      // Le trigger d'audit contient « TRUNCATE » dans la clause BEFORE : on retire les corps de fonctions et déclencheurs append-only
      String sql = Files.readString(p).replaceAll("(?m)--.*$", "").replaceAll("(?is)CREATE TRIGGER .*?;", "");
      assertThat(DESTRUCTIVE.matcher(sql).find()).as("%s contient une instruction destructive hors migration « contract »", p.getFileName()).isFalse();
    }
  }
}
