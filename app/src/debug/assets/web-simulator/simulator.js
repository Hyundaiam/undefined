"use strict";
const $=id=>document.getElementById(id), canvas=$("road"), ctx=canvas.getContext("2d");
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v)), num=id=>Number($(id).value), checked=id=>$(id).checked;
const state={started:false,speed:0,x:0,steer:0,gear:"P",accel:false,brake:false,left:false,right:false,meters:0,segmentMeters:0,elapsed:0,gap:55,lane:0,laneChangeUntil:0,laneDirection:0,persona:"friend",voice:true,connected:false,receiving:false,lastFeedback:0,spoken:0,lastSample:0,sessionSince:null};
let lastTick=performance.now(), sendBusy=false, statusBusy=false, previousRoute="tour", keys=new Set(), timer=0;
const routeNames={city:"도심로",school:"어린이보호구역",tunnel:"터널",curve:"굽은 길",crosswalk:"횡단보도",merge:"합류 구간",rest:"휴게 공간"};
function roadContext(){
 const route=$("route").value; let type=route, d=state.segmentMeters;
 if(route==="tour"){
  const sections=[["city",360],["crosswalk",180],["school",260],["tunnel",330],["curve",280],["merge",260],["rest",180]];
  let cycle=d%1850; for(const [name,length] of sections){if(cycle<length){type=name;d=cycle;break;}cycle-=length;}
 }
 const limit=type==="school"?30:type==="rest"?20:60;
 const tunnel=type==="tunnel"&&d>25&&(d%600)<300;
 const crosswalk=type==="crosswalk"||(type==="city"&&checked("pedestrian"));
 const stopLine=(crosswalk?90:0)-(d%260);
 const red=$("traffic").value==="red"||($("traffic").value==="auto"&&state.elapsed%45>=24);
 return {type,d,limit,tunnel,crosswalk,stopLine,red,night:$("timeOfDay").value==="night",rain:$("weather").value==="rain",curve:type==="curve",merge:type==="merge"};
}
function sensorFrame(){
 const r=roadContext(), laneChange=state.elapsed<state.laneChangeUntil;
 const nearCross=r.crosswalk&&r.stopLine<38&&r.stopLine>-30;
 const side=$("sideCar").value;
 const direction=laneChange?state.laneDirection:state.right?1:state.left?-1:Math.sign(state.steer);
 const towardSide=(side==="right"&&direction>0)||(side==="left"&&direction<0);
 const nearest=Math.round(state.x/3.5)*3.5;
 return {
  speedKph:Number(state.speed.toFixed(2)),speedLimitKph:r.limit,distanceM:state.gap,
  steeringDegrees:state.steer*90,brakePressed:state.brake,emergencyBraking:checked("pedestrian")&&nearCross||checked("emergency")||state.gap<Math.max(8,state.speed/3.6*1.4),
  gear:state.gear==="P"?"GEAR_PARK":"GEAR_DRIVE",tunnel:r.tunnel,windowOpen:checked("windowOpen"),
  occupiedSeats:checked("passenger")?["driver","passenger"]:["driver"],beltBySeat:checked("passenger")?{driver:checked("driverBelt"),passenger:checked("passengerBelt")}:{driver:checked("driverBelt")},
  handsOnWheel:num("hands"),bothFeetOnPedals:checked("bothFeet"),automaticTransmission:true,
  recommendedCurveSpeed:r.curve?30:null,laneChange,indicatorOn:state.left||state.right,
  laneDeviation:Math.abs(state.x)>4.8||Math.abs(state.x-nearest)>1.6&&!state.left&&!state.right&&!laneChange,
  seatDeviationFraction:num("seat")/100,wetRoad:r.rain,weatherRecommendedSpeed:r.rain?Math.max(15,Math.round(r.limit*.7)):null,
  pedestrianAhead:checked("pedestrian")&&nearCross,crosswalkAhead:nearCross,pedestrianYielded:checked("pedestrian")&&nearCross?state.speed<1&&r.stopLine>=0:null,
  ignitionOn:checked("ignition"),parkingBrakeOn:checked("parkingBrake"),driverLeaving:checked("driverLeaving"),
  schoolZone:r.type==="school",redLight:r.crosswalk&&r.red,stopLineDistanceM:r.crosswalk?r.stopLine:null,
  night:r.night,emergencyVehicle:checked("emergency"),emergencyYielded:checked("emergency")?state.speed<1&&(state.x>2.5||state.gear==="P"):null,
  blindSpotOccupied:side!=="none"&&towardSide,drowsy:checked("drowsy"),restStop:r.type==="rest"&&state.speed<1&&state.gear==="P",
  doorsOpen:checked("doorsOpen"),tirePressureLow:$("tires").value==="low",remainingRangeKm:num("battery"),routeRemainingKm:num("remaining"),mergeZone:r.merge
 };
}
function setGear(gear){if(gear==="P"&&state.speed>1){$("driveStatus").textContent="브레이크로 멈춘 뒤 P단을 선택해 주세요.";return;}state.gear=gear;document.querySelectorAll("[data-gear]").forEach(b=>b.classList.toggle("selected",b.dataset.gear===gear));}
function setIndicator(direction){if(direction==="left"){state.left=!state.left;state.right=false;}else{state.right=!state.right;state.left=false;}$("leftSignal").classList.toggle("selected",state.left);$("rightSignal").classList.toggle("selected",state.right);}
async function api(path,data){const response=await fetch(path,data===undefined?{cache:"no-store"}:{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(data)});const result=await response.json();if(!response.ok)throw Error(result.error||"연결을 확인해 주세요");return result;}
async function sendFrame(){if(!state.started||sendBusy)return;sendBusy=true;try{await api("/api/frame",sensorFrame());state.lastSample=performance.now();}catch(e){$("driveStatus").textContent=/[가-힣]/.test(e.message)?e.message:"차량 신호 연결을 확인해 주세요.";}finally{sendBusy=false;}}
function text(el,value){if(el.textContent!==String(value))el.textContent=value;}
async function readStatus(){
 if(statusBusy)return;statusBusy=true;
 try{
  const s=await api("/api/status");state.connected=s.connected;state.receiving=s.receiving;
  if(state.sessionSince===null)state.sessionSince=s.clock;
  $("connection").classList.toggle("online",s.connected);text($("connection").querySelector("span"),s.connected?"곁 앱 연결됨":"곁 앱에서 웹 가상주행을 시작해 주세요");
  text($("nativeStatus"),s.connected?"차량 앱과 연결됨":"앱 연결 대기");
  $("start").disabled=!s.connected;
  $("sensorDot").classList.toggle("on",s.connected&&s.receiving);text($("sensorState"),s.receiving?"차량 조건 수신 중":"차량 신호 대기");
  if(s.persona&&s.persona!==state.persona){state.persona=s.persona;updatePersonas();}
  state.voice=s.voiceEnabled;text($("mute"),state.voice?"음성 켜짐":"음성 꺼짐");$("mute").setAttribute("aria-label",state.voice?"음성 끄기":"음성 켜기");
  const currentFeedback=s.feedback?.at>=state.sessionSince;
  if(currentFeedback&&s.feedback?.title)text($("currentJudgment"),s.feedback.title+" · 곁이 확인했어요.");
  const a=s.audio||{}, speaking=a.phase==="PLAYING", preparing=a.phase==="PREPARING";
  $("wave").classList.toggle("speaking",speaking);
  if(currentFeedback&&a.renderedCount>0&&a.text){text($("feedbackTitle"),a.title||"곁의 한마디");text($("feedbackText"),a.text);}
  else if(currentFeedback&&s.feedback?.text){text($("feedbackTitle"),s.feedback.title);text($("feedbackText"),s.feedback.text);}
  const phases={PLAYING:"● 차량 앱에서 음성 안내 중",PREPARING:"음성을 준비하고 있어요",COMPLETED:"음성 안내 완료",MUTED:"음성 안내 꺼짐",WAITING_FOCUS:"다른 안내가 끝나기를 기다려요",MISSING:"음성 파일 확인이 필요해요",ERROR:"음성 재생을 확인해 주세요",IDLE:"주행을 살피고 있어요"};
  text($("audioState"),!state.voice?"음성 안내 꺼짐":phases[a.phase]||"음성 안내 대기");
  const moments=(s.history||[]).filter(e=>e.at>=state.sessionSince),history=$("history"),signature=JSON.stringify(moments);
  if(history.dataset.signature!==signature){history.dataset.signature=signature;history.replaceChildren();if(!moments.length){const p=document.createElement("p");p.className="empty";p.textContent="아직 기록된 순간이 없어요.";history.append(p);}else for(const e of moments){const row=document.createElement("div");row.className="moment";const icon=document.createElement("i"),title=document.createElement("span"),time=document.createElement("time");icon.textContent=e.tone==="CAUTION"?"!":e.tone==="GOOD"?"+":"·";title.textContent=e.title;time.textContent=new Date(e.at).toLocaleTimeString("ko-KR",{hour:"2-digit",minute:"2-digit",hour12:false});row.append(icon,title,time);history.append(row);}}
 }catch(e){state.connected=false;state.receiving=false;$("start").disabled=true;$("connection").classList.remove("online");text($("connection").querySelector("span"),"곁 연결 확인 중");text($("nativeStatus"),"앱 연결 대기");$("sensorDot").classList.remove("on");}
 finally{statusBusy=false;}
}
function updatePersonas(){document.querySelectorAll("[data-persona]").forEach(b=>b.classList.toggle("selected",b.dataset.persona===state.persona));}
function updateNumbers(){
 const r=roadContext();text($("speed"),Math.round(state.speed));text($("limit"),r.limit);$("speed").classList.toggle("over",state.speed>r.limit);
 $("gap").replaceChildren(document.createTextNode(Math.round(state.gap)+" "));const small=document.createElement("small");small.textContent="m";$("gap").append(small);
 $("gapFill").style.width=clamp(state.gap/80*100,4,100)+"%";
 text($("roadName"),routeNames[r.type]+(r.type==="tunnel"?(r.tunnel?" 안":" 앞"):""));
 text($("environment"),(r.rain?"비 · 젖은 노면":"맑음")+" · "+(r.night?"밤":"낮"));
 text($("sceneState"),!state.started?"출발 전":state.gear==="P"?"P · 정차":"D · 주행 중");
 text($("routeDistance"),(state.meters/1000).toFixed(2)+" km");text($("roadHint"),r.curve?"굽은 길 · 권장 30":r.rain?"빗길 · 권장 "+Math.max(15,Math.round(r.limit*.7)):routeNames[r.type]+" · 제한 "+r.limit);
 $("road").parentElement.classList.toggle("night",r.night||r.tunnel);
 text($("steerLabel"),Math.abs(state.steer)<.05?"중앙":state.steer<0?"왼쪽":"오른쪽");
 if(state.started&&performance.now()-state.lastSample<1500)text($("driveStatus"),state.speed>r.limit?"제한 속도를 넘었어요. 곁이 주행을 확인하고 있어요.":"차량 조건이 곁 앱으로 전해지고 있어요.");
}
function physics(dt){
 if(!state.started)return;
 state.elapsed+=dt;const r=roadContext();const engine=checked("ignition")&&state.gear==="D"&&!checked("parkingBrake");
 const acceleration=state.brake?-(.5+num("pressure")*.05):state.accel&&engine?.2+num("pressure")*.04:checked("parkingBrake")?-4.0:-.16;
 state.speed=clamp(state.speed+acceleration*3.6*dt,0,150);
 if(state.gear==="P")state.speed=0;
 const traveled=state.speed/3.6*dt;state.meters+=traveled;state.segmentMeters+=traveled;
 state.gap=clamp(state.gap+(num("leadSpeed")-state.speed)/3.6*dt,3,180);
 const steer=keys.has("ArrowLeft")||keys.has("a")?-.75:keys.has("ArrowRight")||keys.has("d")?.75:num("steer");
 state.steer=steer;state.x=clamp(state.x+steer*dt*(.8+state.speed*.025),-5.6,5.6);
 const lane=Math.round(state.x/3.5);if(lane!==state.lane&&state.speed>5){state.laneChangeUntil=state.elapsed+2;state.laneDirection=Math.sign(lane-state.lane);state.lane=lane;}
 if(!r.crosswalk&&$("route").value!==previousRoute){previousRoute=$("route").value;state.segmentMeters=0;}
}
function resize(){const box=canvas.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,2);if(canvas.width!==Math.round(box.width*dpr)||canvas.height!==Math.round(box.height*dpr)){canvas.width=Math.round(box.width*dpr);canvas.height=Math.round(box.height*dpr);}ctx.setTransform(dpr,0,0,dpr,0,0);return [box.width,box.height];}
function drawRoad(){
 const [w,h]=resize(),r=roadContext(),dark=r.night||r.tunnel;ctx.clearRect(0,0,w,h);
 const horizon=h*.31, camera=state.x/3.5, bend=r.curve?Math.sin(r.d/100)*.1:0;
 ctx.fillStyle=dark?"#202321":"#e9e9e3";ctx.fillRect(0,0,w,h);
 // A quiet skyline, drawn as part of the scene rather than an image background.
 if(!r.tunnel){ctx.fillStyle=dark?"#30332f":"#dadbd3";for(let i=0;i<18;i++){const bw=w/15, bh=(18+(i*37)%64);ctx.fillRect(i*bw-bw/2,horizon-bh,bw*.7,bh);}ctx.fillStyle=dark?"#323830":"#d3d7ca";ctx.fillRect(0,horizon,w,h-horizon);}
 function project(x,z){const scale=1/(1+z/18);return [w*.5+(x-camera*3.5)*w*.078*scale+bend*w*(1-scale),horizon+(h-horizon)*scale,scale];}
 function polygon(points,color){ctx.fillStyle=color;ctx.beginPath();for(let i=0;i<points.length;i++){const p=points[i];if(i)ctx.lineTo(p[0],p[1]);else ctx.moveTo(p[0],p[1]);}ctx.closePath();ctx.fill();}
 polygon([project(-5.8,500),project(5.8,500),project(5.8,0),project(-5.8,0)],dark?"#484b47":"#777a74");
 function line3(x,z1,z2,width,color){const a=project(x,z1),b=project(x,z2);ctx.strokeStyle=color;ctx.lineWidth=Math.max(1,width*a[2]);ctx.beginPath();ctx.moveTo(...a.slice(0,2));ctx.lineTo(...b.slice(0,2));ctx.stroke();}
 for(const x of [-5.5,5.5])line3(x,0,500,3,"#d1d3c8");
 const stripeOffset=state.meters%18;for(let z=-stripeOffset;z<360;z+=18){if(z<0)continue;for(const x of [-1.75,1.75])line3(x,z,z+8,3,"#e9eae3");}
 if(r.merge){polygon([project(5.7,160),project(9,20),project(5.7,20)],"#8b8d87");line3(7,30,110,2,"#eee");}
 if(r.tunnel){ctx.strokeStyle="#555b54";ctx.lineWidth=28;ctx.beginPath();ctx.ellipse(w*.5,horizon+45,w*.43,h*.42,0,Math.PI,Math.PI*2);ctx.stroke();for(let i=0;i<5;i++){const px=w*(.1+i*.2);ctx.fillStyle="#d5d7c8";ctx.fillRect(px,horizon-8,22,3);}}
 // The stop line and pedestrians have a spatial position relative to the car.
 if(r.crosswalk&&r.stopLine>-15&&r.stopLine<170){const z=r.stopLine;for(let n=0;n<7;n++){const x=-5+n*1.5;polygon([project(x,z),project(x+.65,z),project(x+.65,z+5),project(x,z+5)],"#e7e7dc");}
  const p=project(6,z+5);ctx.fillStyle="#272b27";ctx.fillRect(p[0],p[1]-80*p[2],5*p[2],80*p[2]);ctx.fillRect(p[0]-6*p[2],p[1]-85*p[2],17*p[2],38*p[2]);ctx.fillStyle=r.red?"#f4f3e6":"#777e70";ctx.beginPath();ctx.arc(p[0]+2*p[2],p[1]-(r.red?75:58)*p[2],4*p[2],0,Math.PI*2);ctx.fill();
  if(checked("pedestrian")){const person=project(-3.8+((state.elapsed%10)/10)*7.5,z+2);const s=person[2];ctx.strokeStyle=dark?"#eee":"#353b33";ctx.fillStyle=ctx.strokeStyle;ctx.lineWidth=3*s;ctx.beginPath();ctx.arc(person[0],person[1]-30*s,5*s,0,Math.PI*2);ctx.fill();ctx.beginPath();ctx.moveTo(person[0],person[1]-24*s);ctx.lineTo(person[0],person[1]-10*s);ctx.moveTo(person[0]-8*s,person[1]-19*s);ctx.lineTo(person[0]+8*s,person[1]-19*s);ctx.moveTo(person[0],person[1]-10*s);ctx.lineTo(person[0]-7*s,person[1]);ctx.moveTo(person[0],person[1]-10*s);ctx.lineTo(person[0]+7*s,person[1]);ctx.stroke();}}
 function car(x,z,side=false){const p=project(x,z),s=p[2],cw=w*.105*s,ch=72*s;ctx.fillStyle="#25292333";ctx.beginPath();ctx.ellipse(p[0],p[1]+4*s,cw*.6,9*s,0,0,7);ctx.fill();ctx.fillStyle=side?"#b8b9b0":"#eeeFE7";ctx.beginPath();ctx.roundRect(p[0]-cw/2,p[1]-ch,cw,ch,5*s);ctx.fill();ctx.fillStyle="#5a6157";ctx.fillRect(p[0]-cw*.38,p[1]-ch*.82,cw*.76,ch*.28);ctx.fillStyle="#555b51";ctx.fillRect(p[0]-cw*.44,p[1]-ch*.16,cw*.2,4*s);ctx.fillRect(p[0]+cw*.24,p[1]-ch*.16,cw*.2,4*s);ctx.fillStyle="#85897d";ctx.fillRect(p[0]-cw*.19,p[1]-ch*.16,cw*.38,5*s);}
 car(0,state.gap);if($("sideCar").value!=="none")car($("sideCar").value==="right"?3.5:-3.5,8,true);
 // Roadside speed sign remains legible as the road changes.
 const sign=project(-7,35);ctx.fillStyle=dark?"#aaa":"#666";ctx.fillRect(sign[0]-1,sign[1]-54,2,56);ctx.fillStyle="#f4f4ed";ctx.strokeStyle="#333";ctx.lineWidth=2;ctx.beginPath();ctx.arc(sign[0],sign[1]-57,19,0,Math.PI*2);ctx.fill();ctx.stroke();ctx.fillStyle="#252525";ctx.font="600 17px system-ui";ctx.textAlign="center";ctx.fillText(String(r.limit),sign[0],sign[1]-51);
 if(checked("emergency")){ctx.fillStyle=dark?"#e8e8d9":"#333a30";ctx.font="11px system-ui";ctx.textAlign="center";ctx.fillText("긴급차량 접근 · 주변을 살펴 양보하세요",w*.5,76);}
 if(r.rain){ctx.strokeStyle=dark?"#c9c9ba60":"#f7f7f0a0";ctx.lineWidth=1;for(let i=0;i<65;i++){const x=(i*127+state.elapsed*24)%w,y=(i*71+state.elapsed*280)%h;ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(x-4,y+13);ctx.stroke();}}
 // A small hood anchors the view inside the ego vehicle.
 ctx.fillStyle=dark?"#343933":"#d9ddd2";ctx.beginPath();ctx.ellipse(w*.5,h+24,w*.38,57,0,Math.PI,Math.PI*2);ctx.fill();
}
function tick(now){const dt=Math.min((now-lastTick)/1000,.1);lastTick=now;physics(dt);drawRoad();if(now-timer>120){updateNumbers();timer=now;}requestAnimationFrame(tick);}
function bindPedal(id,key){const el=$(id);const down=e=>{e.preventDefault();if(!state.started)return;el.setPointerCapture?.(e.pointerId);state[key]=true;el.classList.add("held");};const up=()=>{state[key]=false;el.classList.remove("held");};el.addEventListener("pointerdown",down);el.addEventListener("pointerup",up);el.addEventListener("pointercancel",up);el.addEventListener("lostpointercapture",up);}
bindPedal("accel","accel");bindPedal("brake","brake");
document.querySelectorAll("[data-gear]").forEach(b=>b.addEventListener("click",()=>setGear(b.dataset.gear)));
$("leftSignal").addEventListener("click",()=>setIndicator("left"));$("rightSignal").addEventListener("click",()=>setIndicator("right"));$("centerSteer").addEventListener("click",()=>{$("steer").value="0";state.steer=0;});
$("start").addEventListener("click",async()=>{if(!state.connected){$("driveStatus").textContent="곁 앱의 웹 가상주행 연결을 확인해 주세요.";return;}state.started=true;$("ignition").checked=true;setGear("D");$("startCover").classList.add("hidden");await sendFrame();});
$("reset").addEventListener("click",async()=>{state.started=false;state.sessionSince=null;state.speed=0;state.meters=0;state.segmentMeters=0;state.elapsed=0;state.x=0;state.lane=0;state.gap=55;state.left=false;state.right=false;state.accel=false;state.brake=false;$("steer").value=0;setGear("P");$("leftSignal").classList.remove("selected");$("rightSignal").classList.remove("selected");$("startCover").classList.remove("hidden");$("currentJudgment").textContent="출발하면 조건을 확인해요.";try{const reset=await api("/api/reset",{});state.sessionSince=reset.clock;$("feedbackTitle").textContent="새로운 주행을 기다리고 있어요.";$("feedbackText").textContent="준비되면 출발해보세요. 곁이 옆에서 살펴볼게요.";}catch(e){$("driveStatus").textContent=e.message;}});
document.querySelectorAll("[data-persona]").forEach(b=>b.addEventListener("click",async()=>{try{await api("/api/settings",{persona:b.dataset.persona});state.persona=b.dataset.persona;updatePersonas();}catch(e){$("audioState").textContent=e.message;}}));
$("mute").addEventListener("click",async()=>{try{await api("/api/settings",{voice:!state.voice});state.voice=!state.voice;readStatus();}catch(e){$("audioState").textContent=e.message;}});
$("route").addEventListener("change",()=>{state.segmentMeters=0;previousRoute=$("route").value;});
$("leadGap").addEventListener("input",()=>{state.gap=num("leadGap");text($("gapValue"),num("leadGap")+" m");});
for(const [id,label,suffix] of [["leadSpeed","leadSpeedValue"," km/h"],["seat","seatValue","%"],["pressure","pressureValue","%"],["battery","batteryValue"," km"],["remaining","remainingValue"," km"]])$(id).addEventListener("input",()=>text($(label),num(id)+suffix));
window.addEventListener("keydown",e=>{if(e.target.matches("input,select,textarea"))return;const k=e.key.toLowerCase();if(["arrowup","arrowdown","arrowleft","arrowright"," ","w","a","s","d","q","e"].includes(k))e.preventDefault();if(e.repeat&&(k==="q"||k==="e"))return;keys.add(e.key);keys.add(k);if(k==="arrowup"||k==="w"){state.accel=state.started;$("accel").classList.add("held");}if(k==="arrowdown"||k==="s"||k===" "){state.brake=state.started;$("brake").classList.add("held");}if(k==="q")setIndicator("left");if(k==="e")setIndicator("right");});
window.addEventListener("keyup",e=>{const k=e.key.toLowerCase();keys.delete(e.key);keys.delete(k);if(k==="arrowup"||k==="w"){state.accel=false;$("accel").classList.remove("held");}if(k==="arrowdown"||k==="s"||k===" "){state.brake=false;$("brake").classList.remove("held");}});
window.addEventListener("blur",()=>{keys.clear();state.accel=false;state.brake=false;$("accel").classList.remove("held");$("brake").classList.remove("held");});
setInterval(sendFrame,250);setInterval(readStatus,500);readStatus();requestAnimationFrame(tick);
