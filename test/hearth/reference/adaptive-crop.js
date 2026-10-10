// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
// Crop proposals use detected geometry only, never expected reading values.
import {selectCropFallback} from './crop-fallback.js';
import {isPlausibleReading} from './reading.js';

export function selectAdaptiveFallback(full,replays){
  if(isPlausibleReading(full.reading)||replays.length!==2)return full;
  for(const {result:r} of replays){
    const v=r.reading;
    if(!v||!['candidate','review'].includes(r.status))return full;
    if(!isPlausibleReading(v))return full;
  }
  // Reuse agreement/coordinate mapping, then retain the least certain status.
  const selected=selectCropFallback(full,replays.map(v=>({...v,result:{...v.result,status:'candidate'}})));
  if(selected===full)return full;
  const review=replays.some(v=>v.result.status==='review');
  selected.status=review?'review':'candidate';
  selected.reasons=review?['Enhanced views agree, but at least one row or digit has low confidence; check every value.']:[];
  selected.cropFallback.method='two-agreeing-row-crops-light-normalized';
  return selected;
}

export function rowRegion(detections,width,height){
  const rows=detections.filter(d=>d.class==='row'&&d.score>=.25).sort((a,b)=>(a.box[1]+a.box[3])-(b.box[1]+b.box[3]));
  if(rows.length<2||rows.length>3)return null;
  const heights=rows.map(d=>d.box[3]-d.box[1]),widths=rows.map(d=>d.box[2]-d.box[0]);
  const cx=rows.map(d=>(d.box[0]+d.box[2])/2),cy=rows.map(d=>(d.box[1]+d.box[3])/2);
  if(Math.min(...heights.slice(0,2))/Math.max(...heights.slice(0,2))<.45)return null;
  if(Math.max(...cx)-Math.min(...cx)>Math.max(...widths))return null;
  if(rows.slice(1).some((_,i)=>cy[i+1]-cy[i]<.5*Math.min(heights[i],heights[i+1])))return null;
  const x1=Math.min(...rows.map(d=>d.box[0])),y1=Math.min(...rows.map(d=>d.box[1]));
  const x2=Math.max(...rows.map(d=>d.box[2])),y2=Math.max(...rows.map(d=>d.box[3]));
  const rw=x2-x1,rh=(heights[0]+heights[1])/2;
  const left=Math.max(0,Math.floor(x1-.2*rw)),top=Math.max(0,Math.floor(y1-.25*rh));
  const right=Math.min(width,Math.ceil(x2+.2*rw)),bottom=Math.min(height,Math.ceil(Math.max(y2+.3*rh,y1+3.3*rh)));
  return right-left>=40&&bottom-top>=60?[left,top,right-left,bottom-top]:null;
}

// Separable running sums make each replicated-border box blur O(pixel count).
function boxBlur(input,width,height,radius){
  const temp=new Float64Array(input.length),out=new Float64Array(input.length),n=2*radius+1;
  for(let y=0;y<height;y++){
    const base=y*width;let sum=0;
    for(let k=-radius;k<=radius;k++)sum+=input[base+Math.max(0,Math.min(width-1,k))];
    for(let x=0;x<width;x++){
      temp[base+x]=sum/n;
      sum+=input[base+Math.min(width-1,x+radius+1)]-input[base+Math.max(0,x-radius)];
    }
  }
  for(let x=0;x<width;x++){
    let sum=0;
    for(let k=-radius;k<=radius;k++)sum+=temp[Math.max(0,Math.min(height-1,k))*width+x];
    for(let y=0;y<height;y++){
      out[y*width+x]=sum/n;
      sum+=temp[Math.min(height-1,y+radius+1)*width+x]-temp[Math.max(0,y-radius)*width+x];
    }
  }
  return out;
}

export function normalizeRgba(rgba,width,height,fraction){
  const gray=new Float64Array(width*height);
  for(let i=0;i<gray.length;i++)gray[i]=Math.round(.299*rgba[i*4]+.587*rgba[i*4+1]+.114*rgba[i*4+2]);
  const radius=Math.max(3,Math.round(Math.min(width,height)*fraction));
  let background=gray;
  for(let i=0;i<3;i++)background=boxBlur(background,width,height,radius);
  const output=new Uint8ClampedArray(rgba.length);
  for(let i=0;i<gray.length;i++){
    const v=Math.min(255,Math.floor(gray[i]*150/Math.max(background[i],1)+.5));
    output[i*4]=output[i*4+1]=output[i*4+2]=v;output[i*4+3]=255;
  }
  return output;
}
