// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
import test from 'node:test';
import assert from 'node:assert/strict';
import {cropRegions,selectCropFallback} from './reference/crop-fallback.js';
const full={status:'retake',reading:null,rows:[],detections:[]};
function replay(sys=122,status='candidate'){
  const digit={class:'1',score:.8,box:[1,2,3,4]};
  return {name:'test',rect:[10,20,100,200],result:{status,reading:{sys,dia:82,pulse:72},score:.8,
    rows:[{box:[1,2,3,4],cx:2,cy:3,height:2,digits:[digit]}],detections:[digit]}};
}
test('fixed portrait regions fit source pixels; landscape does not get fallback',()=>{
  assert.deepEqual(cropRegions(1080,1920).map(v=>v.rect),[[162,480,756,1152],[270,614,648,960]]);
  assert.deepEqual(cropRegions(1920,1080),[]);
});
test('disagreeing readings preserve the refusal',()=>assert.equal(selectCropFallback(full,[replay(),replay(123)]),full));
test('reviewed or incomplete crop cannot become a candidate',()=>{
  assert.equal(selectCropFallback(full,[replay(),replay(122,'review')]),full);
  assert.equal(selectCropFallback(full,[replay()]),full);
});
test('existing full-frame reading is preserved',()=>{
  const existing={...full,reading:{sys:120,dia:80,pulse:70}};
  assert.equal(selectCropFallback(existing,[replay(),replay()]),existing);
});
test('two agreeing candidates replace an implausible full-frame reading',()=>{
  const invalid={...full,status:'review',reading:{sys:12,dia:82,pulse:72}};
  assert.deepEqual(selectCropFallback(invalid,[replay(),replay()]).reading,{sys:122,dia:82,pulse:72});
});
test('agreeing fallback moves row and digit overlays into source coordinates',()=>{
  const a=replay(),b=replay();b.result.score=.7;
  const selected=selectCropFallback(full,[a,b]);
  assert.deepEqual(selected.reading,{sys:122,dia:82,pulse:72});
  assert.equal(selected.score,.7);
  assert.deepEqual(selected.rows[0].box,[11,22,13,24]);
  assert.deepEqual(selected.rows[0].digits[0].box,[11,22,13,24]);
  assert.deepEqual(selected.detections[0].box,[11,22,13,24]);
  assert.equal(selected.rows[0].cx,12);assert.equal(selected.rows[0].cy,23);
  assert.deepEqual(a.result.rows[0].box,[1,2,3,4]);
});
