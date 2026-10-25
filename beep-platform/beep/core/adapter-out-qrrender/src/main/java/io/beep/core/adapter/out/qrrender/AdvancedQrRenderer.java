package io.beep.core.adapter.out.qrrender;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import io.beep.core.domain.qr.*;
import io.beep.core.domain.DomainException;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.util.zip.DeflaterOutputStream;

/** Same vector primitives for SVG/PDF and PNG. No remote logo URL or system fonts. */
final class AdvancedQrRenderer {
  interface Paint {void shape(double x,double y,double w,double h,double round,String color);void logo(double x,double y,double size,BufferedImage image,String data);}
  static byte[] render(ByteMatrix m,QrStyle s,QrFormat format,int px) {
    var a=s.advanced();int pad=s.margin()+2,n=m.getWidth()+pad*2,h=n+(a.frame().equals("none")?0:7);
    BufferedImage logo=logo(a.logo());
    if(format==QrFormat.SVG){var p=new Svg(n,h,px);draw(m,s,p,pad,n,h,logo);return p.finish();}
    if(format==QrFormat.PDF){var p=new Pdf(n,h);draw(m,s,p,pad,n,h,logo);return p.finish(logo);}
    int scale=Math.max(1,px/n);
    var img=new BufferedImage(n*scale,h*scale,BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();
    g.scale(scale,scale);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    Paint p=new Paint(){public void shape(double x,double y,double w,double h,double round,String color){g.setColor(Color.decode(color));g.fill(round==0?new Rectangle2D.Double(x,y,w,h):new RoundRectangle2D.Double(x,y,w,h,round*2,round*2));}public void logo(double x,double y,double size,BufferedImage image,String data){var t=new AffineTransform();t.translate(x,y);t.scale(size/image.getWidth(),size/image.getHeight());g.drawImage(image,t,null);}};
    try{draw(m,s,p,pad,n,h,logo);var out=new ByteArrayOutputStream();ImageIO.write(img,"png",out);return out.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}finally{g.dispose();}
  }
  static void validate(String data) {logo(data);}
  private static BufferedImage logo(String data) {
    if(data==null)return null;
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data.substring(data.indexOf(',')+1))))){
      var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new DomainException("invalid_qr_logo");var reader=readers.next();
      try{reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>512||h>512)throw new DomainException("invalid_qr_logo");return reader.read(0);}finally{reader.dispose();}
    }catch(IOException|IllegalArgumentException e){throw new DomainException("invalid_qr_logo");}
  }
  private static void draw(ByteMatrix m,QrStyle s,Paint p,int pad,int n,int h,BufferedImage logo){
    p.shape(0,0,n,h,0,s.background());
    if(!s.advanced().frame().equals("none")){p.shape(0,0,n,h,2,s.foreground());p.shape(1,1,n-2,n-2,1,s.background());}
    for(int[] eye:new int[][]{{0,0},{m.getWidth()-7,0},{0,m.getHeight()-7}}){double x=eye[0]+pad,y=eye[1]+pad;p.shape(x,y,7,7,0,s.foreground());p.shape(x+1,y+1,5,5,0,s.background());p.shape(x+2,y+2,3,3,0,s.foreground());}
    for(int y=0;y<m.getHeight();y++)for(int x=0;x<m.getWidth();x++)if(m.get(x,y)==1){
      if((x<7&&y<7)||(x>=m.getWidth()-7&&y<7)||(x<7&&y>=m.getHeight()-7))continue;
      boolean finder=(x<8&&y<8)||(x>=m.getWidth()-8&&y<8)||(x<8&&y>=m.getHeight()-8);
      String shape=finder?"square":s.advanced().shape();double inset=shape.equals("dots")?0.04:0,round=shape.equals("rounded")?0.24:shape.equals("dots")?0.46:0;
      String color=s.advanced().gradient()==null?s.foreground():mix(s.foreground(),s.advanced().gradient(),(x+y)/(2.0*m.getWidth()));
      p.shape(x+pad+inset,y+pad+inset,1-2*inset,1-2*inset,round,color);
    }
    if(logo!=null){double size=Math.max(3,Math.floor(m.getWidth()*0.14));double x=(n-size)/2;p.shape(x-0.5,x-0.5,size+1,size+1,0.3,s.background());p.logo(x,x,size,logo,s.advanced().logo());}
    if(!s.advanced().frame().equals("none")) label(p,s.advanced().frame().equals("scan-fr")?"SCANNEZ":"SCAN ME",n,n+2,s.background());
  }
  private static String mix(String a,String b,double t){int x=Integer.parseInt(a.substring(1),16),y=Integer.parseInt(b.substring(1),16),v=0;for(int shift:new int[]{16,8,0})v|=((int)Math.round(((x>>shift)&255)*(1-t)+((y>>shift)&255)*t))<<shift;return String.format(Locale.ROOT,"#%06X",v);}
  private static final Map<Character,String[]> GLYPHS=Map.of('S',new String[]{"11111","10000","10000","11111","00001","00001","11111"},'C',new String[]{"11111","10000","10000","10000","10000","10000","11111"},'A',new String[]{"01110","10001","10001","11111","10001","10001","10001"},'N',new String[]{"10001","11001","11001","10101","10011","10011","10001"},'M',new String[]{"10001","11011","10101","10101","10001","10001","10001"},'E',new String[]{"11111","10000","10000","11110","10000","10000","11111"},'Z',new String[]{"11111","00001","00010","00100","01000","10000","11111"});
  private static void label(Paint p,String text,int width,double top,String color){double unit=Math.min(0.45,(width-4.0)/(text.length()*6));double left=(width-text.length()*6*unit)/2;for(int i=0;i<text.length();i++){var rows=GLYPHS.get(text.charAt(i));if(rows==null)continue;for(int y=0;y<7;y++)for(int x=0;x<5;x++)if(rows[y].charAt(x)=='1')p.shape(left+(i*6+x)*unit,top+y*unit,unit,unit,0,color);}}
  private static String f(double v){return String.format(Locale.ROOT,"%.4f",v);}
  private static String rgb(String hex){int v=Integer.parseInt(hex.substring(1),16);return f(((v>>16)&255)/255.0)+" "+f(((v>>8)&255)/255.0)+" "+f((v&255)/255.0);}
  static class Svg implements Paint {
    StringBuilder out;Svg(int n,int h,int px){out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""+px+"\" height=\""+(int)Math.ceil(px*(double)h/n)+"\" viewBox=\"0 0 "+n+" "+h+"\" role=\"img\" aria-label=\"QR code\">");}
    public void shape(double x,double y,double w,double h,double r,String color){out.append("<rect x=\"").append(f(x)).append("\" y=\"").append(f(y)).append("\" width=\"").append(f(w)).append("\" height=\"").append(f(h)).append("\" rx=\"").append(f(r)).append("\" fill=\"").append(color).append("\"/>");}
    public void logo(double x,double y,double size,BufferedImage image,String data){out.append("<image x=\"").append(f(x)).append("\" y=\"").append(f(y)).append("\" width=\"").append(f(size)).append("\" height=\"").append(f(size)).append("\" href=\"").append(data).append("\"/>");}
    byte[] finish(){return out.append("</svg>").toString().getBytes(StandardCharsets.UTF_8);}
  }
  static class Pdf implements Paint {
    final int n,h;StringBuilder c=new StringBuilder();Pdf(int n,int h){this.n=n;this.h=h;c.append("q ").append(f(226.77/n)).append(" 0 0 ").append(f(-226.77/n)).append(" 0 ").append(f(226.77*h/n)).append(" cm\n");}
    public void shape(double x,double y,double w,double h,double r,String color){c.append(rgb(color)).append(" rg ");if(r==0){c.append(f(x)).append(' ').append(f(y)).append(' ').append(f(w)).append(' ').append(f(h)).append(" re f\n");return;}
      double k=r*0.55228475,xx=x+w,yy=y+h;c.append(f(x+r)).append(' ').append(f(y)).append(" m ").append(f(xx-r)).append(' ').append(f(y)).append(" l ");curve(xx-r+k,y,xx,y+r-k,xx,y+r);c.append(f(xx)).append(' ').append(f(yy-r)).append(" l ");curve(xx,yy-r+k,xx-r+k,yy,xx-r,yy);c.append(f(x+r)).append(' ').append(f(yy)).append(" l ");curve(x+r-k,yy,x,yy-r+k,x,yy-r);c.append(f(x)).append(' ').append(f(y+r)).append(" l ");curve(x,y+r-k,x+r-k,y,x+r,y);c.append("h f\n");}
    void curve(double a,double b,double d,double e,double g,double h){c.append(f(a)).append(' ').append(f(b)).append(' ').append(f(d)).append(' ').append(f(e)).append(' ').append(f(g)).append(' ').append(f(h)).append(" c ");}
    public void logo(double x,double y,double size,BufferedImage image,String data){c.append("q ").append(f(size)).append(" 0 0 ").append(f(-size)).append(' ').append(f(x)).append(' ').append(f(y+size)).append(" cm /Logo Do Q\n");}
    byte[] finish(BufferedImage logo){
      c.append("Q\n");List<byte[]> objects=new ArrayList<>();objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
      objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 226.77 "+f(226.77*h/n)+"] /Contents 4 0 R /Resources << "+(logo==null?"":"/XObject << /Logo 5 0 R >>")+" >> >>"));objects.add(stream(bytes(c.toString()),""));
      if(logo!=null){try{var raw=new ByteArrayOutputStream();var z=new DeflaterOutputStream(raw);for(int y=0;y<logo.getHeight();y++)for(int x=0;x<logo.getWidth();x++){int v=logo.getRGB(x,y),alpha=(v>>>24)&255;for(int shift:new int[]{16,8,0})z.write((((v>>shift)&255)*alpha+255*(255-alpha))/255);}z.close();objects.add(stream(raw.toByteArray(),"/Type /XObject /Subtype /Image /Width "+logo.getWidth()+" /Height "+logo.getHeight()+" /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode "));}catch(IOException e){throw new UncheckedIOException(e);}}
      var out=new ByteArrayOutputStream();try{out.write(bytes("%PDF-1.4\n"));List<Integer> offsets=new ArrayList<>();for(int i=0;i<objects.size();i++){offsets.add(out.size());out.write(bytes((i+1)+" 0 obj\n"));out.write(objects.get(i));out.write(bytes("\nendobj\n"));}int xref=out.size();out.write(bytes("xref\n0 "+(objects.size()+1)+"\n0000000000 65535 f \n"));for(int offset:offsets)out.write(bytes(String.format(Locale.ROOT,"%010d 00000 n \n",offset)));out.write(bytes("trailer\n<< /Size "+(objects.size()+1)+" /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n"));return out.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}
    }
    static byte[] bytes(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    static byte[] stream(byte[] data,String extra){var out=new ByteArrayOutputStream();try{out.write(bytes("<< "+extra+" /Length "+data.length+" >>\nstream\n"));out.write(data);out.write(bytes("\nendstream"));return out.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}}
  }
}
