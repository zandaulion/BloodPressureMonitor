// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
import test from 'node:test';
import assert from 'node:assert/strict';
import {rowRegion,normalizeRgba,selectAdaptiveFallback} from './reference/adaptive-crop.js';
import {selectCropFallback} from './reference/crop-fallback.js';
const row=(box,score=.9)=>({class:'row',score,box});

test('off-center rows produce a bounded region that includes space for pulse',()=>{
  const rect=rowRegion([row([350,1080,700,1260]),row([455,1240,700,1440])],1080,1920);
  assert.deepEqual(rect,[280,1032,490,675]);
  assert.ok(rect[1]+rect[3]>1600);
});
test('isolated, overlapping and side-by-side row proposals are rejected',()=>{
  assert.equal(rowRegion([row([0,0,100,100])],500,500),null);
  assert.equal(rowRegion([row([0,0,100,100]),row([0,5,100,105])],500,500),null);
  assert.equal(rowRegion([row([0,0,100,100]),row([300,150,400,250])],500,500),null);
});
test('light normalization removes a constant brightness offset and preserves black',()=>{
  for(const level of [0,30,120,240]){
    const pixels=new Uint8ClampedArray(12*10*4);
    for(let i=0;i<pixels.length;i+=4){pixels[i]=pixels[i+1]=pixels[i+2]=level;pixels[i+3]=255;}
    const out=normalizeRgba(pixels,12,10,.04);
    for(let i=0;i<out.length;i+=4)assert.deepEqual([...out.slice(i,i+4)],[level?150:0,level?150:0,level?150:0,255]);
  }
});
test('enhanced views must agree on every digit before replacing refusal',()=>{
  const full={reading:null};
  const candidate={status:'candidate',reading:{sys:120,dia:80,pulse:60},score:.8,rows:[],detections:[]};
  const views=[{rect:[20,30,100,200],name:'a',result:candidate},{rect:[20,30,100,200],name:'b',result:{...candidate,reading:{...candidate.reading,pulse:160}}}];
  assert.equal(selectCropFallback(full,views),full);
  assert.equal(selectAdaptiveFallback(full,views),full);
});
test('agreement with a low-confidence view returns review, never candidate',()=>{
  const full={reading:null},r={reading:{sys:120,dia:80,pulse:60},status:'candidate',score:.8,rows:[],detections:[]};
  const views=[{name:'a',rect:[20,30,100,200],result:r},{name:'b',rect:[20,30,100,200],result:{...r,status:'review',score:.21}}];
  const result=selectAdaptiveFallback(full,views);
  assert.equal(result.status,'review');assert.equal(result.score,.21);assert.deepEqual(result.reading,r.reading);
});
test('agreeing plausible enhanced views replace an implausible full reading',()=>{
  const full={reading:{sys:12,dia:80,pulse:60}},r={reading:{sys:120,dia:80,pulse:60},status:'candidate',score:.8,rows:[],detections:[]};
  const views=[{name:'a',rect:[20,30,100,200],result:r},{name:'b',rect:[20,30,100,200],result:r}];
  assert.deepEqual(selectAdaptiveFallback(full,views).reading,r.reading);
});
test('agreeing implausible readings and incomplete views remain refusals',()=>{
  const full={reading:null},r={reading:{sys:720,dia:80,pulse:60},status:'review',score:.8,rows:[],detections:[]};
  const views=[{name:'a',rect:[0,0,100,200],result:r},{name:'b',rect:[0,0,100,200],result:r}];
  assert.equal(selectAdaptiveFallback(full,views),full);
  views[1].result={...r,reading:null};assert.equal(selectAdaptiveFallback(full,views),full);
});
