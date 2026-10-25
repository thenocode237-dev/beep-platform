-- Un slug n'est plus unique sur toute la plateforme mais par domaine (domaine de Beep = espace sans domain_id).
-- Nommée « contract » : elle remplace la contrainte UNIQUE (slug) par un index unique plus large ; aucune donnée n'est supprimée
-- (tous les liens existants ont domain_id NULL, donc restent uniques) et l'ancien code n'écrit que des liens sans domaine.
ALTER TABLE links DROP CONSTRAINT links_slug_key;
CREATE UNIQUE INDEX links_domain_slug_key ON links (COALESCE(domain_id, '00000000-0000-0000-0000-000000000000'::uuid), slug);

-- Le registre `slugs` est indexé par clé : « slug » (domaine de Beep) ou « <id du domaine>/<slug> ». Les invalidations de cache
-- doivent donc publier la même clé.
CREATE OR REPLACE FUNCTION beep_cache_notify_links() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    old_key text;
    new_key text;
BEGIN
    old_key := CASE WHEN OLD.domain_id IS NULL THEN OLD.slug ELSE OLD.domain_id::text || '/' || OLD.slug END;
    PERFORM pg_notify('beep_cache', 'slug:' || old_key);
    PERFORM pg_notify('beep_cache', 'link:' || OLD.id);
    IF TG_OP = 'UPDATE' THEN
        new_key := CASE WHEN NEW.domain_id IS NULL THEN NEW.slug ELSE NEW.domain_id::text || '/' || NEW.slug END;
        PERFORM pg_notify('beep_cache', 'slug:' || new_key);
    END IF;
    RETURN NULL;
END;
$$;
