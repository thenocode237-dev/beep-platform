-- Couleurs personnalisées des pages bio : {"primary":"#RRGGBB","secondary":"#RRGGBB","accent":"#RRGGBB"}, clés facultatives.
ALTER TABLE bio_pages ADD COLUMN colors jsonb NOT NULL DEFAULT '{}'::jsonb;
