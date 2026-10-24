import Image from "next/image";
import { Icon } from "@beep/ui";
import { experience } from "@/lib/experience";
import type { Locale } from "@/lib/i18n";

/** Composition éditoriale statique : aucune donnée ou fonctionnalité simulée comme réelle. */
export function ProductPreview({
  locale,
  compact = false,
}: {
  locale: Locale;
  compact?: boolean;
}) {
  const c = experience(locale);
  return (
    <figure
      className={`product-scene${compact ? " product-scene--compact" : ""}`}
      aria-label={c.preview}
    >
      <div className="scene-orbit scene-orbit--one" aria-hidden="true" />
      <div className="scene-orbit scene-orbit--two" aria-hidden="true" />
      <svg className="scene-sun" viewBox="0 0 100 100" aria-hidden="true">
        {Array.from({ length: 12 }, (_, i) => (
          <path key={i} d="M50 6v20" transform={`rotate(${i * 30} 50 50)`} />
        ))}
        <circle cx="50" cy="50" r="12" />
      </svg>
      <div className="preview-phone">
        <div className="preview-phone__bar" aria-hidden="true">
          <span />
          beep / naya
          <Icon name="globe" size={12} />
        </div>
        <div className="preview-phone__photo">
          <Image
            src="/assets/atelier.webp"
            alt=""
            width={540}
            height={700}
            priority={!compact}
            sizes="270px"
          />
          <span className="preview-phone__monogram" aria-hidden="true">
            n.
          </span>
        </div>
        <div className="preview-phone__body">
          <p className="preview-phone__name">{c.profileName}</p>
          <p className="preview-phone__bio">{c.profileBio}</p>
          <div className="preview-phone__link">
            {c.collection}
            <Icon name="arrow-right" size={14} />
          </div>
          <div className="preview-phone__link">
            {c.behind}
            <Icon name="arrow-right" size={14} />
          </div>
          <div className="preview-phone__link">
            {c.contact}
            <Icon name="arrow-right" size={14} />
          </div>
          <span className="preview-phone__sign">made with beep.</span>
        </div>
      </div>
      <div className="scene-ticket">
        <span className="scene-ticket__icon">
          <Icon name="link" size={22} />
        </span>
        <span>
          <small>{c.linkLabel}</small>
          <strong>beep.example/naya</strong>
        </span>
        <Icon name="arrow-right" size={20} />
      </div>
      <div className="scene-routing">
        <span className="scene-routing__icon">
          <Icon name="split" size={22} />
        </span>
        <div>
          <strong>{c.destination}</strong>
          <p>
            <Icon name="smartphone" size={14} /> iOS / Android <span>↗</span>{" "}
            Web
          </p>
        </div>
      </div>
      <figcaption>{c.preview}</figcaption>
    </figure>
  );
}
