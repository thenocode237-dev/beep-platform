package io.beep.core.adapter.out.qrrender;
import static org.assertj.core.api.Assertions.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import io.beep.core.domain.qr.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AdvancedQrRendererTest {
  final ZxingQrRendererAdapter renderer=new ZxingQrRendererAdapter();
  @Test void everyModuleShapeAndFrameRemainsReadableWithAGradientAndLogo() throws Exception {
    var logo=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);var graphics=logo.createGraphics();graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,0,32,32);graphics.dispose();
    var out=new ByteArrayOutputStream();ImageIO.write(logo,"png",out);String data="data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
    String content="https://beep.click/q/AbCdEf12";
    for(String shape:List.of("square","rounded","dots"))for(String frame:List.of("none","scan","scan-fr")) {
      var style=QrStyle.DEFAULT.decorated(new QrStyle.Decoration(shape,"#304F36",data,frame));
      var png=renderer.render(content,style,QrFormat.PNG,1024);var image=ImageIO.read(new ByteArrayInputStream(png));int w=image.getWidth(),h=image.getHeight();
      var pixels=image.getRGB(0,0,w,h,null,0,w);
      Result decoded;
      try {decoded=new com.google.zxing.qrcode.QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))),Map.of(DecodeHintType.TRY_HARDER,true));}
      catch(ReaderException e){if(System.getenv("QR_TEST_OUTPUT")!=null)ImageIO.write(image,"png",new File(System.getenv("QR_TEST_OUTPUT"),"qr-"+shape+"-"+frame+".png"));throw new AssertionError("Unreadable QR: "+shape+" / "+frame,e);}
      assertThat(decoded.getText()).as("%s / %s",shape,frame).isEqualTo(content);
      assertThat(new String(renderer.render(content,style,QrFormat.SVG,1024),java.nio.charset.StandardCharsets.UTF_8)).contains("<image","data:image/png;base64");
      assertThat(new String(renderer.render(content,style,QrFormat.PDF,1024),java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-1.4").contains("/Subtype /Image","/XObject");
    }
  }
  @Test void invalidLogoAndUnsafeGradientAreRejected() {
    assertThatThrownBy(()->renderer.validateStyle(QrStyle.DEFAULT.decorated(new QrStyle.Decoration("rounded",null,"data:image/png;base64,YmFk","none")))).hasMessageContaining("invalid_qr_logo");
    assertThatThrownBy(()->QrStyle.DEFAULT.decorated(new QrStyle.Decoration("square","#FFFFFF",null,"none"))).hasMessageContaining("qr_low_contrast");
    assertThatThrownBy(()->new QrStyle.Decoration("square",null,"https://remote.example/logo.png","none")).hasMessageContaining("invalid_qr_logo");
  }
}
