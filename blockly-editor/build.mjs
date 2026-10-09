import {build} from 'esbuild';
import {mkdir,copyFile,cp} from 'node:fs/promises';
const destination='../app/src/main/assets/editor';
await mkdir(destination,{recursive:true});
await build({entryPoints:['editor.js'],bundle:true,outfile:destination+'/editor.bundle.js',format:'iife',minify:true,legalComments:'eof'});
await copyFile('index.html',destination+'/index.html');
await cp('node_modules/blockly/media',destination+'/media',{recursive:true});
await copyFile('node_modules/blockly/LICENSE',destination+'/BLOCKLY_LICENSE');
await copyFile('../sample-workflows/examples.json','../app/src/main/assets/examples.json');
