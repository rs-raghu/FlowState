const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
(async()=>{
 const root=path.resolve(__dirname,'../app/src/main/assets/editor');
 const server=http.createServer((req,res)=>{
  const pathname=decodeURIComponent(req.url.split('?')[0]);
  const file=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));
  if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
  try{res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.html')?'text/html':file.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(file));}catch{res.writeHead(404);res.end();}
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({headless:true,...(process.env.FLOWSTATE_CHROME?{executablePath:process.env.FLOWSTATE_CHROME}:{})});
 fs.mkdirSync(path.join(__dirname,'browser-reports'),{recursive:true});
 try{
  for(const width of [360,412,800]){
   const page=await browser.newPage({viewport:{width,height:800}});const errors=[];page.on('pageerror',error=>errors.push(error.message));
   await page.goto(`http://127.0.0.1:${server.address().port}/`);
   await page.waitForFunction(()=>typeof window.FlowEditor==='object');
   await page.evaluate(()=>FlowEditor.load(null));
   assert.equal(await page.locator('#search').count(),0,'No required search field');
   const {definitions}=await import('./catalogue.js');
   assert.deepEqual(new Set(await page.locator('.block-choice').evaluateAll(nodes=>nodes.map(node=>node.dataset.block))),new Set(definitions.map(block=>block.type)),'Every block is browsable');
   await page.screenshot({path:path.join(__dirname,`browser-reports/blocks-${width}.png`)});
   await page.locator('[data-block="fs_message"]').click();
   await page.waitForFunction(()=>FlowEditor.snapshot().blocks.blocks[0].next.block.next?.block.type==='fs_message');
   assert.equal(await page.locator('#picker').isVisible(),false,'Picker closes after insertion');
   const before=await page.evaluate(()=>FlowEditor.snapshot());
   await page.locator('#more summary').click();await page.locator('#advanced').click();
   await page.locator('#more summary').click();await page.locator('#advanced').click();
   assert.deepEqual(await page.evaluate(()=>FlowEditor.snapshot()),before,'Compact fields preserve workflow data');
   await page.locator('#save').click();
   assert.equal(await page.evaluate(()=>JSON.parse(FlowEditorMessages.at(-1)).action),'save','Save reaches native bridge');
   await page.locator('#blocks').click();await page.locator('[data-block="fs_trigger"]').click();
   assert.equal(await page.evaluate(()=>FlowEditor.snapshot().blocks.blocks.filter(block=>block.type==='fs_trigger').length),1,'Start block is unique');
   await page.screenshot({path:path.join(__dirname,`browser-reports/workflow-${width}.png`)});
   assert.deepEqual(errors,[],'No uncaught browser errors');assert.deepEqual(await page.evaluate(()=>FlowEditorErrors),[]);
   const buttons=await page.locator('header>button,header summary').evaluateAll(nodes=>nodes.map(node=>{const r=node.getBoundingClientRect();return {x:r.x,right:r.right,height:r.height};}));
   assert.ok(buttons.every(button=>button.x>=0&&button.right<=width&&button.height>=44),'Toolbar fits phone with touch-sized actions');
   console.log(`PASS ${width}px: browse all blocks, add/connect, preserve fields, save, toolbar and renderer`);
   await page.close();
  }
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
