import * as Blockly from 'blockly/core';
import * as En from 'blockly/msg/en';
import {register,toolbox,initialWorkspace,setResources} from './catalogue.js';
Blockly.setLocale(En);register(Blockly);
const workspace=Blockly.inject('workspace',{toolbox,media:'./media/',sounds:false,renderer:'zelos',trashcan:true,scrollbars:true,move:{scrollbars:true,drag:true,wheel:true},zoom:{controls:true,wheel:true,startScale:0.8,maxScale:2,minScale:0.3,scaleSpeed:1.15},grid:{spacing:24,length:3,colour:'#33473c',snap:true}});
let dirty=false,defaults={};
function send(action){const state=Blockly.serialization.workspaces.save(workspace); window.FlowBridge?.postMessage(JSON.stringify({action,workspace:state}));}
window.FlowEditor={
 resources(value){setResources(value);defaults=value.defaults||{};},
 load(state){Blockly.Events.disable();try{Blockly.serialization.workspaces.load(state||initialWorkspace(),workspace);}finally{Blockly.Events.enable();}dirty=false;},
 highlight(id){workspace.highlightBlock(id);},
 snapshot(){return Blockly.serialization.workspaces.save(workspace);},
 takeMessage(){return window.FlowEditorMessages.shift()||null;},
 errors(issues){for(const b of workspace.getAllBlocks(false))b.setWarningText(null);for(const i of issues){const b=workspace.getBlockById(i.block);b?.setWarningText((i.severity||'ERROR')+' '+(i.code||'VALIDATION')+': '+i.message+' — '+i.correction);}document.getElementById('status').textContent=issues.length?issues.map(x=>(x.severity||'ERROR')+' '+(x.code||'VALIDATION')+': '+x.message+' — '+x.correction).join('\n'):'Workflow is valid';},
 saved(){dirty=false;document.getElementById('status').textContent='Saved';},
 dirty(){return dirty;}
};
workspace.addChangeListener(event=>{if(!event.isUiEvent){
 if(event.type===Blockly.Events.BLOCK_CREATE)for(const id of event.ids||[]){const b=workspace.getBlockById(id);if(!b)continue;const fields=b.type==='fs_trigger'?{ZONE:defaults.timezone,COOLDOWN:defaults.cooldown,CONCURRENCY:defaults.concurrency,CATCHUP:defaults.catchUp}:['fs_ask','fs_checklist'].includes(b.type)?{TIMEOUT:defaults.expiration,CHANNEL:defaults.channel}:['fs_message','fs_notifyUpdate'].includes(b.type)?{CHANNEL:defaults.channel}:{};for(const [key,value] of Object.entries(fields))if(value!==undefined&&b.getField(key))b.setFieldValue(value,key);}
 dirty=true;send('dirty');}});
for(const action of ['save','copy','validate','simulate','close'])document.getElementById(action).onclick=()=>send(action);
document.getElementById('undo').onclick=()=>workspace.undo(false);
document.getElementById('redo').onclick=()=>workspace.undo(true);
document.getElementById('tidy').onclick=()=>workspace.cleanUp();
document.getElementById('search').oninput=e=>{const q=e.target.value.toLowerCase();workspace.updateToolbox(q?{kind:'flyoutToolbox',contents:toolbox.contents.flatMap(x=>x.contents).filter(b=>b.type.toLowerCase().includes(q)||Blockly.Blocks[b.type]&&b.type.replace('fs_','').includes(q))}:toolbox);};
window.addEventListener('resize',()=>Blockly.svgResize(workspace));
window.FlowBridge?.postMessage(JSON.stringify({action:'ready'}));
if(!window.FlowBridge)window.FlowEditor.load(initialWorkspace());
