// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
// Keep this logic in parity with prototype/reading.py.
export const CLASS_NAMES = ['0','1','row','2','3','4','5','6','7','8','9'];
export const FIELDS = ['sys','dia','pulse'];

export function isPlausibleReading(reading) {
  return !!reading&&FIELDS.every(key=>Number.isInteger(reading[key]))&&
    reading.sys>=50&&reading.sys<=280&&reading.dia>=25&&reading.dia<=180&&
    reading.pulse>=20&&reading.pulse<=250&&reading.sys>reading.dia;
}

export function iou(a,b) {
  const overlap=Math.max(0,Math.min(a[2],b[2])-Math.max(a[0],b[0]))*Math.max(0,Math.min(a[3],b[3])-Math.max(a[1],b[1]));
  const aa=Math.max(0,a[2]-a[0])*Math.max(0,a[3]-a[1]);
  const ab=Math.max(0,b[2]-b[0])*Math.max(0,b[3]-b[1]);
  return overlap/Math.max(aa+ab-overlap,1e-9);
}
export function nms(detections,threshold=.45) {
  const kept=[];
  for(const d of [...detections].sort((a,b)=>b.score-a.score)) {
    if(kept.every(old=>!duplicates(d,old,threshold))) kept.push(d);
  }
  return kept;
}
export function duplicates(a,b,threshold=.45){
  if((a.class==='row')!==(b.class==='row'))return false;
  if(iou(a.box,b.box)>threshold)return true;
  if(a.class==='row')return false;
  const [x1,y1,x2,y2]=a.box,[u1,v1,u2,v2]=b.box;
  const intersection=Math.max(0,Math.min(x2,u2)-Math.max(x1,u1))*Math.max(0,Math.min(y2,v2)-Math.max(y1,v1));
  const smaller=Math.min(Math.max(0,x2-x1)*Math.max(0,y2-y1),Math.max(0,u2-u1)*Math.max(0,v2-v1));
  return smaller>0&&intersection/smaller>.75;
}
export function assemble(detections,minScore=.25,acceptScore=.75) {
  detections=nms(detections.filter(d=>Number.isFinite(d.score)&&d.score>=minScore));
  const rows=detections.filter(d=>d.class==='row').sort((a,b)=>b.score-a.score).slice(0,8);
  const digits=detections.filter(d=>d.class!=='row');
  const candidates=[];
  for(const row of rows) {
    const [x1,y1,x2,y2]=row.box,h=y2-y1;
    const group=digits.filter(d=>{
      const cy=(d.box[1]+d.box[3])/2;
      // Row proposals occasionally end inside a narrow edge digit. Match the
      // digit box's near edge within 20% of row height instead of expanding by
      // row width, which can sweep distant labels/digits into a wide row.
      return d.box[2]>=x1-.2*h&&d.box[0]<=x2+.2*h&&
        cy>=y1-.08*h&&cy<=y2+.08*h;
    }).sort((a,b)=>(a.box[0]+a.box[2])-(b.box[0]+b.box[2]));
    if(![2,3].includes(group.length)||group.some(d=>!/^\d$/.test(d.class))) continue;
    const text=group.map(d=>d.class).join('');
    if(text.startsWith('0')) continue;
    candidates.push({value:Number(text),box:row.box,digits:group,score:Math.min(row.score,...group.map(d=>d.score)),cy:(y1+y2)/2,cx:(x1+x2)/2,height:h});
  }
  const valid=[];
  for(let i=0;i<candidates.length;i++)for(let j=i+1;j<candidates.length;j++)for(let k=j+1;k<candidates.length;k++){
    const stack=[candidates[i],candidates[j],candidates[k]].sort((a,b)=>a.cy-b.cy);
    const [s,d,p]=stack;
    if(iou(s.box,d.box)>.15||iou(s.box,p.box)>.15||iou(d.box,p.box)>.15) continue;
    const all=stack.flatMap(r=>r.digits);
    if(new Set(all).size!==all.length) continue;
    if(!(s.cy<d.cy&&d.cy<p.cy)) continue;
    const span=p.cy-s.cy;
    if(Math.min(d.cy-s.cy,p.cy-d.cy)<.12*span) continue;
    if(Math.abs(s.cx-d.cx)>Math.max(s.box[2]-s.box[0],d.box[2]-d.box[0])) continue;
    if(Math.abs(p.cx-d.cx)>Math.max(s.box[2]-s.box[0],d.box[2]-d.box[0])) continue;
    if(Math.min(s.height,d.height)/Math.max(s.height,d.height)<.45) continue;
    valid.push(stack);
  }
  if(valid.length!==1) return {status:'retake',reading:null,score:null,reasons:['Could not identify one unambiguous three-row reading'],rows:[],detections};
  const stack=valid[0],reading=Object.fromEntries(FIELDS.map((key,i)=>[key,stack[i].value]));
  const score=Math.min(...stack.map(r=>r.score)),reasons=[];
  if(!isPlausibleReading(reading)) reasons.push('Values failed consistency checks; verify every displayed number');
  if(score<acceptScore) reasons.push('At least one detected row or digit has low confidence');
  return {status:reasons.length?'review':'candidate',reading,score,reasons,rows:stack,detections};
}

export function decodeOutput(data,dims,t,minScore=.25) {
  const [batch,channels,count]=dims;
  if(batch!==1||channels!==4+CLASS_NAMES.length) throw new Error(`Unexpected detector output shape: ${dims}`);
  const detections=[];
  for(let i=0;i<count;i++) {
    let cid=0,score=-Infinity;
    for(let c=0;c<CLASS_NAMES.length;c++) if(data[(c+4)*count+i]>score){cid=c;score=data[(c+4)*count+i];}
    if(!Number.isFinite(score)||score<minScore)continue;
    const cx=data[i],cy=data[count+i],w=data[2*count+i],h=data[3*count+i];
    const box=[(cx-w/2-t.left)/t.scale,(cy-h/2-t.top)/t.scale,(cx+w/2-t.left)/t.scale,(cy+h/2-t.top)/t.scale]
      .map((v,j)=>Math.max(0,Math.min(v,j%2===0?t.width:t.height)));
    if(box.every(Number.isFinite)&&box[2]>box[0]&&box[3]>box[1])detections.push({class:CLASS_NAMES[cid],score,box});
  }
  return nms(detections);
}
