/* Beep tracking v1. No cookies, localStorage, fingerprint or cross-page id until explicit consent. */
(function(w,d){
  'use strict';
  var script=d.currentScript;if(!script)return;
  var base=new URL(script.src).origin,key=script.getAttribute('data-key')||'',cookieless=script.getAttribute('data-cookieless')==='true';
  var consent=false,visitor=null,cid=null,queued=w.beep&&w.beep.q||[];
  var incoming=new URL(w.location.href).searchParams.get('beep_cid');
  var utm={};['source','medium','campaign','term','content'].forEach(function(k){var value=new URL(w.location.href).searchParams.get('utm_'+k);if(value)utm[k]=value.slice(0,128);});
  function uuid(){if(w.crypto&&w.crypto.randomUUID)return w.crypto.randomUUID();var b=new Uint8Array(16);w.crypto.getRandomValues(b);b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;return Array.from(b,function(x){return x.toString(16).padStart(2,'0');}).join('').replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/,'$1-$2-$3-$4-$5');}
  function read(name){var part=d.cookie.split('; ').find(function(x){return x.indexOf(name+'=')===0;});return part?decodeURIComponent(part.slice(name.length+1)):null;}
  function cookie(name,value,age){d.cookie=name+'='+encodeURIComponent(value)+'; Max-Age='+age+'; Path=/; SameSite=Lax'+(w.location.protocol==='https:'?'; Secure':'');}
  function track(name,props){props=props||{};if(!key||(!consent&&name!=='pageview'))return;
    var payload={key:key,eventId:props.eventId||uuid(),name:name,visitor:consent?visitor:null,cid:consent?cid:null,value:props.value||0,currency:props.currency||null,page:w.location.origin+w.location.pathname,consent:consent,utm:utm};
    var body=JSON.stringify(payload);if(body.length>16384)return;
    // text/plain is a CORS simple request; the server validates key + exact Origin before any ingestion.
    if(w.navigator.sendBeacon&&w.navigator.sendBeacon(base+'/_b/collect',new Blob([body],{type:'text/plain'})))return;
    w.fetch(base+'/_b/collect',{method:'POST',body:body,headers:{'Content-Type':'text/plain'},credentials:'omit',keepalive:true}).catch(function(){});
  }
  w.beep=function(action,name,props){
    if(action==='consent'){
      consent=name===true&&!cookieless&&w.navigator.globalPrivacyControl!==true;
      if(consent){visitor=read('_beep_vid')||uuid();cid=incoming||read('_beep_cid');cookie('_beep_vid',visitor,7776000);if(cid)cookie('_beep_cid',cid,7776000);if(!Object.keys(utm).length){try{utm=JSON.parse(read('_beep_utm')||'{}');}catch{utm={};}}if(Object.keys(utm).length)cookie('_beep_utm',JSON.stringify(utm),7776000);}
      else{visitor=null;cid=null;cookie('_beep_vid','',0);cookie('_beep_cid','',0);cookie('_beep_utm','',0);}
    }else if(action==='track')track(name,props);
  };
  queued.forEach(function(args){w.beep.apply(w,args);});
  track('pageview');
})(window,document);
