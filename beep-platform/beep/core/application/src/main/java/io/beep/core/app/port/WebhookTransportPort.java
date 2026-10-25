package io.beep.core.app.port;
public interface WebhookTransportPort {
  int deliver(String url, String payload, String signature, String deliveryId) throws Exception;
  default int postback(String provider,String config,String payload,String deliveryId)throws Exception{throw new UnsupportedOperationException();}
}
