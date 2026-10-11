import * as Blockly from 'blockly/core';
import * as En from 'blockly/msg/en';
import {register,toolbox,initialWorkspace,setResources} from './catalogue.js';
Blockly.setLocale(En);register(Blockly);
const workspace=Blockly.inject('workspace',{media:'./media/',sounds:false,renderer:'zelos',trashcan:true,scrollbars:true,move:{scrollbars:true,drag:true,wheel:true},zoom:{controls:true,wheel:true,startScale:0.7,maxScale:2,minScale:0.3,scaleSpeed:1.15},grid:{spacing:24,length:3,colour:'#c3d3c6',snap:true}});
let dirty=false,defaults={},advanced=false;
const status=document.getElementById('status');
const names={trigger:['Start workflow','Choose manual, time or location'],message:['Show message','Send a notification'],ask:['Ask a question','Choose an answer and branch'],checklist:['Checklist','Track required and optional items'],notifyUpdate:['Update notification','Change an earlier message'],notifyCancel:['Cancel notification','Remove an earlier message'],if:['If / else','Choose between two paths'],switch:['Choose a path','Match a value to a branch'],expr:['Expression','Compare, calculate or use location'],value:['Value','Text, number, true or false'],list:['List of values','Group typed values'],wait:['Wait','Pause for a number of seconds'],waitUntil:['Wait until a date','Resume at a timestamp'],waitClock:['Wait until a time','Resume at the next clock time'],waitCondition:['Wait for a condition','Check until a condition is met'],setTimeout:['Set a deadline','Limit this workflow’s duration'],repeat:['Repeat','Run actions a fixed number of times'],while:['While','Repeat while a condition is true'],break:['Leave loop','Stop the current loop'],continue:['Next loop iteration','Skip to the next repetition'],variable:['Create variable','Store a named value'],set:['Set variable','Change a stored value'],get:['Read variable','Use a stored value'],delete:['Reset variable','Clear a stored value'],call:['Run another workflow','Call a reusable automation'],return:['Return a value','Finish a reusable workflow'],try:['Handle an error','Choose actions when something fails'],parallel:['Run two paths','Run branches alongside each other'],stop:['Stop workflow','Finish this run'],log:['Log a note','Add a private activity note'],assert:['Check a condition','Fail with a useful message'],breakpoint:['Debug pause','Pause during simulation']};
const basic={fs_message:['TITLE','BODY','CHANNEL'],fs_notifyUpdate:['TITLE','BODY','CHANNEL','TARGET'],fs_ask:['KIND','TITLE','OPTIONS','TIMEOUT'],fs_checklist:['TITLE','TEMPLATE','OPTIONS','TIMEOUT']};
function report(error){status.textContent='Could not complete that action: '+(error.message||error);}
function safely(action){try{action();}catch(error){report(error);}}
function picker(open){document.getElementById('picker').hidden=!open;document.getElementById('stage').classList.toggle('picker-open',open);document.getElementById('blocks').setAttribute('aria-expanded',String(open));Blockly.svgResize(workspace);}
function simplify(block){
 let visible=basic[block.type];
 if(block.type==='fs_trigger'){
  const kind=block.getFieldValue('KIND'),repeat=block.getFieldValue('RECURRENCE');
  visible=kind==='location'?['KIND','LOCATION','TRANSITION']:kind==='time'?['KIND','TIMES','RECURRENCE','ZONE',...(['weekly','weekdays'].includes(repeat)?['DAYS']:[]),...(['dates','once','monthly'].includes(repeat)?['DATES']:[]),...(repeat==='interval'?['INTERVAL']:[]),...(repeat==='weeklyWindow'?['WINDOWDAY']:[])]:['KIND'];
 }
 if(!visible)return;
 for(const input of block.inputList){const fields=input.fieldRow.map(field=>field.name).filter(Boolean);if(fields.length)input.setVisible(advanced||fields.some(name=>visible.includes(name)));}
 if(block.rendered)block.render();
}
function addBlock(type){
 const trigger=workspace.getTopBlocks(true).find(block=>block.type==='fs_trigger');
 if(type==='fs_trigger'&&trigger){picker(false);trigger.select();workspace.centerOnBlock(trigger.id);status.textContent='Your workflow already has a start block. Change its type to manual, time or location.';return;}
 const selected=Blockly.common.getSelected();
 let anchor=selected&&selected.workspace===workspace?selected:trigger;
 const block=workspace.newBlock(type);block.initSvg();block.render();
 if(block.previousConnection&&anchor){
  // Extend the selected action chain without stealing any existing branch connections.
  while(anchor.getNextBlock())anchor=anchor.getNextBlock();
  if(anchor.nextConnection&&workspace.connectionChecker.canConnect(anchor.nextConnection,block.previousConnection,false))anchor.nextConnection.connect(block.previousConnection);
 }
 if(!block.getParent()){
  const position=anchor?.getRelativeToSurfaceXY()||{x:30,y:30};block.moveBy(position.x+(block.outputConnection?40:0),position.y+(anchor?100:0));
 }
 simplify(block);picker(false);block.select();workspace.centerOnBlock(block.id);
 status.textContent=block.outputConnection?'Drag this value into an empty input on another block.':'Block added. Edit its fields, then tap Save.';
}
const groupOrder=['Interactions','Triggers','Time & loops','Logic','Variables','Control','Debug'];
for(const group of [...toolbox.contents].sort((a,b)=>groupOrder.indexOf(a.name)-groupOrder.indexOf(b.name))){
 const section=document.createElement('section');section.className='group';
 const title=document.createElement('h2');title.textContent=group.name;section.appendChild(title);
 const grid=document.createElement('div');grid.className='block-grid';
 for(const entry of group.contents){
  const [name,description]=names[entry.type.replace('fs_','')];
  const button=document.createElement('button');button.className='block-choice';button.dataset.block=entry.type;
  const label=document.createElement('strong');label.textContent=name;const detail=document.createElement('span');detail.textContent=description;
  button.append(label,detail);button.onclick=()=>safely(()=>addBlock(entry.type));grid.appendChild(button);
 }
 section.appendChild(grid);document.getElementById('catalogue').appendChild(section);
}
function send(action){const state=Blockly.serialization.workspaces.save(workspace);window.FlowBridge?.postMessage(JSON.stringify({action,workspace:state}));}
window.FlowEditor={
 resources(value){setResources(value);defaults=value.defaults||{};},
 load(state){Blockly.Events.disable();try{Blockly.serialization.workspaces.load(state||initialWorkspace(),workspace);workspace.getAllBlocks(false).forEach(simplify);}finally{Blockly.Events.enable();}dirty=false;Blockly.svgResize(workspace);},
 highlight(id){picker(false);workspace.highlightBlock(id);if(id&&workspace.getBlockById(id))workspace.centerOnBlock(id);},
 snapshot(){return Blockly.serialization.workspaces.save(workspace);},
 takeMessage(){return window.FlowEditorMessages.shift()||null;},
 errors(issues){for(const block of workspace.getAllBlocks(false))block.setWarningText(null);for(const issue of issues)workspace.getBlockById(issue.block)?.setWarningText(issue.message+' — '+issue.correction);status.textContent=issues.length?issues.map(issue=>issue.message+' — '+issue.correction).join('\n'):'Workflow is valid';},
 saved(){dirty=false;status.textContent='Saved. Tap Back to return, then Run to try your automation.';},
 dirty(){return dirty;}
};
workspace.addChangeListener(event=>{if(!event.isUiEvent){safely(()=>{
 if(event.type===Blockly.Events.BLOCK_CREATE)for(const id of event.ids||[]){const block=workspace.getBlockById(id);if(!block)continue;const fields=block.type==='fs_trigger'?{ZONE:defaults.timezone,COOLDOWN:defaults.cooldown,CONCURRENCY:defaults.concurrency,CATCHUP:defaults.catchUp}:['fs_ask','fs_checklist'].includes(block.type)?{TIMEOUT:defaults.expiration,CHANNEL:defaults.channel}:['fs_message','fs_notifyUpdate'].includes(block.type)?{CHANNEL:defaults.channel}:{};for(const [key,value] of Object.entries(fields))if(value!==undefined&&block.getField(key))block.setFieldValue(value,key);simplify(block);}
 if(event.type===Blockly.Events.BLOCK_CHANGE&&event.element==='field'){const block=workspace.getBlockById(event.blockId);if(block)simplify(block);}
 dirty=true;send('dirty');});}});
for(const action of ['save','copy','validate','simulate','close'])document.getElementById(action).onclick=()=>safely(()=>{document.getElementById('more').open=false;send(action);});
for(const [id,action] of [['undo',()=>workspace.undo(false)],['redo',()=>workspace.undo(true)],['tidy',()=>workspace.cleanUp()]])document.getElementById(id).onclick=()=>safely(()=>{document.getElementById('more').open=false;action();});
document.getElementById('blocks').onclick=()=>picker(document.getElementById('picker').hidden);
document.getElementById('picker-close').onclick=()=>picker(false);
document.getElementById('advanced').onclick=()=>safely(()=>{advanced=!advanced;workspace.getAllBlocks(false).forEach(simplify);const button=document.getElementById('advanced');button.textContent=advanced?'Hide advanced fields':'Show advanced fields';button.setAttribute('aria-pressed',String(advanced));document.getElementById('more').open=false;Blockly.svgResize(workspace);});
window.addEventListener('resize',()=>Blockly.svgResize(workspace));
window.FlowBridge?.postMessage(JSON.stringify({action:'ready'}));
if(!window.FlowBridge)window.FlowEditor.load(initialWorkspace());
