import test from 'node:test';
import assert from 'node:assert/strict';
import Blockly from 'blockly/core';
import * as En from 'blockly/msg/en';
import {register,definitions,initialWorkspace} from './catalogue.js';
import {backup} from './samples.mjs';
import {setResources} from './catalogue.js';
Blockly.setLocale(En);register(Blockly);
test('all eight example scenarios retain fields, IDs and branches',()=>{
 setResources({locations:backup.locations,workflows:backup.automations});
 for(const automation of backup.automations){const a=new Blockly.Workspace();Blockly.serialization.workspaces.load(JSON.parse(automation.workspace),a);const saved=Blockly.serialization.workspaces.save(a);const b=new Blockly.Workspace();Blockly.serialization.workspaces.load(saved,b);assert.deepEqual(Blockly.serialization.workspaces.save(b),saved,automation.name);a.dispose();b.dispose();}
});
test('every custom block survives JSON serialization',()=>{
 for(const definition of definitions){const a=new Blockly.Workspace();const b=a.newBlock(definition.type);const state=Blockly.serialization.workspaces.save(a);const c=new Blockly.Workspace();Blockly.serialization.workspaces.load(state,c);assert.equal(c.getAllBlocks(false)[0].type,b.type);a.dispose();c.dispose();}
});
test('templates roundtrip stable instance IDs and connections',()=>{
 const a=new Blockly.Workspace();Blockly.serialization.workspaces.load(initialWorkspace('checklist'),a);const saved=Blockly.serialization.workspaces.save(a);const b=new Blockly.Workspace();Blockly.serialization.workspaces.load(saved,b);assert.deepEqual(Blockly.serialization.workspaces.save(b),saved);assert.equal(b.getTopBlocks()[0].getNextBlock().type,'fs_checklist');a.dispose();b.dispose();
});
test('fourth choice retains its independently connected branch',()=>{
 const a=new Blockly.Workspace();const ask=a.newBlock('fs_ask');ask.setFieldValue('A|B|C|D','OPTIONS');ask.updateChoices(4);const action=a.newBlock('fs_message');ask.getInput('CHOICE3').connection.connect(action.previousConnection);const state=Blockly.serialization.workspaces.save(a);const b=new Blockly.Workspace();Blockly.serialization.workspaces.load(state,b);const restored=b.getBlockById(ask.id);assert.equal(restored.getInputTargetBlock('CHOICE3').id,action.id);a.dispose();b.dispose();
});

test('typed lists retain item inputs and element type',()=>{
 const a=new Blockly.Workspace();const list=a.newBlock('fs_list');list.setFieldValue('INTEGER','TYPE');list.setFieldValue(3,'COUNT');list.updateDynamic(3);const value=a.newBlock('fs_value');value.setFieldValue('INTEGER','TYPE');value.setFieldValue('42','VALUE');list.getInput('ITEM2').connection.connect(value.outputConnection);const state=Blockly.serialization.workspaces.save(a);const b=new Blockly.Workspace();Blockly.serialization.workspaces.load(state,b);assert.equal(b.getBlockById(list.id).getInputTargetBlock('ITEM2').id,value.id);assert.equal(b.getBlockById(list.id).getFieldValue('TYPE'),'INTEGER');a.dispose();b.dispose();
});

test('multiple call parameters and bindings roundtrip',()=>{
 const a=new Blockly.Workspace();const call=a.newBlock('fs_call');call.setFieldValue('count,label','INPUTNAMES');call.setFieldValue('count:result,label:text','OUTPUTS');call.setFieldValue('status','STATUSNAME');call.updateDynamic(2);const value=a.newBlock('fs_value');call.getInput('PARAM1').connection.connect(value.outputConnection);const state=Blockly.serialization.workspaces.save(a);const b=new Blockly.Workspace();Blockly.serialization.workspaces.load(state,b);const restored=b.getBlockById(call.id);assert.equal(restored.getInputTargetBlock('PARAM1').id,value.id);assert.equal(restored.getFieldValue('STATUSNAME'),'status');a.dispose();b.dispose();
});
