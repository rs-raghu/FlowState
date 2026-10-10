import * as Blockly from 'blockly/core';
import * as En from 'blockly/msg/en';
import {register,toolbox,initialWorkspace,setResources} from './catalogue.js';
Blockly.setLocale(En);register(Blockly);
const workspace=Blockly.inject('workspace',{toolbox,media:'./media/',renderer:'zelos',trashcan:true,scrollbars:true,move:{scrollbars:true,drag:true,wheel:true},zoom:{controls:true,wheel:true,startScale:0.8,maxScale:2,minScale:0.3,scaleSpeed:1.15},grid:{spacing:24,length:3,colour:'#33473c',snap:true}});
let dirty=false,draftTimer;
function send(action){const state=Blockly.serialization.workspaces.save(workspace); window.FlowBridge?.postMessage(JSON.stringify({action,workspace:state}));}
window.FlowEditor={
 resources(value){setResources(value);},
 load(state){Blockly.Events.disable();try{Blockly.serialization.workspaces.load(state||initialWorkspace(),workspace);}finally{Blockly.Events.enable();}dirty=false;},
 highlight(id){workspace.highlightBlock(id);},
 snapshot(){return Blockly.serialization.workspaces.save(workspace);},
 errors(issues){for(const b of workspace.getAllBlocks(false))b.setWarningText(null);for(const i of issues){const b=workspace.getBlockById(i.block);b?.setWarningText((i.severity||'ERROR')+' '+(i.code||'VALIDATION')+': '+i.message+' — '+i.correction);}document.getElementById('status').textContent=issues.length?issues.map(x=>(x.severity||'ERROR')+' '+(x.code||'VALIDATION')+': '+x.message+' — '+x.correction).join('\n'):'Workflow is valid';},
 saved(){dirty=false;document.getElementById('status').textContent='Saved';},
 dirty(){return dirty;}
};
workspace.addChangeListener(event=>{if(!event.isUiEvent){dirty=true;send('dirty');}});
for(const action of ['save','copy','validate','simulate','close'])document.getElementById(action).onclick=()=>send(action);
document.getElementById('undo').onclick=()=>workspace.undo(false);
document.getElementById('redo').onclick=()=>workspace.undo(true);
document.getElementById('tidy').onclick=()=>workspace.cleanUp();
document.getElementById('search').oninput=e=>{const q=e.target.value.toLowerCase();workspace.updateToolbox(q?{kind:'flyoutToolbox',contents:toolbox.contents.flatMap(x=>x.contents).filter(b=>b.type.toLowerCase().includes(q)||Blockly.Blocks[b.type]&&b.type.replace('fs_','').includes(q))}:toolbox);};
window.addEventListener('resize',()=>Blockly.svgResize(workspace));
window.FlowBridge?.postMessage(JSON.stringify({action:'ready'}));
if(!window.FlowBridge)window.FlowEditor.load(initialWorkspace());
