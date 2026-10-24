const fs = require('node:fs');
const path = require('node:path');
const sharp = require('C:/Users/nmogogabiapsi.ext/Desktop/Vendeer/node_modules/sharp');
const target = path.join(__dirname, 'beep-platform/beep/web/public/assets');
fs.mkdirSync(target, { recursive: true });
Promise.all([
  sharp('C:/Users/nmogogabiapsi.ext/Desktop/Vendeer/front/public/images/auth-atelier-v1.webp').resize(540,700,{fit:'cover'}).webp({quality:78}).toFile(path.join(target,'atelier.webp')),
  sharp('C:/Users/nmogogabiapsi.ext/Desktop/Vendeer/front/public/images/workspace.jpg').resize(900,1000,{fit:'cover'}).webp({quality:78}).toFile(path.join(target,'workspace.webp')),
]).then(r => console.log(r.map(x => ({width:x.width,height:x.height,bytes:x.size}))));
