ALTER TABLE public_content DROP CONSTRAINT public_content_locale_check;
ALTER TABLE public_content ADD CONSTRAINT public_content_locale_check CHECK(locale IN ('fr','en','es','pt'));
