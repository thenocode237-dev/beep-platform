package io.beep.core.domain.enterprise;
import io.beep.core.domain.DomainException;
import java.net.InetAddress;
/** Literal IP networks only: validation never resolves a hostname. */
public record IpNetwork(byte[] address,int prefix) {
 public IpNetwork {address=address.clone();if(prefix<0||prefix>address.length*8)throw new DomainException("invalid_request");}
 @Override public byte[] address(){return address.clone();}
 public static IpNetwork parse(String raw){try{String[] p=raw.split("/",-1);if(p.length>2)throw new IllegalArgumentException();byte[] a=literal(p[0]);int bits=p.length==1?a.length*8:Integer.parseInt(p[1]);return new IpNetwork(a,bits);}catch(Exception e){throw new DomainException("invalid_request");}}
 private static byte[] literal(String s)throws Exception{if(s==null||s.isEmpty()||s.contains("%"))throw new IllegalArgumentException();if(s.contains(":")){if(!s.matches("[0-9a-fA-F:]+"))throw new IllegalArgumentException();}else{if(!s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException();for(String v:s.split("\\."))if(Integer.parseInt(v)>255||v.length()>1&&v.startsWith("0"))throw new IllegalArgumentException();}return InetAddress.getByName(s).getAddress();}
 public boolean contains(String value){try{byte[] b=literal(value);if(b.length!=address.length)return false;for(int i=0;i<prefix;i++)if((address[i/8]&(1<<(7-i%8)))!=(b[i/8]&(1<<(7-i%8))))return false;return true;}catch(Exception e){return false;}}
}
