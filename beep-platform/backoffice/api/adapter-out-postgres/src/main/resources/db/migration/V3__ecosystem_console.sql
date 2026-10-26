CREATE TABLE content_drafts (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),kind text NOT NULL CHECK(kind IN ('blog','roadmap','announcement')),
 slug text NOT NULL,locale text NOT NULL CHECK(locale IN ('fr','en')),title text NOT NULL,body text NOT NULL,
 version integer NOT NULL DEFAULT 1,status text NOT NULL DEFAULT 'draft' CHECK(status IN ('draft','published','withdrawn')),
 updated_by uuid NOT NULL REFERENCES staff_users(id),updated_at timestamptz NOT NULL DEFAULT now(),published_at timestamptz,
 UNIQUE(kind,slug,locale)
);
CREATE TABLE support_notes (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),workspace_id uuid NOT NULL,staff_id uuid NOT NULL REFERENCES staff_users(id),
 body text NOT NULL,created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX support_notes_workspace_idx ON support_notes(workspace_id,created_at DESC);
CREATE TABLE readonly_impersonations (
 token_hash text PRIMARY KEY,staff_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
 workspace_id uuid NOT NULL,expires_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now()
);
