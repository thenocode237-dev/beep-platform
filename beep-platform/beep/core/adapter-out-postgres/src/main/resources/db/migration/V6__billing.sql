-- Phase 2 · cycle 4 : facturation (abonnements prépayés, paiements Mobile Money / carte / manuels, factures, coupons, versions de plans).
-- Expand uniquement : aucune donnée existante n'est modifiée ni supprimée (hors ouverture à la vente du plan Pro, ci-dessous).

-- ---------- Plans : prix trimestriel / annuel explicites (NULL = calculés : −10 % / −30 %), historique des publications ----------
ALTER TABLE plans ADD COLUMN price_quarterly_xaf bigint CHECK (price_quarterly_xaf IS NULL OR price_quarterly_xaf >= 0);
ALTER TABLE plans ADD COLUMN price_annual_xaf    bigint CHECK (price_annual_xaf IS NULL OR price_annual_xaf >= 0);

-- Pro est commercialisé dès la phase 2 ; Elite reste « bientôt » (essai uniquement) jusqu'à la phase 3.
UPDATE plans SET public = true WHERE code = 'pro';

CREATE TABLE plan_versions (
    code         text        NOT NULL REFERENCES plans (code),
    version      integer     NOT NULL CHECK (version >= 1),
    snapshot     jsonb       NOT NULL,
    published_by text,
    published_at timestamptz NOT NULL,
    PRIMARY KEY (code, version)
);

-- ---------- Abonnements : un par workspace ----------
CREATE TABLE subscriptions (
    id               uuid        PRIMARY KEY,
    workspace_id     uuid        NOT NULL UNIQUE REFERENCES workspaces (id) ON DELETE CASCADE,
    plan_code        text        NOT NULL REFERENCES plans (code),
    billing_cycle    text        CHECK (billing_cycle IN ('monthly', 'quarterly', 'annual')),   -- NULL pendant l'essai
    status           text        NOT NULL CHECK (status IN ('trialing', 'active', 'grace', 'expired')),
    period_start     timestamptz NOT NULL,
    period_end       timestamptz NOT NULL,
    monthly_rate_xaf bigint      NOT NULL DEFAULT 0 CHECK (monthly_rate_xaf >= 0),
    notices          text[]      NOT NULL DEFAULT '{}',
    trial_used       boolean     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL
);
CREATE INDEX subscriptions_due_idx ON subscriptions (period_end) WHERE status <> 'expired';
CREATE INDEX subscriptions_status_idx ON subscriptions (status, period_end);

-- ---------- Coupons ----------
CREATE TABLE coupons (
    code            text        PRIMARY KEY,
    percent_off     integer     NOT NULL DEFAULT 0 CHECK (percent_off BETWEEN 0 AND 100),
    amount_off_xaf  bigint      NOT NULL DEFAULT 0 CHECK (amount_off_xaf >= 0),
    max_redemptions integer     CHECK (max_redemptions IS NULL OR max_redemptions >= 1),
    redeemed        integer     NOT NULL DEFAULT 0 CHECK (redeemed >= 0),
    valid_until     timestamptz,
    plan_codes      text[]      NOT NULL DEFAULT '{}',        -- vide = tous les plans payants
    active          boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL,
    CHECK ((percent_off > 0) <> (amount_off_xaf > 0))
);

-- ---------- Paiements ----------
CREATE TABLE payments (
    id             uuid        PRIMARY KEY,
    workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    plan_code      text        NOT NULL REFERENCES plans (code),
    billing_cycle  text        NOT NULL CHECK (billing_cycle IN ('monthly', 'quarterly', 'annual')),
    method         text        NOT NULL CHECK (method IN ('mtn_momo', 'orange_money', 'card', 'manual')),
    provider       text        NOT NULL,
    provider_ref   text        NOT NULL,
    status         text        NOT NULL CHECK (status IN ('pending', 'succeeded', 'failed', 'refunded')),
    list_price_xaf bigint      NOT NULL CHECK (list_price_xaf >= 0),
    discount_xaf   bigint      NOT NULL DEFAULT 0 CHECK (discount_xaf >= 0),
    credit_xaf     bigint      NOT NULL DEFAULT 0 CHECK (credit_xaf >= 0),
    amount_xaf     bigint      NOT NULL CHECK (amount_xaf >= 0),
    coupon_code    text        REFERENCES coupons (code),
    country        text        NOT NULL,
    phone_hint     text,                                       -- 4 derniers chiffres seulement
    note           text,                                       -- référence du virement / de la quittance (paiement manuel)
    recorded_by    text,                                       -- membre du staff (paiement manuel)
    failure_reason text,
    created_at     timestamptz NOT NULL,
    paid_at        timestamptz,
    UNIQUE (provider, provider_ref)
);
CREATE INDEX payments_workspace_idx ON payments (workspace_id, created_at DESC, id DESC);
CREATE INDEX payments_created_idx ON payments (created_at DESC, id DESC);
CREATE INDEX payments_pending_idx ON payments (created_at) WHERE status = 'pending';

-- ---------- Factures : numérotation séquentielle sans trou par année (compteur verrouillé dans la transaction du paiement) ----------
CREATE TABLE invoice_counters (
    year      integer PRIMARY KEY,
    last_value bigint  NOT NULL CHECK (last_value >= 0)
);

CREATE TABLE invoices (
    number         text        PRIMARY KEY,
    payment_id     uuid        NOT NULL UNIQUE REFERENCES payments (id),
    workspace_id   uuid        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    issued_at      timestamptz NOT NULL,
    customer_name  text        NOT NULL,
    customer_email text        NOT NULL,
    country        text        NOT NULL,
    plan_name      text        NOT NULL,
    billing_cycle  text        NOT NULL,
    period_start   timestamptz NOT NULL,
    period_end     timestamptz NOT NULL,
    list_price_xaf bigint      NOT NULL,
    discount_xaf   bigint      NOT NULL,
    credit_xaf     bigint      NOT NULL,
    gross_xaf      bigint      NOT NULL,
    net_xaf        bigint      NOT NULL,
    vat_xaf        bigint      NOT NULL,
    vat_rate_bp    integer     NOT NULL,
    method         text        NOT NULL,
    CHECK (gross_xaf = net_xaf + vat_xaf)
);
CREATE INDEX invoices_workspace_idx ON invoices (workspace_id, issued_at DESC);

-- ---------- Avertissements d'échéance affichés dans l'application ----------
-- (les rappels e-mail J−7 / J−3 / J−1 sont suivis dans subscriptions.notices)
