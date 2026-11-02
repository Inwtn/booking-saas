const $=s=>document.querySelector(s);
let csrf,workspaces=[],resources=[];
const message=(text,error=false)=>{$('#notice').textContent=text;$('#notice').classList.toggle('error',error)};
async function token(){csrf=await fetch('/api/csrf').then(r=>r.json())}
async function api(path,{method='GET',body,form=false}={}){
  const headers={};
  if(method!=='GET'){if(!csrf)await token();headers[csrf.headerName]=csrf.token}
  if(body&&!form)headers['Content-Type']='application/json';
  const r=await fetch('/api'+path,{method,headers,body:body?(form?new URLSearchParams(body):JSON.stringify(body)):undefined});
  if(!r.ok){const data=await r.json().catch(()=>({}));throw Error(data.message||({401:'Sign in to continue.',403:'You do not have access, or your session expired. Please sign in again.',409:'This time is already booked, or this email is registered.'}[r.status])||'Unable to complete the request.')}
  return r.status===204?null:r.json();
}
const current=()=>$('#workspace').value;
const path=part=>'/workspaces/'+current()+part;
async function signedIn(){
  const me=await api('/me');workspaces=me.workspaces;
  $('#workspace').replaceChildren(...workspaces.map(w=>new Option(w.name,w.id)));
  $('#auth').hidden=true;$('#dashboard').hidden=false;$('#logout').hidden=false;
  await loadWorkspace();
}
async function loadWorkspace(){
  const selected=workspaces.find(w=>w.id===current());
  document.querySelectorAll('.owner').forEach(el=>el.hidden=selected?.role!=='OWNER');
  resources=await api(path('/resources'));$('#resource').replaceChildren(...resources.map(r=>new Option(r.name,r.id)));
  $('#booking button').disabled=resources.length===0;
  await loadBookings();
}
async function loadBookings(){
  const start=new Date($('#day').value+'T00:00:00');if(Number.isNaN(start.getTime()))return;
  const end=new Date(start);end.setDate(end.getDate()+1);
  const bookings=await api(path('/bookings')+'?'+new URLSearchParams({from:start.toISOString(),to:end.toISOString()}));
  const container=$('#bookings');container.replaceChildren();
  if(!bookings.length){const empty=document.createElement('p');empty.className='empty';empty.textContent='Your day is clear. Add an appointment to get started.';container.append(empty)}
  for(const booking of bookings){
    const row=document.createElement('div');row.className='appointment';
    const info=document.createElement('div');info.className='info';
    const title=document.createElement('strong');title.textContent=booking.customerName;
    const detail=document.createElement('p');detail.textContent=new Date(booking.startsAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})+' – '+new Date(booking.endsAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})+' · '+(resources.find(r=>r.id===booking.resourceId)?.name||'Resource');
    info.append(title,detail);row.append(info);
    const badge=document.createElement('span');badge.className='badge'+(booking.status==='CANCELLED'?' cancelled':'');badge.textContent=booking.status.toLowerCase();row.append(badge);
    if(booking.status==='CONFIRMED'){const button=document.createElement('button');button.textContent='Cancel';button.addEventListener('click',()=>run(async()=>{await api(path('/bookings/'+booking.id+'/cancel'),{method:'POST'});await loadBookings();message('Appointment cancelled.')},button));row.append(button)}
    container.append(row);
  }
}
async function run(action,button){if(button)button.disabled=true;try{await action()}catch(error){message(error.message,true)}finally{if(button)button.disabled=false}}
function handle(id,action){$(id).addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;run(()=>action(Object.fromEntries(new FormData(form)),form),form.querySelector('button'))})}
handle('#login',async(data,form)=>{await api('/login',{method:'POST',body:data,form:true});form.reset();await token();await signedIn();message('Welcome to your workspace.')});
handle('#register',async(data,form)=>{await api('/register',{method:'POST',body:data});form.reset();message('Workspace created. Sign in with your owner email and password.')});
handle('#resource-form',async(data,form)=>{await api(path('/resources'),{method:'POST',body:data});form.reset();await loadWorkspace();message('Resource added.')});
handle('#staff',async(data,form)=>{await api(path('/staff'),{method:'POST',body:data});form.reset();message('Staff account created.')});
handle('#booking',async(data,form)=>{data.startsAt=new Date(data.startsAt).toISOString();data.endsAt=new Date(data.endsAt).toISOString();await api(path('/bookings'),{method:'POST',body:data});form.reset();await loadBookings();message('Appointment confirmed.')});
$('#workspace').addEventListener('change',()=>run(loadWorkspace));$('#day').addEventListener('change',()=>run(loadBookings));
$('#logout').addEventListener('click',()=>run(async()=>{await api('/logout',{method:'POST'});await token();$('#dashboard').hidden=true;$('#auth').hidden=false;$('#logout').hidden=true;message('Signed out.')}));
const today=new Date();$('#day').value=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');
run(async()=>{await token();const r=await fetch('/api/me');if(r.ok)await signedIn()});
