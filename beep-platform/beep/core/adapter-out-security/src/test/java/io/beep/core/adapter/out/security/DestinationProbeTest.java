package io.beep.core.adapter.out.security;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DestinationProbeTest {
  @Test void destinationHealthCannotReachPrivateNetworksOrNonWebProtocols(){var probe=new PinnedDestinationProbeAdapter();for(String url:java.util.List.of("http://127.0.0.1/","http://169.254.169.254/latest/meta-data/","http://10.0.0.1/","https://[::1]/","file:///etc/passwd","https://example.org:8443/"))assertThatThrownBy(()->probe.probe(url)).isInstanceOf(Exception.class);}
}
