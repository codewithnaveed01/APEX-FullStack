const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const store=(()=>{try{let s=localStorage;s.setItem('_vtest','1');s.removeItem('_vtest');return s}catch(e){const m={};return {getItem:k=>m[k]||null,setItem:(k,v)=>m[k]=v,removeItem:k=>delete m[k]}}})();
const read=(k,d)=>{try{const v=store.getItem('v2_'+k); return v?JSON.parse(v):d}catch(e){return d}};
const write=(k,v)=>store.setItem('v2_'+k,JSON.stringify(v));
const money=n=>'PKR '+Math.round(n).toLocaleString('en-PK');
const photo=n=>{
  if(!n) return '';
  if(n.startsWith('data:')) return n;
  return window.EMBEDDED_PHOTOS?.[n]||'assets/'+n+'.jpg';
};
function getCarPhoto(c,key){
  if(!c) return photo(key);
  if(c.customImages && c.customImages[key]) return c.customImages[key];
  if(key===c.image && c.customImage) return c.customImage;
  return photo(key);
}
function getCarMainPhoto(c){
  return c.customImage ? c.customImage : photo(c.image);
}
const TODAY='2026-09-19';
let config=read('config',{driverRate:4500,overtime:500,jazzCashNumber:'0300-1234567',easypaisaNumber:'0300-7654321',bankAccount:'APEX Rentals - HBL 12345678901234',bankIBAN:'PK36HABB0001234567890123',raastId:'03001234567',officeAddress:'APEX Rental Office, Main Boulevard, Gulberg III, Lahore, Pakistan - 54000',homeDeliveryCharge:1500,companyPhone:'+92 300 1234567'});
// Merge defaults if missing
config={driverRate:4500,overtime:500,jazzCashNumber:'0300-1234567',easypaisaNumber:'0300-7654321',bankAccount:'APEX Rentals - HBL 12345678901234',bankIBAN:'PK36HABB0001234567890123',raastId:'03001234567',officeAddress:'APEX Rental Office, Main Boulevard, Gulberg III, Lahore, Pakistan - 54000',homeDeliveryCharge:1500,companyPhone:'+92 300 1234567',...config};
let adminWallet=read('adminWallet',0);
let ownerWallets=read('ownerWallets',{});
let bannedCNICs=read('bannedCNICs',[]);
const defaultFleet=[
{id:1,name:'Porsche Panamera',brand:'Porsche',category:'Grand touring',image:'porsche',year:2025,rate:65000,seats:4,engine:'2.9L twin-turbo V6',power:'348 hp',fuel:'Petrol',km:'12,400 km',color:'Jet Black',plate:'LEA-901',condition:'Excellent',status:'Active',deposit:100000,features:['Adaptive air suspension','Premium leather interior','Panoramic sunroof','Apple CarPlay','360° parking camera','Dual-zone climate control'],origin:'Premium'},
{id:2,name:'Mercedes-AMG GT',brand:'Mercedes-Benz',category:'Sports',image:'mercedes',year:2024,rate:85000,seats:2,engine:'4.0L biturbo V8',power:'469 hp',fuel:'Petrol',km:'9,800 km',color:'Selenite Silver',plate:'LEA-002',condition:'Excellent',status:'Active',deposit:150000,features:['AMG performance seats','Burmester sound system','Sport driving modes','Premium leather cockpit','Rear parking camera','Automatic climate control'],origin:'Premium'},
{id:3,name:'BMW M5',brand:'BMW',category:'Executive',image:'bmw',year:2024,rate:55000,seats:5,engine:'4.4L twin-turbo V8',power:'600 hp',fuel:'Petrol',km:'16,200 km',color:'Alpine White',plate:'ISB-505',condition:'Very good',status:'Active',deposit:100000,features:['M xDrive all-wheel drive','Heated leather seats','Head-up display','Harman Kardon audio','Wireless phone charging','Adaptive cruise control'],origin:'Premium'},
{id:4,name:'Suzuki Alto VXL AGS',brand:'Suzuki',category:'Hatchback',image:'pk-alto',year:2024,rate:4500,seats:4,engine:'660cc R06A',power:'39 hp',fuel:'Petrol',km:'18,500 km',color:'Silky Silver',plate:'LHR-xxx401',condition:'Very good',status:'Active',deposit:20000,features:['AGS automatic','Keyless entry','Power steering','Air conditioner','Front power windows','Central locking'],origin:'Pakistan',marketNote:'Economy hatchback - most rented city car. Rate from public listings Sep 2026.'},
{id:5,name:'Suzuki Cultus VXL',brand:'Suzuki',category:'Hatchback',image:'pk-cultus',year:2023,rate:5000,seats:5,engine:'1000cc K10B',power:'67 hp',fuel:'Petrol',km:'22,000 km',color:'Pearl White',plate:'ISB-xxx502',condition:'Very good',status:'Active',deposit:25000,features:['AGS automatic','ABS with EBD','Dual airbags','Touchscreen infotainment','Reverse camera','Alloy wheels'],origin:'Pakistan',marketNote:'Popular 1000cc hatchback. Rate 4500-5000/day self-drive.'},
{id:6,name:'Suzuki Swift GLX CVT',brand:'Suzuki',category:'Hatchback',image:'pk-swift',year:2024,rate:6000,seats:5,engine:'1200cc K12C Dualjet',power:'82 hp',fuel:'Petrol',km:'14,800 km',color:'Solid White',plate:'KHI-xxx603',condition:'Excellent',status:'Active',deposit:30000,features:['CVT automatic','Push start','Cruise control','9-inch display','6 airbags','LED projector headlamps'],origin:'Pakistan',marketNote:'Premium hatchback. Market 5500-6500.'},
{id:7,name:'Honda City Aspire 1.5 CVT',brand:'Honda',category:'Sedan',image:'pk-city',year:2024,rate:7000,seats:5,engine:'1.5L i-VTEC',power:'118 hp',fuel:'Petrol',km:'19,200 km',color:'Taffeta White',plate:'LHR-xxx704',condition:'Excellent',status:'Active',deposit:35000,features:['CVT automatic','Sunroof','Smart entry','Push start','7-inch touchscreen','Rear AC vents'],origin:'Pakistan',marketNote:'Best-selling 1.5L sedan. Market 6000-8000/day.'},
{id:8,name:'Honda Civic Oriel 1.5 Turbo',brand:'Honda',category:'Sedan',image:'pk-civic',year:2024,rate:12000,seats:5,engine:'1.5L Turbo VTEC',power:'176 hp',fuel:'Petrol',km:'12,600 km',color:'Platinum White',plate:'ISB-xxx805',condition:'Excellent',status:'Active',deposit:50000,features:['CVT with paddle shift','Honda Sensing','Leather seats','Bose audio','Wireless charging','Dual-zone climate'],origin:'Pakistan',marketNote:'Premium sedan. Market 8000-15000/day.'},
{id:9,name:'Toyota Corolla X 1.8 CVT',brand:'Toyota',category:'Sedan',image:'pk-corolla',year:2023,rate:8000,seats:5,engine:'1.8L Dual VVT-i',power:'138 hp',fuel:'Petrol',km:'27,400 km',color:'Silver Metallic',plate:'LHR-xxx906',condition:'Very good',status:'Active',deposit:40000,features:['CVT-i 7-speed','Push start','Smart entry','9-inch infotainment','Rear camera','Climate control'],origin:'Pakistan',marketNote:'Most trusted sedan. Market 7000-8000.'},
{id:10,name:'Toyota Yaris ATIV X 1.5 CVT',brand:'Toyota',category:'Sedan',image:'pk-yaris',year:2024,rate:7000,seats:5,engine:'1.5L Dual VVT-i',power:'106 hp',fuel:'Petrol',km:'16,900 km',color:'Attitude Black',plate:'KHI-xxx007',condition:'Excellent',status:'Active',deposit:35000,features:['CVT 7-speed','Push start','7 airbags','Roof-mounted AC','6.8-inch display','Parking sensors'],origin:'Pakistan',marketNote:'Compact sedan. Market 6500-7000/day.'},
{id:11,name:'Honda BR-V S 1.5 CVT',brand:'Honda',category:'SUV',image:'pk-brv',year:2023,rate:10000,seats:7,engine:'1.5L i-VTEC',power:'118 hp',fuel:'Petrol',km:'24,300 km',color:'Taffeta White',plate:'LHR-xxx108',condition:'Very good',status:'Active',deposit:45000,features:['7-seat layout','CVT automatic','Rear AC','Touchscreen','Hill start assist','ISOFIX','Roof rails'],origin:'Pakistan',marketNote:'7-seater family SUV. Market 8000-15000/day.'},
{id:12,name:'Toyota Fortuner Sigma 4 2.8',brand:'Toyota',category:'SUV',image:'pk-fortuner',year:2024,rate:22000,seats:7,engine:'2.8L Diesel 1GD-FTV',power:'201 hp',fuel:'Diesel',km:'18,700 km',color:'Super White',plate:'ISB-xxx209',condition:'Excellent',status:'Active',deposit:100000,features:['4x4 with DAC','Push start','JBL audio','Power tailgate','Leather seats','360° camera','7 airbags'],origin:'Pakistan',marketNote:'Full-size SUV. Market 18000-25000/day.'},
{id:13,name:'Kia Sportage L HEV AWD',brand:'Kia',category:'SUV',image:'pk-sportage',year:2025,rate:18000,seats:5,engine:'1.6L Turbo Hybrid Smartstream',power:'227 hp combined',fuel:'Hybrid',km:'9,200 km',color:'Interstellar Grey',plate:'KHI-xxx310',condition:'Excellent',status:'Active',deposit:75000,features:['Hybrid AWD','Panoramic sunroof','Ventilated seats','12.3-inch dual display','ADAS','Wireless charging'],origin:'Pakistan',marketNote:'New Sportage L HEV. Market 12000-20000/day.'}
];
let fleet=read('fleet',defaultFleet);
if(!Array.isArray(fleet)||fleet.length<3){fleet=defaultFleet;write('fleet',fleet);}
else if(fleet.length<defaultFleet.length){const ids=new Set(fleet.map(c=>c.id));defaultFleet.forEach(c=>{if(!ids.has(c.id))fleet.push(c);});write('fleet',fleet);}
let drivers=read('drivers',[{id:1,name:'Ali Raza',phone:'+92 300 0000101',city:'Lahore',experience:8,license:'LHR-xxx102',active:true},{id:2,name:'Imran Shah',phone:'+92 300 0000102',city:'Islamabad',experience:11,license:'ISB-xxx209',active:true},{id:3,name:'Bilal Ahmed',phone:'+92 300 0000103',city:'Karachi',experience:6,license:'KHI-xxx311',active:false}]);
let orders=read('orders',[]),applications=read('applications',[]),account=read('session',null),cart=read('cart',[]);
let users=read('users',[]);
let notifications=read('notifications',[]);
let chats=read('chats',[]);
let trip=read('trip',{city:'Lahore',start:'2026-09-22',end:'2026-09-24',service:'Self-drive'});
let route='',category='All cars',sort='Recommended',galleryIndex=0,currentCar=1,checkoutStep=1,checkoutInfo={},payment='Cash on pickup',adminTab='Payments',adminQuery='',notifOpen=false,chatOpen=false,activeChatUser=null;
let isAdmin=!!window.ADMIN_MODE||location.pathname.endsWith('/admin.html');
let adminSession=read('adminSession',null);
function isAdminAuthenticated(){return !!(adminSession && adminSession.user==='admin');}
const carBy=id=>fleet.find(c=>c.id===+id);
const duration=(s,e)=>Math.max(1,Math.ceil((new Date(e)-new Date(s))/86400000));
const date=d=>new Date(d+'T12:00:00').toLocaleDateString('en-GB',{day:'numeric',month:'short',year:'numeric'});

// --- CHAT SYSTEM ---
function getOrCreateChat(userId, userName){
  let c=chats.find(x=>x.userId===userId);
  if(!c){c={userId, userName:userName||'Customer', messages:[], unreadAdmin:0, unreadUser:0, lastTime:new Date().toISOString()}; chats.push(c);}
  return c;
}
function sendUserChat(text){
  if(!account) return auth(false);
  const chat=getOrCreateChat(account.id, account.name);
  chat.messages.push({sender:'user', text, time:new Date().toISOString()});
  chat.lastTime=new Date().toISOString();
  chat.unreadAdmin=(chat.unreadAdmin||0)+1;
  persist();
  addNotification('admin','New message from '+(account.username||account.name), text.slice(0,60),null,'Messages');
  toast('Message sent');
  renderChatMessages();
}
function sendAdminChat(userId, text){
  const chat=chats.find(c=>c.userId===userId);
  if(!chat) return;
  chat.messages.push({sender:'admin', text, time:new Date().toISOString()});
  chat.lastTime=new Date().toISOString();
  chat.unreadUser=(chat.unreadUser||0)+1;
  persist();
  addNotification(userId,'APEX Support replied', text.slice(0,60),'account');
  toast('Reply sent');
}
function selectChat(userId){activeChatUser=userId; const c=chats.find(x=>x.userId===userId); if(c){c.unreadAdmin=0; persist();} render(); setTimeout(()=>{const el=document.getElementById('admin-chat-messages'); if(el) el.scrollTop=el.scrollHeight;},80);}
function toggleChat(){
  chatOpen=!chatOpen;
  const panel=document.getElementById('chat-panel');
  if(panel) panel.classList.toggle('open',chatOpen);
  if(chatOpen){
    const chat=account?getOrCreateChat(account.id, account.name):null;
    if(chat){ chat.unreadUser=0; persist(); }
    renderChatMessages();
  }
}
function renderChatMessages(){
  const cont=document.getElementById('chat-messages');
  if(!cont) return;
  if(!account){cont.innerHTML=`<div class="chat-empty">Please <button class="text-btn" onclick="auth(false)">sign in</button> to chat with APEX support.</div>`;return;}
  const chat=getOrCreateChat(account.id, account.name);
  if(!chat.messages.length){cont.innerHTML=`<div class="chat-empty">Hi ${esc(account.name.split(' ')[0])}! 👋<br>How can we help you with your APEX journey? Ask about cars, pricing, drivers.</div>`;return;}
  cont.innerHTML=chat.messages.map(m=>`<div class="chat-msg ${m.sender}">${esc(m.text)}<small>${new Date(m.time).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</small></div>`).join('');
  cont.scrollTop=cont.scrollHeight;
}
function submitUserChat(e){
  e.preventDefault();
  const input=e.target.elements.msg;
  const txt=input.value.trim();
  if(!txt) return;
  input.value='';
  sendUserChat(txt);
}
function chatWidget(){
  const unread = account? (chats.find(c=>c.userId===account.id)?.unreadUser||0) : 0;
  return `<div class="chat-widget"><button class="chat-fab ${unread?'has-unread':''}" onclick="toggleChat()" aria-label="Chat">💬</button><div id="chat-panel" class="chat-panel ${chatOpen?'open':''}"><div class="chat-header"><span><b>APEX Support</b> · Live chat</span><button onclick="toggleChat()">×</button></div><div id="chat-messages" class="chat-messages"></div><form onsubmit="submitUserChat(event)" class="chat-input"><input name="msg" placeholder="Ask about booking, cars, drivers..." required autocomplete="off"><button>Send ↗</button></form></div></div>`;
}

// --- CHARTS ---
function getMonthlyStats(){
  const months=[];
  const now=new Date(TODAY);
  for(let i=5;i>=0;i--){
    const d=new Date(now.getFullYear(), now.getMonth()-i, 1);
    months.push({label:d.toLocaleDateString('en-GB',{month:'short'}), year:d.getFullYear(), month:d.getMonth(), income:0, count:0});
  }
  orders.forEach(o=>{
    const d=new Date(o.created);
    const m=months.find(x=>x.year===d.getFullYear() && x.month===d.getMonth());
    if(m){ m.income+=o.totals.rental; m.count+=1; }
  });
  if(months.every(m=>m.income===0)){
    months.forEach((m,i)=>{ m.income=(i+1)*14000+Math.round(Math.random()*9000); m.count=i+1+Math.floor(Math.random()*2); });
  }
  return months;
}
function renderBarChart(){
  const data=getMonthlyStats();
  const max=Math.max(...data.map(d=>d.income),1);
  return `<div class="bar-chart">${data.map(d=>`<div class="bar-group"><div class="bar" style="height:${Math.round((d.income/max)*120)+10}px"><span class="bar-value">${(d.income/1000).toFixed(0)}k</span></div><span>${d.label}</span></div>`).join('')}</div>`;
}
function renderCategoryChart(){
  const cats={};
  fleet.forEach(c=>{ cats[c.category]=(cats[c.category]||0)+1; });
  const total=fleet.length||1;
  const colors=['#1e2f1a','#3a5a2e','#6b8a5e','#a8c19a','#d2ed9e','#eef3e3'];
  let i=0;
  return `<div class="donut-legend">${Object.entries(cats).map(([k,v])=>{
    const col=colors[i++%colors.length];
    const pct=Math.round((v/total)*100);
    return `<div class="legend-item"><span class="legend-dot" style="background:${col}"></span><span style="flex:1">${k}</span><b>${v} · ${pct}%</b></div>`;
  }).join('')}</div>`;
}
function adminOverview(){
  const active=orders.filter(o=>!['Cancelled','Completed'].includes(o.status));
  const rentalReceived=orders.reduce((s,o)=>s+o.totals.rental,0);
  const totalIncome=orders.reduce((s,o)=>s+o.totals.total,0);
  const avgBooking=orders.length?Math.round(rentalReceived/orders.length):0;
  const monthly=getMonthlyStats();
  const thisMonth=monthly[monthly.length-1];
  const unreadChats=chats.reduce((s,c)=>s+(c.unreadAdmin||0),0);
  return `
    <div class="admin-stats">
      ${[['Total revenue',money(totalIncome)],['Rental income',money(rentalReceived)],['This month',money(thisMonth.income)],['Active bookings',active.length],['Avg booking',money(avgBooking)],['Fleet size',fleet.length],['Total customers',new Set(orders.map(o=>o.userId)).size + users.length],['Unread chats',unreadChats]].map(([k,v])=>`<div class="panel depth-layer"><small>${k}</small><h2>${v}</h2></div>`).join('')}
    </div>
    <div class="chart-grid">
      <div class="chart-panel depth-layer"><h3>Monthly revenue · Last 6 months</h3><p class="small muted">Rental income trend — PKR</p>${renderBarChart()}<div style="display:flex;justify-content:space-between;margin-top:12px"><small class="muted">Total ${money(monthly.reduce((s,m)=>s+m.income,0))}</small><small class="muted">Avg ${money(Math.round(monthly.reduce((s,m)=>s+m.income,0)/6))}/mo</small></div></div>
      <div class="chart-panel depth-layer"><h3>Fleet by category</h3><p class="small muted">${fleet.length} vehicles</p>${renderCategoryChart()}<div style="margin-top:18px;padding:12px;background:var(--bg-2);border-radius:10px;border:1px solid var(--line)"><small class="muted">Most rented</small><b style="display:block;margin-top:4px;font-size:12px">${(() => { const counts={}; orders.forEach(o=>o.items.forEach(i=>{counts[i.carId]=(counts[i.carId]||0)+1})); const top=Object.entries(counts).sort((a,b)=>b[1]-a[1])[0]; return top? (carBy(top[0])?.name||'Car')+' · '+top[1]+' bookings' : 'No data yet — sample shown'; })()}</b></div></div>
    </div>
    <div class="chart-grid">
      <div class="chart-panel depth-layer"><h3>Recent activity · History</h3><div class="timeline">${orders.slice(0,8).map(o=>`<div class="timeline-item"><b>${o.id} · ${o.name} — ${o.status}</b><p>${o.items.map(i=>carBy(i.carId)?.name||'Car').join(', ')} · ${money(o.totals.total)} · ${o.payment}</p><small>${new Date(o.created).toLocaleString()}</small></div>`).join('')||'<p class="small muted">No activity yet — history will appear here</p>'}</div></div>
      <div class="chart-panel depth-layer"><h3>Quick actions & health</h3><div style="display:grid;gap:10px;margin-top:12px"><button class="btn full" onclick="adminTab='Fleet';render()">Edit fleet & images ↗</button><button class="btn ghost full" onclick="adminTab='Messages';render()">Open messages · ${unreadChats} unread</button><button class="btn ghost full" onclick="adminTab='Reservations';render()">Manage bookings</button><button class="btn ghost full" onclick="adminTab='History';render()">View full history</button><button class="btn ghost full" onclick="exportOrders()">Export CSV ↓</button></div><div style="margin-top:20px"><h3 style="font-size:13px">System health</h3><div class="info-grid" style="grid-template-columns:1fr 1fr;margin-top:10px"><div class="info-box"><small>Drivers active</small><b>${drivers.filter(d=>d.active).length}/${drivers.length}</b></div><div class="info-box"><small>Pending apps</small><b>${applications.filter(a=>a.status==='Submitted').length}</b></div><div class="info-box"><small>Unread notifs</small><b>${notifications.filter(n=>!n.read).length}</b></div><div class="info-box"><small>Chats</small><b>${chats.length}</b></div></div></div></div>
    </div>
    <div class="section-head"><div><h2 style="font-size:23px">Recent reservations</h2></div><button class="text-btn" onclick="adminTab='Reservations';render()">View all ↗</button></div>${adminBookings(orders.slice(0,6))}
  `;
}
function adminHistory(){
  return `<div class="panel depth-layer"><div class="section-head"><div><h2 style="font-size:20px">Complete history · All bookings</h2><p class="small muted">${orders.length} records · Full audit trail</p></div><button class="btn ghost" onclick="exportOrders()">Export CSV ↓</button></div><div class="timeline">${orders.map(o=>`<div class="timeline-item"><b>${o.id} · ${o.name} — <span class="pill" style="font-size:9px">${o.status}</span></b><p>${o.items.map(i=>carBy(i.carId)?.name||'Deleted').join(' + ')} · ${date(o.items[0]?.start)} → ${date(o.items[0]?.end)} · ${money(o.totals.total)} via ${o.payment}</p><small>${new Date(o.created).toLocaleString()} · ${o.userId} · ${o.email}</small></div>`).join('')||'<p class="small muted">No history yet</p>'}</div></div>`;
}
function adminChatList(){
  if(!chats.length) return `<div class="empty"><h3>No messages yet</h3><p class="small muted">Customer chats will appear here. Customers can chat from the floating button.</p></div>`;
  const sorted=[...chats].sort((a,b)=>new Date(b.lastTime)-new Date(a.lastTime));
  return `<div class="panel depth-layer" style="display:grid;grid-template-columns:300px 1fr;gap:0;overflow:hidden;padding:0;min-height:520px">
    <div style="border-right:1px solid var(--line);overflow:auto;max-height:600px">
      ${sorted.map(c=>`<button onclick="selectChat('${c.userId}')" style="width:100%;text-align:left;padding:14px 16px;border-bottom:1px solid var(--line);display:flex;justify-content:space-between;align-items:center;background:${activeChatUser===c.userId?'var(--bg-2)':'#fff'}"><div><b style="font-size:12px">${c.userName}</b><p style="font-size:10px;color:var(--muted);margin:4px 0 0">${(c.messages[c.messages.length-1]?.text||'No messages').slice(0,32)}</p></div>${c.unreadAdmin?`<span style="background:#e85d4a;color:#fff;font-size:9px;padding:3px 7px;border-radius:100px">${c.unreadAdmin}</span>`:''}</button>`).join('')}
    </div>
    <div style="display:flex;flex-direction:column">
      ${activeChatUser?(()=>{
        const chat=chats.find(c=>c.userId===activeChatUser);
        if(!chat) return `<div class="chat-empty">Select a conversation</div>`;
        chat.unreadAdmin=0; write('chats',chats);
        return `<div style="padding:14px 16px;border-bottom:1px solid var(--line);background:var(--bg-2);display:flex;justify-content:space-between"><b>${chat.userName}</b><small style="color:var(--muted)">${chat.userId}</small></div><div style="flex:1;overflow:auto;padding:16px;display:flex;flex-direction:column;gap:10px" id="admin-chat-messages">${chat.messages.map(m=>`<div class="chat-msg ${m.sender==='user'?'admin':'user'}" style="${m.sender==='user'?'':'align-self:flex-end;background:var(--lime);color:var(--lime-bright)'}">${esc(m.text)}<small>${new Date(m.time).toLocaleString()}</small></div>`).join('')||'<div class="chat-empty">No messages</div>'}</div><form onsubmit="submitAdminChat(event,'${chat.userId}')" class="chat-input"><input name="msg" placeholder="Reply to ${chat.userName}..." required><button>Send ↗</button></form>`;
      })():`<div class="chat-empty" style="flex:1;display:grid;place-items:center">Select a chat from the left to reply</div>`}
    </div>
  </div>`;
}
function submitAdminChat(e,userId){
  e.preventDefault();
  const input=e.target.elements.msg;
  const txt=input?input.value.trim():'';
  if(!txt) return;
  input.value='';
  sendAdminChat(userId, txt);
  // Re-render and keep chat open
  const prevUser=activeChatUser;
  render();
  activeChatUser=prevUser;
  // After render, ensure messages visible
  setTimeout(()=>{
    const list=document.getElementById('admin-chat-messages');
    if(list) list.scrollTop=list.scrollHeight;
    const panel=document.querySelector('#admin-chat-messages');
    if(panel) panel.scrollTop=panel.scrollHeight;
  },100);
}

// Fleet image edit temp
window.fleetEditTemp={};
function previewFleetImage(input, type){
  const file=input.files[0];
  if(!file) return;
  const reader=new FileReader();
  reader.onload=e=>{
    window.fleetEditTemp[type]=e.target.result;
    const preview=document.getElementById('fleet-'+type+'-preview');
    if(preview) preview.innerHTML=`<img src="${e.target.result}" alt=""><p class="small muted">Ready · ${Math.round(file.size/1024)}KB</p>`;
  };
  reader.readAsDataURL(file);
}

function persist(){write('fleet',fleet);write('drivers',drivers);write('orders',orders);write('applications',applications);write('cart',cart);write('trip',trip);write('config',config);write('adminSession',adminSession);write('notifications',notifications);write('session',account);write('users',users);write('chats',chats);write('adminWallet',adminWallet);write('ownerWallets',ownerWallets);write('bannedCNICs',bannedCNICs);}
const initials=n=>esc(n.split(' ').map(s=>s[0]).slice(0,2).join(''));
function toast(t){const el=document.getElementById('toast');el.textContent=t;el.classList.add('show');clearTimeout(window.toastTime);window.toastTime=setTimeout(()=>el.classList.remove('show'),4000)}
function go(p){if(location.hash.slice(1)===p){render();scrollTo(0,0)}else location.hash=p}
function validTrip(t=trip){return t.start>=TODAY&&t.end>t.start}
function clash(carId,start,end,exclude=''){const s=new Date(start+'T12:00:00'), e=new Date(end+'T12:00:00'); return orders.some(o=>o.id!==exclude&&!['Cancelled','Completed','Rejected'].includes(o.status)&&o.items.some(i=>{const is=new Date(i.start+'T12:00:00'), ie=new Date(i.end+'T12:00:00'); return i.carId===+carId && s < ie && e > is;}))}
function available(c,t=trip){return c.status==='Active'&&!clash(c.id,t.start,t.end)}
function quote(item){const c=carBy(item.carId),n=duration(item.start,item.end),discount=n>=30?.2:n>=7?.1:0,base=c.rate*n,saving=Math.round(base*discount),driver=item.service==='With driver'?(item.driverRate??config.driverRate)*n:0,deposit=item.service==='With driver'?0:c.deposit;return {n,base,saving,driver,rental:base-saving+driver,deposit,total:base-saving+driver+deposit}}
function totals(items=cart){return items.reduce((t,i)=>{const q=quote(i);Object.keys(t).forEach(k=>t[k]+=q[k]);return t},{base:0,saving:0,driver:0,rental:0,deposit:0,total:0})}
function addNotification(userId,title,msg,link,adminTab){notifications.unshift({id:'N'+Date.now()+Math.random().toString(36).slice(2,5),userId:userId||account?.id||'admin',title,msg,time:new Date().toISOString(),read:false,link:link||null,adminTab:adminTab||null});if(notifications.length>80)notifications=notifications.slice(0,80);write('notifications',notifications);}
function openNotif(id){const n=notifications.find(x=>x.id===id);notifOpen=false;const dd=document.getElementById('notif-dropdown');if(dd)dd.classList.remove('open');if(!n)return;n.read=true;write('notifications',notifications);if(window.ADMIN_MODE){if(n.adminTab)adminTab=n.adminTab;render();return;}if(n.link){go(n.link);}else if(isAdmin){go('Overview');}render();}
function renderNotif(){const mine=isAdmin?notifications:account?notifications.filter(n=>n.userId===account.id||n.userId==='all'):[];const btn=document.getElementById('notif-btn');if(btn){const unread=mine.filter(n=>!n.read).length;btn.classList.toggle('has-unread',unread>0);}}
function toggleNotif(){notifOpen=!notifOpen;const dd=document.getElementById('notif-dropdown');if(dd)dd.classList.toggle('open',notifOpen);if(notifOpen){const mine=isAdmin?notifications:notifications.filter(n=>n.userId===account?.id||n.userId==='all');mine.forEach(n=>n.read=true);write('notifications',notifications);}}
function clearNotifs(){if(isAdmin)notifications=[];else notifications=notifications.filter(n=>n.userId!==account?.id);write('notifications',notifications);notifOpen=false;render();toast('Notifications cleared');}
function header(){
  const mine=isAdmin?notifications:account?notifications.filter(n=>n.userId===account.id||n.userId==='all'):[];
  const unread=mine.filter(n=>!n.read).length;
  const adminLink = isAdmin ? `<a class="${route==='Overview'?'current':''}" href="#Overview">Dashboard</a>` : `<a href="admin.html" style="display:flex;align-items:center;gap:6px;border:1px solid var(--line);padding:6px 12px;border-radius:100px">Admin ↗</a>`;
  return `<div class="topline">APEX — DRIVEN BEYOND ORDINARY</div><header class="header wrap"><a class="logo" href="#home"><span class="apex-mark"><svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d="M12 2 L22 22 H2 Z" fill="currentColor"/></svg></span> APEX<sup>®</sup></a><nav class="nav"><a class="${route==='fleet'?'current':''}" href="#fleet">Our collection</a><a href="#plans">Rental plans</a><a href="#owner">List your car ↗</a>${adminLink}</nav><div class="header-actions" style="position:relative"><button id="notif-btn" class="notif-btn ${unread?'has-unread':''}" onclick="toggleNotif()" aria-label="Notifications"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M6 9a6 6 0 0 1 12 0c0 7 6 7 6 11H0s6-4 6-11"/><path d="M9 21a3 3 0 0 0 6 0"/></svg></button><div id="notif-dropdown" class="notif-dropdown ${notifOpen?'open':''}"><div style="display:flex;justify-content:space-between;align-items:center;padding:8px 6px 12px;border-bottom:1px solid var(--line)"><b style="font-size:12px">Notifications · ${mine.length}</b><button class="text-btn" onclick="clearNotifs()">Clear</button></div><div style="max-height:320px;overflow:auto">${mine.length?mine.slice(0,12).map(n=>`<div class="notif-item ${!n.read?'unread':''}" style="cursor:pointer" onclick="openNotif('${n.id}')" title="Click to open"><span class="dot"></span><div><b>${esc(n.title)}</b><p>${esc(n.msg)}<br><small style="opacity:.7">${new Date(n.time).toLocaleString()}</small></p></div></div>`).join(''):'<p class="small muted" style="padding:20px;text-align:center">No notifications yet. Book a car to see updates.</p>'}</div></div><button class="basket" onclick="go('cart')" aria-label="Open rental selection">▱<span>${cart.length}</span></button>${account?`<button class="btn ghost" onclick="go('account')">${initials(account.name)} · ${esc(account.username||account.name.split(' ')[0])}</button>`:`<button onclick="auth(false)">Sign in</button><button class="btn" onclick="auth(true)">Create account ↗</button>`}</div></header>`;
}
function footer(){return `<footer class="footer"><div class="wrap"><div class="footer-top"><a class="logo" href="#home"><span class="apex-mark"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 2 L22 22 H2 Z" fill="currentColor"/></svg></span> APEX</a><div class="footer-links"><button onclick="guidelines()">Rental info</button><button onclick="terms()">Terms</button><button onclick="contact()">Contact</button><a href="admin.html">Admin</a></div></div><div class="footer-bottom"><span>© 2026 APEX. All rights reserved.</span><span style="display:flex;gap:18px;align-items:center"><span>Privacy · Terms</span><span style="font-size:10px;letter-spacing:.3px">Developed by <span style="color:#e11d48;font-weight:800">Spidy</span> 🕷️</span></span></div></div></footer>`}
function searchForm(){return `<form class="searchbox" onsubmit="searchFleet(event)"><div><label>Pick-up location</label><select name="city">${cities(trip.city)}</select></div><div><label>Pick-up date</label><input type="date" name="start" min="${TODAY}" value="${trip.start}" required></div><div><label>Return date</label><input type="date" name="end" min="${TODAY}" value="${trip.end}" required></div><div><label>Your journey</label><select name="service"><option ${trip.service==='Self-drive'?'selected':''}>Self-drive</option><option ${trip.service==='With driver'?'selected':''}>With driver</option></select></div><button class="btn">Find your drive ↗</button></form>`}
function cities(selected){return ['Lahore','Islamabad','Karachi','Dera Ghazi Khan'].map(c=>`<option ${c===selected?'selected':''}>${c}</option>`).join('')}
function searchFleet(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));if(!validTrip(f))return toast('Choose a future pick-up and a later return date.');trip=f;write('trip',trip);go('fleet');if(route==='fleet')render()}
function card(c){
  return `<article class="car"><a class="car-photo" href="#car/${c.id}" aria-label="Explore ${esc(c.name)}"><img src="${getCarMainPhoto(c)}" alt="${esc(c.name)}" loading="lazy"><span class="pill" style="${!available(c)?'background:#fef1f0;color:#9e3a2d;border-color:#f0c6c0':''}">${available(c)?'Available':'Rented'}</span><span class="photo-arrow">↗</span></a><div class="car-body"><div class="car-category">${c.category.toUpperCase()}</div><div class="car-title"><h3><a href="#car/${c.id}">${esc(c.name)}</a></h3><small>${c.year}</small></div><div class="specs"><span>◈ ${esc(c.engine.split(' ')[0])}</span><span>♧ ${c.seats} seats</span><span>⛽ ${c.fuel}</span></div><div class="car-price"><div><strong>${money(c.rate)}</strong><small> / day</small></div><button class="btn dark" onclick="go('car/${c.id}')">Explore car ↗</button></div></div></article>`;
}
function home(){return `<div class="wrap"><section class="hero depth-layer"><img src="${photo('hero')}" alt="APEX hero"><span class="hero-index">APEX COLLECTION / 01</span><div class="hero-copy"><div class="eyebrow">FOR THE JOURNEY. AND THE FEELING.</div><h1>Driven<br><em>beyond ordinary.</em></h1><p>APEX — 13 cars, 10 Pakistan favorites, clean official imagery, fully functional booking, notifications and accounts.</p><button class="btn" onclick="go('fleet')">Explore APEX fleet ↗</button></div><div class="hero-bottom"><b>APEX — 13 Cars</b><p>PREMIUM · PAKISTAN FAVORITES · FUNCTIONAL</p></div></section>${searchForm()}<div class="trust"><div class="trust-track"><span>Self-drive & chauffeur</span><span>Transparent pricing</span><span>Flexible rental plans</span><span>Multi-car reservations</span><span>Clean official images</span><span>Functional accounts</span><span>Live notifications</span><span>APEX — Driven Beyond Ordinary</span><span>Self-drive & chauffeur</span><span>Transparent pricing</span><span>Flexible rental plans</span><span>Multi-car reservations</span></div></div><section class="section"><div class="section-head"><div><div class="eyebrow">NOT JUST A CAR. YOUR KIND OF CAR.</div><h2>A collection, not a compromise.</h2><p>3 signature + 10 Pakistan favorites — Hayler-inspired smooth animations.</p></div><button class="text-btn" onclick="go('fleet')">Explore collection ↗</button></div><div class="cars">${fleet.filter(c=>c.status==='Active').map(card).join('')}</div></section><div class="benefits"><div class="benefit"><div class="symbol">✧</div><div><h3>Your pace. Your plan.</h3><p>A day away, a week of discovery, or a month that feels like yours.</p></div></div><div class="benefit"><div class="symbol">⌘</div><div><h3>Take the wheel. Or relax.</h3><p>Choose self-drive or add a professional chauffeur.</p></div></div><div class="benefit"><div class="symbol">↗</div><div><h3>More cars. One journey.</h3><p>Multiple cars in one reservation — fully functional.</p></div></div></div><div class="owner-banner depth-layer"><div><div class="eyebrow">THE APEX PARTNER PROGRAM</div><h2>Your car. A new possibility.</h2><p>List your vehicle and earn.</p></div><button class="btn ghost" onclick="go('owner')">Become a partner ↗</button></div></div>`}
function fleetPage(){let cars=fleet.filter(c=>c.status==='Active'&&(category==='All cars'||c.category===category||(category==='Pakistan Favorites'&&c.origin==='Pakistan')||(category==='Premium'&&c.origin==='Premium')));if(sort==='Price: low to high')cars.sort((a,b)=>a.rate-b.rate);if(sort==='Price: high to low')cars.sort((a,b)=>b.rate-a.rate);return `<div class="wrap"><div class="page-top"><div class="eyebrow">APEX COLLECTION</div><h1>Find your kind of extraordinary.</h1><p>10 Pakistan favorites + 3 premium — all functional, clean images, plates hidden.</p></div>${searchForm()}<div style="height:36px"></div><div class="filters">${['All cars','Pakistan Favorites','Premium','Hatchback','Sedan','SUV'].map(x=>`<button class="chip ${category===x?'selected':''}" onclick="category='${x}';render()">${x}</button>`).join('')}<select onchange="sort=this.value;render()">${['Recommended','Price: low to high','Price: high to low'].map(x=>`<option ${sort===x?'selected':''}>${x}</option>`).join('')}</select></div><p class="small muted">${cars.length} vehicles · ${esc(trip.city)} · ${date(trip.start)} — ${date(trip.end)} · ${esc(trip.service)}</p><div class="cars">${cars.map(card).join('')}</div><div style="height:65px"></div></div>`}
function gallery(c){return [{key:c.image,label:''},{key:c.image+'-interior',label:''},{key:c.image.startsWith('pk-')?c.image+'-detail':c.image+'-engine',label:''}]}
function detail(id){const c=carBy(id);if(!c)return empty('Vehicle not found','Return to collection','Explore collection','fleet');currentCar=c.id;const pics=gallery(c),p=pics[galleryIndex];return `<div class="wrap"><div class="page-top"><a class="back" href="#fleet">← Back to collection</a><div style="display:flex;justify-content:space-between;gap:15px;align-items:center;margin-top:20px"><div><div class="eyebrow">${c.brand.toUpperCase()} / ${c.category.toUpperCase()}</div><h1>${esc(c.name)}</h1><p>${c.year} · ${esc(c.color)} · ${c.condition}</p></div><span class="pill">${c.condition}</span></div></div><div class="detail-layout"><div><div class="gallery-main depth-layer"><img id="main-photo" src="${getCarPhoto(c,p.key)}" alt="${esc(c.name)}" onclick="lightbox(${c.id},galleryIndex)"><span class="gallery-label" id="gallery-label">${galleryIndex+1} / ${pics.length}</span><div class="gallery-controls"><button onclick="switchGallery(-1)">‹</button><button onclick="switchGallery(1)">›</button></div></div><div class="thumbs">${pics.map((p,i)=>`<button class="thumb ${galleryIndex===i?'on':''}" onclick="setGallery(${i})"><img src="${photo(p.key)}" alt=""></button>`).join('')}</div><div class="info-grid">${[['Model year',c.year],['Engine',c.engine],['Power',c.power],['Transmission','Automatic'],['Seats',c.seats+' passengers'],['Odometer',c.km]].map(([k,v])=>`<div class="info-box"><small>${k}</small><b>${esc(v)}</b></div>`).join('')}</div><div class="detail-section"><h3>${esc(c.name)}</h3><div class="checklist">${c.features.map(f=>`<span>${esc(f)}</span>`).join('')}</div></div></div><aside class="panel reservation depth-layer"><div class="eyebrow">RESERVE</div><h2>${money(c.rate)} <small>/ day</small></h2><form id="reservation-form" onsubmit="addToCart(event,${c.id})"><div class="formgrid dates"><div class="field"><label>Pick-up date</label><input type="date" name="start" min="${TODAY}" required value="${trip.start}" onchange="refreshQuote(${c.id})"></div><div class="field"><label>Return date</label><input type="date" name="end" min="${TODAY}" required value="${trip.end}" onchange="refreshQuote(${c.id})"></div><div class="field wide"><label>Branch</label><select name="city">${cities(trip.city)}</select></div><div class="field wide"><label>Service</label><select name="service" onchange="refreshQuote(${c.id})"><option ${trip.service==='Self-drive'?'selected':''}>Self-drive</option><option ${trip.service==='With driver'?'selected':''}>With driver</option></select></div></div><div id="quote-breakdown"></div><button class="btn full" id="add-car" style="margin-top:20px">Add to your journey ↗</button></form></aside></div><div style="height:65px"></div></div>`}
function priceLines(q){return `<div class="line"><span>Vehicle rental${q.n?' · '+q.n+' days':''}</span><strong>${money(q.base)}</strong></div>${q.saving?`<div class="line"><span>Long-stay savings</span><strong>− ${money(q.saving)}</strong></div>`:''}${q.driver?`<div class="line"><span>Chauffeur service</span><strong>${money(q.driver)}</strong></div>`:''}<div class="line"><span>Refundable deposit</span><strong>${money(q.deposit)}</strong></div><div class="line total"><span>Total due</span><strong>${money(q.total)}</strong></div>`}
function refreshQuote(id){const form=document.getElementById('reservation-form');if(!form)return;const f=Object.fromEntries(new FormData(form)),c=carBy(id),valid=validTrip(f),ok=valid&&available(c,f);document.getElementById('quote-breakdown').innerHTML=valid?`<div class="notice">${f.service==='With driver'?`${money(config.driverRate)}/day · ${c.seats-1} passenger seats with driver.`:'200 km/day included'}${!ok?'<br><b>Unavailable for these dates.</b>':''}</div>${priceLines(quote({...f,carId:id}))}`:'<div class="notice">Please select valid dates.</div>';document.getElementById('add-car').disabled=!ok}
function addToCart(e,id){e.preventDefault();let f=Object.fromEntries(new FormData(e.target));if(!validTrip(f)||!available(carBy(id),f))return toast('Please choose available dates.');if(cart.some(i=>i.carId===id))return toast('Already in your selection.');cart.push({...f,carId:id,driverRate:config.driverRate});trip=f;persist();toast('Added to your journey.');modal('Your journey is taking shape.',`<div class="cart-item"><img src="${photo(carBy(id).image)}" alt=""><div><h3>${esc(carBy(id).name)}</h3><p>${date(f.start)} — ${date(f.end)}</p><p>${esc(f.service)} · ${esc(f.city)}</p></div></div><p class="muted small" style="margin-top:20px">${cart.length} cars selected.</p><div class="button-row"><button class="btn ghost" onclick="closeModal();go('fleet')">Explore more</button><button class="btn" onclick="closeModal();go('cart')">View journey ↗</button></div>`);document.querySelector('.basket span').textContent=cart.length}
function setGallery(i){galleryIndex=i;const c=carBy(currentCar),p=gallery(c)[i];document.getElementById('main-photo').src=getCarPhoto(carBy(currentCar),p.key);document.getElementById('gallery-label').textContent=(i+1)+' / 3';document.querySelectorAll('.thumb').forEach((t,j)=>t.classList.toggle('on',i===j))}
function switchGallery(d){setGallery((galleryIndex+d+3)%3)}
function lightbox(id,index){const c=carBy(id),p=gallery(c)[index];modal(esc(c.name),`<img class="lightbox-image" src="${getCarPhoto(c,p.key)}" alt=""><div class="lightbox-footer"><button onclick="lightbox(${id},${(index+2)%3})">← Previous</button><span>${index+1} / 3</span><button onclick="lightbox(${id},${(index+1)%3})">Next →</button></div>`,true)}
function plansPage(){return `<div class="wrap"><div class="page-top"><div class="eyebrow">MORE TIME. MORE POSSIBILITY.</div><h1>A plan for every journey.</h1><p>Longer rentals save automatically.</p></div><div class="plans">${[['The day escape','Daily','From PKR 4,500','1–6 days','No long-stay discount','24-hour periods',2],['A week, well spent','Weekly','From PKR 28,350','7 days','10% off vehicle rental','More freedom',7],['Make yourself at home','Monthly','From PKR 108,000','30 days','20% off vehicle rental','Extended stay',30]].map((p,i)=>`<div class="panel plan ${i===1?'featured':''} depth-layer"><span class="pill">${p[1]}</span><h3>${p[0]}</h3><div class="plan-price">${p[2]}<br><small>${p[3]}</small></div><ul><li>${p[4]}</li><li>${p[5]}</li><li>200 km/day</li><li>Optional chauffeur</li></ul><button class="btn ${i===1?'':'ghost'} full" onclick="choosePlan(${p[6]})">Explore ${p[1].toLowerCase()} ↗</button></div>`).join('')}</div></div>`}
function choosePlan(n){const end=new Date(trip.start+'T12:00:00');end.setDate(end.getDate()+n);trip.end=end.toISOString().slice(0,10);write('trip',trip);go('fleet')}
function empty(title,sub,button='Explore cars',dest='fleet'){return `<div class="wrap empty"><div class="eyebrow">YOUR APEX JOURNEY</div><h2>${title}</h2><p>${sub}</p><button class="btn" onclick="go('${dest}')">${button} ↗</button></div>`}
function cartItem(i,index,removable=true){const c=carBy(i.carId),q=quote(i);return `<div class="cart-item"><img src="${getCarMainPhoto(c)}" alt=""><div><h3>${esc(c.name)}</h3><p>${date(i.start)} — ${date(i.end)} · ${q.n} days</p><p>${esc(i.city)} · ${esc(i.service)}</p>${!available(c,i)&&removable?'<p class="error-text">No longer available.</p>':''}</div><div><strong style="font-size:13px">${money(q.rental)}</strong>${removable?`<p style="margin-top:12px"><button class="remove" onclick="removeCart(${index})">Remove ×</button></p>`:''}</div></div>`}
function removeCart(i){cart.splice(i,1);write('cart',cart);render()}
function cartPage(){if(!cart.length)return empty('A little possibility. A blank itinerary.','Your selected vehicles will appear here.');return `<div class="wrap"><div class="page-top"><a href="#fleet" class="back">← Keep exploring</a><h1>Your journey, your way.</h1><p>${cart.length} vehicles selected.</p></div><div class="checkout-layout"><div class="panel depth-layer">${cart.map((i,n)=>cartItem(i,n)).join('')}<button class="text-btn" style="margin-top:23px" onclick="go('fleet')">+ Add another car</button></div><aside class="panel reservation depth-layer"><h3 class="formtitle">Your rental summary</h3>${priceLines(totals())}<button class="btn full" onclick="beginCheckout()">Continue to booking ↗</button></aside></div><div style="height:65px"></div></div>`}
function beginCheckout(){if(cart.some(i=>!validTrip(i)||!available(carBy(i.carId),i)))return toast('Some vehicles unavailable.');if(!account)return auth(true,'checkout');checkoutStep=1;go('checkout')}

function auth(signup=true,next='account'){
  const isSignup=signup;
  modal(isSignup?'Create your APEX account':'Welcome back to APEX',
  `<div class="auth-panel"><div class="eyebrow">${isSignup?'JOIN APEX · FUNCTIONAL ACCOUNTS':'SIGN IN · SECURE ACCESS'}</div><h2 style="margin-bottom:8px">${isSignup?'Your next journey starts here.':'Welcome back.'}</h2><p class="small muted" style="margin-bottom:18px">${isSignup?'Create a functional account with username and password. All data saved locally and works across bookings.':'Sign in with your username or email and password.'}</p><form onsubmit="submitAuth(event,${isSignup},'${next}')" id="auth-form"><div id="auth-error"></div><div class="formgrid">${isSignup?'<div class="field wide"><label>Full name</label><input name="name" required minlength="2" maxlength="70" placeholder="e.g. Ali Hassan"></div>':''}<div class="field ${isSignup?'':'wide'}"><label>Username</label><input name="username" required minlength="3" maxlength="20" pattern="[A-Za-z0-9_]{3,20}" placeholder="${isSignup?'apex_user123':'your username or email'}" autocomplete="username"></div>${isSignup?'<div class="field"><label>Email address</label><input name="email" type="email" required placeholder="you@example.com" autocomplete="email"></div>':''}${isSignup?'<div class="field"><label>Mobile number</label><input name="phone" type="tel" required pattern="[+0-9 ()-]{10,18}" placeholder="0300 0000000" autocomplete="tel"></div>':''}<div class="field ${isSignup?'':'wide'}"><label>Password</label><input name="password" type="password" required minlength="6" maxlength="30" placeholder="Min 6 characters" autocomplete="${isSignup?'new-password':'current-password'}"></div>${isSignup?'<div class="field"><label>Confirm password</label><input name="confirm" type="password" required minlength="6" placeholder="Repeat password" autocomplete="new-password"></div>':''}</div>${isSignup?'<label class="check"><input type="checkbox" required><span>I agree to APEX terms and privacy.</span></label>':''}<button class="btn full" style="margin-top:14px">${isSignup?'Create account ↗':'Sign in ↗'}</button></form><div class="auth-switch">${isSignup?'Already have an account?':'New to APEX?'} <button class="text-btn" onclick="auth(${!isSignup},'${next}')">${isSignup?'Sign in':'Create account'}</button></div></div>`);
}

function submitAuth(e,signup,next){
  e.preventDefault();
  const f=Object.fromEntries(new FormData(e.target));
  const errorEl=document.getElementById('auth-error');
  const showErr=m=>{if(errorEl)errorEl.innerHTML=`<div class="auth-error">${esc(m)}</div>`;};
  users=read('users',[]);
  if(signup){
    if(f.password!==f.confirm)return showErr('Passwords do not match.');
    if(users.some(u=>u.username.toLowerCase()===f.username.trim().toLowerCase()))return showErr('Username already taken. Choose another.');
    if(users.some(u=>u.email.toLowerCase()===f.email.trim().toLowerCase()))return showErr('Email already registered. Please sign in.');
    if(f.password.length<6)return showErr('Password must be at least 6 characters.');
    const u={id:'C'+Date.now(),name:f.name.trim(),username:f.username.trim(),email:f.email.trim().toLowerCase(),phone:f.phone.trim(),password:f.password};
    users.push(u);write('users',users);
    account=u;write('session',u);
    addNotification(u.id,'Account created','Welcome to APEX! Your account '+u.username+' is ready.','account');
    closeModal();go(next);render();toast('Welcome, '+u.name.split(' ')[0]+'!');
  }else{
    const identifier=f.username.trim().toLowerCase();
    if(identifier==='admin' && f.password==='admin1234'){
      adminSession={user:'admin',at:new Date().toISOString()};
      adminTab='Payments';
      account={id:'admin',name:'Administrator',username:'admin',email:'admin@apex.local',phone:'03000000000'};
      apexAdminLogin('admin', f.password);
      persist();
      closeModal();
      toast('Admin signed in — opening operations');
      if(isAdmin){render();return;}
      if(window.EMBEDDED_PHOTOS){
        window.ADMIN_MODE=true;isAdmin=true;
        document.getElementById('app').innerHTML=adminShell();
        renderNotif();
        return;
      }else{
        location.href='admin.html';
        return;
      }
    }
    const u=users.find(x=>x.username.toLowerCase()===identifier||x.email.toLowerCase()===identifier);
    if(!u)return showErr('No account found with that username or email.');
    if(u.password!==f.password)return showErr('Incorrect password. Try again.');
    account=u;write('session',u);
    addNotification(u.id,'Signed in','You signed in to APEX as '+u.username);
    closeModal();go(next);render();toast('Welcome back, '+u.name.split(' ')[0]+'!');
  }
}

function checkoutPage(){if(!cart.length)return empty('Your selection is empty.','Add a vehicle before booking.');if(!account)return `<div class="wrap empty"><h2>Sign in to continue</h2><p>Create your APEX account to book.</p><button class="btn" onclick="auth(true,'checkout')">Create account</button></div>`;return `<div class="wrap"><div class="page-top"><a class="back" href="#cart">← Your selection</a><h1>Make it your journey.</h1><p>Functional booking — all fields required.</p></div><div class="steps"><span class="${checkoutStep===1?'active':''}"><b>1</b> Renter details</span><span class="${checkoutStep===2?'active':''}"><b>2</b> Review & payment</span></div><div class="checkout-layout"><section class="panel depth-layer">${checkoutStep===1?renterForm():paymentForm()}</section><aside class="panel reservation depth-layer"><h3 class="formtitle">Your selection</h3>${cart.map(i=>`<div style="display:flex;gap:12px;margin-bottom:18px"><img src="${getCarMainPhoto(carBy(i.carId))}" style="width:65px;height:49px;border-radius:5px;object-fit:cover" alt=""><div><b class="small">${esc(carBy(i.carId).name)}</b><p class="file-note" style="margin:4px 0">${duration(i.start,i.end)} days · ${i.service}</p></div></div>`).join('')}${priceLines(totals())}</aside></div><div style="height:60px"></div></div>`}
function renterForm(){return `<h2 class="formtitle">Renter details — functional</h2><div class="notice">All fields functional. CNIC ban check active. Refundable deposit only for Self-drive. With driver: only car rent + driver rent. Home delivery +PKR charge.</div><form id="renter-form" onsubmit="reviewCheckout(event)"><div class="formgrid"><div class="field"><label>Full legal name</label><input name="name" required value="${esc(checkoutInfo.name||account.name)}" minlength="2"></div><div class="field"><label>Phone number</label><input name="phone" type="tel" required value="${esc(checkoutInfo.phone||account.phone)}"></div><div class="field"><label>Email address</label><input type="email" required name="email" value="${esc(account.email)}"></div><div class="field"><label>Date of birth (18+)</label><input type="date" name="dob" required max="2008-09-19" value="${esc(checkoutInfo.dob||'')}"></div><div class="field"><label>Identity document</label><select name="idType" onchange="updateID(this.value)"><option>CNIC</option><option>Passport</option></select></div><div class="field"><label id="identity-label">CNIC number · 13 digits</label><input id="identity-number" name="identity" required pattern="[0-9]{13}" placeholder="0000000000000"></div><div class="field wide"><label>Current address</label><input name="address" required minlength="8" value="${esc(checkoutInfo.address||'')}"></div><div class="field"><label>Emergency contact name</label><input name="emergencyName" required minlength="2" value="${esc(checkoutInfo.emergencyName||'')}"></div><div class="field"><label>Emergency contact phone</label><input name="emergencyPhone" type="tel" required value="${esc(checkoutInfo.emergencyPhone||'')}"></div><div class="field wide"><label>Trip purpose / Destination</label><input name="destination" required value="${esc(checkoutInfo.destination||'')}"></div><div class="field wide"><label>Pick-up mode</label><select name="pickupMode" onchange="toggleHomeDelivery(this.value)"><option value="Office pickup">Office pickup — ${esc(config.officeAddress)}</option><option value="Home delivery">Home delivery — +${money(config.homeDeliveryCharge)} car at your home</option></select></div><div class="field wide" id="home-delivery-field" style="display:none"><label>Home delivery full address</label><input name="homeAddress" placeholder="House #, Street, Area, City" value="${esc(checkoutInfo.homeAddress||'')}"></div></div>${cart.map((i,n)=>i.service==='Self-drive'?`<div class="detail-section" style="margin-top:25px"><h3 style="font-size:17px">Driver ${n+1} · ${esc(carBy(i.carId).name)}</h3><div class="formgrid"><div class="field"><label>Driver full name</label><input name="driverName_${n}" required></div><div class="field"><label>Driver DOB · 25+</label><input name="driverDOB_${n}" type="date" required max="2001-09-19"></div><div class="field"><label>Licence number</label><input name="license_${n}" required></div><div class="field"><label>Licence expiry</label><input name="expiry_${n}" type="date" required min="${i.end}"></div></div></div>`:'').join('')}<label class="check"><input type="checkbox" required name="terms"><span>I accept rental & cancellation terms.</span></label><div class="button-row"><button class="btn" type="submit">Review & pay ↗</button></div></form>`}
function toggleHomeDelivery(v){const el=document.getElementById('home-delivery-field'); if(el) el.style.display=v==='Home delivery'?'block':'none';}
function updateID(t){let el=document.getElementById('identity-number');el.pattern=t==='CNIC'?'[0-9]{13}':'[A-Za-z0-9]{6,12}';document.getElementById('identity-label').textContent=t==='CNIC'?'CNIC number · 13 digits':'Passport number'}
function reviewCheckout(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));checkoutInfo=f;checkoutStep=2;render();scrollTo(0,0)}
function paymentForm(){return `<h2 class="formtitle">Payment — with company details</h2><p class="small muted">${esc(checkoutInfo.name)} · ${cart.length} vehicles · ${esc(checkoutInfo.pickupMode||'Office pickup')}</p><button class="text-btn" onclick="checkoutStep=1;render()">← Edit details</button><form onsubmit="submitOrder(event)" style="margin-top:18px"><div class="payment-options">${[['Cash on pickup','Pay at '+esc(config.officeAddress),'◈'],['JazzCash','Send to '+esc(config.jazzCashNumber)+' — APEX Rentals','J'],['easypaisa','Send to '+esc(config.easypaisaNumber)+' — APEX Rentals','e'],['Raast / bank transfer','Bank: '+esc(config.bankAccount)+' | IBAN: '+esc(config.bankIBAN)+' | Raast: '+esc(config.raastId),'↗'],['Visa / Mastercard','Secure card — 2.5% fee','▣']].map(([p,s,ic])=>`<label class="pay"><input type="radio" name="payment" value="${p}" ${payment===p?'checked':''} onchange="choosePayment(this.value)"><span><b>${ic}  ${p}</b><small>${s}</small></span></label>`).join('')}</div><div class="notice" id="payment-note" style="background:#fffbe6;border-color:#f5e6a0;color:#7a5a00"></div><div class="panel" style="margin:16px 0;background:var(--bg-2)"><small style="font-weight:700">OFFICE ADDRESS FOR PICKUP</small><p style="font-size:11px;margin:8px 0 0;color:var(--text)">${esc(config.officeAddress)}<br>Phone: ${esc(config.companyPhone)}<br>${checkoutInfo.pickupMode==='Home delivery'?'<b>Home delivery: +'+money(config.homeDeliveryCharge)+'</b><br>Address: '+esc(checkoutInfo.homeAddress||''):''}</p></div><label class="check"><input required type="checkbox"><span>I confirm ${money(totals().rental)} rental ${totals().deposit?'+ '+money(totals().deposit)+' refundable (self-drive only)':''} ${checkoutInfo.pickupMode==='Home delivery'?'+ '+money(config.homeDeliveryCharge)+' home delivery':''} — All money to admin wallet first.</span></label><button class="btn full">Confirm booking & Pay to Admin Wallet ↗</button></form>`}
function choosePayment(v){payment=v;paymentNote()}
function paymentNote(){const el=document.getElementById('payment-note');if(!el) return; if(payment==='Cash on pickup'){el.innerHTML=`Cash on pickup: Pay at office<br><b>${esc(config.officeAddress)}</b><br>Admin will manually add to wallet for revenue.`}else if(payment==='JazzCash'){el.innerHTML=`JazzCash: Send <b>${money(totals().total)}</b> to <b>${esc(config.jazzCashNumber)}</b> (APEX Rentals)<br>After payment, share TID in chat. Money goes to admin wallet.`}else if(payment==='easypaisa'){el.innerHTML=`Easypaisa: Send <b>${money(totals().total)}</b> to <b>${esc(config.easypaisaNumber)}</b><br>Money to admin wallet first, then 90% to owner.`}else if(payment.includes('Raast')||payment.includes('bank')){el.innerHTML=`Bank: <b>${esc(config.bankAccount)}</b><br>IBAN: <b>${esc(config.bankIBAN)}</b><br>Raast: <b>${esc(config.raastId)}</b><br>Amount ${money(totals().total)} to admin wallet.`}else{el.innerHTML=`${esc(payment)}: Secure checkout<br>Amount ${money(totals().total)} to admin wallet. Owner gets 90% after ride complete, 10% company.`}}
function submitOrder(e){e.preventDefault();payment=new FormData(e.target).get('payment')||payment;const id='VR-'+Date.now().toString().slice(-7);if(bannedCNICs.includes(checkoutInfo.identity)){toast('This CNIC is banned from services'); return;} let homeCharge=checkoutInfo.pickupMode==='Home delivery'?config.homeDeliveryCharge:0; let baseTotals=totals(); let finalTotals={...baseTotals, homeDelivery:homeCharge, total:baseTotals.total+homeCharge}; let o={id,userId:account.id,name:checkoutInfo.name,email:account.email,phone:checkoutInfo.phone,destination:checkoutInfo.destination,pickupMode:checkoutInfo.pickupMode||'Office pickup',homeAddress:checkoutInfo.homeAddress||'',officeAddress:config.officeAddress,identityType:checkoutInfo.idType,identity:checkoutInfo.identity,identityMasked:checkoutInfo.idType==='CNIC'?'xxxxx-xxxxxxx-x':'xxxxxxxx',identityStatus:'Verified',items:cart.map((i,n)=>({...i,price:quote(i),assignedDriver:null,selfDriver:i.service==='Self-drive'?{name:checkoutInfo['driverName_'+n],license:checkoutInfo['license_'+n],status:'Verified'}:null})),totals:finalTotals,payment,status:'Confirmed',paid:0,depositPaid:0,created:new Date().toISOString(),cancellationFee:0,ownerPayoutDone:false};orders.unshift(o);cart=[]; if(payment!=='Cash on pickup'){const payAmount=finalTotals.rental+homeCharge+(finalTotals.deposit||0);adminWallet+=payAmount;apexRecordPayment(payAmount,'payment',id+' · '+payment);} persist();addNotification(account.id,'Booking confirmed','Your booking '+id+' for '+o.items.length+' vehicle(s) is confirmed. Total '+money(finalTotals.total)+' — Pickup: '+o.pickupMode,'account');addNotification('admin','New booking '+id+' — '+money(finalTotals.total)+' to admin wallet',account.username+' booked '+o.items.length+' vehicle(s) via '+payment+' — '+o.pickupMode,null,'Reservations');go('success/'+id);}
function successPage(id){const o=orders.find(o=>o.id===id&&o.userId===account?.id);if(!o)return empty('Reservation not found.','Check My journeys.','My journeys','account');return `<div class="wrap" style="max-width:800px"><div class="empty" style="padding-bottom:20px"><div class="success-symbol">✓</div><div class="eyebrow">BOOKING CONFIRMED</div><h2>Your APEX journey is confirmed!</h2><p>Reservation ${o.id} · ${o.items.length} vehicle(s) · Total ${money(o.totals.total)}</p></div><div class="panel depth-layer">${orderItems(o)}${priceLines(o.totals)}<div class="button-row"><button class="btn ghost" onclick="go('fleet')">Explore more</button><button class="btn" onclick="go('account')">My journeys ↗</button></div></div></div>`}
function orderItems(o){return o.items.map(i=>`<div class="cart-item"><img src="${photo(carBy(i.carId).image)}" alt=""><div><h3>${esc(carBy(i.carId).name)}</h3><p>${date(i.start)} — ${date(i.end)}</p><p>${esc(i.service)} · ${esc(i.city)}</p>${i.service==='With driver'?`<p>${i.assignedDriver?'Chauffeur: '+esc(drivers.find(d=>d.id===i.assignedDriver)?.name||'Assigned'):'Chauffeur pending'}</p>`:''}</div><strong class="small">${money(i.price.rental)}</strong></div>`).join('')}
function accountPage(){
  if(!account)return `<div class="wrap empty"><h2>Your APEX account</h2><p class="muted">Sign in to see your bookings.</p><button class="btn" onclick="auth(false)">Sign in ↗</button></div>`;
  const mine=orders.filter(o=>o.userId===account.id),apps=applications.filter(a=>a.userId===account.id),mineNotifs=notifications.filter(n=>n.userId===account.id).slice(0,6);
  return `<div class="wrap"><div class="page-top"><div class="eyebrow">YOUR APEX SPACE · @${esc(account.username)}</div><div class="section-head"><div><h1>Hello, ${esc(account.name.split(' ')[0])}.</h1><p>${esc(account.email)} · ${esc(account.phone)} · Functional account</p></div><button class="btn ghost" onclick="signout()">Sign out</button></div></div>${mineNotifs.length?`<div class="panel depth-layer" style="margin-bottom:20px"><h3 style="font-size:14px;margin-bottom:12px">Recent notifications</h3>${mineNotifs.map(n=>`<div class="notif-item"><span class="dot" style="background:${n.title.includes('confirmed')?'var(--lime)':'#6b7f5a'}"></span><div><b>${esc(n.title)}</b><p>${esc(n.msg)}</p></div></div>`).join('')}</div>`:''}${mine.length?mine.map(o=>`<div class="panel mybooking depth-layer"><div class="booking-top"><h3>${o.id}</h3><span class="pill">${o.status}</span></div>${orderItems(o)}<div class="line"><span>Total, including deposit</span><strong>${money(o.totals.total)}</strong></div><div class="button-row"><button class="btn ghost" onclick="cancelOrder('${o.id}')">Cancel</button><button class="btn ghost" onclick="receipt('${o.id}')">Receipt ↗</button></div></div>`).join(''):'<div class="panel empty depth-layer"><h2>Your first journey awaits.</h2><button class="btn" onclick="go(\'fleet\')">Explore cars ↗</button></div>'}${apps.length?`<div class="section"><h2>Your vehicle applications</h2>${apps.map(a=>`<div class="panel mybooking depth-layer"><div class="booking-top"><h3>${esc(a.brand+' '+a.model)} · ${a.year}</h3><span class="pill">${a.status}</span></div><p class="small muted">${a.id} · ${esc(a.city)}</p>${a.note?`<div class="notice">${esc(a.note)}</div>`:''}</div>`).join('')}</div>`:''}</div>`;
}
function signout(){account=null;store.removeItem('v2_session');go('home');render();toast('Signed out')}
function cancelOrder(id){const o=orders.find(o=>o.id===id);modal('Cancel booking?',`<p class="small muted">${o.id} will be cancelled.</p><div class="button-row"><button class="btn ghost" onclick="closeModal()">Keep</button><button class="btn" onclick="confirmCancel('${id}')">Cancel</button></div>`)}
function confirmCancel(id){let o=orders.find(o=>o.id===id);const fee=Math.round(o.totals.rental*0.05);o.cancellationFee=fee;o.status='Cancelled';adminWallet+=fee;apexRecordPayment(fee,'cancellation-fee',id); if(o.paid>0){const refund=o.paid-fee; addNotification(o.userId,'Booking cancelled — 5% fee','Your booking '+id+' cancelled. Fee '+money(fee)+' deducted, refund '+(refund>0?money(refund):money(0)),'account');}else{addNotification(o.userId,'Booking cancelled','Your booking '+id+' cancelled. 5% fee '+money(fee)+' applied.','account');} persist();closeModal();render();toast('Cancelled — 5% fee '+money(fee)+' to admin wallet');}
function banCNIC(cnic){if(!cnic) return; if(!bannedCNICs.includes(cnic)){bannedCNICs.push(cnic); persist(); toast('CNIC '+cnic+' banned'); render();}}
function unbanCNIC(cnic){bannedCNICs=bannedCNICs.filter(x=>x!==cnic); persist(); toast('CNIC '+cnic+' unbanned'); render();}
function adminManualBooking(){modal('Manual booking — Admin entry for walk-in customer', `<form onsubmit="submitManualBooking(event)"><div class="formgrid"><div class="field"><label>Customer ID (existing or blank for guest)</label><input name="userId" placeholder="Existing user ID or blank"></div><div class="field"><label>Customer full name</label><input name="name" required></div><div class="field"><label>Phone</label><input name="phone" required></div><div class="field"><label>Email</label><input type="email" name="email" required></div><div class="field"><label>CNIC (13 digits)</label><input name="identity" pattern="[0-9]{13}" required placeholder="0000000000000"></div><div class="field"><label>Car ID</label><select name="carId" required>${fleet.map(c=>`<option value="${c.id}">${c.id} — ${esc(c.name)} — ${money(c.rate)}/day</option>`).join('')}</select></div><div class="field"><label>Start date</label><input type="date" name="start" required min="${TODAY}"></div><div class="field"><label>End date</label><input type="date" name="end" required min="${TODAY}"></div><div class="field"><label>City</label><select name="city">${cities('Lahore')}</select></div><div class="field"><label>Service</label><select name="service"><option>Self-drive</option><option>With driver</option></select></div><div class="field"><label>Pickup mode</label><select name="pickupMode"><option>Office pickup</option><option>Home delivery</option></select></div><div class="field"><label>Payment method</label><select name="payment"><option>Cash on pickup</option><option>JazzCash</option><option>easypaisa</option><option>Raast / bank transfer</option></select></div><div class="field"><label>Paid amount (if cash, admin manually adds to wallet)</label><input type="number" name="paid" value="0"></div></div><button class="btn full" style="margin-top:14px">Create manual booking ↗</button></form>`, true);}
function submitManualBooking(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target)); if(bannedCNICs.includes(f.identity)){toast('CNIC banned — cannot book'); return;} const car=carBy(f.carId); if(!car){toast('Car not found'); return;} if(clash(car.id,f.start,f.end)){toast('Car not available for these dates — already rented'); return;} const item={carId:+f.carId,start:f.start,end:f.end,city:f.city,service:f.service,driverRate:config.driverRate}; const q=quote(item); const homeCharge=f.pickupMode==='Home delivery'?config.homeDeliveryCharge:0; const total={...totals([item]), homeDelivery:homeCharge, total:totals([item]).total+homeCharge}; const id='VR-MANUAL-'+Date.now().toString().slice(-5); const o={id,userId:f.userId||'guest-'+Date.now(),name:f.name,email:f.email,phone:f.phone,destination:f.city,pickupMode:f.pickupMode,homeAddress:'',officeAddress:config.officeAddress,identity:f.identity,identityMasked:'xxxxx',identityStatus:'Verified',items:[{...item,price:q}],totals:total,payment:f.payment,status:'Confirmed',paid:+f.paid||0,depositPaid:0,created:new Date().toISOString(),cancellationFee:0,ownerPayoutDone:false,manual:true}; orders.unshift(o); if(f.payment!=='Cash on pickup' || +f.paid>0){const payAmt=(+f.paid||total.rental+homeCharge);adminWallet+=payAmt;apexRecordPayment(payAmt,'payment',id+' · manual · '+f.payment);} persist(); addNotification(o.userId,'Manual booking created','Admin created booking '+id+' for you','account'); addNotification('admin','Manual booking '+id+' created','For '+f.name+' — '+money(total.total),null,'Reservations'); closeModal(); render(); toast('Manual booking created — '+id);}
function openWithdrawModal(){modal('Withdraw from admin wallet',`<form onsubmit="submitWithdraw(event)"><div class="formgrid"><div class="field wide"><label>Amount (PKR)</label><input name="amount" type="number" min="1" required placeholder="e.g. 10000"></div><div class="field wide"><label>Account type</label><select name="atype"><option>Bank transfer</option><option>JazzCash</option><option>Easypaisa</option></select></div><div class="field wide"><label>Account number / IBAN</label><input name="anumber" required minlength="7" placeholder="e.g. PK36HABB0001... or 0300..."></div><div class="field wide"><label>Account title</label><input name="atitle" required placeholder="Name on the account"></div></div><p class="small muted" style="margin:12px 0">Balance: <b>${money(adminWallet)}</b> — har withdrawal destination account ke sath server ledger me record hoti hai.</p><button class="btn full">Withdraw ↗</button></form>`,true)}
function submitWithdraw(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));withdrawAdmin(+f.amount,f.atype+' '+f.anumber+' — '+f.atitle);}
function withdrawAdmin(amount,account){amount=+amount; if(!amount||amount<=0){toast('Enter a valid amount');return;} if(!account||String(account).trim().length<7){toast('Withdrawal needs a destination account');return;} if(amount>adminWallet){toast('Not enough in wallet — balance '+money(adminWallet)); return;} adminWallet-=amount; persist(); fetch('/api/wallet/withdraw',{method:'POST',headers:apexHeaders(true),body:JSON.stringify({amount,account,note:'Admin withdrawal'})}).catch(()=>{}); closeModal(); render(); toast('Withdrawn '+money(amount)+' to '+account);}
function addCashToWallet(amount){amount=+amount; if(!amount||amount<=0) return; adminWallet+=amount; persist(); fetch('/api/wallet/add-cash',{method:'POST',headers:apexHeaders(true),body:JSON.stringify({amount,note:'Cash added manually'})}).catch(()=>{}); render(); toast('Added '+money(amount)+' cash to admin wallet');}
function viewWalletLedger(){fetch('/api/wallet',{headers:apexHeaders(false)}).then(r=>r.ok?r.json():null).then(w=>{if(!w){toast('Server wallet not reachable');return;}modal('Admin wallet — server ledger',`<div class="info-grid"><div class="info-box"><small>Server balance</small><b>${money(w.balance)}</b></div><div class="info-box"><small>Owner payouts</small><b>${money(Object.values(w.ownerWallets||{}).reduce((a,b)=>a+b,0))}</b></div></div><div class="table-scroll" style="margin-top:12px"><table><thead><tr><th>Type</th><th>Amount</th><th>Account / note</th><th>When</th></tr></thead><tbody>${(w.transactions||[]).map(t=>`<tr><td>${esc(t.type)}</td><td>${money(t.amount)}</td><td>${esc(t.account||t.note||'')}</td><td><small>${t.created?new Date(t.created).toLocaleString():''}</small></td></tr>`).join('')||'<tr><td colspan="4">No ledger entries yet</td></tr>'}</tbody></table></div>`,true);}).catch(()=>toast('Server wallet not reachable'));}
function downloadReceiptImage(id){const o=orders.find(x=>x.id===id); if(!o){toast('Booking not found'); return;} const canvas=document.createElement('canvas'); canvas.width=800; canvas.height=1150; const ctx=canvas.getContext('2d'); ctx.fillStyle='#fbfaf6'; ctx.fillRect(0,0,800,1150); ctx.fillStyle='#ffffff'; ctx.fillRect(20,20,760,1110); ctx.strokeStyle='#eae6dc'; ctx.lineWidth=1; ctx.strokeRect(20,20,760,1110); ctx.fillStyle='#1e2f1a'; ctx.fillRect(20,20,760,80); ctx.fillStyle='#d2ed9e'; ctx.font='bold 28px Arial'; ctx.fillText('APEX',40,60); ctx.fillStyle='#fbfaf6'; ctx.font='12px Arial'; ctx.fillText('DRIVEN BEYOND ORDINARY',120,60); ctx.fillStyle='#ffffff'; ctx.font='bold 14px Arial'; ctx.fillText('RECEIPT',650,60); ctx.fillStyle='#161c14'; ctx.font='bold 18px Arial'; ctx.fillText('Booking: '+o.id,40,130); ctx.font='11px Arial'; ctx.fillStyle='#7e8b75'; ctx.fillText('Created: '+new Date(o.created).toLocaleString()+' | Status: '+o.status+' | Payment: '+o.payment,40,150); ctx.fillStyle='#1e2f1a'; ctx.font='bold 13px Arial'; ctx.fillText('CUSTOMER DETAILS',40,190); ctx.fillStyle='#161c14'; ctx.font='12px Arial'; ctx.fillText('Name: '+o.name,40,210); ctx.fillText('Phone: '+o.phone,40,230); ctx.fillText('Email: '+o.email,40,250); ctx.fillText('CNIC: '+(o.identityMasked||'Verified'),40,270); ctx.fillText('Destination: '+o.destination,40,290); ctx.fillStyle='#1e2f1a'; ctx.font='bold 13px Arial'; ctx.fillText('PICKUP DETAILS',40,330); ctx.fillStyle='#161c14'; ctx.font='12px Arial'; ctx.fillText('Mode: '+o.pickupMode,40,350); ctx.fillText('Office: '+config.officeAddress,40,370); if(o.homeAddress) ctx.fillText('Home Delivery: '+o.homeAddress,40,390); ctx.fillText('Phone: '+config.companyPhone,40,410); ctx.fillStyle='#1e2f1a'; ctx.font='bold 13px Arial'; ctx.fillText('VEHICLES ('+o.items.length+')',40,450); let y=470; o.items.forEach((item,idx)=>{const car=carBy(item.carId); ctx.fillStyle='#161c14'; ctx.font='bold 12px Arial'; ctx.fillText((idx+1)+'. '+(car?car.name:'Car #'+item.carId),40,y); ctx.font='11px Arial'; ctx.fillText(date(item.start)+' -> '+date(item.end)+' | '+item.city+' | '+item.service+' | '+(item.price?money(item.price.rental):''),40,y+18); y+=40;}); y=Math.max(y,620); ctx.fillStyle='#f5f2eb'; ctx.fillRect(40,y,720,170); ctx.fillStyle='#161c14'; ctx.font='12px Arial'; ctx.fillText('Base: '+money(o.totals.base),60,y+20); if(o.totals.saving) ctx.fillText('Saving: -'+money(o.totals.saving),60,y+40); if(o.totals.driver) ctx.fillText('Driver: '+money(o.totals.driver),60,y+60); if(o.totals.homeDelivery) ctx.fillText('Home Delivery: '+money(o.totals.homeDelivery),60,y+80); if(o.totals.deposit) ctx.fillText('Refundable Deposit (self-drive only): '+money(o.totals.deposit),60,y+100); ctx.font='bold 16px Arial'; ctx.fillStyle='#1e2f1a'; ctx.fillText('Total: '+money(o.totals.total),60,y+130); if(o.cancellationFee) ctx.fillText('Cancellation Fee (5%): '+money(o.cancellationFee),60,y+150); ctx.fillStyle='#7e8b75'; ctx.font='10px Arial'; ctx.fillText('Admin Wallet: All payments to admin wallet first, then 90% to owner, 10% company.',40,y+200); ctx.fillText('Office: '+config.officeAddress+' | Phone: '+config.companyPhone,40,y+220); ctx.fillStyle='#e11d48'; ctx.font='bold 11px Arial'; ctx.fillText('Developed by Spidy',40,y+240); ctx.fillText('JazzCash: '+config.jazzCashNumber+' | Easypaisa: '+config.easypaisaNumber+' | Bank: '+config.bankAccount,40,y+260); const url=canvas.toDataURL('image/png'); const a=document.createElement('a'); a.href=url; a.download='APEX-Receipt-'+o.id+'.png'; a.click(); toast('Receipt image downloaded');}
function receipt(id){const o=orders.find(o=>o.id===id);modal('Receipt · '+id+' — Image', `<div class="eyebrow">APEX / ${o.id} — ${esc(o.pickupMode||'Office')}</div><p class="small muted">${esc(o.name)} · ${esc(o.payment)} · ${esc(o.pickupMode||'Office pickup')}<br>Office: ${esc(config.officeAddress)}<br>Home: ${esc(o.homeAddress||'N/A')}</p>${priceLines(o.totals)}<div class="button-row"><button class="btn ghost" onclick="downloadReceipt('${id}')">Text receipt ↓</button><button class="btn" onclick="downloadReceiptImage('${id}')">Image receipt (PNG) ↓</button></div>`, true);}
function downloadReceipt(id){const o=orders.find(o=>o.id===id);download('APEX-'+id+'.txt',`APEX RESERVATION\n${id}\nCustomer: ${o.name}\nPhone: ${o.phone}\nEmail: ${o.email}\nPickup: ${o.pickupMode}\nOffice: ${config.officeAddress}\nHome: ${o.homeAddress||'N/A'}\nStatus: ${o.status}\nPayment: ${o.payment}\nTotal: ${money(o.totals.total)}\nDeposit: ${money(o.totals.deposit)} (self-drive only)\nHome Delivery: ${money(o.totals.homeDelivery||0)}\nJazzCash: ${config.jazzCashNumber}\nEasypaisa: ${config.easypaisaNumber}\nBank: ${config.bankAccount}\nDeveloped by Spidy`,'text/plain')}
function ownerPage(){return `<div class="wrap"><div class="page-top"><a class="back" href="#home">← Back to APEX</a><div class="eyebrow" style="margin-top:28px">PARTNER PROGRAM</div><h1>List your car with APEX.</h1><p>Functional application — admin reviews and approves.</p></div><div class="owner-steps">${[['01','Tell us about your car','Model, year, condition, location.'],['02','Verification','Ownership and documents verified.'],['03','Inspection & agreement','Pricing and terms agreed.'],['04','Live listing','Your car goes live after approval.']].map(p=>`<div class="panel owner-step depth-layer"><b>${p[0]}</b><h3>${p[1]}</h3><p>${p[2]}</p></div>`).join('')}</div><div class="checkout-layout"><div class="panel depth-layer"><h2 class="formtitle">Register your vehicle</h2>${!account?`<p class="small muted">Create account first.</p><button class="btn" onclick="auth(true,'owner')">Create account ↗</button>`:ownerForm()}</div><div><div class="panel depth-layer"><h3>Requirements</h3><div class="checklist" style="grid-template-columns:1fr"><span>You own the car or have authorization</span><span>Valid registration</span><span>Insurance allows rental</span><span>Clear photos</span></div></div></div></div></div>`}
function ownerForm(){return `<form onsubmit="submitOwner(event)"><div class="formgrid"><div class="field"><label>Owner name</label><input name="owner" required value="${esc(account.name)}"></div><div class="field"><label>Phone</label><input name="phone" required value="${esc(account.phone)}"></div><div class="field"><label>Make</label><input name="brand" required></div><div class="field"><label>Model</label><input name="model" required></div><div class="field"><label>Year</label><input name="year" type="number" required min="2016" max="2026" value="2024"></div><div class="field"><label>Registration</label><input name="registration" required></div><div class="field"><label>Owner CNIC (full)</label><input name="cnic" required placeholder="35202-1234567-1" pattern="\d{5}-\d{7}-\d" title="Format: 35202-1234567-1"></div><div class="field"><label>Driving licence No.</label><input name="license" required placeholder="e.g. LHR-123456"></div><div class="field"><label>City</label><select name="city">${cities('Lahore')}</select></div><div class="field"><label>Odometer km</label><input type="number" name="mileage" required></div><div class="field"><label>Condition</label><select name="condition"><option>Excellent</option><option>Very good</option><option>Good</option></select></div><div class="field"><label>Daily rate PKR</label><input name="rate" type="number" required></div><div class="field"><label>Preference</label><select name="preference"><option>With driver only</option><option>Self-drive only</option><option>Either</option></select></div><div class="field"><label>Available from</label><input name="availableFrom" type="date" min="${TODAY}" required></div><div class="field wide"><label>Photos</label><input id="owner-photos" type="file" accept="image/*" multiple required onchange="previewOwnerPhotos(this)"><div id="owner-photo-preview" style="display:flex;gap:8px;flex-wrap:wrap;margin-top:8px"></div></div><div class="field wide"><label>Notes</label><textarea name="notes" rows="3" required></textarea></div><label class="check" style="grid-column:1/-1;margin-top:4px"><input type="checkbox" required><span>I confirm my CNIC, licence and registration are genuine and belong to me. APEX verifies all documents (CNIC + ownership + insurance) before approval.</span></label></div><button class="btn full">Submit for verification ↗</button></form>`}
function previewOwnerPhotos(input){const files=[...input.files];window.ownerUrls?.forEach(u=>URL.revokeObjectURL(u));window.ownerUrls=files.map(f=>URL.createObjectURL(f));document.getElementById('owner-photo-preview').innerHTML=window.ownerUrls.map(u=>`<img src="${u}" style="width:72px;height:56px;object-fit:cover;border-radius:6px">`).join('')}
function submitOwner(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));const cnic=(f.cnic||'').trim();if(!/^\d{5}-\d{7}-\d$/.test(cnic)){toast('CNIC format must be 35202-1234567-1');return;}if(bannedCNICs.includes(cnic)){toast('This CNIC is banned — cannot list a car');return;}if(!(f.license||'').trim()){toast('Driving licence number is required');return;}const app={...f,cnic,id:'VP-'+Date.now().toString().slice(-6),userId:account.id,email:account.email,photoCount:document.getElementById('owner-photos').files.length,status:'Submitted',verification:'Pending',created:new Date().toISOString(),note:''};applications.unshift(app);persist();addNotification(account.id,'Application submitted','Your vehicle '+f.brand+' '+f.model+' is under CNIC & document verification.','account');addNotification('admin','New partner application',account.username+' submitted '+f.brand+' '+f.model+' — CNIC '+cnic+' — verify documents',null,'Partner applications');toast('Application submitted — verification pending');go('account')}
function guidelines(){modal('Rental requirements',`<div class="eyebrow">APEX RENTAL POLICY</div><details open><summary>For every renter</summary><p>Age 18+, CNIC/passport, phone, address, deposit.</p></details><details open><summary>Self-drive</summary><p>Age 25+, 2 years experience, valid licence.</p></details><details><summary>With driver</summary><p>No licence needed, ID required. ${money(config.driverRate)}/day.</p></details>`)}
function terms(){modal('Terms & cancellation',`<details open><summary>Booking & payment</summary><p>Availability and document review required.</p></details><details><summary>Deposit</summary><p>Refundable within 7 days after inspection.</p></details><details><summary>Cancellation</summary><p>Free before confirmation or 48h before pick-up.</p></details><details><summary>Mileage</summary><p>200 km/day, PKR 100/km extra.</p></details>`)}
function contact(){modal('Contact APEX',`<form onsubmit="event.preventDefault();addNotification('admin','New enquiry',this.elements.name.value+': '+this.elements.message.value,null,'Messages');addNotification(account?.id||'all','Enquiry sent','Your enquiry was sent to APEX team.','home');closeModal();toast('Enquiry sent')"><div class="formgrid"><div class="field wide"><label>Your name</label><input name="name" required value="${esc(account?.name||'')}"></div><div class="field wide"><label>Message</label><textarea name="message" rows="4" required></textarea></div></div><button class="btn full" style="margin-top:16px">Send enquiry ↗</button></form>`)}
function modal(title,body,large=false){document.getElementById('overlay').innerHTML=`<div class="modal-bg" onclick="if(event.target===this)closeModal()"><section class="modal ${large?'large':''}" role="dialog" aria-modal="true"><div class="modal-head"><h2>${title}</h2><button class="close" onclick="closeModal()">×</button></div>${body}</section></div>`;document.body.style.overflow='hidden'}
function closeModal(){document.getElementById('overlay').innerHTML='';document.body.style.overflow=''}
function download(name,text,type='text/csv'){const url=URL.createObjectURL(new Blob([text],{type}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
function adminLoginPage(){return `<div class="admin-login-wrap"><div class="panel admin-login depth-layer"><div class="eyebrow">APEX OPERATIONS</div><h1>Admin login</h1><p class="small muted">Username: <b>admin</b> · Password: <b>admin1234</b></p><form onsubmit="submitAdminLogin(event)"><div class="formgrid"><div class="field wide"><label>Username</label><input name="user" required autocomplete="username" value="admin"></div><div class="field wide"><label>Password</label><input name="pass" type="password" required autocomplete="current-password"></div></div><button class="btn full" style="margin-top:14px">Sign in to operations ↗</button></form></div></div>`}
function submitAdminLogin(e){
  e.preventDefault();
  const f=Object.fromEntries(new FormData(e.target));
  if(f.user==='admin'&&f.pass==='admin1234'){
    adminSession={user:'admin',at:new Date().toISOString()};
    adminTab='Payments';
    account={id:'admin',name:'Administrator',username:'admin',email:'admin@apex.local',phone:'03000000000'};
    apexAdminLogin(f.user, f.pass);
    persist();
    render();
    toast('Admin signed in — website + operations accessible');
  }else toast('Invalid credentials. Use admin / admin1234');
}
function adminLogout(){adminSession=null;store.removeItem('v2_adminSession');if(account && account.id==='admin'){account=null;store.removeItem('v2_session');}apexClearToken();persist();render();toast('Admin signed out')}

function adminShell(){
  if(!isAdminAuthenticated()){return `<div class="topline">APEX OPERATIONS · ADMIN ACCESS</div><div class="wrap">${adminLoginPage()}</div>`}
  const active=orders.filter(o=>!['Cancelled','Completed'].includes(o.status)),rentalReceived=orders.reduce((s,o)=>s+o.paid,0),depositHeld=orders.reduce((s,o)=>s+o.depositPaid,0),pending=active.reduce((s,o)=>s+o.totals.rental-o.paid,0);
  const adminNotifs=notifications.filter(n=>n.userId==='admin'||n.userId==='all');
  const unreadAdmin=adminNotifs.filter(n=>!n.read).length;
  const unreadChats=chats.reduce((s,c)=>s+(c.unreadAdmin||0),0);
  return `<div class="topline">APEX OPERATIONS · ${adminNotifs.length} notifications · ${unreadAdmin} unread · ${unreadChats} chats · Single login: website + panel</div><div class="wrap"><header class="admin-top"><a class="logo" href="#Overview"><span class="apex-mark"><svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 2 L22 22 H2 Z" fill="currentColor"/></svg></span> APEX <sup>OPERATIONS</sup></a><div style="display:flex;align-items:center;gap:12px"><a href="index.html" class="btn ghost" style="padding:8px 14px">View Website ↗</a><button id="notif-btn" class="notif-btn ${unreadAdmin?'has-unread':''}" onclick="toggleNotif()"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M6 9a6 6 0 0 1 12 0c0 7 6 7 6 11H0s6-4 6-11"/><path d="M9 21a3 3 0 0 0 6 0"/></svg></button><span class="pill">Admin · ${esc(adminSession.user)}</span><button class="btn ghost" onclick="adminLogout()">Logout</button><button class="btn ghost" onclick="exportOrders()">Export ↓</button></div></header><div id="notif-dropdown" class="notif-dropdown ${notifOpen?'open':''}" style="position:absolute;right:48px;top:92px"><div style="display:flex;justify-content:space-between;align-items:center;padding:8px 6px 12px;border-bottom:1px solid var(--line)"><b style="font-size:12px">Admin notifications · ${adminNotifs.length}</b><button class="text-btn" onclick="clearNotifs()">Clear</button></div><div style="max-height:320px;overflow:auto">${adminNotifs.length?adminNotifs.slice(0,15).map(n=>`<div class="notif-item ${!n.read?'unread':''}" style="cursor:pointer" onclick="openNotif('${n.id}')" title="Click to open"><span class="dot"></span><div><b>${esc(n.title)}</b><p>${esc(n.msg)}<br><small>${new Date(n.time).toLocaleString()}</small></p></div></div>`).join(''):'<p class="small muted" style="padding:20px;text-align:center">No notifications</p>'}</div></div><div class="page-top" style="padding-top:15px"><div class="eyebrow">FULL EDIT ACCESS · APEX — LIGHT THEME</div><h1>${adminTab==='Overview'?'Your business, at a glance.':adminTab}</h1><p>Admin can edit every detail, photos, chats, history — graphs included.</p></div><nav class="admin-tabs">${['Overview','Reservations','Customers','Fleet','Drivers','Messages','History','Payments','Partner applications','Settings'].map(t=>`<button class="chip ${adminTab===t?'selected':''}" onclick="adminTab='${t}';adminQuery='';render()">${t}${t==='Partner applications'&&applications.length?' · '+applications.length:''}${t==='Messages'&&unreadChats?' · '+unreadChats+' new':''}</button>`).join('')}</nav>${adminTab==='Overview'?adminOverview():adminTab==='Reservations'?`<div class="section-head"><div><h2>Reservations — Manual entry + 5% cancel fee</h2><p class="small muted">Clash check prevents double booking. All payments to admin wallet.</p></div><button class="btn" onclick="adminManualBooking()">+ Manual Booking (Walk-in)</button></div><div class="filters"><input style="max-width:400px" placeholder="Search reservation" value="${esc(adminQuery)}" oninput="adminSearch(this.value)"></div><div id="admin-bookings">${adminBookings(orders.filter(o=>(o.id+' '+o.name+' '+o.email).toLowerCase().includes(adminQuery.toLowerCase())))}</div>`:adminTab==='Customers'?adminCustomers():adminTab==='Fleet'?adminFleet():adminTab==='Drivers'?adminDrivers():adminTab==='Messages'?adminChatList():adminTab==='History'?adminHistory():adminTab==='Payments'?adminPayments():adminTab==='Partner applications'?adminApplications():adminSettings()}<div style="height:45px"></div></div>`;
}

function adminSearch(v){adminQuery=v;document.getElementById('admin-bookings').innerHTML=adminBookings(orders.filter(o=>(o.id+' '+o.name+' '+o.email).toLowerCase().includes(v.toLowerCase())))}
function adminBookings(list){return `<div class="panel table-scroll depth-layer"><table><thead><tr><th>Reservation</th><th>Customer</th><th>Vehicles</th><th>Rental</th><th>Status</th><th>Payment</th><th></th></tr></thead><tbody>${list.map(o=>`<tr><td>${o.id}<small>${new Date(o.created).toLocaleDateString('en-GB')}</small></td><td>${esc(o.name)}<small>${esc(o.email)}</small></td><td>${o.items.length} vehicle(s)<small>${date(o.items[0].start)} — ${date(o.items[0].end)}</small></td><td>${money(o.totals.rental)}<small>Deposit ${money(o.totals.deposit)}</small></td><td><span class="pill">${o.status}</span></td><td>${o.paid>=o.totals.rental?'Paid':'Pending'}<small>${esc(o.payment)}</small></td><td><button class="btn dark" onclick="manageOrder('${o.id}')">Manage ↗</button></td></tr>`).join('')}</tbody></table>${!list.length?'<div class="empty"><h3>No reservations yet.</h3><button class="btn ghost" onclick="seedDemo()">Load sample records</button></div>':''}</div>`}
function driverFree(d,start,end,exclude='',itemIndex=-1){return d.active&&!orders.some(o=>!['Cancelled','Completed'].includes(o.status)&&o.items.some((i,n)=>!(o.id===exclude&&n===itemIndex)&&i.assignedDriver===d.id&&start<i.end&&end>i.start))}
function manageOrder(id){const o=orders.find(o=>o.id===id);modal('Reservation '+id,`<span class="pill">${o.status}</span><div class="info-grid">${[['Renter',o.name],['Phone',o.phone],['Email',o.email],['Verification',o.identityStatus],['Destination',o.destination]].map(([k,v])=>`<div class="info-box"><small>${k}</small><b>${esc(v)}</b></div>`).join('')}</div>${o.items.map((i,n)=>`<div class="panel" style="margin-bottom:15px"><h3 style="font-size:17px">${esc(carBy(i.carId).name)}</h3><p class="small muted">${date(i.start)} — ${date(i.end)} · ${esc(i.city)} · ${esc(i.service)}</p>${i.service==='With driver'?`<label>Assign chauffeur</label><select onchange="assignDriver('${id}',${n},this.value)"><option value="">Not assigned</option>${drivers.map(d=>{const free=driverFree(d,i.start,i.end,id,n);return `<option value="${d.id}" ${i.assignedDriver===d.id?'selected':''} ${!free?'disabled':''}>${esc(d.name)} · ${d.city} · ${free?'Available':'Busy'}</option>`}).join('')}</select>`:''}</div>`).join('')}${priceLines(o.totals)}<div class="button-row">${o.status==='Confirmed'?`<button class="btn" onclick="startOrder('${id}')">Start rental</button>`:''}${o.status==='Active'?`<button class="btn" onclick="completeOrder('${id}')">Complete rental</button>`:''}<button class="btn ghost" onclick="cancelOrder('${id}')">Cancel</button></div>`,true)}
function assignDriver(id,n,value){const o=orders.find(o=>o.id===id),i=o.items[n];i.assignedDriver=value?+value:null;persist();render();modal('Driver assigned','<p class="small muted">Chauffeur assigned to vehicle.</p><div class="button-row"><button class="btn" onclick="closeModal();manageOrder(\''+id+'\')">Back to order</button></div>');addNotification(o.userId,'Chauffeur assigned','Driver assigned to your booking '+id,'account');toast('Driver assigned')}
function startOrder(id){const o=orders.find(o=>o.id===id);o.status='Active';persist();addNotification(o.userId,'Rental started','Your rental '+id+' is now active.','account');closeModal();render();toast('Rental started')}
function completeOrder(id){const o=orders.find(o=>o.id===id);o.status='Completed'; let totalOwnerPayout=0; o.items.forEach(item=>{const car=carBy(item.carId); if(car && car.ownerId){const rental=item.price?item.price.rental:0; const ownerShare=Math.round(rental*0.90); const companyShare=rental-ownerShare; totalOwnerPayout+=ownerShare; if(!ownerWallets[car.ownerId]) ownerWallets[car.ownerId]=0; ownerWallets[car.ownerId]+=ownerShare; adminWallet-=ownerShare; addNotification(car.ownerId,'Ride completed — payout','Your car '+car.name+' ride '+id+' completed. You received '+money(ownerShare)+' (90%), company kept '+money(companyShare)+' (10%)','account');}}); if(totalOwnerPayout>0){addNotification('admin','Owner payout done — '+money(totalOwnerPayout),'For booking '+id+' distributed to owners',null,'Payments');} if(o.totals.deposit>0 && o.items.some(i=>i.service==='Self-drive')){addNotification(o.userId,'Deposit refund','Your refundable deposit '+money(o.totals.deposit)+' will be refunded within 7 days after inspection','account');} o.ownerPayoutDone=true; persist();closeModal();render();toast('Completed — '+(totalOwnerPayout?money(totalOwnerPayout)+' to owners, 10% company':'Company revenue'));}
function adminCustomers(){const map=new Map();orders.forEach(o=>{if(!map.has(o.userId))map.set(o.userId,{id:o.userId,name:o.name,email:o.email,phone:o.phone,username:o.name,cnic:o.identity||''})});read('users',[]).forEach(u=>map.set(u.id,u));return `<div class="panel table-scroll depth-layer"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:10px"><h3>Customers + Ban CNIC + Owner Earnings</h3><div style="display:flex;gap:8px"><input id="ban-input" placeholder="Enter CNIC 13 digits to ban" style="width:200px"><button class="btn dark" onclick="banCNIC(document.getElementById('ban-input').value.trim())">Ban CNIC</button></div></div><table><thead><tr><th>Customer</th><th>Contact</th><th>Bookings</th><th>CNIC / Earnings</th><th>Ban</th></tr></thead><tbody>${[...map.values()].map(u=>{const os=orders.filter(o=>o.userId===u.id);return `<tr><td>${esc(u.name)}<small>${u.id}</small></td><td>${esc(u.email)}<small>${esc(u.phone||'')}</small></td><td>${os.length}</td><td>${esc(u.cnic||u.identity||'')}<br><small>${ownerWallets[u.id]?money(ownerWallets[u.id])+' earnings':''}</small></td><td>${bannedCNICs.includes(u.cnic||u.identity)?`<button class="btn ghost" onclick="unbanCNIC('${esc(u.cnic||u.identity)}')">Unban</button>`:`<button class="btn ghost" onclick="banCNIC('${esc(u.cnic||u.identity||'')}')">Ban</button>`}</td></tr>`}).join('')}</tbody></table><div style="margin-top:16px"><h3>Banned CNICs (${bannedCNICs.length})</h3><p class="small muted">${bannedCNICs.map(c=>`<span class="pill" style="background:#fef1f0;color:#9e3a2d">${esc(c)} <button onclick="unbanCNIC('${esc(c)}')">×</button></span>`).join(' ')||'None'}</p></div></div>`}
function adminFleet(){return `<div class="section-head"><div><h2 style="font-size:23px">Fleet — edit images & all details</h2><p>Click Edit to upload new photos — stored locally, fully functional.</p></div><button class="btn" onclick="addFleet()">+ Add vehicle</button></div><div class="cars">${fleet.map(c=>`<div class="car depth-layer"><div class="car-photo"><img src="${getCarMainPhoto(c)}" alt="${esc(c.name)}"><span class="pill">${c.status} · ${c.origin}</span></div><div class="car-body"><h3>${esc(c.name)}</h3><p class="small muted" style="margin-top:8px">${c.year} · ${c.brand} · ${money(c.rate)}/day</p><div class="button-row" style="justify-content:flex-start;margin-top:12px"><button class="btn dark" onclick="editFleet(${c.id})">Edit + photos ↗</button><button class="btn ghost" onclick="deleteFleet(${c.id})">Delete</button></div></div></div>`).join('')}</div>`}


function editFleet(id){const c=carBy(id);window.fleetEditTemp={};const allImages=['porsche','mercedes','bmw','pk-alto','pk-cultus','pk-swift','pk-city','pk-civic','pk-corolla','pk-yaris','pk-brv','pk-fortuner','pk-sportage'];modal('Edit · '+esc(c.name)+' — photos & details',`<form onsubmit="saveFleetFull(event,${id})"><div class="formgrid"><div class="field"><label>Name</label><input name="name" required value="${esc(c.name)}"></div><div class="field"><label>Brand</label><input name="brand" required value="${esc(c.brand)}"></div><div class="field"><label>Category</label><select name="category"><option ${c.category==='Hatchback'?'selected':''}>Hatchback</option><option ${c.category==='Sedan'?'selected':''}>Sedan</option><option ${c.category==='SUV'?'selected':''}>SUV</option><option ${c.category==='Grand touring'?'selected':''}>Grand touring</option><option ${c.category==='Sports'?'selected':''}>Sports</option><option ${c.category==='Executive'?'selected':''}>Executive</option></select></div><div class="field"><label>Origin</label><select name="origin"><option ${c.origin==='Pakistan'?'selected':''}>Pakistan</option><option ${c.origin==='Premium'?'selected':''}>Premium</option></select></div><div class="field"><label>Image key (fallback)</label><select name="image">${allImages.map(k=>`<option ${k===c.image?'selected':''}>${k}</option>`).join('')}</select></div><div class="field"><label>Year</label><input name="year" type="number" required value="${c.year}"></div><div class="field"><label>Rate PKR/day</label><input name="rate" type="number" required value="${c.rate}"></div><div class="field"><label>Deposit PKR</label><input name="deposit" type="number" required value="${c.deposit}"></div><div class="field"><label>Seats</label><input name="seats" type="number" required value="${c.seats}"></div><div class="field"><label>Engine</label><input name="engine" required value="${esc(c.engine)}"></div><div class="field"><label>Power</label><input name="power" required value="${esc(c.power)}"></div><div class="field"><label>Fuel</label><select name="fuel"><option ${c.fuel==='Petrol'?'selected':''}>Petrol</option><option ${c.fuel==='Diesel'?'selected':''}>Diesel</option><option ${c.fuel==='Hybrid'?'selected':''}>Hybrid</option></select></div><div class="field"><label>Color</label><input name="color" required value="${esc(c.color)}"></div><div class="field"><label>Plate</label><input name="plate" required value="${esc(c.plate)}"></div><div class="field"><label>Condition</label><select name="condition"><option ${c.condition==='Excellent'?'selected':''}>Excellent</option><option ${c.condition==='Very good'?'selected':''}>Very good</option><option ${c.condition==='Good'?'selected':''}>Good</option></select></div><div class="field"><label>Status</label><select name="status"><option ${c.status==='Active'?'selected':''}>Active</option><option ${c.status==='Maintenance'?'selected':''}>Maintenance</option><option ${c.status==='Hidden'?'selected':''}>Hidden</option></select></div><div class="field wide"><label>Features (comma)</label><textarea name="features" rows="3" required>${esc(c.features.join(', '))}</textarea></div><div class="field wide"><label>Market note</label><textarea name="marketNote" rows="2">${esc(c.marketNote||'')}</textarea></div><div class="field wide"><div class="eyebrow">PHOTO EDIT — UPLOAD NEW IMAGES (optional)</div></div><div class="field wide"><label>Main photo — exterior</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'main')"><div id="fleet-main-preview" class="fleet-edit-preview">${c.customImage?`<img src="${c.customImage}" alt="">`:''}</div></div></div><div class="field"><label>Interior photo</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'interior')"><div id="fleet-interior-preview" class="fleet-edit-preview">${c.customImages?.[c.image+'-interior']?`<img src="${c.customImages[c.image+'-interior']}" alt="">`:''}</div></div></div><div class="field"><label>Detail / Engine photo</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'detail')"><div id="fleet-detail-preview" class="fleet-edit-preview">${(c.customImages?.[c.image+'-detail']||c.customImages?.[c.image+'-engine'])?`<img src="${c.customImages[c.image+'-detail']||c.customImages[c.image+'-engine']}" alt="">`:''}</div></div></div></div><button class="btn full" style="margin-top:18px">Save with new photos ↗</button></form>`,true)}
function saveFleetFull(e,id){e.preventDefault();const f=Object.fromEntries(new FormData(e.target)),c=carBy(id);Object.assign(c,{name:f.name.trim(),brand:f.brand.trim(),category:f.category,origin:f.origin,image:f.image,year:+f.year,rate:+f.rate,deposit:+f.deposit,seats:+f.seats,engine:f.engine.trim(),power:f.power.trim(),fuel:f.fuel,km:c.km,color:f.color.trim(),plate:f.plate.trim(),condition:f.condition,status:f.status,features:f.features.split(',').map(s=>s.trim()).filter(Boolean),marketNote:f.marketNote.trim()});if(window.fleetEditTemp.main) c.customImage=window.fleetEditTemp.main;if(!c.customImages) c.customImages={};if(window.fleetEditTemp.interior) c.customImages[c.image+'-interior']=window.fleetEditTemp.interior;if(window.fleetEditTemp.detail){c.customImages[c.image+'-detail']=window.fleetEditTemp.detail;c.customImages[c.image+'-engine']=window.fleetEditTemp.detail;}persist();closeModal();render();toast('Vehicle & photos updated');}
function addFleet(){const allImages=['porsche','mercedes','bmw','pk-alto','pk-cultus','pk-swift','pk-city','pk-civic','pk-corolla','pk-yaris','pk-brv','pk-fortuner','pk-sportage','custom'];window.fleetEditTemp={};modal('Add new vehicle + 3 photos — any company',`<form onsubmit="saveNewFleet(event)"><div class="formgrid"><div class="field"><label>Name (any company)</label><input name="name" required placeholder="e.g. Toyota Hilux, Honda Accord, Any Brand"></div><div class="field"><label>Brand (any company)</label><input name="brand" required placeholder="e.g. Toyota, Honda, Suzuki, Any"></div><div class="field"><label>Category</label><select name="category"><option>Hatchback</option><option>Sedan</option><option>SUV</option><option>Grand touring</option><option>Sports</option><option>Executive</option><option>Pickup</option><option>Van</option></select></div><div class="field"><label>Origin</label><select name="origin"><option>Pakistan</option><option>Premium</option></select></div><div class="field"><label>Image key fallback</label><select name="image">${allImages.map(k=>`<option>${k}</option>`).join('')}</select></div><div class="field"><label>Year</label><input name="year" type="number" required value="2024"></div><div class="field"><label>Rate PKR/day</label><input name="rate" type="number" required value="6000"></div><div class="field"><label>Deposit (self-drive only, 0 for with driver)</label><input name="deposit" type="number" required value="30000"></div><div class="field"><label>Seats</label><input name="seats" type="number" required value="5"></div><div class="field"><label>Engine</label><input name="engine" required value="1200cc"></div><div class="field"><label>Power</label><input name="power" required value="80 hp"></div><div class="field"><label>Fuel</label><select name="fuel"><option>Petrol</option><option>Diesel</option><option>Hybrid</option><option>Electric</option></select></div><div class="field"><label>Color</label><input name="color" required value="White"></div><div class="field"><label>Plate</label><input name="plate" required value="LHR-xxx000"></div><div class="field wide"><label>Main photo — exterior (required)</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'main')"><div id="fleet-main-preview" class="fleet-edit-preview"></div></div></div><div class="field"><label>Interior photo (2nd image)</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'interior')"><div id="fleet-interior-preview" class="fleet-edit-preview"></div></div></div><div class="field"><label>Detail/Engine photo (3rd image)</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'detail')"><div id="fleet-detail-preview" class="fleet-edit-preview"></div></div></div></div><button class="btn full" style="margin-top:14px">Add vehicle with 3 photos — any company</button></form>`,true)}
function saveNewFleet(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));const newId=Math.max(...fleet.map(c=>c.id))+1;const newCar={id:newId,name:f.name.trim(),brand:f.brand.trim(),category:f.category,origin:f.origin,image:f.image,year:+f.year,rate:+f.rate,deposit:+f.deposit,seats:+f.seats,engine:f.engine.trim(),power:f.power.trim(),fuel:f.fuel,km:'0 km',color:f.color.trim(),plate:f.plate.trim(),condition:'Excellent',status:'Active',features:['Automatic','3 photos'],marketNote:'Any company — 3 images: main/interior/detail',customImage:window.fleetEditTemp.main||null,customImages:{interior:window.fleetEditTemp.interior||null,detail:window.fleetEditTemp.detail||null}};fleet.push(newCar);persist();closeModal();render();toast('Vehicle added — '+f.brand+' with 3 photos');}

function deleteFleet(id){if(orders.some(o=>!['Cancelled','Completed'].includes(o.status)&&o.items.some(i=>i.carId===id)))return toast('Cannot delete — open bookings exist.');modal('Delete vehicle?','<p class="small muted">Remove this vehicle from fleet?</p><div class="button-row"><button class="btn ghost" onclick="closeModal()">Keep</button><button class="btn" onclick="confirmDeleteFleet('+id+')">Delete</button></div>')}
function confirmDeleteFleet(id){fleet=fleet.filter(c=>c.id!==+id);persist();closeModal();render();toast('Deleted')}
function adminDrivers(){return `<div class="section-head"><div><h2 style="font-size:23px">Drivers — full edit</h2><p>Functional driver management.</p></div><button class="btn" onclick="driverForm()">+ Add driver</button></div><div class="driver-grid">${drivers.map(d=>`<div class="panel driver-card depth-layer"><div class="driver-avatar">${initials(d.name)}</div><h3>${esc(d.name)}</h3><p>${esc(d.city)} · ${d.experience}y · ${esc(d.phone)}<br>Licence: ${esc(d.license)}</p><div class="button-row" style="justify-content:flex-start;margin-top:10px"><button class="btn ghost" onclick="editDriver(${d.id})">Edit</button><button class="btn ghost" onclick="toggleDriver(${d.id})">${d.active?'Off':'On'}</button><button class="btn ghost" onclick="deleteDriver(${d.id})">Delete</button></div></div>`).join('')}</div>`}
function toggleDriver(id){const d=drivers.find(d=>d.id===id);d.active=!d.active;persist();render();toast('Driver updated')}
function driverForm(){modal('Add driver',`<form onsubmit="saveDriver(event)"><div class="formgrid"><div class="field wide"><label>Full name</label><input name="name" required></div><div class="field"><label>Phone</label><input name="phone" required></div><div class="field"><label>City</label><select name="city">${cities('Lahore')}</select></div><div class="field"><label>Experience years</label><input name="experience" type="number" required></div><div class="field wide"><label>Licence</label><input name="license" required></div></div><button class="btn full" style="margin-top:12px">Add driver</button></form>`)}
function editDriver(id){const d=drivers.find(d=>d.id===id);modal('Edit driver',`<form onsubmit="saveDriverEdit(event,${id})"><div class="formgrid"><div class="field wide"><label>Name</label><input name="name" required value="${esc(d.name)}"></div><div class="field"><label>Phone</label><input name="phone" required value="${esc(d.phone)}"></div><div class="field"><label>City</label><select name="city">${cities(d.city)}</select></div><div class="field"><label>Experience</label><input name="experience" type="number" required value="${d.experience}"></div><div class="field wide"><label>Licence</label><input name="license" required value="${esc(d.license)}"></div></div><button class="btn full" style="margin-top:12px">Save driver</button></form>`)}
function saveDriver(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));drivers.push({id:Date.now(),name:f.name,phone:f.phone,city:f.city,experience:+f.experience,license:f.license,active:true});persist();closeModal();render();toast('Driver added')}
function saveDriverEdit(e,id){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));Object.assign(drivers.find(d=>d.id===id),{name:f.name,phone:f.phone,city:f.city,experience:+f.experience,license:f.license});persist();closeModal();render();toast('Driver updated')}
function deleteDriver(id){modal('Delete driver?','<p class="small muted">Remove driver?</p><div class="button-row"><button class="btn ghost" onclick="closeModal()">Keep</button><button class="btn" onclick="confirmDeleteDriver('+id+')">Delete</button></div>')}
function confirmDeleteDriver(id){drivers=drivers.filter(d=>d.id!==+id);persist();closeModal();render();toast('Driver deleted')}
function adminPayments(){return `<div class="panel depth-layer" style="margin-bottom:20px"><div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px"><div><h2 style="font-size:20px">Admin Wallet — All payments first</h2><p class="small muted">Wallet: ${money(adminWallet)} — Company 10%, owner 90% after complete. Cash manually added.</p></div><div style="display:flex;gap:8px;flex-wrap:wrap"><input id="cash-add" type="number" placeholder="Cash amount" style="width:140px"><button class="btn dark" onclick="addCashToWallet(document.getElementById('cash-add').value)">Add Cash</button><button class="btn ghost" onclick="openWithdrawModal()">Withdraw ↗</button><button class="btn ghost" onclick="viewWalletLedger()">Server ledger ↗</button></div></div><div class="info-grid" style="margin-top:14px"><div class="info-box"><small>Admin wallet balance</small><b>${money(adminWallet)}</b></div><div class="info-box"><small>Total owner payouts</small><b>${money(Object.values(ownerWallets).reduce((a,b)=>a+b,0))}</b></div><div class="info-box"><small>Banned CNICs</small><b>${bannedCNICs.length}</b></div><div class="info-box"><small>Payment methods</small><b>JazzCash ${esc(config.jazzCashNumber)} / Easypaisa ${esc(config.easypaisaNumber)}</b></div></div></div><div class="panel table-scroll depth-layer"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px"><h3>Payments & Receipts (Image)</h3><button class="btn ghost" onclick="exportOrders()">Export CSV</button></div><table><thead><tr><th>Reservation</th><th>Rental</th><th>Deposit</th><th>Home Delivery</th><th>Total</th><th>Status</th><th>Payment</th><th></th></tr></thead><tbody>${orders.map(o=>`<tr><td>${o.id}<small>${esc(o.name)} — ${esc(o.pickupMode||'Office')}</small></td><td>${money(o.totals.rental)}</td><td>${money(o.totals.deposit)}</td><td>${money(o.totals.homeDelivery||0)}</td><td>${money(o.totals.total)}${o.cancellationFee?'<br><small>Fee '+money(o.cancellationFee)+'</small>':''}</td><td><span class="pill">${o.status}</span></td><td>${esc(o.payment)}<small>Paid ${money(o.paid||0)}</small></td><td><button class="btn ghost" onclick="receipt('${o.id}')">Receipt</button> <button class="btn ghost" onclick="downloadReceiptImage('${o.id}')">Image ↓</button></td></tr>`).join('')}</tbody></table></div>`}
function adminApplications(){return `<div class="panel table-scroll depth-layer"><table><thead><tr><th>Application</th><th>Partner</th><th>Vehicle</th><th>Status</th><th></th></tr></thead><tbody>${applications.map(a=>`<tr><td>${a.id}</td><td>${esc(a.owner)}<small>${esc(a.email)}</small></td><td>${esc(a.brand+' '+a.model)}</td><td><span class="pill">${a.status}</span></td><td><button class="btn dark" onclick="reviewApplication('${a.id}')">Review</button></td></tr>`).join('')}</tbody></table>${!applications.length?'<div class="empty"><h3>No applications</h3></div>':''}</div>`}
function reviewApplication(id){const a=applications.find(a=>a.id===id);modal('Application · '+id,`<div class="info-grid">${[['Owner',a.owner],['Vehicle',a.brand+' '+a.model],['City',a.city],['Rate',money(a.rate)],['CNIC',a.cnic||'NOT PROVIDED'],['Licence',a.license||'—'],['Registration',a.registration||'—'],['Verification',(a.verification||'Pending')==='Verified'?'✔ Verified':'⏳ Pending']].map(([k,v])=>`<div class="info-box"><small>${k}</small><b>${esc(v)}</b></div>`).join('')}</div>${bannedCNICs.includes(a.cnic||'')?'<p class="notice" style="background:#fef1f0;border-color:#f3c1ba;color:#9e3a2d;margin:10px 0">⛔ This CNIC is BANNED — approval blocked.</p>':''}<form onsubmit="updateApplication(event,'${id}')"><div class="field wide"><label>Document verification (CNIC / registration / insurance)</label><select name="verification"><option ${(a.verification||'Pending')==='Pending'?'selected':''}>Pending</option><option ${a.verification==='Verified'?'selected':''}>Verified</option><option ${a.verification==='Rejected'?'selected':''}>Rejected</option></select></div><div class="field wide"><label>Status</label><select name="status"><option ${a.status==='Submitted'?'selected':''}>Submitted</option><option ${a.status==='Documents requested'?'selected':''}>Documents requested</option><option ${a.status==='Inspection scheduled'?'selected':''}>Inspection scheduled</option><option ${a.status==='Approved for onboarding'?'selected':''}>Approved for onboarding</option><option ${a.status==='Rejected'?'selected':''}>Rejected</option></select></div><div class="field wide"><label>Note</label><textarea name="note" rows="3">${esc(a.note||'')}</textarea></div><button class="btn full" style="margin-top:12px">Save</button></form>`,true)}
function updateApplication(e,id){e.preventDefault();const data=Object.fromEntries(new FormData(e.target));const app=applications.find(a=>a.id===id);Object.assign(app,data); if(data.status==='Approved for onboarding'){ if(!app.cnic){closeModal();toast('Approval blocked — owner CNIC missing (full verification required)');return;} if(bannedCNICs.includes(app.cnic)){closeModal();toast('Approval blocked — this CNIC is banned');return;} if((app.verification||'Pending')!=='Verified'){closeModal();toast('Approval blocked — mark documents Verified first');return;} } if(data.status==='Approved for onboarding' && !fleet.some(c=>c.ownerId===app.userId && c.name.includes(app.brand))){const newId=Math.max(...fleet.map(c=>c.id))+100;const newCar={id:newId,name:app.brand+' '+app.model,brand:app.brand,category:'Sedan',origin:'Pakistan',image:'pk-city',year:+app.year,rate:+app.rate||7000,deposit:35000,seats:5,engine:'1.5L',power:'100 hp',fuel:'Petrol',km:(app.mileage||0)+' km',color:'White',plate:app.registration||'LHR-xxx',condition:app.condition||'Very good',status:'Active',features:['Owner car — 10% company profit, 90% owner'],marketNote:'Owner listed — 10% company, 90% owner after complete',ownerId:app.userId,ownerShare:0.90,companyShare:0.10,customImage:null,customImages:{}};fleet.push(newCar); addNotification(app.userId,'Car approved — 90/10 profit chain','Your car '+app.brand+' '+app.model+' approved. You get 90% after ride complete, company keeps 10%.','account');} persist();closeModal();render();toast('Application updated — '+(data.status==='Approved for onboarding'?'Car added with 90/10 profit chain':'Status '+data.status));addNotification(app.userId,'Application '+data.status,'Your vehicle application '+id+' is '+data.status,'account')}
function adminSettings(){return `<form class="panel depth-layer" style="max-width:700px" onsubmit="saveAdminSettings(event)"><h2 class="formtitle">Settings — functional</h2><div class="formgrid"><div class="field"><label>Driver rate PKR/day</label><input name="driverRate" type="number" required value="${config.driverRate}"></div><div class="field"><label>Overtime PKR/hour</label><input name="overtime" type="number" required value="${config.overtime}"></div></div><button class="btn" style="margin-top:14px">Save settings</button><div style="margin-top:24px"><button type="button" class="btn ghost" onclick="seedDemo()">Load sample data</button> <button type="button" class="btn ghost" onclick="resetFleet()">Reset fleet</button></div></form>`}
function saveAdminSettings(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));config={driverRate:+f.driverRate,overtime:+f.overtime};write('config',config);toast('Settings saved')}
function resetFleet(){if(confirm('Reset fleet to 13 defaults?')){fleet=defaultFleet;write('fleet',fleet);render();toast('Fleet reset')}}
function exportOrders(){const rows=[['Booking','Customer','Email','Status','Cars','Rental','Method'],...orders.map(o=>[o.id,o.name,o.email,o.status,o.items.map(i=>carBy(i.carId)?.name||'Deleted').join(' / '),o.totals.rental,o.payment])];download('APEX-reservations.csv',rows.map(r=>r.map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(',')).join('\n'));toast('Exported')}
function seedDemo(){if(orders.some(o=>o.id==='VR-DEMO01'))return toast('Sample already loaded');const samples=[{id:'VR-DEMO01',name:'Hassan Ali',email:'hassan@example.com',carId:4,service:'With driver',city:'Lahore',start:'2026-10-01',end:'2026-10-04',payment:'JazzCash'},{id:'VR-DEMO02',name:'Sara Ahmed',email:'sara@example.com',carId:7,service:'Self-drive',city:'Islamabad',start:'2026-10-06',end:'2026-10-08',payment:'Cash on pickup'}];samples.forEach(s=>{const item={carId:s.carId,start:s.start,end:s.end,city:s.city,service:s.service,driverRate:config.driverRate};const t=totals([item]);orders.push({id:s.id,userId:s.id+'-customer',name:s.name,email:s.email,phone:'03000000000',destination:s.city,identityType:'CNIC',identityMasked:'xxxxx',identityStatus:'Verified',items:[{...item,price:quote(item)}],totals:t,payment:s.payment,status:'Confirmed',paid:0,depositPaid:0,created:new Date().toISOString()})});persist();render();toast('Sample loaded')}

function render(){
  if(isAdmin){
    if(!isAdminAuthenticated()){document.getElementById('app').innerHTML=`<div class="topline">APEX OPERATIONS</div><div class="wrap">${adminLoginPage()}</div>`;return}
    document.getElementById('app').innerHTML=adminShell();
    renderNotif();
    return;
  }
  route=location.hash.slice(1)||'home';
  let content;
  if(route==='home')content=home();
  else if(route==='fleet')content=fleetPage();
  else if(route.startsWith('car/'))content=detail(route.split('/')[1]);
  else if(route==='plans')content=plansPage();
  else if(route==='cart')content=cartPage();
  else if(route==='checkout')content=checkoutPage();
  else if(route.startsWith('success/'))content=successPage(route.split('/')[1]);
  else if(route==='account')content=accountPage();
  else if(route==='owner')content=ownerPage();
  else content=empty('Road not found','Back to collection');
  document.getElementById('app').innerHTML=header()+`<main class="fade">${content}</main>`+footer()+chatWidget();
  renderNotif();
  renderChatMessages();
  if(route.startsWith('car/'))refreshQuote(route.split('/')[1]);
  if(route==='checkout'&&checkoutStep===2)paymentNote();
}

window.addEventListener('hashchange',()=>{galleryIndex=0;closeModal();notifOpen=false;render();scrollTo(0,0)});
document.addEventListener('click',e=>{if(!e.target.closest('#notif-btn')&&!e.target.closest('#notif-dropdown')){notifOpen=false;const dd=document.getElementById('notif-dropdown');if(dd)dd.classList.remove('open')}});
document.addEventListener('keydown',e=>{if(e.key==='Escape'){closeModal();notifOpen=false;const dd=document.getElementById('notif-dropdown');if(dd)dd.classList.remove('open')}});
render();
document.addEventListener('click',e=>{const a=e.target.closest('a[href^="#"]');if(a){e.preventDefault();go(a.getAttribute('href').slice(1))}});
document.addEventListener('click',e=>{const b=e.target.closest('button');if(b&&b.form&&b.type==='submit'){e.preventDefault();if(b.form.reportValidity())b.form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))}},true);

/* ================= APEX JAVA BACKEND SYNC ADAPTER =================
   Connects this frontend to the Java OOP backend (SQLite database).
   - On load  : pulls the full state from GET  /api/bootstrap
   - On change: pushes the full state to  PUT /api/sync (admin token)
   - Admin login also mints a bearer token via POST /api/auth/login
   Without a server (file:// double-click) everything gracefully falls
   back to the original localStorage-only behaviour.                   */
let __apexToken=read('apiToken',null);
function apexHeaders(withJson){const h={};if(withJson)h['Content-Type']='application/json';if(__apexToken)h['Authorization']='Bearer '+__apexToken;return h;}
function apexClearToken(){__apexToken=null;store.removeItem('v2_apiToken');}
function apexAdminLogin(u,p){if(window.location.protocol==='file:')return;fetch('/api/auth/login',{method:'POST',headers:apexHeaders(true),body:JSON.stringify({username:u,password:p})}).then(r=>r.ok?r.json():null).then(d=>{if(d&&d.token){__apexToken=d.token;write('apiToken',__apexToken);apexPushNow();}}).catch(()=>{});}
let __apexPushT=null;
function apexSchedulePush(){if(window.location.protocol==='file:'||!__apexToken)return;clearTimeout(__apexPushT);__apexPushT=setTimeout(apexPushNow,700);}
function apexPushNow(){if(window.location.protocol==='file:'||!__apexToken)return;fetch('/api/sync',{method:'PUT',headers:apexHeaders(true),body:JSON.stringify({fleet,drivers,users,orders,applications,notifications,chats,bannedCNICs,adminWallet,ownerWallets,config})}).then(r=>{if(r.status===401)apexClearToken();}).catch(()=>{});}
function apexRecordPayment(amount,type,note){if(window.location.protocol==='file:'||!amount)return;fetch('/api/payments/record',{method:'POST',headers:apexHeaders(true),body:JSON.stringify({amount:Math.round(amount),type:type||'payment',note:note||''})}).catch(()=>{});}
const __apexPersist=persist;
persist=function(){__apexPersist();apexSchedulePush();};
if(window.location.protocol!=='file:'){
  fetch('/api/bootstrap',{headers:apexHeaders(false)}).then(r=>r.ok?r.json():null).then(s=>{
    if(!s)return;
    if(Array.isArray(s.fleet)&&s.fleet.length)fleet=s.fleet;
    if(Array.isArray(s.drivers)&&s.drivers.length)drivers=s.drivers;
    if(Array.isArray(s.users))users=s.users;
    if(Array.isArray(s.orders))orders=s.orders;
    if(Array.isArray(s.applications))applications=s.applications;
    if(Array.isArray(s.notifications))notifications=s.notifications;
    if(Array.isArray(s.chats))chats=s.chats;
    if(Array.isArray(s.bannedCNICs))bannedCNICs=s.bannedCNICs;
    if(typeof s.adminWallet==='number')adminWallet=s.adminWallet;
    if(s.ownerWallets)ownerWallets=s.ownerWallets;
    if(s.config&&Object.keys(s.config).length)config={...config,...s.config};
    __apexPersist();render();
  }).catch(()=>{});
}

/* =====================================================================
   APEX V3 UPGRADE — hourly+daily pricing, date+time pickers, server auth,
   CNIC/receipt uploads, payment verification, pickup/return settlement,
   reviews with moderation, admin overview stats, availability on server.
   Later function declarations intentionally OVERRIDE the ones above.
   ===================================================================== */

/* ---------- helpers ---------- */
if(!trip.startTime)trip.startTime='09:00';
if(!trip.endTime)trip.endTime='09:00';
function v3Pad(n){return String(n).padStart(2,'0')}
function v3Today(){const d=new Date();return d.getFullYear()+'-'+v3Pad(d.getMonth()+1)+'-'+v3Pad(d.getDate())}
function v3NowLocal(){const d=new Date();return v3Today()+'T'+v3Pad(d.getHours())+':'+v3Pad(d.getMinutes())}
function v3DT(d,t){return d+'T'+(t||'09:00')}
function v3Cfg(k,d){return config[k]===undefined||config[k]===null||config[k]===''?d:config[k]}
function v3Hourly(c){return +(c&&c.hourlyRate?c.hourlyRate:Math.max(1,Math.round((c?c.rate:0)/10)))}
function v3Grace(){return +v3Cfg('graceMinutes',30)||0}
function v3Cap(){return v3Cfg('capExtraAtDaily','true')!=='false'}
function v3Online(){return window.location.protocol!=='file:'}
async function v3Api(path,opts={}){
  const r=await fetch(path,{...opts,headers:{...(opts.body?{'Content-Type':'application/json'}:{}),...(__apexToken?{Authorization:'Bearer '+__apexToken}:{})}});
  let d=null;try{d=await r.json()}catch(e){}
  if(!r.ok)throw new Error((d&&d.error)||('HTTP '+r.status));
  return d;
}
function v3ReadFile(file){return new Promise((res,rej)=>{const fr=new FileReader();fr.onload=()=>res(fr.result);fr.onerror=rej;fr.readAsDataURL(file)})}
async function v3Upload(dataUrl,kind,ownerType,ownerId){
  return await v3Api('/api/uploads',{method:'POST',body:JSON.stringify({dataUrl,kind,ownerType:ownerType||'user',ownerId:ownerId||account?.id||''})});
}
function v3CaptureDoc(input,slot,bag){
  const f=input.files&&input.files[0];if(!f)return;
  if(!f.type.startsWith('image/'))return toast('Only image files allowed');
  if(f.size>2.5*1024*1024)return toast('Image too large (max 2.5 MB)');
  window[bag]=window[bag]||{};
  v3ReadFile(f).then(u=>{window[bag][slot]=u;const pv=document.getElementById('v3-pv-'+slot);if(pv)pv.innerHTML='<img src="'+u+'" style="width:84px;height:60px;object-fit:cover;border-radius:6px;border:1px solid var(--line)">';toast('Picture attached: '+slot.replace('_',' '))});
}
function v3ItemStart(i){return i.startDt||(i.start+'T'+(i.startTime||'00:00'))}
function v3ItemEnd(i){return i.endDt||(i.end+'T'+(i.endTime||'23:59'))}

/* ---------- date+time aware validity / clash / availability ---------- */
function validTrip(t=trip){
  if(!t.start||!t.end)return false;
  const s=t.startDt||(t.start+'T'+(t.startTime||'09:00')),e=t.endDt||(t.end+'T'+(t.endTime||'09:00'));
  if(e<=s)return false;
  return (t.start+'T23:59')>=v3NowLocal();
}
function clash(carId,start,end,exclude=''){
  const s=start+'T00:00',e=end+'T23:59';
  return orders.some(o=>o.id!==exclude&&!['Cancelled','Completed','Rejected'].includes(o.status)&&o.items.some(i=>i.carId===+carId&&s<v3ItemEnd(i)&&e>v3ItemStart(i)));
}
function available(c,t=trip){
  if(c.status!=='Active')return false;
  const s=t.startDt||(t.start+'T'+(t.startTime||'09:00')),e=t.endDt||(t.end+'T'+(t.endTime||'09:00'));
  return !orders.some(o=>!['Cancelled','Completed','Rejected'].includes(o.status)&&o.items.some(i=>i.carId===+c.id&&s<v3ItemEnd(i)&&e>v3ItemStart(i)));
}

/* ---------- pricing: hourly + daily from config, never hard-coded ---------- */
function quote(item){
  const c=carBy(item.carId);
  const sDt=item.startDt||(item.start+'T'+(item.startTime||'09:00')),eDt=item.endDt||(item.end+'T'+(item.endTime||'09:00'));
  const hours=Math.max(1,Math.ceil((new Date(eDt)-new Date(sDt))/3600000));
  const days=Math.floor(hours/24),extraH=hours%24;
  const hourly=v3Hourly(c),cap=v3Cap();
  let extra=extraH*hourly;if(cap&&extra>c.rate)extra=c.rate;
  const n=Math.max(1,Math.ceil(hours/24));
  const discount=days>=30?.2:days>=7?.1:0;
  const base=c.rate*days+extra;
  const saving=Math.round(c.rate*days*discount);
  const driver=item.service==='With driver'?(item.driverRate??config.driverRate)*n:0;
  const deposit=item.service==='With driver'?0:c.deposit;
  return {n,hours,days,extraHours:extraH,base,saving,driver,rental:base-saving+driver,deposit,total:base-saving+driver+deposit};
}
function priceLines(q){
  const dur=(q.hours!=null)?`<div class="line"><span>Rental duration</span><strong>${q.hours} h (${q.days||0}d ${q.extraHours||0}h)</strong></div>`:'';
  const baseLabel=(q.hours!=null)?'Daily + hourly charges':'Vehicle rental';
  return dur+
  `<div class="line"><span>${baseLabel}</span><strong>${money(q.base)}</strong></div>`+
  (q.saving?`<div class="line"><span>Long-stay savings</span><strong>− ${money(q.saving)}</strong></div>`:'')+
  (q.driver?`<div class="line"><span>Chauffeur service</span><strong>${money(q.driver)}</strong></div>`:'')+
  `<div class="line"><span>Refundable deposit</span><strong>${money(q.deposit)}</strong></div>`+
  `<div class="line total"><span>Total due</span><strong>${money(q.total)}</strong></div>`;
}

/* ---------- search with separate date + time pickers ---------- */
function searchForm(){return `<form class="searchbox v3-searchbox" onsubmit="searchFleet(event)"><div><label>Pick-up location</label><select name="city">${cities(trip.city)}</select></div><div><label>Pick-up date</label><input type="date" name="start" min="${v3Today()}" value="${trip.start}" required></div><div><label>Pick-up time</label><input type="time" name="startTime" value="${trip.startTime||'09:00'}" required></div><div><label>Return date</label><input type="date" name="end" min="${v3Today()}" value="${trip.end}" required></div><div><label>Return time</label><input type="time" name="endTime" value="${trip.endTime||'09:00'}" required></div><div><label>Your journey</label><select name="service"><option ${trip.service==='Self-drive'?'selected':''}>Self-drive</option><option ${trip.service==='With driver'?'selected':''}>With driver</option></select></div><button class="btn">Find your drive ↗</button></form>`}
function searchFleet(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));if(!validTrip(f))return toast('Pick a valid pick-up date/time and a later return date/time.');trip=f;write('trip',trip);go('fleet');if(route==='fleet')render()}

function refreshQuote(id){
  const form=document.getElementById('reservation-form');if(!form)return;
  const f=Object.fromEntries(new FormData(form)),c=carBy(id);
  if(!c)return;
  const valid=validTrip(f),ok=valid&&available(c,f);
  const el=document.getElementById('quote-breakdown');
  el.innerHTML=valid?`<div class="notice">${f.service==='With driver'?money(config.driverRate)+'/day chauffeur':money(v3Hourly(c))+'/hour · '+money(c.rate)+'/day · 200 km/day included'}${!ok?'<br><b style="color:#9e3a2d">Unavailable for this date/time window.</b>':''}<span id="v3-server-avail"></span></div>${priceLines(quote({...f,carId:id}))}`:'<div class="notice">Please select valid pick-up and return date + time.</div>';
  const btn=document.getElementById('add-car');if(btn)btn.disabled=!ok;
  if(valid&&v3Online()){
    const sDt=f.start+'T'+(f.startTime||'09:00'),eDt=f.end+'T'+(f.endTime||'09:00');
    fetch('/api/availability?carId='+id+'&start='+encodeURIComponent(sDt)+'&end='+encodeURIComponent(eDt)).then(r=>r.ok?r.json():null).then(d=>{
      const box=document.getElementById('v3-server-avail');if(!box||!d)return;
      box.innerHTML=d.available?'<br><b style="color:#2f7d32">✔ Server confirms availability</b>':'<br><b style="color:#9e3a2d">✖ Server: already booked in this window</b>';
      if(!d.available&&btn)btn.disabled=true;
    }).catch(()=>{});
  }
}

/* ---------- car card: full image, name, hourly + daily, availability, Rent Now ---------- */
function card(c){
  const av=available(c);
  return `<article class="car"><a class="car-photo" href="#car/${c.id}" aria-label="Explore ${esc(c.name)}"><img src="${getCarMainPhoto(c)}" alt="${esc(c.name)}" loading="lazy"><span class="pill" style="${!av?'background:#fef1f0;color:#9e3a2d;border-color:#f0c6c0':''}">${av?'Available now':'Booked in window'}</span><span class="photo-arrow">↗</span></a><div class="car-body"><div class="car-category">${c.category.toUpperCase()}</div><div class="car-title"><h3><a href="#car/${c.id}">${esc(c.name)}</a></h3><small>${c.year}</small></div><div class="specs"><span>◈ ${esc(c.engine.split(' ')[0])}</span><span>♧ ${c.seats} seats</span><span>⛽ ${c.fuel}</span></div><div class="v3-rates"><span><b>${money(v3Hourly(c))}</b><small>/hour</small></span><span><b>${money(c.rate)}</b><small>/day</small></span></div><div class="car-price"><button class="btn dark" onclick="go('car/${c.id}')">Rent Now ↗</button><button class="btn ghost" onclick="v3Reviews(${c.id})">★ Reviews</button></div></div></article>`;
}

/* ---------- hero without fleet count, visible text ---------- */
function home(){return `<div class="wrap"><section class="hero depth-layer v3-hero"><img src="${photo('hero')}" alt="APEX hero"><span class="hero-index">APEX COLLECTION / 01</span><div class="hero-copy"><div class="eyebrow">FOR THE JOURNEY. AND THE FEELING.</div><h1>Driven<br><em>beyond ordinary.</em></h1><p>Premium rentals across Pakistan — hourly & daily plans, self-drive or chauffeur, fully functional booking with CNIC verification.</p><button class="btn" onclick="go('fleet')">Explore APEX fleet ↗</button></div><div class="hero-bottom"><b>APEX — Premium Rentals</b><p>HOURLY · DAILY · SELF-DRIVE · CHAUFFEUR</p></div></section>${searchForm()}<div class="trust"><div class="trust-track"><span>Hourly & daily rates</span><span>Self-drive & chauffeur</span><span>Transparent pricing</span><span>Flexible rental plans</span><span>Multi-car reservations</span><span>Verified accounts</span><span>Live notifications</span><span>APEX — Driven Beyond Ordinary</span><span>Hourly & daily rates</span><span>Self-drive & chauffeur</span><span>Transparent pricing</span><span>Flexible rental plans</span></div></div><section class="section"><div class="section-head"><div><div class="eyebrow">NOT JUST A CAR. YOUR KIND OF CAR.</div><h2>A collection, not a compromise.</h2><p>Pakistan favorites + premium picks — live availability, hourly & daily pricing.</p></div><button class="text-btn" onclick="go('fleet')">Explore collection ↗</button></div><div class="cars">${fleet.filter(c=>c.status==='Active').map(card).join('')}</div></section><div class="benefits"><div class="benefit"><div class="symbol">✧</div><div><h3>Your pace. Your plan.</h3><p>Pay hourly or daily — long stays save automatically.</p></div></div><div class="benefit"><div class="symbol">⌘</div><div><h3>Take the wheel. Or relax.</h3><p>Choose self-drive or add a professional chauffeur.</p></div></div><div class="benefit"><div class="symbol">↗</div><div><h3>More cars. One journey.</h3><p>Multiple cars in one reservation — fully functional.</p></div></div></div><div class="owner-banner depth-layer"><div><div class="eyebrow">THE APEX PARTNER PROGRAM</div><h2>Your car. A new possibility.</h2><p>List your vehicle and earn.</p></div><button class="btn ghost" onclick="go('owner')">Become a partner ↗</button></div></div>`}

/* ---------- cart item with date+time ---------- */
function cartItem(i,index,removable=true){const c=carBy(i.carId),q=quote(i);return `<div class="cart-item"><img src="${getCarMainPhoto(c)}" alt=""><div><h3>${esc(c.name)}</h3><p>${date(i.start)} ${i.startTime||''} — ${date(i.end)} ${i.endTime||''}</p><p>${esc(i.city)} · ${esc(i.service)} · ${q.hours} h</p>${!available(c,i)&&removable?'<p class="error-text">No longer available.</p>':''}</div><div><strong style="font-size:13px">${money(q.rental)}</strong>${removable?`<p style="margin-top:12px"><button class="remove" onclick="removeCart(${index})">Remove ×</button></p>`:''}</div></div>`}
function addToCart(e,id){e.preventDefault();let f=Object.fromEntries(new FormData(e.target));if(!validTrip(f)||!available(carBy(id),f))return toast('Please choose an available date/time window.');if(cart.some(i=>i.carId===id))return toast('Already in your selection.');const startDt=f.start+'T'+(f.startTime||'09:00'),endDt=f.end+'T'+(f.endTime||'09:00');cart.push({...f,carId:id,driverRate:config.driverRate,startDt,endDt});trip={...f};persist();toast('Added to your journey.');modal('Your journey is taking shape.',`<div class="cart-item"><img src="${photo(carBy(id).image)}" alt=""><div><h3>${esc(carBy(id).name)}</h3><p>${date(f.start)} ${f.startTime} — ${date(f.end)} ${f.endTime}</p><p>${esc(f.service)} · ${esc(f.city)}</p></div></div><p class="muted small" style="margin-top:20px">${cart.length} cars selected.</p><div class="button-row"><button class="btn ghost" onclick="closeModal();go('fleet')">Explore more</button><button class="btn" onclick="closeModal();go('cart')">View journey ↗</button></div>`);document.querySelector('.basket span').textContent=cart.length}
function orderItems(o){return o.items.map(i=>`<div class="cart-item"><img src="${photo(carBy(i.carId).image)}" alt=""><div><h3>${esc(carBy(i.carId).name)}</h3><p>${date(i.start)} ${i.startTime||''} — ${date(i.end)} ${i.endTime||''}</p><p>${esc(i.service)} · ${esc(i.city)}</p>${i.service==='With driver'?`<p>${i.assignedDriver?'Chauffeur: '+esc(drivers.find(d=>d.id===i.assignedDriver)?.name||'Assigned'):'Chauffeur pending'}</p>`:''}</div><strong class="small">${money(i.price.rental)}</strong></div>`).join('')}

/* ---------- checkout: CNIC front/back required for renting ---------- */
function renterForm(){return `<h2 class="formtitle">Renter details — with ID verification</h2><div class="notice">CNIC front & back pictures are REQUIRED — visible to admin only, never public. Refundable deposit only for Self-drive. Home delivery +PKR charge.</div><form id="renter-form" onsubmit="reviewCheckout(event)"><div class="formgrid"><div class="field"><label>Full legal name</label><input name="name" required value="${esc(checkoutInfo.name||account.name)}" minlength="2"></div><div class="field"><label>Phone number</label><input name="phone" type="tel" required value="${esc(checkoutInfo.phone||account.phone)}"></div><div class="field"><label>Email address</label><input type="email" required name="email" value="${esc(account.email)}"></div><div class="field"><label>Date of birth (18+)</label><input type="date" name="dob" required max="2008-09-19" value="${esc(checkoutInfo.dob||'')}"></div><div class="field"><label>Identity document</label><select name="idType" onchange="updateID(this.value)"><option>CNIC</option><option>Passport</option></select></div><div class="field"><label id="identity-label">CNIC number · 13 digits</label><input id="identity-number" name="identity" required pattern="[0-9]{13}" placeholder="0000000000000"></div><div class="field"><label>CNIC front picture *</label><input type="file" accept="image/*" required onchange="v3CaptureDoc(this,'cnic_front','__v3Docs')"><div id="v3-pv-cnic_front" class="v3-upload-preview"></div></div><div class="field"><label>CNIC back picture *</label><input type="file" accept="image/*" required onchange="v3CaptureDoc(this,'cnic_back','__v3Docs')"><div id="v3-pv-cnic_back" class="v3-upload-preview"></div></div><div class="field wide"><label>Current address</label><input name="address" required minlength="8" value="${esc(checkoutInfo.address||'')}"></div><div class="field"><label>Emergency contact name</label><input name="emergencyName" required minlength="2" value="${esc(checkoutInfo.emergencyName||'')}"></div><div class="field"><label>Emergency contact phone</label><input name="emergencyPhone" type="tel" required value="${esc(checkoutInfo.emergencyPhone||'')}"></div><div class="field wide"><label>Trip purpose / Destination</label><input name="destination" required value="${esc(checkoutInfo.destination||'')}"></div><div class="field wide"><label>Pick-up mode</label><select name="pickupMode" onchange="toggleHomeDelivery(this.value)"><option value="Office pickup">Office pickup — ${esc(config.officeAddress)}</option><option value="Home delivery">Home delivery — +${money(config.homeDeliveryCharge)} car at your home</option></select></div><div class="field wide" id="home-delivery-field" style="display:none"><label>Home delivery full address</label><input name="homeAddress" placeholder="House #, Street, Area, City" value="${esc(checkoutInfo.homeAddress||'')}"></div></div>${cart.map((i,n)=>i.service==='Self-drive'?`<div class="detail-section" style="margin-top:25px"><h3 style="font-size:17px">Driver ${n+1} · ${esc(carBy(i.carId).name)}</h3><div class="formgrid"><div class="field"><label>Driver full name</label><input name="driverName_${n}" required></div><div class="field"><label>Driver DOB · 25+</label><input name="driverDOB_${n}" type="date" required max="2001-09-19"></div><div class="field"><label>Licence number</label><input name="license_${n}" required></div><div class="field"><label>Licence expiry</label><input name="expiry_${n}" type="date" required min="${i.end}"></div></div></div>`:'').join('')}<label class="check"><input type="checkbox" required name="terms"><span>I accept rental & cancellation terms.</span></label><div class="button-row"><button class="btn" type="submit">Review & pay ↗</button></div></form>`}

/* ---------- payment: receipt + TID upload, never auto-confirmed ---------- */
function paymentForm(){return `<h2 class="formtitle">Payment — receipt verification</h2><p class="small muted">${esc(checkoutInfo.name)} · ${cart.length} vehicles · ${esc(checkoutInfo.pickupMode||'Office pickup')}</p><button class="text-btn" onclick="checkoutStep=1;render()">← Edit details</button><form onsubmit="submitOrder(event)" style="margin-top:18px"><div class="payment-options">${[['Cash on pickup','Pay at '+esc(config.officeAddress),'◈'],['JazzCash','Send to '+esc(config.jazzCashNumber)+' — APEX Rentals','J'],['easypaisa','Send to '+esc(config.easypaisaNumber)+' — APEX Rentals','e'],['Raast / bank transfer','Bank: '+esc(config.bankAccount)+' | IBAN: '+esc(config.bankIBAN)+' | Raast: '+esc(config.raastId),'↗'],['Visa / Mastercard','Secure card — 2.5% fee','▣']].map(([p,s,ic])=>`<label class="pay"><input type="radio" name="payment" value="${p}" ${payment===p?'checked':''} onchange="choosePayment(this.value)"><span><b>${ic}  ${p}</b><small>${s}</small></span></label>`).join('')}</div><div class="notice" id="payment-note" style="background:#fffbe6;border-color:#f5e6a0;color:#7a5a00"></div><div class="panel" style="margin:16px 0;background:var(--bg-2)"><small style="font-weight:700">OFFICE ADDRESS FOR PICKUP</small><p style="font-size:11px;margin:8px 0 0;color:var(--text)">${esc(config.officeAddress)}<br>Phone: ${esc(config.companyPhone)}<br>${checkoutInfo.pickupMode==='Home delivery'?'<b>Home delivery: +'+money(config.homeDeliveryCharge)+'</b><br>Address: '+esc(checkoutInfo.homeAddress||''):''}</p></div><div id="v3-online-fields" class="panel" style="margin:16px 0;background:var(--bg-2);display:${payment==='Cash on pickup'?'none':'block'}"><div class="eyebrow">PAYMENT PROOF — REQUIRED FOR ONLINE PAYMENTS</div><div class="formgrid" style="margin-top:10px"><div class="field"><label>Transaction ID (TID) *</label><input name="tid" placeholder="e.g. MPAY-882910" minlength="4"></div><div class="field"><label>Receipt / screenshot *</label><input type="file" accept="image/*" onchange="v3CaptureDoc(this,'receipt','__v3Docs')"><div id="v3-pv-receipt" class="v3-upload-preview"></div></div></div><p class="file-note" style="margin-top:8px">After upload your booking goes to <b>Pending Verification</b> — admin verifies receipt + TID before pickup. Money is confirmed by admin only.</p></div><label class="check"><input required type="checkbox"><span>I confirm ${money(totals().rental)} rental ${totals().deposit?'+ '+money(totals().deposit)+' refundable (self-drive only)':''} ${checkoutInfo.pickupMode==='Home delivery'?'+ '+money(config.homeDeliveryCharge)+' home delivery':''}.</span></label><button class="btn full">Confirm booking ↗</button></form>`}
function paymentNote(){const el=document.getElementById('payment-note');const of=document.getElementById('v3-online-fields');if(of)of.style.display=payment==='Cash on pickup'?'none':'block';if(!el) return; if(payment==='Cash on pickup'){el.innerHTML=`Cash on pickup: Pay at office<br><b>${esc(config.officeAddress)}</b><br>Booking goes to <b>Pickup Pending</b> — admin confirms handover.`}else if(payment==='JazzCash'){el.innerHTML=`JazzCash: Send <b>${money(totals().total)}</b> to <b>${esc(config.jazzCashNumber)}</b> (APEX Rentals)<br>Then attach receipt + TID below — admin verifies before confirmation.`}else if(payment==='easypaisa'){el.innerHTML=`easypaisa: Send <b>${money(totals().total)}</b> to <b>${esc(config.easypaisaNumber)}</b><br>Attach receipt + TID below — admin verifies.`}else if(payment.includes('Raast')||payment.includes('bank')){el.innerHTML=`Bank: <b>${esc(config.bankAccount)}</b><br>IBAN: <b>${esc(config.bankIBAN)}</b> · Raast: <b>${esc(config.raastId)}</b><br>Amount ${money(totals().total)} — attach receipt + TID below.`}else{el.innerHTML=`${esc(payment)}: Amount ${money(totals().total)}<br>Attach payment proof + TID below — admin verifies.`}}

/* ---------- order submission: uploads + server persistence + verification flow ---------- */
async function submitOrder(e){
  e.preventDefault();
  const fd=new FormData(e.target);payment=fd.get('payment')||payment;
  if(bannedCNICs.includes(checkoutInfo.identity))return toast('This CNIC is banned from services');
  const online=payment!=='Cash on pickup';
  const tid=online?String(fd.get('tid')||'').trim():'';
  const D=window.__v3Docs||{};
  if(!D.cnic_front||!D.cnic_back)return toast('CNIC front & back pictures are required — go back and attach them.');
  if(online&&tid.length<4)return toast('Transaction ID (TID) is required for online payments.');
  if(online&&!D.receipt)return toast('Payment receipt picture is required.');
  const btn=e.target.querySelector('button.full');if(btn){btn.disabled=true;btn.textContent='Uploading & booking…'}
  try{
    const idF=await v3Upload(D.cnic_front,'cnic_front');
    const idB=await v3Upload(D.cnic_back,'cnic_back');
    let receiptDoc=null;
    if(online)receiptDoc=(await v3Upload(D.receipt,'receipt','booking','pending')).id;
    const id='VR-'+Date.now().toString().slice(-7);
    const homeCharge=checkoutInfo.pickupMode==='Home delivery'?config.homeDeliveryCharge:0;
    const baseTotals=totals();
    const finalTotals={...baseTotals,homeDelivery:homeCharge,total:baseTotals.total+homeCharge};
    const items=cart.map((i,n)=>({...i,price:quote(i),assignedDriver:null,startDt:i.startDt||(i.start+'T'+(i.startTime||'09:00')),endDt:i.endDt||(i.end+'T'+(i.endTime||'09:00')),selfDriver:i.service==='Self-drive'?{name:checkoutInfo['driverName_'+n],license:checkoutInfo['license_'+n],status:'Verified'}:null}));
    const o={id,userId:account.id,name:checkoutInfo.name,email:account.email,phone:checkoutInfo.phone,destination:checkoutInfo.destination,pickupMode:checkoutInfo.pickupMode||'Office pickup',homeAddress:checkoutInfo.homeAddress||'',officeAddress:config.officeAddress,identityType:checkoutInfo.idType,identity:checkoutInfo.identity,identityMasked:checkoutInfo.idType==='CNIC'?'xxxxx-xxxxxxx-x':'xxxxxxxx',identityStatus:'Documents uploaded',identityDocs:[idF.id,idB.id],receiptDoc,tid,items,totals:finalTotals,payment,status:online?'Pending Verification':'Pickup Pending',paymentStatus:online?'Pending Verification':'Pay at pickup',startDt:items[0].startDt,endDt:items[0].endDt,paid:0,depositPaid:0,created:new Date().toISOString(),cancellationFee:0,ownerPayoutDone:false};
    if(v3Online()){
      await v3Api('/api/orders',{method:'POST',body:JSON.stringify(o)});   // server-side clash check
      if(online){
        const payAmount=finalTotals.rental+homeCharge+(finalTotals.deposit||0);
        await v3Api('/api/payments/submit',{method:'POST',body:JSON.stringify({bookingId:id,method:payment,amount:payAmount,tid,receiptDoc})});
      }
    }
    orders.unshift(o);cart=[];window.__v3Docs={};
    persist();
    addNotification(account.id,online?'Booking created — payment pending verification':'Booking created — pickup pending',
      online?'Booking '+id+' ('+money(finalTotals.total)+') is waiting for admin payment verification.':'Booking '+id+' ('+money(finalTotals.total)+') — pay at pickup, admin will confirm handover.','account');
    addNotification('admin','New booking '+id+' — '+money(finalTotals.total),account.username+' booked '+o.items.length+' vehicle(s) via '+payment+' — '+o.pickupMode,null,'Reservations');
    go('success/'+id);
  }catch(err){
    if(btn){btn.disabled=false;btn.textContent='Confirm booking ↗'}
    toast('Booking failed: '+err.message);
  }
}

function successPage(id){const o=orders.find(o=>o.id===id&&o.userId===account?.id);if(!o)return empty('Reservation not found.','Check My journeys.','My journeys','account');
  const pay=o.paymentStatus||'';
  const banner=pay==='Pending Verification'?`<div class="notice" style="background:#fffbe6;border-color:#f5e6a0;color:#7a5a00">⏳ <b>Pending Verification</b> — admin is verifying your receipt + TID. You will be notified. Pickup is confirmed only after verification.</div>`
    :pay==='Rejected'?`<div class="notice" style="background:#fef1f0;border-color:#f3c1ba;color:#9e3a2d">✖ Payment rejected — please contact support or pay again. <button class="text-btn" onclick="v3UploadReceipt('${o.id}')">Upload new receipt</button></div>`
    :pay==='Reupload Requested'?`<div class="notice" style="background:#fffbe6;border-color:#f5e6a0;color:#7a5a00">↻ Admin needs a clearer receipt. <button class="text-btn" onclick="v3UploadReceipt('${o.id}')">Re-upload receipt</button></div>`
    :pay==='Verified'?`<div class="notice" style="background:#f0f7ec;border-color:#cfe3c4;color:#2f5d24">✔ Payment verified — your booking is now <b>Pickup Pending</b>.</div>`
    :o.status==='Pickup Pending'?`<div class="notice" style="background:#f0f7ec;border-color:#cfe3c4;color:#2f5d24">◈ <b>Pickup Pending</b> — pay at office; admin will confirm the handover.</div>`
    :o.status==='Active'?`<div class="notice" style="background:#f0f7ec;border-color:#cfe3c4;color:#2f5d24">🚗 Rental is <b>Active</b> — enjoy your journey!</div>`:'';
  return `<div class="wrap" style="max-width:800px"><div class="empty" style="padding-bottom:20px"><div class="success-symbol">✓</div><div class="eyebrow">BOOKING CREATED</div><h2>Your APEX journey is booked!</h2><p>Reservation ${o.id} · ${o.items.length} vehicle(s) · Total ${money(o.totals.total)}</p></div><div class="panel depth-layer">${banner}${orderItems(o)}${priceLines(o.totals)}<div class="button-row"><button class="btn ghost" onclick="go('fleet')">Explore more</button><button class="btn" onclick="go('account')">My journeys ↗</button></div></div></div>`}

/* ---------- account page: statuses, receipts, reviews ---------- */
function accountPage(){
  if(!account)return `<div class="wrap empty"><h2>Your APEX account</h2><p class="muted">Sign in to see your bookings.</p><button class="btn" onclick="auth(false)">Sign in ↗</button></div>`;
  const mine=orders.filter(o=>o.userId===account.id),apps=applications.filter(a=>a.userId===account.id),mineNotifs=notifications.filter(n=>n.userId===account.id).slice(0,6);
  const payBtn=o=>{
    if(o.paymentStatus==='Rejected'||o.paymentStatus==='Reupload Requested')return `<button class="btn ghost" onclick="v3UploadReceipt('${o.id}')">Upload receipt ↗</button>`;
    if(o.status==='Completed')return `<button class="btn ghost" onclick="v3ReviewModal('${o.id}')">★ Review</button>`;
    return '';
  };
  return `<div class="wrap"><div class="page-top"><div class="eyebrow">YOUR APEX SPACE · @${esc(account.username)}</div><div class="section-head"><div><h1>Hello, ${esc(account.name.split(' ')[0])}.</h1><p>${esc(account.email)} · ${esc(account.phone)}</p></div><button class="btn ghost" onclick="signout()">Sign out</button></div></div>${mineNotifs.length?`<div class="panel depth-layer" style="margin-bottom:20px"><h3 style="font-size:14px;margin-bottom:12px">Recent notifications</h3>${mineNotifs.map(n=>`<div class="notif-item"><span class="dot" style="background:${n.title.includes('confirmed')?'var(--lime)':'#6b7f5a'}"></span><div><b>${esc(n.title)}</b><p>${esc(n.msg)}</p></div></div>`).join('')}</div>`:''}${mine.length?mine.map(o=>`<div class="panel mybooking depth-layer"><div class="booking-top"><h3>${o.id}</h3><span class="pill">${o.status}</span>${o.paymentStatus?`<span class="pill" style="margin-left:6px">${o.paymentStatus}</span>`:''}</div>${orderItems(o)}<div class="line"><span>Total, including deposit</span><strong>${money(o.totals.total)}</strong></div>${o.extraCharges?`<div class="line"><span>Late charges (${o.extraHours||0} h extra)</span><strong>${money(o.extraCharges)}</strong></div><div class="line total"><span>Final settled amount</span><strong>${money(o.finalAmount||o.totals.total)}</strong></div>`:''}<div class="button-row">${!['Cancelled','Completed','Active'].includes(o.status)?`<button class="btn ghost" onclick="cancelOrder('${o.id}')">Cancel</button>`:''}<button class="btn ghost" onclick="receipt('${o.id}')">Receipt ↗</button>${payBtn(o)}</div></div>`).join(''):'<div class="panel empty depth-layer"><h2>Your first journey awaits.</h2><button class="btn" onclick="go(\'fleet\')">Explore cars ↗</button></div>'}${apps.length?`<div class="section"><h2>Your vehicle applications</h2>${apps.map(a=>`<div class="panel mybooking depth-layer"><div class="booking-top"><h3>${esc(a.brand+' '+a.model)} · ${a.year}</h3><span class="pill">${a.status}</span></div><p class="small muted">${a.id} · ${esc(a.city)}</p>${a.note?`<div class="notice">${esc(a.note)}</div>`:''}</div>`).join('')}</div>`:''}</div>`;
}

/* ---------- server-side auth: register + login ---------- */
async function submitAuth(e,signup,next){
  e.preventDefault();
  const f=Object.fromEntries(new FormData(e.target));
  const errorEl=document.getElementById('auth-error');
  const showErr=m=>{if(errorEl)errorEl.innerHTML=`<div class="auth-error">${esc(m)}</div>`};
  if(signup&&f.password!==f.confirm)return showErr('Passwords do not match.');
  if(v3Online()){
    const btn=e.target.querySelector('button.full');if(btn){btn.disabled=true;btn.textContent=signup?'Creating account…':'Signing in…'}
    try{
      const res=signup
        ? await v3Api('/api/auth/register',{method:'POST',body:JSON.stringify({username:f.username.trim(),email:f.email.trim(),name:f.name.trim(),phone:f.phone.trim(),password:f.password})})
        : await v3Api('/api/auth/login',{method:'POST',body:JSON.stringify({username:f.username.trim(),password:f.password})});
      __apexToken=res.token;write('apiToken',__apexToken);
      if(res.role==='admin'){
        adminSession={user:'admin',at:new Date().toISOString()};
        adminTab='Overview';
        account={id:'admin',name:'Administrator',username:'admin',email:'admin@apex.local',phone:'03000000000'};
        persist();closeModal();toast('Admin signed in');
        if(!isAdmin){location.href='admin.html';return;}
        render();return;
      }
      account=res.user;
      if(!users.some(u=>u.id===account.id)){users.push({...account});}
      write('session',account);write('users',users);
      addNotification(account.id,signup?'Account created':'Signed in',signup?'Welcome to APEX! Your account '+account.username+' is ready.':'You signed in to APEX as '+account.username,'account');
      persist();closeModal();v3RefreshBootstrap();go(next);render();
      toast((signup?'Welcome, ':'Welcome back, ')+account.name.split(' ')[0]+'!');
      return;
    }catch(err){
      if(btn){btn.disabled=false;btn.textContent=signup?'Create account ↗':'Sign in ↗'}
      showErr(err.message||'Server unavailable — try again.');
      return;
    }
  }
  /* offline (file://) fallback — original local behaviour */
  users=read('users',[]);
  if(signup){
    if(users.some(u=>u.username.toLowerCase()===f.username.trim().toLowerCase()))return showErr('Username already taken.');
    if(users.some(u=>u.email.toLowerCase()===f.email.trim().toLowerCase()))return showErr('Email already registered.');
    const u={id:'C'+Date.now(),name:f.name.trim(),username:f.username.trim(),email:f.email.trim().toLowerCase(),phone:f.phone.trim(),password:f.password};
    users.push(u);write('users',users);account=u;write('session',u);
    addNotification(u.id,'Account created','Welcome to APEX!','account');
    closeModal();go(next);render();toast('Welcome, '+u.name.split(' ')[0]+'!');
  }else{
    const identifier=f.username.trim().toLowerCase();
    if(identifier==='admin'&&f.password==='admin1234'){
      adminSession={user:'admin',at:new Date().toISOString()};adminTab='Overview';
      account={id:'admin',name:'Administrator',username:'admin',email:'admin@apex.local',phone:'03000000000'};
      persist();closeModal();
      if(isAdmin){render();return;}
      location.href='admin.html';return;
    }
    const u=users.find(x=>x.username.toLowerCase()===identifier||x.email.toLowerCase()===identifier);
    if(!u)return showErr('No account found with that username or email.');
    if(u.password!==f.password)return showErr('Incorrect password. Try again.');
    account=u;write('session',u);closeModal();go(next);render();toast('Welcome back, '+u.name.split(' ')[0]+'!');
  }
}
function submitAdminLogin(e){
  e.preventDefault();
  const f=Object.fromEntries(new FormData(e.target));
  if(f.user==='admin'&&f.pass==='admin1234'){
    adminSession={user:'admin',at:new Date().toISOString()};
    adminTab='Overview';
    account={id:'admin',name:'Administrator',username:'admin',email:'admin@apex.local',phone:'03000000000'};
    apexAdminLogin(f.user,f.pass);
    persist();render();
    toast('Admin signed in — opening Overview dashboard');
  }else toast('Invalid credentials. Use admin / admin1234');
}

/* ---------- sync adapter: pull after login, push only as admin ---------- */
function apexAdminLogin(u,p){
  if(!v3Online())return;
  fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:u,password:p})})
    .then(r=>r.ok?r.json():null).then(d=>{
      if(d&&d.token){__apexToken=d.token;write('apiToken',__apexToken);v3RefreshBootstrap();}
    }).catch(()=>{});
}
function apexPushNow(){
  if(!v3Online()||!__apexToken)return;
  if(!isAdminAuthenticated())return;               // customers sync via dedicated APIs, never full-state push
  fetch('/api/sync',{method:'PUT',headers:apexHeaders(true),body:JSON.stringify({fleet,drivers,users,orders,applications,notifications,chats,bannedCNICs,adminWallet,ownerWallets,config})}).then(r=>{if(r.status===401)apexClearToken();}).catch(()=>{});
}
function v3RefreshBootstrap(){
  if(!v3Online())return;
  fetch('/api/bootstrap',{headers:apexHeaders(false)}).then(r=>r.ok?r.json():null).then(s=>{
    if(!s)return;
    if(Array.isArray(s.fleet)&&s.fleet.length)fleet=s.fleet;
    if(Array.isArray(s.drivers)&&s.drivers.length)drivers=s.drivers;
    if(Array.isArray(s.users))users=s.users;
    if(Array.isArray(s.orders))orders=s.orders;
    if(Array.isArray(s.applications))applications=s.applications;
    if(Array.isArray(s.notifications))notifications=s.notifications;
    if(Array.isArray(s.chats))chats=s.chats;
    if(Array.isArray(s.bannedCNICs))bannedCNICs=s.bannedCNICs;
    if(typeof s.adminWallet==='number')adminWallet=s.adminWallet;
    if(s.ownerWallets)ownerWallets=s.ownerWallets;
    if(s.config&&Object.keys(s.config).length)config={...config,...s.config};
    if(!trip.startTime)trip.startTime='09:00';
    if(!trip.endTime)trip.endTime='09:00';
    __apexPersist();render();
  }).catch(()=>{});
}

/* ---------- customer chat -> server endpoint ---------- */
function sendUserChat(text){
  if(!account)return;
  const chat=getOrCreateChat(account.id,account.name);
  chat.messages.push({from:'user',text,time:new Date().toISOString()});
  chat.unreadAdmin=(chat.unreadAdmin||0)+1;
  chat.lastTime=new Date().toISOString();
  persist();render();
  if(v3Online()&&__apexToken){
    fetch('/api/chats/send',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+__apexToken},body:JSON.stringify({text,from:'user'})}).catch(()=>{});
  }
}

/* ---------- admin OVERVIEW: live stat cards from the database ---------- */
function adminOverview(){
  const active=orders.filter(o=>!['Cancelled','Completed'].includes(o.status));
  const monthly=getMonthlyStats();
  const thisMonth=monthly[monthly.length-1];
  const unreadChats=chats.reduce((s,c)=>s+(c.unreadAdmin||0),0);
  return `
    <div class="admin-stats" id="v3-stats"><div class="panel depth-layer"><small>Loading live stats…</small><h2>…</h2></div></div>
    <div id="v3-tasks"></div>
    <div class="chart-grid">
      <div class="chart-panel depth-layer"><h3>Monthly revenue · Last 6 months</h3><p class="small muted">Rental income trend — PKR</p>${renderBarChart()}<div style="display:flex;justify-content:space-between;margin-top:12px"><small class="muted">Total ${money(monthly.reduce((s,m)=>s+m.income,0))}</small><small class="muted">Avg ${money(Math.round(monthly.reduce((s,m)=>s+m.income,0)/6))}/mo</small></div></div>
      <div class="chart-panel depth-layer"><h3>Fleet by category</h3><p class="small muted">${fleet.length} vehicles</p>${renderCategoryChart()}<div style="margin-top:18px;padding:12px;background:var(--bg-2);border-radius:10px;border:1px solid var(--line)"><small class="muted">Most rented</small><b style="display:block;margin-top:4px;font-size:12px">${(()=>{const counts={};orders.forEach(o=>o.items.forEach(i=>{counts[i.carId]=(counts[i.carId]||0)+1}));const top=Object.entries(counts).sort((a,b)=>b[1]-a[1])[0];return top?(carBy(top[0])?.name||'Car')+' · '+top[1]+' bookings':'No data yet';})()}</b></div></div>
    </div>
    <div class="chart-grid">
      <div class="chart-panel depth-layer"><h3>Recent activity · History</h3><div class="timeline">${orders.slice(0,8).map(o=>`<div class="timeline-item"><b>${o.id} · ${esc(o.name)} — ${o.status}</b><p>${o.items.map(i=>carBy(i.carId)?.name||'Car').join(', ')} · ${money(o.totals.total)} · ${esc(o.payment)}</p><small>${new Date(o.created).toLocaleString()}</small></div>`).join('')||'<p class="small muted">No activity yet</p>'}</div></div>
      <div class="chart-panel depth-layer"><h3>Quick actions & health</h3><div style="display:grid;gap:10px;margin-top:12px"><button class="btn full" onclick="adminTab='Payments';render()">Payment verifications ↗</button><button class="btn ghost full" onclick="v3ShowDocs()">Verify CNIC documents ↗</button><button class="btn ghost full" onclick="v3ShowReviews()">Moderate reviews ↗</button><button class="btn ghost full" onclick="adminTab='Fleet';render()">Edit fleet & pricing ↗</button><button class="btn ghost full" onclick="adminTab='Messages';render()">Open messages · ${unreadChats} unread</button><button class="btn ghost full" onclick="adminTab='Reservations';render()">Manage bookings</button><button class="btn ghost full" onclick="exportOrders()">Export CSV ↓</button></div><div style="margin-top:20px"><h3 style="font-size:13px">System health</h3><div class="info-grid" style="grid-template-columns:1fr 1fr;margin-top:10px"><div class="info-box"><small>Drivers active</small><b>${drivers.filter(d=>d.active).length}/${drivers.length}</b></div><div class="info-box"><small>Pending apps</small><b>${applications.filter(a=>a.status==='Submitted').length}</b></div><div class="info-box"><small>Active rentals</small><b>${active.length}</b></div><div class="info-box"><small>Chats</small><b>${chats.length}</b></div></div></div></div>
    </div>
    <div class="section-head"><div><h2 style="font-size:23px">Recent reservations</h2></div><button class="text-btn" onclick="adminTab='Reservations';render()">View all ↗</button></div>${adminBookings(orders.slice(0,6))}
  `;
}
async function v3FillStats(){
  const box=document.getElementById('v3-stats');if(!box)return;
  try{
    const d=await v3Api('/api/stats');const s=d.stats||{};
    box.innerHTML=[
      ['Total revenue',money(s.revenue||0)],['Wallet balance',money(s.walletBalance||0)],
      ['Payments pending',`${s.payPending||0} · ${money(s.payPendingAmount||0)}`],['Payments verified',`${s.payVerified||0} · ${money(s.payVerifiedAmount||0)}`],
      ['Bookings',`${s.bookings||0} total`],['Rented now',`${s.rentedNow||0} active`],
      ['Pending / completed',`${s.pending||0} / ${s.completed||0}`],['Deposit held',money(s.depositHeld||0)],
      ['Late charges collected',money(s.extraCollected||0)],['Fleet size',`${s.cars||0} cars`],
      ['Customers',`${s.users||0} (${s.userListedCars||0} cars listed)`],['Drivers',`${s.driversApproved||0} approved · ${s.driversPending||0} pending`],
      ['Documents pending',`${s.docsPending||0}`],['Reviews pending',`${s.reviewsPending||0}`],
      ['Unread messages',`${s.unreadMessages||0}`],['Withdrawals pending',money(s.withdrawPending||0)]
    ].map(([k,v])=>`<div class="panel depth-layer"><small>${k}</small><h2 style="font-size:19px">${v}</h2></div>`).join('');
  }catch(e){box.innerHTML=`<div class="panel depth-layer"><small>Stats</small><h2 style="font-size:15px">Server offline</h2></div>`}
}

/* ---------- admin PAYMENTS: receipt + TID verification UI ---------- */
function adminPayments(){return `<div id="v3-payments" class="panel depth-layer" style="margin-bottom:20px"><h2 style="font-size:18px">Payment verifications — loading…</h2></div><div class="panel depth-layer" style="margin-bottom:20px"><div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px"><div><h2 style="font-size:20px">Admin Wallet — credited on verification</h2><p class="small muted">Wallet: ${money(adminWallet)} — money is added ONLY when admin verifies a payment. Cash added manually.</p></div><div style="display:flex;gap:8px;flex-wrap:wrap"><input id="cash-add" type="number" placeholder="Cash amount" style="width:140px"><button class="btn dark" onclick="addCashToWallet(document.getElementById('cash-add').value)">Add Cash</button><button class="btn ghost" onclick="openWithdrawModal()">Withdraw ↗</button><button class="btn ghost" onclick="viewWalletLedger()">Server ledger ↗</button></div></div><div class="info-grid" style="margin-top:14px"><div class="info-box"><small>Admin wallet balance</small><b>${money(adminWallet)}</b></div><div class="info-box"><small>Total owner payouts</small><b>${money(Object.values(ownerWallets).reduce((a,b)=>a+b,0))}</b></div><div class="info-box"><small>Banned CNICs</small><b>${bannedCNICs.length}</b></div><div class="info-box"><small>Payment methods</small><b>JazzCash ${esc(config.jazzCashNumber)} / Easypaisa ${esc(config.easypaisaNumber)}</b></div></div></div><div class="panel table-scroll depth-layer"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px"><h3>Reservations & Receipts</h3><button class="btn ghost" onclick="exportOrders()">Export CSV</button></div><table><thead><tr><th>Reservation</th><th>Rental</th><th>Deposit</th><th>Home Delivery</th><th>Total</th><th>Status</th><th>Payment</th><th></th></tr></thead><tbody>${orders.map(o=>`<tr><td>${o.id}<small>${esc(o.name)} — ${esc(o.pickupMode||'Office')}</small></td><td>${money(o.totals.rental)}</td><td>${money(o.totals.deposit)}</td><td>${money(o.totals.homeDelivery||0)}</td><td>${money(o.totals.total)}${o.extraCharges?'<br><small>Late '+money(o.extraCharges)+'</small>':''}</td><td><span class="pill">${o.status}</span>${o.paymentStatus?`<br><small>${esc(o.paymentStatus)}</small>`:''}</td><td>${esc(o.payment)}<small>Paid ${money(o.paid||0)}</small></td><td><button class="btn ghost" onclick="receipt('${o.id}')">Receipt</button> <button class="btn dark" onclick="manageOrder('${o.id}')">Manage</button></td></tr>`).join('')}</tbody></table></div>`}
async function v3FillPayments(){
  const box=document.getElementById('v3-payments');if(!box)return;
  try{
    const list=await v3Api('/api/payments');
    if(!list.length){box.innerHTML='<h2 style="font-size:18px">Payment verifications</h2><p class="small muted">No online payments submitted yet.</p>';return;}
    box.innerHTML=`<h2 style="font-size:18px">Payment verifications — receipt + TID</h2><div class="table-scroll" style="margin-top:10px"><table><thead><tr><th>Booking</th><th>Method</th><th>Amount</th><th>TID</th><th>Receipt</th><th>Status</th><th>Actions</th></tr></thead><tbody>${list.map(p=>`<tr><td>${esc(p.bookingId)}</td><td>${esc(p.method)}</td><td>${money(p.amount)}</td><td>${esc(p.tid||'')}</td><td>${p.receiptDoc?`<button class="btn ghost" onclick="v3ViewDoc(${p.receiptDoc})">View ↗</button>`:'—'}</td><td><span class="pill">${esc(p.status)}</span>${p.note?'<br><small>'+esc(p.note)+'</small>':''}</td><td>${p.status==='Pending Verification'?`<button class="btn dark" onclick="v3ReviewPayment(${p.id},'verify')">✔ Verify</button> <button class="btn ghost" onclick="v3ReviewPayment(${p.id},'reject')">✖ Reject</button> <button class="btn ghost" onclick="v3ReviewPayment(${p.id},'reupload')">↻ Re-upload</button>`:'—'}</td></tr>`).join('')}</tbody></table></div>`;
  }catch(e){box.innerHTML='<h2 style="font-size:18px">Payment verifications</h2><p class="small muted">Server offline.</p>'}
}
async function v3ReviewPayment(id,action){
  const note=action==='verify'?'':(prompt('Reason / note for the customer:')||'');
  if(action!=='verify'&&note===null)return;
  try{
    await v3Api('/api/payments/'+id+'/review',{method:'POST',body:JSON.stringify({action,note})});
    toast(action==='verify'?'Payment verified — wallet credited, pickup pending':action==='reject'?'Payment rejected':'Re-upload requested');
    v3RefreshBootstrap();v3FillPayments();
  }catch(e){toast('Failed: '+e.message)}
}
function v3UploadReceipt(orderId){
  const o=orders.find(x=>x.id===orderId);if(!o)return;
  modal('Upload payment receipt — '+orderId,`<div class="notice">Send payment to the account shown, then attach the receipt screenshot and TID. Admin verifies before pickup.</div><div class="panel" style="background:var(--bg-2);margin:12px 0"><p class="small">JazzCash: <b>${esc(config.jazzCashNumber)}</b><br>easypaisa: <b>${esc(config.easypaisaNumber)}</b><br>Bank: <b>${esc(config.bankAccount)}</b><br>Amount: <b>${money(o.totals.total)}</b></p></div><div class="formgrid"><div class="field"><label>Transaction ID (TID) *</label><input id="v3-rc-tid" placeholder="MPAY-XXXX" minlength="4"></div><div class="field"><label>Receipt picture *</label><input type="file" accept="image/*" onchange="v3CaptureDoc(this,'receipt2','__v3Docs')"><div id="v3-pv-receipt2" class="v3-upload-preview"></div></div></div><div class="button-row" style="margin-top:14px"><button class="btn ghost" onclick="closeModal()">Cancel</button><button class="btn" onclick="v3SubmitReceipt('${orderId}')">Submit for verification ↗</button></div>`);
}
async function v3SubmitReceipt(orderId){
  const o=orders.find(x=>x.id===orderId);if(!o)return;
  const tid=(document.getElementById('v3-rc-tid').value||'').trim();
  const D=window.__v3Docs||{};
  if(tid.length<4)return toast('TID is required (4+ characters)');
  if(!D.receipt2)return toast('Receipt picture is required');
  try{
    const up=await v3Upload(D.receipt2,'receipt','booking',orderId);
    await v3Api('/api/payments/submit',{method:'POST',body:JSON.stringify({bookingId:orderId,method:o.payment,amount:o.totals.total,tid,receiptDoc:up.id})});
    o.paymentStatus='Pending Verification';o.status='Pending Verification';o.receiptDoc=up.id;o.tid=tid;
    window.__v3Docs={};persist();closeModal();render();toast('Receipt submitted — pending admin verification');
  }catch(e){toast('Failed: '+e.message)}
}

/* ---------- manage order: pickup confirm + return settlement ---------- */
function manageOrder(id){
  const o=orders.find(o=>o.id===id);
  const payRow=o.paymentStatus?`<div class="info-box"><small>Payment status</small><b>${esc(o.paymentStatus)}</b></div>`:'';
  const settle=o.status==='Completed'&&o.extraCharges?`<div class="notice">Late return: ${o.extraHours||0} extra hour(s) — charges ${money(o.extraCharges)} · Final ${money(o.finalAmount||o.totals.total)}</div>`:'';
  modal('Reservation '+id,`<span class="pill">${o.status}</span> ${o.paymentStatus?`<span class="pill" style="margin-left:6px">${esc(o.paymentStatus)}</span>`:''}<div class="info-grid" style="margin-top:12px">${[['Renter',o.name],['Phone',o.phone],['Email',o.email],['CNIC docs',o.identityDocs?'Uploaded ('+o.identityDocs.length+')':'None'],['Destination',o.destination]].map(([k,v])=>`<div class="info-box"><small>${k}</small><b>${esc(String(v))}</b></div>`).join('')}${payRow}</div><div id="v3-order-docs" style="margin:10px 0"></div>${o.items.map((i,n)=>`<div class="panel" style="margin-bottom:15px"><h3 style="font-size:17px">${esc(carBy(i.carId).name)}</h3><p class="small muted">${date(i.start)} ${i.startTime||''} — ${date(i.end)} ${i.endTime||''} · ${esc(i.city)} · ${esc(i.service)}</p>${i.service==='With driver'?`<label>Assign chauffeur</label><select id="v3-driver-sel-${n}" onchange="assignDriver('${id}',${n},this.value)"><option value="">Not assigned</option>${drivers.map(d=>{const free=driverFree(d,i.start,i.end,id,n);return `<option value="${d.id}" ${i.assignedDriver===d.id?'selected':''} ${!free?'disabled':''}>${esc(d.name)} · ${esc(d.city)} · ${free?'Available':'Busy'}</option>`}).join('')}</select>`:''}</div>`).join('')}${priceLines(o.totals)}${settle}${o.status==='Pickup Pending'?`<div class="notice" style="background:#fffbe6;border-color:#f5e6a0;color:#7a5a00">Payment verified / cash booking — confirm the handover to start the rental.</div><div class="button-row"><button class="btn" onclick="v3Pickup('${id}')">✔ Confirm pickup — start rental</button></div>`:''}${o.status==='Active'?`<div class="panel" style="background:var(--bg-2);margin-top:10px"><label>Actual return date/time (late charges use grace ${v3Grace()} min)</label><input type="datetime-local" id="v3-return-dt" value="${v3NowLocal()}"><div class="button-row" style="margin-top:10px"><button class="btn" onclick="v3Return('${id}')">✔ Confirm return & settle</button></div></div>`:''}<div class="button-row" style="margin-top:12px">${o.status==='Confirmed'?`<button class="btn" onclick="startOrder('${id}')">Start rental</button>`:''}<button class="btn ghost" onclick="cancelOrder('${id}')">Cancel</button></div>`,true);
  setTimeout(()=>v3LoadOrderDocs(o),60);
}
async function v3LoadOrderDocs(o){
  const box=document.getElementById('v3-order-docs');if(!box)return;
  try{
    const docs=await v3Api('/api/documents');
    const mine=docs.filter(d=>d.ownerId===o.userId||d.ownerId===o.id);
    box.innerHTML=mine.length?`<div class="eyebrow" style="margin-bottom:6px">CUSTOMER DOCUMENTS (private)</div><div style="display:flex;gap:8px;flex-wrap:wrap">${mine.map(d=>`<button class="btn ghost" onclick="v3ViewDoc(${d.id})">${esc(d.kind.replace('_',' '))} ${d.status==='Verified'?'✔':d.status==='Rejected'?'✖':'⏳'}</button>`).join('')}</div>`:'';
  }catch(e){}
}
async function v3Pickup(id){
  try{
    await v3Api('/api/bookings/'+id+'/pickup',{method:'POST',body:JSON.stringify({})});
    const o=orders.find(x=>x.id===id);if(o){o.status='Active';o.pickupAt=new Date().toISOString();}
    persist();closeModal();render();toast('Pickup confirmed — rental is active');
  }catch(e){toast('Failed: '+e.message)}
}
async function v3Return(id){
  const dt=document.getElementById('v3-return-dt');
  const actualReturn=dt&&dt.value?dt.value:v3NowLocal();
  try{
    const done=await v3Api('/api/bookings/'+id+'/return',{method:'POST',body:JSON.stringify({actualReturn})});
    const o=orders.find(x=>x.id===id);
    if(o){o.status='Completed';o.actualReturn=actualReturn;o.extraHours=done.extraHours;o.extraCharges=done.extraCharges;o.finalAmount=done.finalAmount;o.paymentStatus=done.paymentStatus||'Settled';}
    persist();closeModal();render();
    toast(done.extraCharges>0?('Return settled — late charges '+money(done.extraCharges)):'Return settled on time');
  }catch(e){toast('Failed: '+e.message)}
}
async function v3ViewDoc(id){
  try{
    const d=await v3Api('/api/documents/'+id);
    modal('Document · '+d.kind.replace('_',' '),`<img src="${d.dataUrl}" style="width:100%;border-radius:10px;border:1px solid var(--line)" alt="document"><p class="small muted" style="margin-top:8px">Status: ${esc(d.status||'Pending')} — private document, admin/owner only.</p>`+(isAdmin?`<div class="button-row"><button class="btn ghost" onclick="v3DocReview(${id},'Verified')">✔ Mark verified</button><button class="btn ghost" onclick="v3DocReview(${id},'Rejected')">✖ Mark rejected</button></div>`:''),true);
  }catch(e){toast('Cannot open document: '+e.message)}
}
async function v3DocReview(id,status){
  try{await v3Api('/api/documents/'+id+'/review',{method:'POST',body:JSON.stringify({status})});closeModal();toast('Document '+status.toLowerCase());v3ShowDocs();}catch(e){toast('Failed: '+e.message)}
}
async function v3ShowDocs(){
  modal('CNIC & document verification',`<div id="v3-docs-list" class="small muted">Loading…</div>`,true);
  try{
    const docs=await v3Api('/api/documents');
    const box=document.getElementById('v3-docs-list');
    box.innerHTML=docs.length?`<div class="table-scroll"><table><thead><tr><th>ID</th><th>Owner</th><th>Kind</th><th>Status</th><th></th></tr></thead><tbody>${docs.map(d=>`<tr><td>${d.id}</td><td>${esc(d.ownerType)} · ${esc(d.ownerId)}</td><td>${esc(d.kind.replace('_',' '))}</td><td><span class="pill">${esc(d.status||'Pending')}</span></td><td><button class="btn ghost" onclick="v3ViewDoc(${d.id})">View ↗</button></td></tr>`).join('')}</tbody></table></div>`:'<p>No documents uploaded yet.</p>';
  }catch(e){document.getElementById('v3-docs-list').textContent='Server offline.'}
}

/* ---------- reviews: customer submit + admin moderation ---------- */
async function v3Reviews(carId){
  const c=carBy(carId);
  modal('Reviews · '+esc(c?c.name:'Car'),`<div id="v3-rev-list" class="small muted">Loading…</div>`);
  try{
    const list=v3Online()?await v3Api('/api/reviews/car/'+carId):[];
    const box=document.getElementById('v3-rev-list');
    if(!list.length){box.innerHTML='<p class="small muted">No approved reviews yet.</p>';return;}
    const avg=(list.reduce((s,r)=>s+r.rating,0)/list.length).toFixed(1);
    box.innerHTML=`<p style="margin-bottom:10px"><b>${'★'.repeat(Math.round(avg))}</b> ${avg} / 5 · ${list.length} review(s)</p>`+list.map(r=>`<div class="notif-item"><span class="dot"></span><div><b>${'★'.repeat(r.rating)}${'☆'.repeat(5-r.rating)} · ${esc(r.userId)}</b><p>${esc(r.body||'(no text)')}<br><small>${new Date(r.created).toLocaleDateString()}</small></p></div></div>`).join('');
  }catch(e){document.getElementById('v3-rev-list').textContent='Reviews unavailable.'}
}
function v3ReviewModal(orderId){
  const o=orders.find(x=>x.id===orderId);if(!o)return;
  const carId=o.items[0].carId;
  modal('Review your rental · '+orderId,`<p class="small muted">${esc(carBy(carId)?.name||'Car')} — reviews appear after admin approval.</p><div class="formgrid" style="margin-top:12px"><div class="field"><label>Rating *</label><select id="v3-rev-rating"><option value="5">★★★★★ Excellent</option><option value="4">★★★★ Good</option><option value="3">★★★ Okay</option><option value="2">★★ Poor</option><option value="1">★ Bad</option></select></div><div class="field wide"><label>Your review</label><textarea id="v3-rev-body" rows="4" maxlength="500" placeholder="How was the car and service?"></textarea></div></div><div class="button-row" style="margin-top:12px"><button class="btn ghost" onclick="closeModal()">Cancel</button><button class="btn" onclick="v3SubmitReview('${orderId}',${carId})">Submit review ↗</button></div>`);
}
async function v3SubmitReview(orderId,carId){
  const rating=+document.getElementById('v3-rev-rating').value;
  const body=document.getElementById('v3-rev-body').value.trim();
  try{
    await v3Api('/api/reviews',{method:'POST',body:JSON.stringify({bookingId:orderId,carId,rating,body})});
    closeModal();toast('Review submitted — waiting for admin approval');
  }catch(e){toast('Failed: '+e.message)}
}
async function v3ShowReviews(){
  modal('Review moderation',`<div id="v3-mod-list" class="small muted">Loading…</div>`,true);
  try{
    const list=await v3Api('/api/reviews');
    const box=document.getElementById('v3-mod-list');
    box.innerHTML=list.length?`<div class="table-scroll"><table><thead><tr><th>Car</th><th>User</th><th>Rating</th><th>Review</th><th>Status</th><th></th></tr></thead><tbody>${list.map(r=>`<tr><td>${esc(carBy(r.carId)?.name||('#'+r.carId))}</td><td>${esc(r.userId)}</td><td>${'★'.repeat(r.rating)}</td><td>${esc(r.body||'')}</td><td><span class="pill">${esc(r.status)}</span></td><td>${r.status!=='Approved'?`<button class="btn dark" onclick="v3ModReview(${r.id},'Approved')">Approve</button>`:''} ${r.status!=='Hidden'?`<button class="btn ghost" onclick="v3ModReview(${r.id},'Hidden')">Hide</button>`:''}</td></tr>`).join('')}</tbody></table></div>`:'<p class="small muted">No reviews submitted yet.</p>';
  }catch(e){document.getElementById('v3-mod-list').textContent='Server offline.'}
}
async function v3ModReview(id,status){
  try{await v3Api('/api/reviews/'+id+'/review',{method:'POST',body:JSON.stringify({status})});toast('Review '+status.toLowerCase());v3ShowReviews();}catch(e){toast('Failed: '+e.message)}
}

/* ---------- fleet: admin-configurable hourly + daily rates ---------- */
function adminFleet(){return `<div class="section-head"><div><h2 style="font-size:23px">Fleet — pricing, photos & details</h2><p>Hourly & daily rates are fully configurable here — nothing is hard-coded.</p></div><button class="btn" onclick="addFleet()">+ Add vehicle</button></div><div class="cars">${fleet.map(c=>`<div class="car depth-layer"><div class="car-photo"><img src="${getCarMainPhoto(c)}" alt="${esc(c.name)}"><span class="pill">${c.status} · ${c.origin}</span></div><div class="car-body"><h3>${esc(c.name)}</h3><p class="small muted" style="margin-top:8px">${c.year} · ${esc(c.brand)}<br><b>${money(v3Hourly(c))}/hour</b> · <b>${money(c.rate)}/day</b></p><div class="button-row" style="justify-content:flex-start;margin-top:12px"><button class="btn dark" onclick="editFleet(${c.id})">Edit + photos ↗</button><button class="btn ghost" onclick="v3EditPricing(${c.id})">Pricing ⚙</button><button class="btn ghost" onclick="deleteFleet(${c.id})">Delete</button></div></div></div>`).join('')}</div>`}
function v3EditPricing(id){
  const c=carBy(id);
  modal('Pricing rules · '+esc(c.name),`<div class="notice">Late-return policy (grace + hourly cap) is set globally in Settings — per-car you set the rates.</div><div class="formgrid" style="margin-top:12px"><div class="field"><label>Daily rate PKR *</label><input id="v3-price-daily" type="number" required value="${c.rate}"></div><div class="field"><label>Hourly rate PKR *</label><input id="v3-price-hourly" type="number" required value="${v3Hourly(c)}"></div><div class="field"><label>Deposit PKR</label><input id="v3-price-deposit" type="number" required value="${c.deposit}"></div></div><div class="button-row" style="margin-top:14px"><button class="btn ghost" onclick="closeModal()">Cancel</button><button class="btn" onclick="v3SavePricing(${id})">Save pricing ↗</button></div>`);
}
function v3SavePricing(id){
  const c=carBy(id);
  const daily=+document.getElementById('v3-price-daily').value,hourly=+document.getElementById('v3-price-hourly').value,dep=+document.getElementById('v3-price-deposit').value;
  if(!(daily>0)||!(hourly>0))return toast('Rates must be positive numbers');
  c.rate=daily;c.hourlyRate=hourly;c.deposit=dep;
  persist();closeModal();render();toast('Pricing saved for '+c.name);
}
function v3SaveHourly(e,id){const f=Object.fromEntries(new FormData(e.target));const c=carBy(id);if(c&&f.hourlyRate)c.hourlyRate=+f.hourlyRate;}
function editFleet(id){const c=carBy(id);window.fleetEditTemp={};const allImages=['porsche','mercedes','bmw','pk-alto','pk-cultus','pk-swift','pk-city','pk-civic','pk-corolla','pk-yaris','pk-brv','pk-fortuner','pk-sportage'];modal('Edit · '+esc(c.name)+' — pricing, photos & details',`<form onsubmit="v3SaveHourly(event,${id});saveFleetFull(event,${id})"><div class="formgrid"><div class="field"><label>Name</label><input name="name" required value="${esc(c.name)}"></div><div class="field"><label>Brand</label><input name="brand" required value="${esc(c.brand)}"></div><div class="field"><label>Category</label><select name="category"><option ${c.category==='Hatchback'?'selected':''}>Hatchback</option><option ${c.category==='Sedan'?'selected':''}>Sedan</option><option ${c.category==='SUV'?'selected':''}>SUV</option><option ${c.category==='Grand touring'?'selected':''}>Grand touring</option><option ${c.category==='Sports'?'selected':''}>Sports</option><option ${c.category==='Executive'?'selected':''}>Executive</option></select></div><div class="field"><label>Origin</label><select name="origin"><option ${c.origin==='Pakistan'?'selected':''}>Pakistan</option><option ${c.origin==='Premium'?'selected':''}>Premium</option></select></div><div class="field"><label>Image key (fallback)</label><select name="image">${allImages.map(k=>`<option ${k===c.image?'selected':''}>${k}</option>`).join('')}</select></div><div class="field"><label>Year</label><input name="year" type="number" required value="${c.year}"></div><div class="field"><label>Daily rate PKR *</label><input name="rate" type="number" required value="${c.rate}"></div><div class="field"><label>Hourly rate PKR *</label><input name="hourlyRate" type="number" required value="${v3Hourly(c)}"></div><div class="field"><label>Deposit PKR</label><input name="deposit" type="number" required value="${c.deposit}"></div><div class="field"><label>Seats</label><input name="seats" type="number" required value="${c.seats}"></div><div class="field"><label>Engine</label><input name="engine" required value="${esc(c.engine)}"></div><div class="field"><label>Power</label><input name="power" required value="${esc(c.power)}"></div><div class="field"><label>Fuel</label><select name="fuel"><option ${c.fuel==='Petrol'?'selected':''}>Petrol</option><option ${c.fuel==='Diesel'?'selected':''}>Diesel</option><option ${c.fuel==='Hybrid'?'selected':''}>Hybrid</option></select></div><div class="field"><label>Color</label><input name="color" required value="${esc(c.color)}"></div><div class="field"><label>Plate</label><input name="plate" required value="${esc(c.plate)}"></div><div class="field"><label>Condition</label><select name="condition"><option ${c.condition==='Excellent'?'selected':''}>Excellent</option><option ${c.condition==='Very good'?'selected':''}>Very good</option><option ${c.condition==='Good'?'selected':''}>Good</option></select></div><div class="field"><label>Status</label><select name="status"><option ${c.status==='Active'?'selected':''}>Active</option><option ${c.status==='Maintenance'?'selected':''}>Maintenance</option><option ${c.status==='Hidden'?'selected':''}>Hidden</option></select></div><div class="field wide"><label>Features (comma)</label><textarea name="features" rows="3" required>${esc((c.features||[]).join(', '))}</textarea></div><div class="field wide"><label>Market note</label><textarea name="marketNote" rows="2">${esc(c.marketNote||'')}</textarea></div><div class="field wide"><div class="eyebrow">PHOTO EDIT — UPLOAD NEW IMAGES (optional)</div></div><div class="field wide"><label>Main photo — exterior</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'main')"><div id="fleet-main-preview" class="fleet-edit-preview">${c.customImage?`<img src="${c.customImage}" alt="">`:''}</div></div></div><div class="field"><label>Interior photo</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'interior')"><div id="fleet-interior-preview" class="fleet-edit-preview">${c.customImages?.[c.image+'-interior']?`<img src="${c.customImages[c.image+'-interior']}" alt="">`:''}</div></div></div><div class="field"><label>Detail / Engine photo</label><div class="image-upload-box"><input type="file" accept="image/*" onchange="previewFleetImage(this,'detail')"><div id="fleet-detail-preview" class="fleet-edit-preview">${(c.customImages?.[c.image+'-detail']||c.customImages?.[c.image+'-engine'])?`<img src="${c.customImages[c.image+'-detail']||c.customImages[c.image+'-engine']}" alt="">`:''}</div></div></div></div><button class="btn full" style="margin-top:18px">Save with new photos ↗</button></form>`,true)}

/* ---------- drivers: licence picture + CNIC front/back required ---------- */
function driverForm(){window.__v3DriverDocs={};modal('Add driver — documents required',`<form onsubmit="saveDriver(event)"><div class="formgrid"><div class="field wide"><label>Full name</label><input name="name" required></div><div class="field"><label>Phone</label><input name="phone" required></div><div class="field"><label>City</label><select name="city">${cities('Lahore')}</select></div><div class="field"><label>Experience years</label><input name="experience" type="number" required></div><div class="field wide"><label>Licence number</label><input name="license" required></div><div class="field"><label>Licence picture *</label><input type="file" accept="image/*" required onchange="v3CaptureDoc(this,'license','__v3DriverDocs')"><div id="v3-pv-license" class="v3-upload-preview"></div></div><div class="field"><label>CNIC front picture *</label><input type="file" accept="image/*" required onchange="v3CaptureDoc(this,'cnicFront','__v3DriverDocs')"><div id="v3-pv-cnicFront" class="v3-upload-preview"></div></div><div class="field"><label>CNIC back picture *</label><input type="file" accept="image/*" required onchange="v3CaptureDoc(this,'cnicBack','__v3DriverDocs')"><div id="v3-pv-cnicBack" class="v3-upload-preview"></div></div></div><p class="file-note" style="margin-top:8px">Documents are stored privately — only admin can view them.</p><button class="btn full" style="margin-top:12px">Add driver ↗</button></form>`)}
function editDriver(id){const d=drivers.find(d=>d.id===id);window.__v3DriverDocs={};modal('Edit driver — documents',`<form onsubmit="saveDriverEdit(event,${id})"><div class="formgrid"><div class="field wide"><label>Name</label><input name="name" required value="${esc(d.name)}"></div><div class="field"><label>Phone</label><input name="phone" required value="${esc(d.phone)}"></div><div class="field"><label>City</label><select name="city">${cities(d.city)}</select></div><div class="field"><label>Experience</label><input name="experience" type="number" required value="${d.experience}"></div><div class="field wide"><label>Licence number</label><input name="license" required value="${esc(d.license)}"></div><div class="field"><label>Licence picture ${d.docs?'(replace)':'*'}</label><input type="file" accept="image/*" ${d.docs?'':'required'} onchange="v3CaptureDoc(this,'license','__v3DriverDocs')"><div id="v3-pv-license" class="v3-upload-preview"></div></div><div class="field"><label>CNIC front ${d.docs?'(replace)':'*'}</label><input type="file" accept="image/*" ${d.docs?'':'required'} onchange="v3CaptureDoc(this,'cnicFront','__v3DriverDocs')"><div id="v3-pv-cnicFront" class="v3-upload-preview"></div></div><div class="field"><label>CNIC back ${d.docs?'(replace)':'*'}</label><input type="file" accept="image/*" ${d.docs?'':'required'} onchange="v3CaptureDoc(this,'cnicBack','__v3DriverDocs')"><div id="v3-pv-cnicBack" class="v3-upload-preview"></div></div></div>${d.docs?`<div class="button-row" style="margin-top:10px">${d.docs.map(x=>`<button type="button" class="btn ghost" onclick="v3ViewDoc(${x})">Doc ${x} ↗</button>`).join('')}</div>`:''}<button class="btn full" style="margin-top:12px">Save driver ↗</button></form>`)}
async function saveDriver(e){
  e.preventDefault();const f=Object.fromEntries(new FormData(e.target));
  const D=window.__v3DriverDocs||{};
  if(!D.license||!D.cnicFront||!D.cnicBack)return toast('Licence picture + CNIC front & back pictures are required');
  const btn=e.target.querySelector('button.full');if(btn){btn.disabled=true;btn.textContent='Uploading documents…'}
  try{
    const id=Date.now();
    const up=v3Online()?await Promise.all([v3Upload(D.license,'license','driver',String(id)),v3Upload(D.cnicFront,'cnic_front','driver',String(id)),v3Upload(D.cnicBack,'cnic_back','driver',String(id))]):[{id:-1},{id:-2},{id:-3}];
    drivers.push({id,name:f.name,phone:f.phone,city:f.city,experience:+f.experience,license:f.license,active:true,status:'Pending',docs:up.map(u=>u.id)});
    window.__v3DriverDocs={};persist();closeModal();render();toast('Driver added — documents pending admin verification');
  }catch(err){if(btn){btn.disabled=false;btn.textContent='Add driver ↗'}toast('Upload failed: '+err.message)}
}
async function saveDriverEdit(e,id){
  e.preventDefault();const f=Object.fromEntries(new FormData(e.target));
  const d=drivers.find(d=>d.id===id);
  const D=window.__v3DriverDocs||{};
  if(!d.docs&&(!D.license||!D.cnicFront||!D.cnicBack))return toast('Licence + CNIC front/back pictures are required');
  const btn=e.target.querySelector('button.full');if(btn){btn.disabled=true;btn.textContent='Saving…'}
  try{
    let docs=d.docs||[];
    if(v3Online()&&(D.license||D.cnicFront||D.cnicBack)){
      const up=[];
      if(D.license)up.push(await v3Upload(D.license,'license','driver',String(id)));
      if(D.cnicFront)up.push(await v3Upload(D.cnicFront,'cnic_front','driver',String(id)));
      if(D.cnicBack)up.push(await v3Upload(D.cnicBack,'cnic_back','driver',String(id)));
      docs=docs.concat(up.map(u=>u.id));
    }
    Object.assign(d,{name:f.name,phone:f.phone,city:f.city,experience:+f.experience,license:f.license,docs});
    window.__v3DriverDocs={};persist();closeModal();render();toast('Driver updated');
  }catch(err){if(btn){btn.disabled=false;btn.textContent='Save driver ↗'}toast('Upload failed: '+err.message)}
}

/* ---------- settings: late-return policy is configurable ---------- */
function adminSettings(){return `<form class="panel depth-layer" style="max-width:760px" onsubmit="saveAdminSettings(event)"><h2 class="formtitle">Settings — pricing & late-return policy</h2><div class="formgrid"><div class="field"><label>Driver rate PKR/day</label><input name="driverRate" type="number" required value="${config.driverRate}"></div><div class="field"><label>Overtime PKR/hour</label><input name="overtime" type="number" required value="${config.overtime}"></div><div class="field"><label>Grace period (minutes) — late charging starts after this</label><input name="graceMinutes" type="number" required min="0" value="${v3Grace()}"></div><div class="field"><label>Extra hours billed at hourly rate</label><select name="capExtraAtDaily"><option value="true" ${v3Cap()?'selected':''}>Yes — cap extra-hours cost at 1 daily rate</option><option value="false" ${!v3Cap()?'selected':''}>No — pure hourly billing</option></select></div><div class="field"><label>Home delivery charge PKR</label><input name="homeDeliveryCharge" type="number" required value="${config.homeDeliveryCharge||1500}"></div><div class="field"><label>Company phone</label><input name="companyPhone" required value="${esc(config.companyPhone||'')}"></div></div><p class="file-note" style="margin-top:10px">Per-car hourly & daily rates are edited in the Fleet tab (Pricing ⚙). Late returns are settled automatically at return using this policy.</p><button class="btn" style="margin-top:14px">Save settings</button><div style="margin-top:24px"><button type="button" class="btn ghost" onclick="seedDemo()">Load sample data</button> <button type="button" class="btn ghost" onclick="resetFleet()">Reset fleet</button></div></form>`}
function saveAdminSettings(e){e.preventDefault();const f=Object.fromEntries(new FormData(e.target));config={...config,driverRate:+f.driverRate,overtime:+f.overtime,graceMinutes:+f.graceMinutes,capExtraAtDaily:f.capExtraAtDaily,homeDeliveryCharge:+f.homeDeliveryCharge,companyPhone:f.companyPhone};write('config',config);persist();render();toast('Settings saved — policy applies to new quotes & returns')}

/* ---------- post-render hooks: time inputs on car page + live data fills ---------- */
function v3PatchTimeInputs(){
  const form=document.getElementById('reservation-form');
  if(form&&!form.querySelector('input[name="startTime"]')){
    const startInput=form.querySelector('input[name="start"]'),endInput=form.querySelector('input[name="end"]');
    if(startInput&&endInput){
      const mk=(name,label,val)=>{const w=document.createElement('div');w.className='field';w.innerHTML=`<label>${label}</label><input type="time" name="${name}" value="${val}" required onchange="refreshQuote(${currentCar})">`;return w;};
      startInput.closest('div').after(mk('startTime','Pick-up time',trip.startTime||'09:00'));
      endInput.closest('div').after(mk('endTime','Return time',trip.endTime||'09:00'));
      refreshQuote(currentCar);
    }
  }
  if(route==='checkout'&&checkoutStep===2)paymentNote();
}
let __v3LastFill=0;
function v3FillDynamic(){
  const now=Date.now();
  if(document.getElementById('v3-stats')&&now-__v3LastFill>1500){__v3LastFill=now;v3FillStats();}
  if(document.getElementById('v3-payments')&&now-__v3LastFill>1500){__v3LastFill=now;v3FillPayments();}
}
(function(){
  const app=document.getElementById('app');
  if(app&&window.MutationObserver){
    new MutationObserver(()=>{v3PatchTimeInputs();v3FillDynamic();}).observe(app,{childList:true});
  }
})();

/* admin now lands on the Overview dashboard */
adminTab='Overview';
