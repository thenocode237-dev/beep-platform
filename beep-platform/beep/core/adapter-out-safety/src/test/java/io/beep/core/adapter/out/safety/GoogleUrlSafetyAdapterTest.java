package io.beep.core.adapter.out.safety;
import static org.assertj.core.api.Assertions.*;
import io.beep.core.app.port.UrlSafetyPort.Verdict;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
class GoogleUrlSafetyAdapterTest {
 @Test void lookupEncodesTheUrlAndDistinguishesThreatsEmptyResultsAndFailures() throws Exception {
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var body=new AtomicReference<>("{}");var query=new AtomicReference<String>();
   server.createContext("/lookup",e->{query.set(e.getRequestURI().getRawQuery());byte[] b=body.get().getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
   try {var adapter=new GoogleUrlSafetyAdapter("web_risk","test-key","http://127.0.0.1:"+server.getAddress().getPort()+"/lookup");
     assertThat(adapter.check("https://example.com/a?x=1&y=2")).isEqualTo(Verdict.SAFE);assertThat(query.get()).contains("uri=https%3A%2F%2Fexample.com%2Fa%3Fx%3D1%26y%3D2");
     body.set("{\"threat\":{\"threatTypes\":[\"MALWARE\"]}}");assertThat(adapter.check("https://example.com")).isEqualTo(Verdict.UNSAFE);
     body.set("not-json");assertThat(adapter.check("https://example.com")).isEqualTo(Verdict.UNAVAILABLE);
   } finally {server.stop(0);}
   assertThat(new GoogleUrlSafetyAdapter("web_risk","").check("https://example.com")).isEqualTo(Verdict.UNCONFIGURED);
 }
}
