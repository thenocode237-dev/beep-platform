package io.beep.core.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentTest {
  static final String IPHONE_SAFARI = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";
  static final String ANDROID_CHROME = "Mozilla/5.0 (Linux; Android 13; SM-A145F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
  static final String WINDOWS_EDGE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0";
  static final String IPAD = "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1";
  static final String INSTAGRAM = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 312.0.0.0";

  @Test
  void recognisesDevicesSystemsAndBrowsers() {
    assertThat(UserAgent.parse(IPHONE_SAFARI)).isEqualTo(new UserAgent(DeviceType.MOBILE, "ios", "safari", false));
    assertThat(UserAgent.parse(ANDROID_CHROME)).isEqualTo(new UserAgent(DeviceType.MOBILE, "android", "chrome", false));
    assertThat(UserAgent.parse(WINDOWS_EDGE)).isEqualTo(new UserAgent(DeviceType.DESKTOP, "windows", "edge", false));
    assertThat(UserAgent.parse(IPAD).device()).isEqualTo(DeviceType.TABLET);
    assertThat(UserAgent.parse(INSTAGRAM).browser()).isEqualTo("instagram");
  }

  @Test
  void linkPreviewsScriptsAndEmptyAgentsAreBots() {
    for (String ua : new String[] {"WhatsApp/2.23.20.0 A", "facebookexternalhit/1.1", "Mozilla/5.0 (compatible; Googlebot/2.1)", "curl/8.4.0",
        "python-requests/2.31", "Grafana k6/0.50.0", "TelegramBot (like TwitterBot)", "", null}) {
      assertThat(UserAgent.parse(ua).bot()).as(String.valueOf(ua)).isTrue();
    }
  }

  @Test
  void referrerKeepsOnlyTheHostAndLanguageOnlyThePrimaryTag() {
    assertThat(VisitSignals.referrerHost("https://www.Instagram.com/p/xyz?igshid=1")).isEqualTo("instagram.com");
    assertThat(VisitSignals.referrerHost(null)).isEqualTo("direct");
    assertThat(VisitSignals.referrerHost("not a url")).isEqualTo("direct");
    assertThat(VisitSignals.primaryLanguage("fr-CM,fr;q=0.9,en;q=0.8")).isEqualTo("fr");
    assertThat(VisitSignals.primaryLanguage("*")).isNull();
  }
}
