// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
// Fixed views chosen during Poco development; agreement is not a probability.
import {isPlausibleReading} from './reading.js';
const VIEWS=[['center-wide',[.15,.25,.7,.6]],['center-tight',[.25,.32,.6,.5]]];
export function cropRegions(width,height){
  if(height<1.2*width)return [];
  return VIEWS.map(([name,fractions])=>({name,rect:fractions.map((v,i)=>Math.round(v*(i%2===0?width:height)))}));
}
export function selectCropFallback(full,replays){
  if(isPlausibleReading(full.reading)||replays.length!==2||replays.some(r=>r.result.status!=='candidate'||!isPlausibleReading(r.result.reading)))return full;
  if(['sys','dia','pulse'].some(k=>replays[0].result.reading[k]!==replays[1].result.reading[k]))return full;
  const chosen=replays[0], [x,y]=chosen.rect;
  const move=d=>({...d,box:d.box.map((v,i)=>v+(i%2===0?x:y))});
  return {...chosen.result,score:Math.min(...replays.map(r=>r.result.score)),
    rows:chosen.result.rows.map(row=>({...move(row),cx:row.cx+x,cy:row.cy+y,digits:row.digits.map(move)})),
    detections:chosen.result.detections.map(move),
    cropFallback:{method:'two-agreeing-center-crops',views:replays.map(({name,rect})=>({name,rect}))}};
}
