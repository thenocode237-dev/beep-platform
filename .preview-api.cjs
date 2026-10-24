// Données de recette uniquement ; ce serveur n'est jamais livré dans l'application.
const http = require('node:http');
const user = {id:'preview-user',email:'camille@example.test',emailVerified:true,totpEnabled:false};
http.createServer((req,res) => {
  res.setHeader('content-type','application/json');
  const staff = (req.headers.cookie || '').includes('preview_staff=1');
  const account = (req.headers.cookie || '').includes('preview_user=1');
  let status=200, body={};
  if(req.url==='/v1/auth/me') {status=account?200:401;body=user;}
  else if(req.url==='/api/auth/me') {status=staff?200:401;body={id:'preview-staff',email:'equipe@example.test',role:'ADMIN'};}
  else if(req.url==='/api/status') body={core:'up'};
  else if(req.url.startsWith('/api/users')) body={items:[{...user,status:'ACTIVE',createdAt:'2026-10-23T09:30:00Z'},{id:'preview-2',email:'un.nom.particulierement.long.pour.verifier.le.mobile@example.test',status:'ACTIVE',emailVerified:false,totpEnabled:true,createdAt:'2026-10-22T08:00:00Z'}],nextCursor:null};
  else {status=404;body={code:'not_found'};}
  res.writeHead(status);res.end(JSON.stringify(body));
}).listen(3199,'127.0.0.1',()=>console.log('Preview API on 3199 (fixtures only)'));
