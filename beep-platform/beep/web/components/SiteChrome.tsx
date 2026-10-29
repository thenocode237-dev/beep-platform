import { Icon, Logo } from "@beep/ui";
import type { Dict } from "@/lib/dictionaries/fr";
import { experience } from "@/lib/experience";
import { href, type Locale } from "@/lib/i18n";

export function SiteHeader({
  dict,
  locale,
  path = "/",
}: {
  dict: Dict;
  locale: Locale;
  path?: string;
}) {
  const other: Locale = locale === "fr" ? "en" : "fr";
  const c = experience(locale);
  return (
    <header className="site-header">
      <div className="bp-container site-header__inner">
        <Logo href={href(locale)} label={dict.nav.home} variant="wordmark" />
        <nav className="site-header__sections" aria-label={c.navLabel}>
          <a href={href(locale,"/features")}>{c.navFeatures}</a>
          <a href={href(locale,"/pricing")}>{locale==="fr"?"Tarifs":"Pricing"}</a>
          <a href={`${href(locale)}#how-it-works`}>{c.navHow}</a>
        </nav>
        <nav className="site-header__nav" aria-label={c.footerAccount}>
          <a
            className="lang-link"
            href={href(other, path)}
            hrefLang={other}
            lang={other}
            aria-label={`${dict.common.language}: ${other.toUpperCase()}`}
          >
            <Icon name="globe" size={16} /> {other.toUpperCase()}
          </a>
          <a
            className="bp-btn bp-btn--ghost bp-btn--sm header-login"
            href={href(locale, "/login")}
          >
            {dict.nav.login}
          </a>
          <a
            className="bp-btn bp-btn--sm site-header__cta"
            href={href(locale, "/signup")}
          >
            {dict.nav.signup}
            <Icon name="arrow-right" size={16} />
          </a>
        </nav>
      </div>
    </header>
  );
}

export function SiteFooter({ dict, locale }: { dict: Dict; locale: Locale }) {
  const c = experience(locale);
  return (
    <footer className="site-footer">
      <div className="bp-container">
        <div className="site-footer__main">
          <div className="site-footer__brand">
            <Logo href={href(locale)} label={dict.nav.home} />
            <p>{c.footerText}</p>
          </div>
          <nav aria-label={c.footerExplore}>
            <p>{c.footerExplore}</p>
            <a href={href(locale,"/features")}>{c.navFeatures}</a>
          <a href={href(locale,"/pricing")}>{locale==="fr"?"Tarifs":"Pricing"}</a>
            <a href={`${href(locale)}#how-it-works`}>{c.navHow}</a>
            <a href={`${href(locale)}#faq`}>{c.navFaq}</a>
            <a href={href(locale,"/docs")}>{c.navDocs}</a>
            <a href={href(locale,"/blog")}>{locale==="fr"?"Journal":"Journal"}</a>
            <a href={href(locale,"/status")}>{locale==="fr"?"État des services":"Service status"}</a>
          </nav>
          <nav aria-label={c.footerAccount}>
            <p>{c.footerAccount}</p>
            <a href={href(locale, "/signup")}>{dict.auth.signup.title}</a>
            <a href={href(locale, "/login")}>{dict.nav.login}</a>
            <a href={href(locale,"/legal")}>{locale==="fr"?"Mentions légales":"Publisher information"}</a>
            <a href={href(locale,"/legal/privacy")}>{locale==="fr"?"Confidentialité":"Privacy"}</a>
            <a href={href(locale,"/legal/terms")}>{locale==="fr"?"Conditions":"Terms"}</a>
            <a href={href(locale,"/report")}>{locale==="fr"?"Signaler un abus":"Report abuse"}</a>
          </nav>
        </div>
        <div className="site-footer__bottom">
          <p>
            © {new Date().getFullYear()} BeepClick. {dict.footer.rights}
          </p>
          <span>FR / EN</span>
        </div>
      </div>
    </footer>
  );
}
