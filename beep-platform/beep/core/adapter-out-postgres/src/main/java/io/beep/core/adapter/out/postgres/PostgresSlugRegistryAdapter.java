package io.beep.core.adapter.out.postgres;

import static io.beep.core.adapter.out.postgres.Schema.*;

import io.beep.core.app.port.SlugRegistryPort;
import io.beep.core.domain.link.Slug;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Registre des chemins publics. Les dépôts de liens et de pages y écrivent dans la transaction de l'appelant ;
 * la clé primaire garantit qu'un slug ne désigne qu'une ressource, même en cas de course.
 */
public class PostgresSlugRegistryAdapter implements SlugRegistryPort {
  private final DSLContext dsl;

  public PostgresSlugRegistryAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public boolean isTaken(Slug slug) {
    return dsl.fetchExists(dsl.selectOne().from(SLUGS).where(SL_SLUG.eq(slug.value())));
  }

  static void claim(DSLContext dsl, String slug, String kind, UUID target) {
    try {
      dsl.insertInto(SLUGS).set(SL_SLUG, slug).set(SL_KIND, kind).set(SL_TARGET, target).execute();
    } catch (RuntimeException e) {
      if (Sql.hasState(e, Sql.UNIQUE_VIOLATION)) throw new SlugTakenException();
      throw e;
    }
  }

  static void release(DSLContext dsl, String slug, UUID target) {
    dsl.deleteFrom(SLUGS).where(SL_SLUG.eq(slug).and(SL_TARGET.eq(target))).execute();
  }
}
