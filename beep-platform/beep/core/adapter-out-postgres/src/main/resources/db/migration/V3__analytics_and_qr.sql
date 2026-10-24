-- Phase 1 · cycle 2 : événements partitionnés par mois, agrégats horaires/journaliers, sels quotidiens, jobs, QR codes.

-- ---------- Événements bruts (13 mois au plus ; IP jamais stockée : empreinte salée du jour) ----------
CREATE TABLE click_events (
    id           uuid        NOT NULL,
    ts           timestamptz NOT NULL,
    workspace_id uuid        NOT NULL,           -- pas de clé étrangère : écriture par lots sans verrou, purge par rétention
    kind         text        NOT NULL CHECK (kind IN ('click', 'scan', 'view', 'bio_click')),
    link_id      uuid,
    qr_id        uuid,
    bio_page_id  uuid,
    bio_block_id text,
    visitor_hash text        NOT NULL,
    country      text,
    device       text        NOT NULL,
    os           text        NOT NULL,
    browser      text        NOT NULL,
    lang         text,
    referrer     text        NOT NULL,
    is_bot       boolean     NOT NULL,
    PRIMARY KEY (ts, id)                         -- la clé de partition doit faire partie de la clé primaire
) PARTITION BY RANGE (ts);
CREATE INDEX click_events_ts_brin ON click_events USING brin (ts);
CREATE INDEX click_events_link_idx ON click_events (link_id, ts) WHERE link_id IS NOT NULL;
CREATE INDEX click_events_workspace_idx ON click_events (workspace_id, ts);

-- Partitions mensuelles (UTC) : du mois de `from_month` à `months_ahead` mois plus tard. Idempotent.
CREATE FUNCTION ensure_click_partitions(from_month date, months_ahead integer) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE
    created integer := 0;
    lo      timestamp;
    part    text;
BEGIN
    FOR i IN 0..months_ahead LOOP
        lo := date_trunc('month', from_month::timestamp) + make_interval(months => i);
        part := 'click_events_' || to_char(lo, 'YYYY_MM');
        IF to_regclass(part) IS NULL THEN
            EXECUTE format('CREATE TABLE %I PARTITION OF click_events FOR VALUES FROM (%L) TO (%L)',
                           part, lo AT TIME ZONE 'UTC', (lo + interval '1 month') AT TIME ZONE 'UTC');
            created := created + 1;
        END IF;
    END LOOP;
    RETURN created;
END;
$$;
SELECT ensure_click_partitions(((now() AT TIME ZONE 'UTC') - interval '1 month')::date, 3);

-- ---------- Agrégats : les tableaux de bord ne lisent qu'eux ----------
-- resource_type : link (clics + scans), qr (scans), bio (vues), bio_click (clics de blocs). key '' pour la dimension total.
CREATE TABLE rollups_hourly (
    resource_type text        NOT NULL,
    resource_id   uuid        NOT NULL,
    workspace_id  uuid        NOT NULL,
    bucket        timestamptz NOT NULL,
    dimension     text        NOT NULL,
    key           text        NOT NULL,
    clicks        bigint      NOT NULL,
    uniques       bigint      NOT NULL,
    PRIMARY KEY (resource_type, resource_id, bucket, dimension, key)
);
CREATE INDEX rollups_hourly_bucket_idx ON rollups_hourly (bucket);
CREATE INDEX rollups_hourly_workspace_idx ON rollups_hourly (workspace_id, bucket);

CREATE TABLE rollups_daily (
    resource_type text   NOT NULL,
    resource_id   uuid   NOT NULL,
    workspace_id  uuid   NOT NULL,
    day           date   NOT NULL,
    dimension     text   NOT NULL,
    key           text   NOT NULL,
    clicks        bigint NOT NULL,
    uniques       bigint NOT NULL,
    PRIMARY KEY (resource_type, resource_id, day, dimension, key)
);
CREATE INDEX rollups_daily_day_idx ON rollups_daily (day);
CREATE INDEX rollups_daily_workspace_idx ON rollups_daily (workspace_id, day);

-- ---------- Sel quotidien des empreintes (détruit après 2 jours) ----------
CREATE TABLE visitor_salts (
    day  date  PRIMARY KEY,
    salt bytea NOT NULL
);

-- ---------- Historique des tâches de fond (7 jours) ----------
CREATE TABLE job_runs (
    id          bigserial   PRIMARY KEY,
    job         text        NOT NULL,
    started_at  timestamptz NOT NULL,
    finished_at timestamptz,
    ok          boolean,
    detail      text
);
CREATE INDEX job_runs_job_idx ON job_runs (job, started_at DESC);

-- ---------- QR codes ----------
CREATE TABLE qr_codes (
    id           uuid        PRIMARY KEY,
    workspace_id uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    kind         text        NOT NULL CHECK (kind IN ('dynamic', 'static')),
    code         text        UNIQUE,                                -- dynamique : /q/{code}
    link_id      uuid        REFERENCES links (id),                 -- NO ACTION : un lien suivi par un QR ne peut pas être supprimé
    content_type text,
    content      jsonb,
    style        jsonb       NOT NULL,
    title        text,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT qr_codes_shape CHECK ((kind = 'dynamic' AND code IS NOT NULL AND link_id IS NOT NULL)
                                  OR (kind = 'static' AND content_type IS NOT NULL AND content IS NOT NULL))
);
CREATE INDEX qr_codes_workspace_idx ON qr_codes (workspace_id, created_at DESC, id DESC);
CREATE INDEX qr_codes_link_idx ON qr_codes (link_id) WHERE link_id IS NOT NULL;

CREATE FUNCTION beep_cache_notify_qr() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.code IS NOT NULL THEN PERFORM pg_notify('beep_cache', 'qr:' || OLD.code); END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER qr_codes_cache AFTER UPDATE OR DELETE ON qr_codes
    FOR EACH ROW EXECUTE FUNCTION beep_cache_notify_qr();
