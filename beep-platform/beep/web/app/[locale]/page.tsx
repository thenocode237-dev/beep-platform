import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/");
import Image from "next/image";
import { SiteStructuredData } from "@/components/SiteStructuredData";
import { notFound } from "next/navigation";
import { Icon, type IconName } from "@beep/ui";
import { SiteFooter, SiteHeader } from "@/components/SiteChrome";
import { ProductPreview } from "@/components/ProductPreview";
import { LandingEnhancements } from "@/components/LandingEnhancements";
import { experience } from "@/lib/experience";
import { getDictionary, href, isLocale } from "@/lib/i18n";
import "./landing.css";

export default async function Home({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = getDictionary(locale);
  const h = dict.home;
  const c = experience(locale);
  return (
    <div className="landing-page">
      <SiteStructuredData locale={locale} />
      <SiteHeader dict={dict} locale={locale} />
      <main id="main">
        <section className="hero">
          <div className="bp-container hero__inner">
            <div className="hero__copy" data-reveal="">
              <h1>
                {c.title}
                <br />
                <span>{c.titleAccent}</span>
              </h1>
              <p className="hero__sub">{c.intro}</p>
              <div className="hero__actions" data-hero-cta="">
                <a className="bp-btn" href={href(locale, "/signup")}>
                  {h.ctaPrimary}
                  <Icon name="arrow-right" size={18} />
                </a>
                <a className="text-link" href="#possibilities">
                  {c.explore}
                  <Icon name="arrow-right" size={18} />
                </a>
              </div>
              <p className="hero__note">
                <Icon name="check" size={16} />
                {c.accountNote}
              </p>
            </div>
            <ProductPreview locale={locale} />
          </div>
          <div className="bp-container">
            <div className="hero__platform">
              <span><Icon name="check" size={17} />{c.release}</span>
              <a className="text-link" href={href(locale, "/pricing")}>
                {c.pricingLink}<Icon name="arrow-right" size={16} />
              </a>
            </div>
          </div>
        </section>

        <section className="audiences" aria-label={c.audienceTitle}>
          <div className="bp-container audiences__inner">
            <p>{c.audienceTitle}</p>
            <ul>
              {h.audiences.map((a, i) => (
                <li key={a}>
                  <Icon
                    name={["bio", "bag", "chart", "globe"][i] as IconName}
                    size={22}
                  />
                  {a}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section
          className="benefits"
          aria-label={locale === "fr" ? "Pourquoi Beep" : "Why Beep"}
        >
          <div className="bp-container">
            <ul className="benefit-grid">
              {c.benefits.map((b) => (
                <li key={b.title} data-reveal="">
                  <Icon name={b.icon as IconName} size={25} />
                  <h2>{b.title}</h2>
                  <p>{b.text}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="possibilities" className="section features-section">
          <div className="bp-container">
            <div className="section-heading" data-reveal="">
              <h2>
                {c.featureTitle}
                <br />
                <span>{c.featureAccent}</span>
              </h2>
              <p>{c.featureSub}</p>
            </div>
            <ul className="feature-grid">
              {h.features.map((f, i) => (
                <li key={f.title} className={`feature feature--${i}`} data-reveal="">
                  <div className="feature__top">
                    <Icon name={f.icon as IconName} size={24} />
                    <span className="feature__number" aria-hidden="true">{String(i + 1).padStart(2, "0")}</span>
                  </div>
                  <h3>{f.title}</h3>
                  <p className="muted">{f.text}</p>
                  <div className="feature__visual" aria-hidden="true">
                    {i === 0 && (
                      <div className="mini-short">
                        <small>{c.shortFrom}</small>
                        <span className="mini-short__long">
                          votre-boutique.example/collections/nouvelle-saison
                        </span>
                        <Icon name="arrow-right" size={18} />
                        <strong>
                          <Icon name="link" size={18} />
                          beep.example/vous
                        </strong>
                      </div>
                    )}
                    {i === 1 && (
                      <div className="mini-app">
                        <span>
                          <Icon name="smartphone" size={30} />
                        </span>
                        <span className="mini-app__line" />
                        <span>
                          <Icon name="arrow-right" size={25} />
                        </span>
                      </div>
                    )}
                    {i === 2 && (
                      <div className="mini-routing">
                        {c.routing.map((r, j) => (
                          <div key={r}>
                            <Icon
                              name={j === 2 ? "globe" : "smartphone"}
                              size={17}
                            />
                            {r}
                            <Icon name="check" size={16} />
                          </div>
                        ))}
                      </div>
                    )}
                    {i === 3 && (
                      <div className="mini-qr">
                        <svg viewBox="0 0 100 100">
                          <path
                            d="M5 5h28v28H5zM67 5h28v28H67zM5 67h28v28H5z"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="8"
                          />
                          <path
                            d="M15 15h8v8h-8zm62 0h8v8h-8zM15 77h8v8h-8zM43 5h9v19h-9zm0 34h14v13H43zm25 5h10v13H68zM43 64h10v30H43zm18 8h13v10H61zm22-9h12v31H83zM5 43h24v9H5zm63 46h9v9h-9z"
                            fill="currentColor"
                          />
                        </svg>
                        <span>{c.qrDemo}</span>
                      </div>
                    )}
                    {i === 4 && (
                      <div className="mini-bio">
                        <span className="mini-bio__avatar">n.</span>
                        <strong>atelier naya</strong>
                        <span />
                        <span />
                        <span />
                      </div>
                    )}
                    {i === 5 && (
                      <div className="mini-chart">
                        {[25, 42, 32, 60, 49, 78, 66, 94, 83, 100].map(
                          (v, j) => (
                            <span key={j} style={{ height: `${v}%` }} />
                          ),
                        )}
                      </div>
                    )}
                  </div>
                  {i === 3 && <span className="bp-sr-only">{c.qrNote}</span>}
                  {i === 5 && (
                    <span className="visual-caption">{c.chartCaption}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="growth-section" aria-labelledby="growth-title">
          <div className="bp-container">
            <div className="growth-section__heading" data-reveal="">
              <p className="section-kicker">{c.growth.eyebrow}</p>
              <h2 id="growth-title">{c.growth.title}</h2>
              <p>{c.growth.intro}</p>
            </div>
            <ul className="growth-grid">
              {c.growth.cards.map((card) => (
                <li key={card.title} data-reveal="">
                  <Icon name={card.icon as IconName} size={26} />
                  <h3>{card.title}</h3>
                  <p>{card.text}</p>
                </li>
              ))}
            </ul>
            <div className="growth-section__actions">
              <a className="bp-btn" href={href(locale, "/pricing")}>
                {c.pricingLink}<Icon name="arrow-right" size={18} />
              </a>
              <a className="text-link" href={href(locale, "/docs")}>
                {c.growth.docs}<Icon name="arrow-right" size={18} />
              </a>
            </div>
            <p className="growth-section__note">{c.growth.note}</p>
          </div>
        </section>

        <section id="how-it-works" className="section story-section">
          <div className="bp-container story-grid">
            <div className="story-photo" data-reveal="">
              <Image
                src="/assets/workspace.webp"
                alt={c.photoAlt}
                width={900}
                height={1000}
                sizes="(max-width: 767px) 100vw, 45vw"
              />
              <div className="story-photo__note">
                <Icon name="link" size={22} />
                <span>
                  {c.storyTitle}
                  <br />
                  <strong>{c.storyAccent}</strong>
                </span>
              </div>
            </div>
            <div className="story-copy" data-reveal="">
              <h2>
                {c.storyTitle}
                <br />
                <span>{c.storyAccent}</span>
              </h2>
              <p className="muted">{c.storyText}</p>
              <h3 className="steps-title">{c.stepsTitle}</h3>
              <ol className="steps">
                {h.steps.map((s, i) => (
                  <li key={s.title}>
                    <span className="steps__n">0{i + 1}</span>
                    <div>
                      <h3>{s.title}</h3>
                      <p className="muted">{s.text}</p>
                    </div>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </section>

        <section id="faq" className="section faq-section">
          <div className="bp-container faq-grid">
            <div data-reveal="">
              <h2>{c.faqTitle}</h2>
              <p>{c.faqIntro}</p>
              <a className="text-link" href={href(locale, "/pricing")}>
                {c.pricingLink}<Icon name="arrow-right" size={16} />
              </a>
            </div>
            <div>
              {c.faqs.map((f) => (
                <details className="faq" key={f.q}>
                  <summary>
                    {f.q}
                    <Icon name="plus" size={20} />
                  </summary>
                  <p>{f.a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section className="final-cta">
          <div className="bp-container final-cta__inner" data-reveal="">
            <div>
              <h2>
                {c.finalTitle}
                <br />
                <span>{c.finalAccent}</span>
              </h2>
              <p>{c.finalText}</p>
            </div>
            <a className="bp-btn" href={href(locale, "/signup")} data-final-cta="">
              {h.ctaPrimary}
              <Icon name="arrow-right" size={19} />
            </a>
            <svg
              className="final-cta__flower"
              viewBox="0 0 100 100"
              aria-hidden="true"
            >
              {Array.from({ length: 8 }, (_, i) => (
                <ellipse
                  key={i}
                  cx="50"
                  cy="28"
                  rx="10"
                  ry="25"
                  transform={`rotate(${i * 45} 50 50)`}
                />
              ))}
            </svg>
          </div>
        </section>
      </main>
      <SiteFooter dict={dict} locale={locale} />
      <LandingEnhancements href={href(locale, "/signup")} label={h.ctaPrimary} />
    </div>
  );
}
