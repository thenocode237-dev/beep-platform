package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.GetStatsUseCase.StatsView;
import io.beep.core.app.port.AnalyticsRepository;
import io.beep.core.domain.bio.BioBlock;
import io.beep.core.domain.bio.BioPage;
import io.beep.core.domain.link.Link;
import io.beep.core.domain.plan.QuotaPolicy;
import io.beep.core.domain.qr.QrCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Représentations JSON de l'API /v1 de l'espace client (aucune donnée interne exposée). */
final class Views {
  private Views() {}

  record LinkView(UUID id, String slug, String shortUrl, String destination, String title, String status, Instant createdAt, Instant updatedAt, Long clicks30d, UUID domainId,
      String domain, RulesView rules, boolean passwordProtected) {}

  record OnelinkView(String ios, String android, String desktop) {}

  record RouteView(java.util.Set<String> countries, java.util.Set<String> os, java.util.Set<String> devices, java.util.Set<String> languages, String url) {}

  record VariantView(String url, int weight) {}

  record UtmView(String source, String medium, String campaign, String term, String content) {}

  record RulesView(Instant expiresAt, boolean deeplink, OnelinkView onelink, List<RouteView> routes, List<VariantView> variants, UtmView utm) {}

  record QuotaView(String resource, String period, long used, long limit, String level) {}

  record QrView(UUID id, String kind, String code, UUID linkId, String contentType, Map<String, String> fields, String foreground, String background,
      String errorCorrection, int margin, String title, String encoded, Instant createdAt, Instant updatedAt, long scans30d, io.beep.core.domain.qr.QrStyle.Decoration advanced) {}

  record BlockView(String id, String type, Map<String, String> config) {}

  record BioView(UUID id, String slug, String publicUrl, String title, String description, String avatarUrl, String theme, String seoTitle,
      String seoDescription, String seoImageUrl, boolean noindex, String primaryColor, String secondaryColor, String accentColor, List<BlockView> blocks, Instant createdAt, Instant updatedAt, long views30d) {}

  record StatsResponse(String resource, String from, String to, int days, int maxDays, String granularity, long clicks, long uniques, Long secondaryClicks,
      List<AnalyticsRepository.Point> series, Map<String, List<AnalyticsRepository.Breakdown>> breakdowns, boolean advanced, boolean exportAllowed) {}

  static LinkView link(Link l, String shortUrl, Long clicks30d) {
    return new LinkView(l.id(), l.slug().value(), shortUrl, l.destination().url(), l.title(), l.status().db(), l.createdAt(), l.updatedAt(), clicks30d, l.domainId(), l.domainHost(), rules(l.rules()), l.passwordProtected());
  }

  static RulesView rules(io.beep.core.domain.link.LinkRules r) {
    return new RulesView(r.expiresAt(), r.deeplink(), r.onelink() == null ? null : new OnelinkView(r.onelink().ios(), r.onelink().android(), r.onelink().desktop()),
        r.routes().stream().map(x -> new RouteView(new java.util.TreeSet<>(x.countries()), new java.util.TreeSet<>(x.os()), new java.util.TreeSet<>(x.devices()), new java.util.TreeSet<>(x.languages()), x.url())).toList(),
        r.variants().stream().map(v -> new VariantView(v.url(), v.weight())).toList(),
        r.utm() == null ? null : new UtmView(r.utm().source(), r.utm().medium(), r.utm().campaign(), r.utm().term(), r.utm().content()));
  }

  static QuotaView quota(QuotaPolicy.Status s) {
    return new QuotaView(s.resource().key(), s.period().db(), s.used(), s.limit(), s.level().db());
  }

  static QrView qr(QrCode q, String encoded, long scans) {
    return new QrView(q.id(), q.kind().db(), q.code(), q.linkId(), q.content() == null ? null : q.content().type().db(), q.content() == null ? null : q.content().fields(),
        q.style().foreground(), q.style().background(), String.valueOf(q.style().errorCorrection()), q.style().margin(), q.title(), encoded, q.createdAt(),
        q.updatedAt(), scans,q.style().advanced());
  }

  static BioView bio(BioPage p, String publicUrl, long views) {
    return new BioView(p.id(), p.slug().value(), publicUrl, p.title(), p.description(), p.avatarUrl(), p.theme().db(), p.seo().title(), p.seo().description(),
        p.seo().imageUrl(), p.seo().noindex(), p.colors().primary(), p.colors().secondary(), p.colors().accent(), p.blocks().stream().map(Views::block).toList(), p.createdAt(), p.updatedAt(), views);
  }

  static BlockView block(BioBlock b) {
    return new BlockView(b.id(), b.type().db(), b.config());
  }

  static StatsResponse stats(StatsView v) {
    return new StatsResponse(v.resource().db(), v.from().toString(), v.to().toString(), v.days(), v.maxDays(), v.granularity(), v.totals().clicks(),
        v.totals().uniques(), v.secondary() == null ? null : v.secondary().clicks(), v.series(), v.breakdowns(), v.advanced(), v.exportAllowed());
  }

  /** CSV (RFC 4180, séparateur virgule, UTF-8 avec BOM pour Excel) : série puis répartitions. */
  static String csv(StatsView v) {
    StringBuilder b = new StringBuilder("﻿");
    b.append("section,cle,clics,visiteurs_uniques\r\n");
    for (AnalyticsRepository.Point p : v.series()) b.append(v.granularity()).append(',').append(cell(p.bucket())).append(',').append(p.clicks()).append(',').append(p.uniques()).append("\r\n");
    v.breakdowns().forEach((dim, rows) -> rows.forEach(r -> b.append(cell(dim)).append(',').append(cell(r.key())).append(',').append(r.clicks()).append(",\r\n")));
    return b.toString();
  }

  private static String cell(String raw) {
    String s = raw == null ? "" : raw;
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s; // neutralise l'injection de formules dans les tableurs
    return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
  }

  static Map<String, Long> entitlements(Map<String, Long> values) {
    return values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.TreeMap::new));
  }
}
