package io.beep.core.domain.qr;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Apparence d'un QR code : couleurs, niveau de correction d'erreur, marge (en modules).
 * Pour rester lisible par tous les lecteurs, les modules doivent être plus foncés que le fond, avec un contraste ≥ 3:1.
 * La personnalisation avancée (formes, logo, cadre) est réservée au plan Pro (phase 2).
 */
public record QrStyle(String foreground, String background, char errorCorrection, int margin, Decoration advanced) {
  public record Decoration(String shape,String gradient,String logo,String frame) {
    public Decoration {
      shape=shape==null?"square":shape;
      frame=frame==null?"none":frame;
      if(!java.util.Set.of("square","rounded","dots").contains(shape)||!java.util.Set.of("none","scan","scan-fr").contains(frame)) throw new DomainException("invalid_qr");
      if(gradient!=null&&!gradient.isBlank()) gradient=color(gradient);else gradient=null;
      if(logo!=null&&!logo.isBlank()) {
        if(logo.length()>90000||!logo.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+")) throw new DomainException("invalid_qr_logo");
      } else logo=null;
    }
  }
  public QrStyle(String foreground,String background,char ecc,int margin) {this(foreground,background,ecc,margin,null);}
  public QrStyle decorated(Decoration decoration) {return new QrStyle(foreground,background,decoration!=null&&decoration.logo()!=null?'H':errorCorrection,decoration==null?margin:Math.max(4,margin),decoration);}

  private static final Pattern HEX = Pattern.compile("^#[0-9A-F]{6}$"); // avant DEFAULT : ordre d'initialisation statique
  public static final QrStyle DEFAULT = new QrStyle("#222B25", "#FFFFFF", 'M', 2);

  public QrStyle {
    foreground = color(foreground);
    background = color(background);
    errorCorrection = advanced!=null&&advanced.logo()!=null?'H':Character.toUpperCase(errorCorrection);
    if(advanced!=null) margin=Math.max(margin,4);
    if ("LMQH".indexOf(errorCorrection) < 0) throw new DomainException("invalid_qr");
    if (margin < 0 || margin > 8) throw new DomainException("invalid_qr");
    double lf = luminance(foreground);
    double lb = luminance(background);
    if(advanced!=null && advanced.gradient()!=null) {
      double other=luminance(advanced.gradient());
      if(other>=lb||(lb+0.05)/(other+0.05)<3.0) throw new DomainException("qr_low_contrast");
    }
    if (lf >= lb || (lb + 0.05) / (lf + 0.05) < 3.0) throw new DomainException("qr_low_contrast");
  }

  public static QrStyle of(String foreground, String background, String errorCorrection, Integer margin) {
    return new QrStyle(foreground == null ? DEFAULT.foreground : foreground, background == null ? DEFAULT.background : background,
        errorCorrection == null || errorCorrection.isBlank() ? DEFAULT.errorCorrection : errorCorrection.charAt(0), margin == null ? DEFAULT.margin : margin);
  }

  private static String color(String raw) {
    String v = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    if (!HEX.matcher(v).matches()) throw new DomainException("invalid_qr");
    return v;
  }

  /** Luminance relative WCAG 2.x. */
  static double luminance(String hex) {
    int rgb = Integer.parseInt(hex.substring(1), 16);
    return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
  }

  private static double channel(int c) {
    double s = c / 255.0;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  }
}
