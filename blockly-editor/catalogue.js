export const types = ['BOOLEAN','INTEGER','DECIMAL','STRING','INSTANT','DURATION','LIST','NULL'];
let locations=[],workflows=[];
export function setResources(value){locations=value.locations||[];workflows=value.workflows||[];}
const resource=(name,kind)=>({type:'field_dropdown',name,options:()=>{const list=kind==='location'?locations:workflows;return list.length?list.map(x=>[x.name,x.id]):[['Select a saved '+kind,'']];}});
const text=(name,value='')=>({type:'field_input',name,text:value});
const number=(name,value,min=0,max=31536000)=>({type:'field_number',name,value,min,max,precision:1});
const dropdown=(name,values)=>({type:'field_dropdown',name,options:values.map(x=>[x,x])});
const expr=(name,check)=>({type:'input_value',name,...(check?{check}: {})});
const body=name=>({type:'input_statement',name});
const scope=()=>dropdown('SCOPE',['LOCAL','AUTOMATION','GLOBAL']);
function block(type,label,args,colour=175,output){
  const def={type:'fs_'+type,message0:label.split(' / ')[0],args0:[],colour,tooltip:label+' — configure fields and connect typed inputs. Version 1.',helpUrl:''};
  args.forEach((arg,i)=>{def['message'+(i+1)]=arg.name.replaceAll('_',' ')+' %1';def['args'+(i+1)]=[arg];});
  if(output) def.output=output; else {def.previousStatement=null;def.nextStatement=null;}
  return def;
}
export const definitions=[
 {...block('trigger','WHEN kind / times / zone / repeat / days / dates / interval / start / end / location / event / cooldown / catch-up / window day',[
 dropdown('KIND',['manual','time','location']),text('TIMES','09:00'),text('ZONE','device'),dropdown('RECURRENCE',['daily','weekdays','weekly','monthly','dates','interval','once','weeklyWindow']),text('DAYS','1,2,3,4,5,6,7'),text('DATES'),number('INTERVAL',1,1,3650),text('START'),text('END'),resource('LOCATION','location'),dropdown('TRANSITION',['enter','exit','dwell']),number('COOLDOWN',300),dropdown('CATCHUP',['skip','grace','window','record','ask']),number('WINDOWDAY',5,1,7)],40),previousStatement:undefined},
 block('message','SHOW MESSAGE title / body',[text('TITLE','Reminder'),text('BODY','Take your essentials')]),
 block('ask','ASK kind / title / choices separated by | / response variable / scope / timeout seconds (0 never) / min / max / first choice or valid input / second / other / cancel / timeout',[dropdown('KIND',['choice','yesno','text','number','confirm']),text('TITLE','Where are you going?'),text('OPTIONS','Class|Gym|Other'),text('NAME'),scope(),number('TIMEOUT',900),text('MIN'),text('MAX'),body('YES'),body('NO'),body('OTHER'),body('CANCEL'),body('TIMEOUT')],275),
 block('checklist','CHECKLIST title / items separated by | (?optional) / timeout / complete / cancel / timeout',[text('TITLE','Essentials'),text('OPTIONS','Keys|Wallet|?Water'),number('TIMEOUT',900),body('DONE'),body('CANCEL'),body('TIMEOUT')],275),
 block('if','IF / then / else',[expr('TEST','Boolean'),body('YES'),body('NO')],210),
 block('switch','SWITCH value / case 1 / case 2 / first / second / default',[expr('VALUE'),text('CASE1','Class'),text('CASE2','Gym'),body('YES'),body('NO'),body('OTHER')],210),
 block('wait','WAIT seconds',[number('SECONDS',60,1)],55),
 block('waitUntil','WAIT UNTIL timestamp',[expr('VALUE','Instant')],55),
 block('waitClock','WAIT UNTIL next clock time / timezone',[text('TIME','09:00'),text('ZONE','device')],55),
 block('setTimeout','SET execution timeout seconds',[number('SECONDS',3600,1)],55),
 block('waitCondition','WAIT FOR condition / check seconds / max checks / then',[expr('TEST','Boolean'),number('SECONDS',60,60),number('LIMIT',60,1,1000),body('DO')],55),
 block('repeat','REPEAT bounded times / body',[number('LIMIT',3,1,1000),body('DO')],120),
 block('while','WHILE condition / max iterations / body',[expr('TEST','Boolean'),number('LIMIT',100,1,1000),body('DO')],120),
 block('break','BREAK loop',[],120),block('continue','CONTINUE loop',[],120),block('stop','STOP workflow',[],120),block('return','RETURN value (optional)',[expr('VALUE')],120),
 block('call','CALL workflow / input variable name / input value / output local variable name',[resource('WORKFLOW','workflow'),text('INPUTNAME'),expr('INPUT'),text('OUTPUTNAME')],120),
 block('try','TRY / handle error',[body('DO'),body('ERROR')],120),
 block('parallel','BRANCHES (A completes then B; shared variables) / A / B',[body('A'),body('B')],120),
 block('variable','DECLARE name / type / scope / default (blank = null)',[text('NAME','answer'),dropdown('TYPE',types),scope(),text('DEFAULT')],330),
 block('set','SET variable / scope / value',[text('NAME','answer'),scope(),expr('VALUE')],330),
 block('delete','RESET variable value / scope',[text('NAME','answer'),scope()],330),
 block('get','GET variable / scope',[text('NAME','answer'),scope()],330,'Any'),
 block('value','VALUE type / value (duration seconds, instant ISO, list |)',[dropdown('TYPE',types),text('VALUE','Hello')],300,'Any'),
 block('expr','EXPRESSION operation / location ID when occupancy / A / B / C',[
 dropdown('OP',['and','or','xor','not','eq','ne','gt','lt','ge','le','range','empty','contains','starts','ends','add','subtract','multiply','divide','mod','concat','trim','upper','lower','length','append','remove','item','join','toString','toNumber','now','date','time','weekday','month','weekend','betweenTime','beforeTime','afterTime','elapsed','addDuration','subtractDuration','format','occupancy']),text('NAME'),expr('A'),expr('B'),expr('C')],210,'Any'),
 block('log','LOG private trace message',[text('MESSAGE','Checkpoint')],0),
 block('assert','ASSERT condition / error message',[expr('TEST','Boolean'),text('MESSAGE','Assertion failed')],0),
 block('breakpoint','SIMULATION breakpoint',[],0),
 block('notifyCancel','CANCEL this execution notification',[],175)
];
export function register(Blockly){
 Blockly.common.defineBlocksWithJsonArray(definitions);
 for(const d of definitions){const init=Blockly.Blocks[d.type].init;Blockly.Blocks[d.type].init=function(){init.call(this);this.saveExtraState=()=>({version:1});this.loadExtraState=state=>{if(state.version!==undefined&&state.version!==1)throw Error('Unsupported block version');};};}
 const askInit=Blockly.Blocks.fs_ask.init;
 Blockly.Blocks.fs_ask.init=function(){
   askInit.call(this);this.choiceCount=0;
   this.updateChoices=function(count){
     const labels=(this.getFieldValue('OPTIONS')||'').split('|').filter(x=>x.trim());
     for(let n=this.choiceCount;n<count;n++)this.appendStatementInput('CHOICE'+n).appendField('Choice '+(n+1));
     for(let n=this.choiceCount-1;n>=count;n--)this.removeInput('CHOICE'+n);
     this.choiceCount=count;
     for(let n=0;n<count;n++)this.getInput('CHOICE'+n).fieldRow[0].setValue('If '+(labels[n]||'choice '+(n+1)));
   };
   this.saveExtraState=()=>({version:1,choices:this.choiceCount});
   this.loadExtraState=state=>{if(state.version!==undefined&&state.version!==1)throw Error('Unsupported block version');this.updateChoices(Math.min(100,state.choices||0));};
   this.setOnChange(()=>{const kind=this.getFieldValue('KIND');this.updateChoices(kind==='choice'?Math.min(100,(this.getFieldValue('OPTIONS')||'').split('|').filter(x=>x.trim()).length):0);});
 };
 // The language has dynamic result types; native validation remains authoritative.
 for(const name of ['fs_value','fs_expr','fs_get']) {
   const original=Blockly.Blocks[name].init;
   Blockly.Blocks[name].init=function(){ original.call(this); this.setOutput(true); if(name==='fs_expr'||name==='fs_value') this.setOnChange(()=>{
     const op=this.getFieldValue('OP'),t=this.getFieldValue('TYPE');
     const booleans=['and','or','xor','not','eq','ne','gt','lt','ge','le','range','empty','contains','starts','ends','weekend','betweenTime','beforeTime','afterTime'];
     this.outputConnection.setCheck(t==='BOOLEAN'||booleans.includes(op)?'Boolean':t==='INSTANT'||['now','addDuration','subtractDuration'].includes(op)?'Instant':null);
   }); };
 }
}
export const toolbox={kind:'categoryToolbox',contents:[
 ['Triggers',40,['trigger']],['Logic',210,['if','switch','expr','value']],['Interactions',275,['ask','checklist','message','notifyCancel']],['Time & loops',55,['wait','waitUntil','waitClock','waitCondition','setTimeout','repeat','while','break','continue']],['Variables',330,['variable','set','get','delete']],['Control',120,['call','return','try','parallel','stop']],['Debug',0,['log','assert','breakpoint']]
].map(([name,colour,blocks])=>({kind:'category',name,colour:String(colour),contents:blocks.map(type=>({kind:'block',type:'fs_'+type}))}))};
export function initialWorkspace(template='blank'){
 const trigger={type:'fs_trigger',id:crypto.randomUUID(),x:30,y:40,fields:{KIND:'manual'}};
 const action={type:template==='checklist'?'fs_checklist':'fs_message',id:crypto.randomUUID(),fields:template==='checklist'?{TITLE:'Essentials',OPTIONS:'Keys|Wallet|?Water'}:{TITLE:'Hello',BODY:'Your workflow is running'}};
 trigger.next={block:action};return {blocks:{languageVersion:0,blocks:[trigger]}};
}
