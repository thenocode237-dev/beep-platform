"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "@beep/ui";

/** Progressive enhancement: the full page remains visible and usable without JS. */
export function LandingEnhancements({ href, label }: { href: string; label: string }) {
  const bar = useRef<HTMLElement>(null);
  const desiredVisibility = useRef(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const element = bar.current;
    const root = element?.closest<HTMLElement>(".landing-page");
    if (!element || !root || !("IntersectionObserver" in window)) return;

    const hero = root.querySelector("[data-hero-cta]");
    const final = root.querySelector("[data-final-cta]");
    let heroVisible = true;
    let finalVisible = false;
    const ctaObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.target === hero) heroVisible = entry.isIntersecting;
        if (entry.target === final) finalVisible = entry.isIntersecting;
      }
      const next = !heroVisible && !finalVisible;
      desiredVisibility.current = next;
      // A scroll or resize must never hide the control currently holding focus.
      if (next || !element.contains(document.activeElement)) setVisible(next);
    }, { rootMargin: "-80px 0px -92px 0px" });
    if (hero) ctaObserver.observe(hero);
    if (final) ctaObserver.observe(final);

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    const mobile = window.matchMedia("(max-width: 767px)");
    const saveData = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection?.saveData;
    const animations = new Set<Animation>();
    const updateMotion = () => {
      root.dataset.motion = reduced.matches || saveData ? "reduced" : "enabled";
      if (reduced.matches || saveData) {
        for (const animation of animations) animation.cancel();
        animations.clear();
      }
    };
    updateMotion();
    reduced.addEventListener("change", updateMotion);

    const revealObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        revealObserver.unobserve(entry.target);
        if (reduced.matches || saveData || typeof entry.target.animate !== "function") continue;
        const animation = entry.target.animate([
          { opacity: 0, transform: `translateY(${mobile.matches ? 10 : 16}px)` },
          { opacity: 1, transform: "none" },
        ], {
          duration: mobile.matches ? 360 : 480,
          easing: "cubic-bezier(0.16, 1, 0.3, 1)",
          // No permanent fill or hidden CSS state: interrupted animations stay readable.
          fill: "none",
        });
        animations.add(animation);
        void animation.finished.then(() => animations.delete(animation), () => animations.delete(animation));
      }
    }, { threshold: 0.1, rootMargin: "0px 0px -20px 0px" });
    root.querySelectorAll("[data-reveal]").forEach((target) => revealObserver.observe(target));

    return () => {
      ctaObserver.disconnect();
      revealObserver.disconnect();
      reduced.removeEventListener("change", updateMotion);
      for (const animation of animations) animation.cancel();
      delete root.dataset.motion;
    };
  }, []);

  return (
    <aside
      ref={bar}
      className="sticky-cta"
      data-visible={visible}
      aria-label={label}
      aria-hidden={!visible}
      inert={!visible}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setVisible(desiredVisibility.current);
      }}
    >
      <a className="bp-btn bp-btn--block" href={href}>
        {label}
        <Icon name="arrow-right" size={18} />
      </a>
    </aside>
  );
}
