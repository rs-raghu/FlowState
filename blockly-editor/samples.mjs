import {createHash} from 'node:crypto';
import {writeFileSync, mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
// Stable IDs keep relationships and generated examples reviewable across builds.
const uuid=name=>{const h=createHash('sha256').update('flowstate-demo:'+name).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-8${h.slice(17,20)}-${h.slice(20,32)}`;};
let sequence=0;
const b=(type,fields={},inputs={},next)=>({type:'fs_'+type,id:uuid('block-'+sequence++),fields,extraState:{version:1,...(type==='ask'?{choices:fields.KIND==='choice'?fields.OPTIONS.split('|').length:0}:{})},...(Object.keys(inputs).length?{inputs:Object.fromEntries(Object.entries(inputs).map(([k,v])=>[k,{block:v}]))}:{}),...(next?{next:{block:next}}:{})});
const literal=(type,value)=>b('value',{TYPE:type,VALUE:String(value)});
const expr=(op,...args)=>b('expr',{OP:op},Object.fromEntries(args.map((v,i)=>[['A','B','C'][i],v])));
const checklist=(title,items)=>b('checklist',{TITLE:title,OPTIONS:items,TIMEOUT:900});
const message=(title,body='')=>b('message',{TITLE:title,BODY:body});
const choice=(title,options,branches)=>b('ask',{KIND:'choice',TITLE:title,OPTIONS:options.join('|'),TIMEOUT:900},Object.fromEntries(branches.map((v,i)=>['CHOICE'+i,v])));
const yesno=(title,yes,no)=>b('ask',{KIND:'yesno',TITLE:title,TIMEOUT:900},{...(yes?{YES:yes}:{}),...(no?{NO:no}:{})});
const ids=Object.fromEntries(['hostel','gym','academic','leaving','morning','weekly','delayed','evening','preparedMorning','timeout','nested','outing','essentials'].map(n=>[n,uuid(n)]));
const workspace=(trigger,action)=>JSON.stringify({blocks:{languageVersion:0,blocks:[b('trigger',trigger,{},action)]}});
const weekday=()=>expr('le',expr('weekday'),literal('INTEGER',5));
const automations=[];
const add=(key,name,trigger,action)=>automations.push({id:ids[key],name,workspace:workspace(trigger,action)});
add('leaving','1. Leaving Hostel',{KIND:'location',LOCATION:ids.hostel,TRANSITION:'exit',COOLDOWN:300},b('if',{}, {TEST:weekday(),YES:choice('Where are you going?',['Class','Mess','Gym','Going Out'],[
 choice('Lecture or Lab?',['Lecture','Lab'],[checklist('Lecture','Notebook|Pen|ID card'),checklist('Lab','Lab coat|Lab record|ID card')]),
 checklist('Mess','Protein powder|?Water'),checklist('Gym','Towel|Shoes|Water'),checklist('Going Out','Wallet|ID card|Keys')])}));
add('morning','2. Morning Routine',{KIND:'time',TIMES:'07:30',RECURRENCE:'weekdays',DAYS:'1,2,3,4,5'},b('message',{TITLE:'Good morning',BODY:'Plan your day'},{},yesno('Do you have class today?',checklist('Morning','Breakfast|Notebook|Keys'),choice('Study or relax?',['Study','Relax'],[checklist('Study','Choose a topic|Set a study timer'),message('Relax','Enjoy a quiet morning')]))));
add('weekly','3. Weekly Task',{KIND:'time',TIMES:'09:00',RECURRENCE:'weeklyWindow',WINDOWDAY:5,CATCHUP:'window'},checklist('Weekly review','Review the week|Plan next week'));
add('delayed','4. Delayed Follow-up',{KIND:'location',LOCATION:ids.gym,TRANSITION:'exit'},b('wait',{SECONDS:900},{},yesno('Have you had your post-workout meal?',null,b('wait',{SECONDS:600},{},b('ask',{KIND:'yesno',TITLE:'Final meal reminder',TIMEOUT:600})))));
const declaration=(next)=>b('variable',{NAME:'prepared',TYPE:'BOOLEAN',SCOPE:'GLOBAL',DEFAULT:'false'},{},next);
const setPrepared=(value,next)=>b('set',{NAME:'prepared',SCOPE:'GLOBAL'},{VALUE:literal('BOOLEAN',value)},next);
add('evening','5a. Prepare Tomorrow',{KIND:'time',TIMES:'21:00'},declaration(yesno("Did you prepare tomorrow's essentials?",setPrepared(true),setPrepared(false))));
add('preparedMorning','5b. Preparation Check',{KIND:'time',TIMES:'07:00'},declaration(b('if',{}, {TEST:b('get',{NAME:'prepared',SCOPE:'GLOBAL'}),YES:message('Ready','You prepared last night'),NO:checklist('Prepare now','Keys|Wallet|Notebook')},setPrepared(false))));
add('timeout','6. Notification Timeout',{KIND:'location',LOCATION:ids.hostel,TRANSITION:'exit'},choice('Where are you going?',['Class','Gym','Other'],[message('Class'),message('Gym'),message('Other')]));
add('nested','7. Nested Conditions',{KIND:'location',LOCATION:ids.academic,TRANSITION:'enter'},b('if',{}, {TEST:expr('and',weekday(),expr('betweenTime',literal('STRING','08:00'),literal('STRING','17:00'))),YES:yesno('Are you attending class?',checklist('Class','Notebook|Pen'),message('Visit','Enjoy your visit')),NO:message('Outside class hours','Check your schedule')}));
add('essentials','8b. Essential Items Checklist',{KIND:'manual'},checklist('Essential Items','Wallet|ID card|Keys'));
add('outing','8a. Going Out',{KIND:'manual'},choice('Where are you going?',['Going Out','Staying In'],[b('call',{WORKFLOW:ids.essentials},{},message('Ready to go','Checklist complete')),message('Staying in')]));
const locations=[['hostel','Demo Hostel',150],['gym','Demo Gym',150],['academic','Demo Academic Building',150]].map(([key,name,radius],i)=>({id:ids[key],name,latitude:21.1458+i*.005,longitude:79.0882,radius,description:'Demonstration coordinates. Edit to your actual place before enabling.'}));
const backup={schema:1,automations,locations};
const directory=fileURLToPath(new URL('../sample-workflows/',import.meta.url));mkdirSync(directory,{recursive:true});
writeFileSync(directory+'examples.json',JSON.stringify(backup,null,2)+'\n');
export {backup};
