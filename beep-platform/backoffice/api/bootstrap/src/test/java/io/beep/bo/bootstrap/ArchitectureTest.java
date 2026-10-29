package io.beep.bo.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Règles d'architecture du back office (mêmes principes que beep-core). Une violation fait échouer la CI. */
class ArchitectureTest {
  private static final String[] FRAMEWORKS = {"org.springframework..", "org.jooq..", "jakarta..", "javax.sql..", "com.fasterxml..", "org.slf4j..", "java.sql.."};

  /**
   * Importe uniquement le code compilé de nos modules (`../<module>/target/classes`) : rapide, et identique que Maven
   * résolve les modules frères en répertoires (`test`) ou en jars (`verify`).
   */
  private static final JavaClasses CLASSES = importModules();

  private static JavaClasses importModules() {
    java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath().getParent();
    try (Stream<java.nio.file.Path> modules = java.nio.file.Files.list(root)) {
      java.nio.file.Path[] outputs = modules.map(m -> m.resolve("target").resolve("classes")).filter(java.nio.file.Files::isDirectory).toArray(java.nio.file.Path[]::new);
      return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPaths(outputs);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  static Map<String, ArchRule> rules() {
    Map<String, ArchRule> r = new LinkedHashMap<>();

    r.put("le domaine n'importe ni framework ni couche externe",
        noClasses().that().resideInAPackage("io.beep.bo.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("io.beep.bo.app..", "io.beep.bo.adapter..", "io.beep.bo.bootstrap..")
            .orShould().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS));

    r.put("l'application ne dépend que du domaine",
        noClasses().that().resideInAPackage("io.beep.bo.app..").should().dependOnClassesThat()
            .resideInAnyPackage("io.beep.bo.adapter..", "io.beep.bo.bootstrap..")
            .orShould().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS));

    r.put("les dépendances pointent vers l'intérieur",
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("io.beep.bo.domain..")
            .layer("Application").definedBy("io.beep.bo.app..")
            .layer("AdapterIn").definedBy("io.beep.bo.adapter.in..")
            .layer("AdapterOut").definedBy("io.beep.bo.adapter.out..")
            .layer("Bootstrap").definedBy("io.beep.bo.bootstrap..")
            .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
            .whereLayer("AdapterIn").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("AdapterOut").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("AdapterIn", "AdapterOut", "Bootstrap")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "AdapterIn", "AdapterOut", "Bootstrap"));

    r.put("les adapters entrants n'utilisent pas les adapters sortants",
        noClasses().that().resideInAPackage("io.beep.bo.adapter.in..").should().dependOnClassesThat().resideInAPackage("io.beep.bo.adapter.out.."));

    r.put("les adapters sortants n'utilisent pas les adapters entrants",
        noClasses().that().resideInAPackage("io.beep.bo.adapter.out..").should().dependOnClassesThat().resideInAPackage("io.beep.bo.adapter.in.."));

    // Le back office ne parle au core QUE par l'API admin : seul le gateway utilise un client HTTP sortant.
    r.put("seul l'adapter coregateway utilise un client HTTP sortant (jamais d'accès direct à la base de BeepClick)",
        noClasses().that().resideOutsideOfPackage("io.beep.bo.adapter.out.coregateway..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.client..", "java.net.http.."));

    r.put("nomenclature : *UseCase vit dans la couche application",
        classes().that().haveSimpleNameEndingWith("UseCase").should().resideInAPackage("io.beep.bo.app.."));

    r.put("nomenclature : les ports sont des interfaces nommées *Port ou *Repository",
        classes().that().resideInAPackage("io.beep.bo.app.port..").and().areTopLevelClasses()
            .should().beInterfaces().andShould().haveNameMatching(".*(Port|Repository)"));

    r.put("nomenclature : une implémentation de port dans un adapter sortant s'appelle *Adapter",
        classes().that().areNotInterfaces().and().resideInAPackage("io.beep.bo.adapter.out..")
            .and().implement(JavaClass.Predicates.resideInAPackage("io.beep.bo.app.port.."))
            .should().haveSimpleNameEndingWith("Adapter"));

    return r;
  }

  /** Garde-fou : si l'import ne voyait aucune classe (classpath, jars), toutes les règles passeraient à vide. */
  @Test
  void importSeesEveryLayer() {
    for (String pkg : new String[] {"io.beep.bo.domain", "io.beep.bo.app", "io.beep.bo.adapter.in", "io.beep.bo.adapter.out", "io.beep.bo.bootstrap"}) {
      org.assertj.core.api.Assertions.assertThat(CLASSES.stream().filter(c -> c.getPackageName().startsWith(pkg)).count()).as("classes importées sous %s", pkg).isPositive();
    }
  }

  @TestFactory
  Stream<DynamicTest> architectureRules() {
    return rules().entrySet().stream().map(e -> dynamicTest(e.getKey(), () -> e.getValue().check(CLASSES)));
  }
}
