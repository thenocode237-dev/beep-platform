package io.beep.core.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.beep.core.domain.link.LinkRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Document JSON des règles d'un lien (colonne `links.rules`). Lecture tolérante : un champ absent prend sa valeur par défaut. */
final class LinkRulesJson {
  private LinkRulesJson() {}

  private static final ObjectMapper JSON = new ObjectMapper();

  static String write(LinkRules r) {
    ObjectNode root = JSON.createObjectNode();
    if (r.expiresAt() != null) root.put("expiresAt", r.expiresAt().toString());
    root.put("deeplink", r.deeplink());
    if (r.onelink() != null) {
      ObjectNode o = root.putObject("onelink");
      if (r.onelink().ios() != null) o.put("ios", r.onelink().ios());
      if (r.onelink().android() != null) o.put("android", r.onelink().android());
      if (r.onelink().desktop() != null) o.put("desktop", r.onelink().desktop());
    }
    ArrayNode routes = root.putArray("routes");
    for (LinkRules.Route route : r.routes()) {
      ObjectNode n = routes.addObject();
      n.set("countries", array(route.countries()));
      n.set("os", array(route.os()));
      n.set("devices", array(route.devices()));
      n.set("languages", array(route.languages()));
      n.put("url", route.url());
    }
    ArrayNode variants = root.putArray("variants");
    for (LinkRules.Variant v : r.variants()) variants.addObject().put("url", v.url()).put("weight", v.weight());
    if (r.utm() != null) {
      ObjectNode u = root.putObject("utm");
      put(u, "source", r.utm().source());
      put(u, "medium", r.utm().medium());
      put(u, "campaign", r.utm().campaign());
      put(u, "term", r.utm().term());
      put(u, "content", r.utm().content());
    }
    try {
      return JSON.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  static LinkRules read(String json) {
    if (json == null || json.isBlank() || json.equals("{}")) return LinkRules.NONE;
    try {
      JsonNode n = JSON.readTree(json);
      Instant expiry = n.hasNonNull("expiresAt") ? Instant.parse(n.get("expiresAt").asText()) : null;
      LinkRules.Onelink onelink = null;
      if (n.hasNonNull("onelink")) {
        JsonNode o = n.get("onelink");
        onelink = new LinkRules.Onelink(text(o, "ios"), text(o, "android"), text(o, "desktop"));
      }
      List<LinkRules.Route> routes = new ArrayList<>();
      for (JsonNode r : n.path("routes")) routes.add(new LinkRules.Route(set(r.path("countries")), set(r.path("os")), set(r.path("devices")), set(r.path("languages")), r.path("url").asText()));
      List<LinkRules.Variant> variants = new ArrayList<>();
      for (JsonNode v : n.path("variants")) variants.add(new LinkRules.Variant(v.path("url").asText(), v.path("weight").asInt(1)));
      LinkRules.Utm utm = null;
      if (n.hasNonNull("utm")) {
        JsonNode u = n.get("utm");
        utm = new LinkRules.Utm(text(u, "source"), text(u, "medium"), text(u, "campaign"), text(u, "term"), text(u, "content"));
      }
      return new LinkRules(expiry, n.path("deeplink").asBoolean(true), onelink, routes, variants, utm);
    } catch (JsonProcessingException | RuntimeException e) {
      throw new IllegalStateException("règles de lien illisibles", e);
    }
  }

  private static void put(ObjectNode n, String key, String value) {
    if (value != null) n.put(key, value);
  }

  private static String text(JsonNode n, String key) {
    return n.hasNonNull(key) ? n.get(key).asText() : null;
  }

  private static ArrayNode array(Set<String> values) {
    ArrayNode a = JSON.createArrayNode();
    values.stream().sorted().forEach(a::add);
    return a;
  }

  private static Set<String> set(JsonNode n) {
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode v : n) out.add(v.asText());
    return out;
  }
}
