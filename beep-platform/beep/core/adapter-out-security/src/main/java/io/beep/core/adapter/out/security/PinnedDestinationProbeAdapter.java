package io.beep.core.adapter.out.security;
import io.beep.core.app.port.DestinationProbePort;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** Bounded status-only probe. Every DNS answer must be public; redirects are reported, never followed. */
public class PinnedDestinationProbeAdapter implements DestinationProbePort {
  @Override public int probe(String url)throws Exception{
    URI u=URI.create(url);boolean secure="https".equals(u.getScheme());int port=secure?443:80;
    if(!secure&&!"http".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=port)throw new IOException("unsafe destination");
    InetAddress[] addresses=InetAddress.getAllByName(u.getHost());if(addresses.length==0||Arrays.stream(addresses).anyMatch(a->!PinnedWebhookTransportAdapter.publicAddress(a)))throw new IOException("non-public destination");
    try(Socket tcp=new Socket()){
      tcp.connect(new InetSocketAddress(addresses[0],port),2000);tcp.setSoTimeout(5000);Socket channel=tcp;
      if(secure){var tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,u.getHost(),port,false);var p=tls.getSSLParameters();p.setEndpointIdentificationAlgorithm("HTTPS");p.setServerNames(List.of(new SNIHostName(u.getHost())));tls.setSSLParameters(p);tls.setSoTimeout(5000);tls.startHandshake();channel=tls;}
      try(Socket connection=channel){String path=(u.getRawPath()==null||u.getRawPath().isBlank()?"/":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery());connection.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: "+u.getHost()+"\r\nUser-Agent: Beep-Health/1\r\nRange: bytes=0-0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));connection.getOutputStream().flush();var input=connection.getInputStream();var line=new ByteArrayOutputStream();for(int n=0;n<4096;n++){int c=input.read();if(c<0||c=='\n')break;line.write(c);}return PinnedWebhookTransportAdapter.statusLine(line.toString(StandardCharsets.US_ASCII));}
    }
  }
}
