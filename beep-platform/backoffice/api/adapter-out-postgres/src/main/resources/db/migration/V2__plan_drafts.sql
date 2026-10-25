-- Phase 2 : brouillons de plans du back office (la publication vers le core se fait par l'API admin).
CREATE TABLE plan_drafts (
    code                 text        PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_-]{1,31}$'),
    name                 text        NOT NULL,
    price_xaf            bigint      NOT NULL CHECK (price_xaf >= 0),
    price_quarterly_xaf  bigint      CHECK (price_quarterly_xaf IS NULL OR price_quarterly_xaf >= 0),
    price_annual_xaf     bigint      CHECK (price_annual_xaf IS NULL OR price_annual_xaf >= 0),
    is_public            boolean     NOT NULL,
    entitlements         jsonb       NOT NULL,
    base_version         integer     NOT NULL CHECK (base_version >= 0),   -- version publiée au moment où le brouillon a été commencé
    note                 text,
    updated_by           text        NOT NULL,
    updated_at           timestamptz NOT NULL,
    published_version    integer,
    published_at         timestamptz
);
