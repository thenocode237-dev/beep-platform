package io.beep.core.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Politique « forward-only / expand-contract » : une migration qui supprime des données ou des colonnes ne peut pas
 * partir en même temps que le code qui les lit. Elle doit être explicitement nommée `…__contract_….sql` et n'arriver
 * qu'un déploiement après l'« expand ». Ce test échoue sinon, donc la CI aussi.
 */
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
  void migrationsFollowTheNamingConvention() throws IOException {
    assertThat(migrations()).isNotEmpty().allSatisfy(p -> assertThat(p.getFileName().toString()).matches(NAME));
  }

  @Test
  void versionsAreContiguous() throws IOException {
    List<Integer> versions = migrations().stream().map(p -> Integer.parseInt(p.getFileName().toString().substring(1).split("__")[0])).toList();
    for (int i = 0; i < versions.size(); i++) assertThat(versions.get(i)).isEqualTo(i + 1);
  }

  @Test
  void destructiveStatementsAreOnlyAllowedInContractMigrations() throws IOException {
    for (Path p : migrations()) {
      if (p.getFileName().toString().contains("__contract_")) continue;
      String sql = Files.readString(p).replaceAll("(?m)--.*$", ""); // commentaires ignorés
      assertThat(DESTRUCTIVE.matcher(sql).find()).as("%s contient une instruction destructive hors migration « contract »", p.getFileName()).isFalse();
    }
  }
}
