-- Phase 2 · cycle 6 : règles de lien (expiration, mot de passe, ciblage, A/B, Onelink, UTM) et catalogue d'applications (deeplinks). Expand uniquement.

-- Règles : un document JSON validé par le domaine (voir LinkRules) ; l'empreinte du mot de passe est à part.
ALTER TABLE links ADD COLUMN rules         jsonb NOT NULL DEFAULT '{}';
ALTER TABLE links ADD COLUMN password_hash text;

-- Droits des nouvelles fonctions (specs §6.1) : expiration + mot de passe et UTM = Pro et Elite ; ciblage + A/B = Elite.
UPDATE plans SET entitlements = entitlements || '{"features.link_rules": 0, "features.link_routing": 0, "features.utm": 0}'::jsonb WHERE code = 'starter';
UPDATE plans SET entitlements = entitlements || '{"features.link_rules": 1, "features.link_routing": 0, "features.utm": 1}'::jsonb WHERE code = 'pro';
UPDATE plans SET entitlements = entitlements || '{"features.link_rules": 1, "features.link_routing": 1, "features.utm": 1}'::jsonb WHERE code = 'elite';

-- ---------- Catalogue d'applications ----------
CREATE TABLE app_catalog (
    code             text        PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9_-]{1,31}$'),
    name             text        NOT NULL,
    hosts            text[]      NOT NULL CHECK (cardinality(hosts) BETWEEN 1 AND 20),
    ios_template     text,
    android_package  text,
    android_template text,
    enabled          boolean     NOT NULL DEFAULT true,
    version          integer     NOT NULL DEFAULT 1 CHECK (version >= 1),
    updated_by       text,
    updated_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX app_catalog_hosts_idx ON app_catalog USING gin (hosts);

CREATE TABLE app_catalog_versions (
    code         text        NOT NULL,
    version      integer     NOT NULL,
    snapshot     jsonb       NOT NULL,
    published_by text,
    published_at timestamptz NOT NULL,
    PRIMARY KEY (code, version)
);

-- Graine : 30 applications courantes. Les schémas iOS et intents Android sont des valeurs de départ à valider sur appareils réels
-- (critère de sortie « testées sur iOS/Android réels ») ; le back office les corrige et les republie sans déploiement.
INSERT INTO app_catalog (code, name, hosts, ios_template, android_package, android_template, enabled, version) VALUES
  ('youtube', 'YouTube', ARRAY['youtube.com'], 'youtube://{host}{path}{query}', 'com.google.android.youtube', NULL, true, 1),
  ('youtu-be', 'YouTube (lien court)', ARRAY['youtu.be'], 'youtube://www.youtube.com/watch?v={path1}', 'com.google.android.youtube', NULL, true, 1),
  ('youtube-music', 'YouTube Music', ARRAY['music.youtube.com'], 'youtubemusic://{host}{path}{query}', 'com.google.android.apps.youtube.music', NULL, true, 1),
  ('instagram', 'Instagram', ARRAY['instagram.com'], 'instagram://user?username={path1}', 'com.instagram.android', NULL, true, 1),
  ('threads', 'Threads', ARRAY['threads.net'], NULL, 'com.instagram.barcelona', NULL, true, 1),
  ('facebook', 'Facebook', ARRAY['facebook.com', 'fb.com', 'fb.watch'], 'fb://facewebmodal/f?href={url}', 'com.facebook.katana', NULL, true, 1),
  ('messenger', 'Messenger', ARRAY['m.me'], 'fb-messenger://user-thread/{path1}', 'com.facebook.orca', NULL, true, 1),
  ('x-twitter', 'X (Twitter)', ARRAY['x.com', 'twitter.com'], 'twitter://user?screen_name={path1}', 'com.twitter.android', NULL, true, 1),
  ('tiktok', 'TikTok', ARRAY['tiktok.com'], NULL, 'com.zhiliaoapp.musically', NULL, true, 1),
  ('whatsapp', 'WhatsApp', ARRAY['wa.me', 'api.whatsapp.com'], 'whatsapp://send?phone={path1}', 'com.whatsapp', NULL, true, 1),
  ('telegram', 'Telegram', ARRAY['t.me', 'telegram.me'], 'tg://resolve?domain={path1}', 'org.telegram.messenger', NULL, true, 1),
  ('linkedin', 'LinkedIn', ARRAY['linkedin.com', 'lnkd.in'], NULL, 'com.linkedin.android', NULL, true, 1),
  ('spotify', 'Spotify', ARRAY['open.spotify.com', 'spotify.link'], 'spotify://{rest}', 'com.spotify.music', NULL, true, 1),
  ('snapchat', 'Snapchat', ARRAY['snapchat.com'], 'snapchat://add/{path2}', 'com.snapchat.android', NULL, true, 1),
  ('pinterest', 'Pinterest', ARRAY['pinterest.com', 'pin.it'], 'pinterest://{rest}', 'com.pinterest', NULL, true, 1),
  ('reddit', 'Reddit', ARRAY['reddit.com', 'redd.it'], 'reddit://{path}', 'com.reddit.frontpage', NULL, true, 1),
  ('twitch', 'Twitch', ARRAY['twitch.tv'], 'twitch://stream/{path1}', 'tv.twitch.android.app', NULL, true, 1),
  ('netflix', 'Netflix', ARRAY['netflix.com'], 'nflx://www.netflix.com{path}', 'com.netflix.mediaclient', NULL, true, 1),
  ('amazon', 'Amazon', ARRAY['amazon.com', 'amazon.fr', 'amzn.to', 'amzn.eu'], 'com.amazon.mobile.shopping.web://{host}{path}{query}', 'com.amazon.mShop.android.shopping', NULL, true, 1),
  ('discord', 'Discord', ARRAY['discord.gg'], 'discord://discord.com/invite/{path1}', 'com.discord', NULL, true, 1),
  ('github', 'GitHub', ARRAY['github.com'], NULL, 'com.github.android', NULL, true, 1),
  ('medium', 'Medium', ARRAY['medium.com'], 'medium://{rest}', 'com.medium.reader', NULL, true, 1),
  ('google-maps', 'Google Maps', ARRAY['maps.app.goo.gl'], 'comgooglemaps://?url={url}', 'com.google.android.apps.maps', NULL, true, 1),
  ('tripadvisor', 'Tripadvisor', ARRAY['tripadvisor.com', 'tripadvisor.fr'], NULL, 'com.tripadvisor.tripadvisor', NULL, true, 1),
  ('booking', 'Booking.com', ARRAY['booking.com'], NULL, 'com.booking', NULL, true, 1),
  ('zoom', 'Zoom', ARRAY['zoom.us'], 'zoomus://zoom.us/join?confno={path2}', 'us.zoom.videomeetings', NULL, true, 1),
  ('teams', 'Microsoft Teams', ARRAY['teams.microsoft.com', 'teams.live.com'], 'msteams://{host}{path}{query}', 'com.microsoft.teams', NULL, true, 1),
  ('vimeo', 'Vimeo', ARRAY['vimeo.com'], 'vimeo://app.vimeo.com/videos/{path1}', 'com.vimeo.android.videoapp', NULL, true, 1),
  ('soundcloud', 'SoundCloud', ARRAY['soundcloud.com', 'on.soundcloud.com'], 'soundcloud://resolve?url={url}', 'com.soundcloud.android', NULL, true, 1),
  ('deezer', 'Deezer', ARRAY['deezer.com', 'deezer.page.link'], 'deezer://www.deezer.com{path}', 'deezer.android.app', NULL, true, 1);

-- Invalidation du catalogue entre instances (canal beep_cache, charge utile `catalog:`).
CREATE FUNCTION beep_cache_notify_catalog() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_notify('beep_cache', 'catalog:all');
    RETURN NULL;
END;
$$;
CREATE TRIGGER app_catalog_cache AFTER INSERT OR UPDATE OR DELETE ON app_catalog
    FOR EACH STATEMENT EXECUTE FUNCTION beep_cache_notify_catalog();
