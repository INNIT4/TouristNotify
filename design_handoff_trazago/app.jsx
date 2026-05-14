const { useState, useEffect, useRef } = React;

/* ── Color helpers ── */
function T(theme, light, dark) { return theme === 'dark' ? dark : light; }

/* ── SVG Icons ── */
const Icons = {
  compass: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.5" strokeLinecap="round"><circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88" fill={c} opacity=".2" stroke={c}/></svg>,
  map: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d="M1 6v16l7-4 8 4 7-4V2l-7 4-8-4-7 4z"/><path d="M8 2v16M16 6v16"/></svg>,
  pin: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill={c}><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>,
  star: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill={c}><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>,
  heart: (c='currentColor',s=24,fill='none') => <svg width={s} height={s} viewBox="0 0 24 24" fill={fill} stroke={c} strokeWidth="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>,
  search: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>,
  back: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6"/></svg>,
  clock: (c='currentColor',s=20) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>,
  sun: (c='#F59E0B',s=20) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>,
  route: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round"><circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M18 8c0 4-6 7-6 11"/></svg>,
  grid: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>,
  user: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>,
  check: (c='currentColor',s=20) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6L9 17l-5-5"/></svg>,
  camera: (c='currentColor',s=20) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>,
  nav: (c='currentColor',s=20) => <svg width={s} height={s} viewBox="0 0 24 24" fill={c}><path d="M3 11l19-9-9 19-2-8-8-2z"/></svg>,
  trophy: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><path d="M6 9H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h2M18 9h2a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2h-2"/><path d="M6 3h12v6a6 6 0 0 1-12 0V3z"/><path d="M9 21h6M12 15v6"/></svg>,
  event: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>,
  blog: (c='currentColor',s=24) => <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>,
};

/* ── LOGO component ── */
function Logo({ size = 60 }) {
  return <img src="logo.jpeg" alt="TrazaGo" style={{width:size,height:size,borderRadius:size*0.2,objectFit:'cover'}} />;
}

/* ─────────────────── SCREENS ─────────────────── */

/* ── Onboarding ── */
function OnboardingScreen({ onDone, theme, accent }) {
  const [page, setPage] = useState(0);
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');

  const pages = [
    { icon:'🗺️', title:'Explora Álamos', desc:'Descubre lugares históricos, eventos culturales y experiencias únicas en este Pueblo Mágico' },
    { icon:'📍', title:'Notificaciones Inteligentes', desc:'Recibe avisos automáticos cuando estés cerca de lugares turísticos interesantes' },
    { icon:'✨', title:'Crea Rutas Personalizadas', desc:'Genera itinerarios con IA o explora rutas predeterminadas diseñadas por expertos' },
  ];

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg,position:'relative',overflow:'hidden'}}>
      {/* Skip */}
      <div style={{display:'flex',justifyContent:'flex-end',padding:'54px 20px 0'}}>
        <button onClick={onDone} style={{background:'none',border:'none',color:sub,fontSize:15,fontFamily:'inherit',cursor:'pointer'}}>Omitir</button>
      </div>

      {/* Content */}
      <div style={{flex:1,display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',padding:'0 40px',textAlign:'center',gap:24}}>
        {/* Decorative circle */}
        <div style={{width:160,height:160,borderRadius:'50%',background:`${accent}14`,display:'flex',alignItems:'center',justifyContent:'center',fontSize:72,transition:'all .3s'}}>
          {pages[page].icon}
        </div>
        <h1 style={{fontSize:28,fontWeight:700,color:fg,lineHeight:1.2}}>{pages[page].title}</h1>
        <p style={{fontSize:16,color:sub,lineHeight:1.6}}>{pages[page].desc}</p>
      </div>

      {/* Dots */}
      <div style={{display:'flex',justifyContent:'center',gap:8,padding:'0 0 24px'}}>
        {pages.map((_,i) => (
          <div key={i} style={{width:i===page?24:8,height:8,borderRadius:4,background:i===page?accent:`${accent}30`,transition:'all .3s',cursor:'pointer'}} onClick={()=>setPage(i)} />
        ))}
      </div>

      {/* Button */}
      <div style={{padding:'0 24px 32px'}}>
        <button onClick={()=>page<2?setPage(page+1):onDone()} style={{width:'100%',padding:'16px',borderRadius:16,background:accent,color:'#fff',fontSize:17,fontWeight:600,border:'none',fontFamily:'inherit',cursor:'pointer',transition:'transform .1s'}} onMouseDown={e=>e.target.style.transform='scale(.97)'} onMouseUp={e=>e.target.style.transform='scale(1)'}>
          {page===2?'Comenzar':'Siguiente'}
        </button>
      </div>
    </div>
  );
}

/* ── Login ── */
function LoginScreen({ onLogin, onSkip, theme, accent }) {
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');
  const card = T(theme,'#FFFFFF','#2A1F19');
  const inputBg = T(theme,'#F4DED4','#3A2E26');
  const [email, setEmail] = useState('');
  const [pass, setPass] = useState('');

  const inputStyle = {width:'100%',padding:'14px 16px',borderRadius:12,border:`2px solid ${T(theme,'#F4DED4','#52443C')}`,background:inputBg,color:fg,fontSize:15,fontFamily:'inherit',outline:'none'};

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg,paddingTop:54}}>
      <div style={{flex:1,display:'flex',flexDirection:'column',alignItems:'center',justifyContent:'center',padding:'0 28px',gap:20}}>
        <Logo size={72} />
        <h1 style={{fontSize:26,fontWeight:700,color:fg}}>TrazaGo</h1>
        <p style={{fontSize:14,color:sub,textAlign:'center'}}>Explora Álamos, Sonora</p>

        <div style={{width:'100%',display:'flex',flexDirection:'column',gap:12,marginTop:12}}>
          <input placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} style={inputStyle} />
          <input placeholder="Contraseña" type="password" value={pass} onChange={e=>setPass(e.target.value)} style={inputStyle} />
          <button onClick={onLogin} style={{width:'100%',padding:'15px',borderRadius:16,background:accent,color:'#fff',fontSize:16,fontWeight:600,border:'none',fontFamily:'inherit',cursor:'pointer',marginTop:4}}>
            Iniciar Sesión
          </button>
        </div>

        <button onClick={onLogin} style={{background:'none',border:`2px solid ${accent}`,borderRadius:16,padding:'14px',width:'100%',color:accent,fontSize:15,fontWeight:500,fontFamily:'inherit',cursor:'pointer'}}>
          Crear Cuenta
        </button>

        <button onClick={onSkip} style={{background:'none',border:'none',color:sub,fontSize:14,fontFamily:'inherit',cursor:'pointer',marginTop:8}}>
          Continuar como invitado
        </button>
      </div>
    </div>
  );
}

/* ── Menu (Home) ── */
function MenuScreen({ onNavigate, theme, accent }) {
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');
  const card = T(theme,'#FFFFFF','#2A1F19');
  const green = '#2E7D32';

  const menuItems = [
    { id:'route', icon:Icons.route, title:'Generar Ruta IA', subtitle:'Personalizada con IA', color:green, large:true },
    { id:'map', icon:Icons.map, title:'Ver Mapa', subtitle:'Explora puntos', color:accent, large:true },
    { id:'themed', icon:(c,s)=>Icons.compass(c,s), title:'Rutas Temáticas', color:'#5CB8B2' },
    { id:'myroutes', icon:Icons.pin, title:'Mis Rutas', color:'#6C5E2F' },
    { id:'top', icon:Icons.trophy, title:'Top Lugares', color:'#F59E0B' },
    { id:'events', icon:Icons.event, title:'Eventos', color:'#9C27B0' },
    { id:'favorites', icon:(c,s)=>Icons.heart(c,s,c), title:'Favoritos', color:'#E53935' },
    { id:'stats', icon:(c,s)=><svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2" strokeLinecap="round"><path d="M18 20V10M12 20V4M6 20v-6"/></svg>, title:'Estadísticas', color:'#1A73E8' },
    { id:'blog', icon:Icons.blog, title:'Blog', color:'#FF5722' },
    { id:'services', icon:(c,s)=><svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/></svg>, title:'Servicios', color:'#006A67' },
  ];

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg}}>
      {/* Header */}
      <div style={{padding:'54px 20px 12px',display:'flex',alignItems:'center',gap:12}}>
        <Logo size={44} />
        <div style={{flex:1}}>
          <p style={{fontSize:13,color:sub}}>Bienvenido a</p>
          <h1 style={{fontSize:20,fontWeight:700,color:fg}}>TrazaGo</h1>
        </div>
        <div style={{width:40,height:40,borderRadius:20,background:`${accent}18`,display:'flex',alignItems:'center',justifyContent:'center',cursor:'pointer'}} onClick={()=>onNavigate('profile')}>
          {Icons.user(accent,20)}
        </div>
      </div>

      {/* Weather card */}
      <div style={{margin:'0 20px 16px',padding:'16px 20px',borderRadius:20,background:'linear-gradient(135deg, #5CB8B2, #2E7D32)',color:'#fff',display:'flex',alignItems:'center',gap:16}}>
        <div>{Icons.sun('#FFF',32)}</div>
        <div style={{flex:1}}>
          <p style={{fontSize:24,fontWeight:700}}>32°C</p>
          <p style={{fontSize:13,opacity:.85}}>Álamos, Sonora · Soleado</p>
        </div>
        <p style={{fontSize:12,opacity:.7}}>Ideal para<br/>explorar</p>
      </div>

      {/* Search */}
      <div style={{margin:'0 20px 16px',position:'relative'}}>
        <div style={{position:'absolute',left:14,top:'50%',transform:'translateY(-50%)'}}>{Icons.search(sub,18)}</div>
        <input placeholder="Buscar lugares…" style={{width:'100%',padding:'13px 16px 13px 42px',borderRadius:14,border:'none',background:T(theme,'#F4DED4','#3A2E26'),color:fg,fontSize:15,fontFamily:'inherit',outline:'none'}} onClick={()=>onNavigate('map')} readOnly />
      </div>

      {/* Menu Grid */}
      <div style={{flex:1,overflowY:'auto',padding:'0 20px 20px'}}>
        <p style={{fontSize:15,fontWeight:600,color:fg,marginBottom:12}}>¿Qué deseas hacer hoy?</p>
        <div style={{display:'grid',gridTemplateColumns:'1fr 1fr',gap:12}}>
          {menuItems.map((item,i) => (
            <div key={i}
              onClick={()=>onNavigate(item.id)}
              style={{
                gridColumn: item.large ? 'span 1' : 'span 1',
                padding:'20px 16px',
                borderRadius:20,
                background:card,
                boxShadow:T(theme,'0 2px 12px rgba(0,0,0,.06)','0 2px 12px rgba(0,0,0,.3)'),
                cursor:'pointer',
                display:'flex',flexDirection:'column',gap:10,
                transition:'transform .15s',
              }}
              onMouseDown={e=>e.currentTarget.style.transform='scale(.96)'}
              onMouseUp={e=>e.currentTarget.style.transform='scale(1)'}
              onMouseLeave={e=>e.currentTarget.style.transform='scale(1)'}
            >
              <div style={{width:44,height:44,borderRadius:14,background:`${item.color}15`,display:'flex',alignItems:'center',justifyContent:'center'}}>
                {item.icon(item.color,22)}
              </div>
              <div>
                <p style={{fontSize:15,fontWeight:600,color:fg}}>{item.title}</p>
                {item.subtitle && <p style={{fontSize:12,color:sub,marginTop:2}}>{item.subtitle}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Bottom Nav */}
      <BottomNav active="home" onNavigate={onNavigate} theme={theme} accent={accent} />
    </div>
  );
}

/* ── Map Screen ── */
function MapScreen({ onNavigate, onBack, theme, accent }) {
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');
  const card = T(theme,'#FFFFFF','#2A1F19');
  const teal = '#006A67';

  const categories = [
    { label:'Todos', active:true },
    { label:'Museos', color:'#9C27B0' },
    { label:'Restaurantes', color:'#FF5722' },
    { label:'Hoteles', color:'#2196F3' },
    { label:'Iglesias', color:'#00BCD4' },
    { label:'Parques', color:'#4CAF50' },
  ];

  const [activeCat, setActiveCat] = useState(0);
  const [showPlace, setShowPlace] = useState(false);

  const pins = [
    { x:55, y:35, color:'#9C27B0', name:'Museo Costumbrista', cat:1 },
    { x:40, y:50, color:'#FF5722', name:'Las Palmeras', cat:2 },
    { x:70, y:45, color:'#00BCD4', name:'Catedral de Álamos', cat:4 },
    { x:30, y:65, color:'#4CAF50', name:'Plaza de Armas', cat:5 },
    { x:65, y:70, color:'#2196F3', name:'Hotel Colonial', cat:3 },
    { x:48, y:28, color:'#9C27B0', name:'Casa de María Félix', cat:1 },
  ];

  const filtered = activeCat === 0 ? pins : pins.filter(p => p.cat === activeCat);

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg,position:'relative'}}>
      {/* Map area */}
      <div style={{flex:1,position:'relative',background:T(theme,'#E8E0D8','#2A2018'),overflow:'hidden',paddingTop:44}}>
        {/* Fake map grid */}
        <svg width="100%" height="100%" style={{position:'absolute',opacity:.15}}>
          {Array.from({length:20}).map((_,i)=><line key={`h${i}`} x1="0" y1={i*40} x2="400" y2={i*40} stroke={fg} strokeWidth=".5"/>)}
          {Array.from({length:20}).map((_,i)=><line key={`v${i}`} x1={i*40} y1="0" x2={i*40} y2="900" stroke={fg} strokeWidth=".5"/>)}
        </svg>
        {/* Streets */}
        <svg width="100%" height="100%" style={{position:'absolute',opacity:.2}}>
          <path d="M0 300 Q200 280 393 320" stroke={fg} strokeWidth="8" fill="none"/>
          <path d="M150 0 Q160 400 180 850" stroke={fg} strokeWidth="6" fill="none"/>
          <path d="M0 500 Q200 480 393 520" stroke={fg} strokeWidth="5" fill="none"/>
          <path d="M280 0 Q270 300 300 850" stroke={fg} strokeWidth="5" fill="none"/>
        </svg>

        {/* Pins */}
        {filtered.map((pin,i) => (
          <div key={i} onClick={()=>{setShowPlace(pin.name)}} style={{position:'absolute',left:`${pin.x}%`,top:`${pin.y}%`,transform:'translate(-50%,-100%)',cursor:'pointer',transition:'transform .2s',zIndex:2}} onMouseDown={e=>e.currentTarget.style.transform='translate(-50%,-100%) scale(1.2)'} onMouseUp={e=>e.currentTarget.style.transform='translate(-50%,-100%)'}>
            <div style={{display:'flex',flexDirection:'column',alignItems:'center'}}>
              <div style={{width:36,height:36,borderRadius:'50% 50% 50% 0',transform:'rotate(-45deg)',background:pin.color,display:'flex',alignItems:'center',justifyContent:'center',boxShadow:'0 3px 8px rgba(0,0,0,.25)'}}>
                <div style={{width:12,height:12,borderRadius:'50%',background:'#fff',transform:'rotate(45deg)'}}/>
              </div>
            </div>
          </div>
        ))}

        {/* Search bar overlay */}
        <div style={{position:'absolute',top:12,left:12,right:12,display:'flex',gap:8,zIndex:5}}>
          <button onClick={onBack} style={{width:44,height:44,borderRadius:14,background:card,border:'none',display:'flex',alignItems:'center',justifyContent:'center',boxShadow:'0 2px 10px rgba(0,0,0,.1)',cursor:'pointer'}}>
            {Icons.back(fg)}
          </button>
          <div style={{flex:1,position:'relative'}}>
            <div style={{position:'absolute',left:14,top:'50%',transform:'translateY(-50%)'}}>{Icons.search(sub,16)}</div>
            <input placeholder="Buscar lugares…" style={{width:'100%',height:44,padding:'0 16px 0 40px',borderRadius:14,border:'none',background:card,boxShadow:'0 2px 10px rgba(0,0,0,.1)',fontSize:14,fontFamily:'inherit',color:fg,outline:'none'}} />
          </div>
        </div>

        {/* Category chips */}
        <div style={{position:'absolute',top:66,left:0,right:0,display:'flex',gap:8,padding:'0 12px',overflowX:'auto',zIndex:5}}>
          {categories.map((cat,i)=>(
            <button key={i} onClick={()=>setActiveCat(i)} style={{
              padding:'8px 16px',borderRadius:20,border:'none',
              background:activeCat===i?accent:card,
              color:activeCat===i?'#fff':fg,
              fontSize:13,fontWeight:500,fontFamily:'inherit',
              whiteSpace:'nowrap',cursor:'pointer',
              boxShadow:'0 1px 4px rgba(0,0,0,.08)',
              transition:'all .2s',
            }}>
              {cat.label}
            </button>
          ))}
        </div>

        {/* My Location btn */}
        <button style={{position:'absolute',bottom:showPlace?220:80,right:16,width:48,height:48,borderRadius:24,background:card,border:'none',boxShadow:'0 2px 10px rgba(0,0,0,.15)',display:'flex',alignItems:'center',justifyContent:'center',cursor:'pointer',zIndex:5,transition:'bottom .3s'}}>
          {Icons.nav(accent,20)}
        </button>
      </div>

      {/* Place preview card */}
      {showPlace && (
        <div style={{position:'absolute',bottom:0,left:0,right:0,background:card,borderRadius:'24px 24px 0 0',padding:'20px',boxShadow:'0 -4px 20px rgba(0,0,0,.1)',zIndex:10,animation:'slideUp .3s ease'}}>
          <style>{`@keyframes slideUp{from{transform:translateY(100%)}to{transform:translateY(0)}}`}</style>
          <div style={{width:40,height:4,borderRadius:2,background:T(theme,'#ddd','#555'),margin:'0 auto 16px'}} />
          <div style={{display:'flex',gap:14}}>
            <div style={{width:80,height:80,borderRadius:16,background:`${accent}20`,display:'flex',alignItems:'center',justifyContent:'center'}}>
              {Icons.camera(accent,28)}
            </div>
            <div style={{flex:1}}>
              <h3 style={{fontSize:17,fontWeight:600,color:fg}}>{showPlace}</h3>
              <div style={{display:'flex',alignItems:'center',gap:4,marginTop:4}}>
                {Icons.star('#F59E0B',14)}
                <span style={{fontSize:13,color:sub}}>4.7 · 128 reseñas</span>
              </div>
              <p style={{fontSize:13,color:sub,marginTop:4}}>Abierto · Cierra a las 18:00</p>
            </div>
          </div>
          <div style={{display:'flex',gap:10,marginTop:16}}>
            <button onClick={()=>{onNavigate('placeDetail');setShowPlace(false)}} style={{flex:1,padding:'13px',borderRadius:14,background:accent,color:'#fff',fontSize:14,fontWeight:600,border:'none',fontFamily:'inherit',cursor:'pointer'}}>Ver Detalles</button>
            <button style={{padding:'13px 18px',borderRadius:14,background:`${accent}15`,border:'none',cursor:'pointer'}}>
              {Icons.heart(accent,20)}
            </button>
            <button onClick={()=>setShowPlace(false)} style={{padding:'13px 18px',borderRadius:14,background:T(theme,'#f0e8e3','#3A2E26'),border:'none',cursor:'pointer',color:sub,fontSize:18}}>✕</button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ── Place Detail ── */
function PlaceDetailScreen({ onBack, theme, accent }) {
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');
  const card = T(theme,'#FFFFFF','#2A1F19');
  const [fav, setFav] = useState(false);
  const [checkedIn, setCheckedIn] = useState(false);

  const reviews = [
    { name:'María G.', rating:5, text:'Un lugar increíble, la arquitectura es hermosa. Muy recomendado para conocer la historia de Álamos.' },
    { name:'Carlos R.', rating:4, text:'Muy interesante, el guía explica muy bien. Vale la pena la visita.' },
  ];

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg,overflowY:'auto'}}>
      {/* Hero image area */}
      <div style={{height:260,background:`linear-gradient(135deg, ${accent}40, #006A6740)`,position:'relative',display:'flex',alignItems:'center',justifyContent:'center',flexShrink:0}}>
        <div style={{textAlign:'center',color:fg,opacity:.4}}>
          {Icons.camera(fg,48)}
          <p style={{fontSize:13,marginTop:8}}>Foto del lugar</p>
        </div>
        {/* Top buttons */}
        <div style={{position:'absolute',top:12,left:12,right:12,display:'flex',justifyContent:'space-between'}}>
          <button onClick={onBack} style={{width:40,height:40,borderRadius:20,background:'rgba(0,0,0,.3)',backdropFilter:'blur(10px)',border:'none',display:'flex',alignItems:'center',justifyContent:'center',cursor:'pointer'}}>
            {Icons.back('#fff')}
          </button>
          <button onClick={()=>setFav(!fav)} style={{width:40,height:40,borderRadius:20,background:'rgba(0,0,0,.3)',backdropFilter:'blur(10px)',border:'none',display:'flex',alignItems:'center',justifyContent:'center',cursor:'pointer'}}>
            {Icons.heart(fav?'#E53935':'#fff',20,fav?'#E53935':'none')}
          </button>
        </div>
        {/* Photo dots */}
        <div style={{position:'absolute',bottom:12,display:'flex',gap:6}}>
          {[0,1,2,3].map(i=><div key={i} style={{width:i===0?16:6,height:6,borderRadius:3,background:i===0?'#fff':'rgba(255,255,255,.5)'}} />)}
        </div>
      </div>

      {/* Content */}
      <div style={{padding:'20px',display:'flex',flexDirection:'column',gap:16}}>
        <div>
          <div style={{display:'flex',alignItems:'center',gap:8,marginBottom:4}}>
            <span style={{padding:'4px 10px',borderRadius:8,background:'#9C27B020',color:'#9C27B0',fontSize:12,fontWeight:600}}>Museo</span>
            <span style={{fontSize:13,color:sub}}>· 1.2 km</span>
          </div>
          <h1 style={{fontSize:24,fontWeight:700,color:fg}}>Museo Costumbrista de Sonora</h1>
          <div style={{display:'flex',alignItems:'center',gap:6,marginTop:6}}>
            <div style={{display:'flex',gap:2}}>{[1,2,3,4,5].map(i=><span key={i}>{Icons.star(i<=4?'#F59E0B':'#ddd',16)}</span>)}</div>
            <span style={{fontSize:14,color:sub}}>4.7 (128)</span>
          </div>
        </div>

        {/* Quick info */}
        <div style={{display:'flex',gap:10}}>
          {[
            { icon:Icons.clock, text:'9:00 - 18:00', label:'Horario' },
            { icon:Icons.pin, text:'Centro', label:'Zona' },
          ].map((item,i) => (
            <div key={i} style={{flex:1,padding:'12px',borderRadius:14,background:T(theme,'#F4DED4','#3A2E26'),display:'flex',alignItems:'center',gap:10}}>
              {item.icon(sub,18)}
              <div>
                <p style={{fontSize:12,color:sub}}>{item.label}</p>
                <p style={{fontSize:14,fontWeight:500,color:fg}}>{item.text}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Action buttons */}
        <div style={{display:'flex',gap:10}}>
          <button onClick={()=>setCheckedIn(true)} style={{
            flex:1,padding:'14px',borderRadius:14,border:'none',fontFamily:'inherit',cursor:'pointer',fontSize:14,fontWeight:600,
            background:checkedIn?'#4CAF5020':accent,
            color:checkedIn?'#4CAF50':'#fff',
            display:'flex',alignItems:'center',justifyContent:'center',gap:8,
          }}>
            {checkedIn ? <>{Icons.check('#4CAF50',18)} Check-in hecho</> : <>📍 Hacer Check-in</>}
          </button>
          <button style={{padding:'14px 18px',borderRadius:14,background:`${accent}15`,border:'none',cursor:'pointer'}}>
            {Icons.nav(accent,18)}
          </button>
        </div>

        {/* Description */}
        <div>
          <h3 style={{fontSize:16,fontWeight:600,color:fg,marginBottom:8}}>Acerca de</h3>
          <p style={{fontSize:14,color:sub,lineHeight:1.7}}>
            El Museo Costumbrista de Sonora es una joya cultural que preserva la rica historia y tradiciones del estado. Ubicado en el corazón de Álamos, ofrece una colección fascinante de artefactos y exhibiciones que narran la vida cotidiana de los sonorenses a través de los siglos.
          </p>
        </div>

        {/* Reviews */}
        <div>
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:12}}>
            <h3 style={{fontSize:16,fontWeight:600,color:fg}}>Reseñas</h3>
            <span style={{fontSize:13,color:accent,cursor:'pointer'}}>Ver todas</span>
          </div>
          <div style={{display:'flex',flexDirection:'column',gap:12}}>
            {reviews.map((r,i)=>(
              <div key={i} style={{padding:'14px',borderRadius:14,background:T(theme,'#fff','#2A1F19'),boxShadow:T(theme,'0 1px 6px rgba(0,0,0,.05)','none')}}>
                <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:6}}>
                  <p style={{fontSize:14,fontWeight:600,color:fg}}>{r.name}</p>
                  <div style={{display:'flex',gap:1}}>{Array.from({length:r.rating}).map((_,j)=><span key={j}>{Icons.star('#F59E0B',12)}</span>)}</div>
                </div>
                <p style={{fontSize:13,color:sub,lineHeight:1.5}}>{r.text}</p>
              </div>
            ))}
          </div>
        </div>

        <div style={{height:20}} />
      </div>
    </div>
  );
}

/* ── Bottom Nav ── */
function BottomNav({ active, onNavigate, theme, accent }) {
  const bg = T(theme,'#FFFFFF','#2A1F19');
  const sub = T(theme,'#84746A','#A08D84');
  const items = [
    { id:'home', icon:Icons.grid, label:'Inicio' },
    { id:'map', icon:Icons.map, label:'Mapa' },
    { id:'favorites', icon:(c,s)=>Icons.heart(c,s), label:'Favoritos' },
    { id:'profile', icon:Icons.user, label:'Perfil' },
  ];

  return (
    <div style={{display:'flex',padding:'10px 8px 8px',background:bg,borderTop:`1px solid ${T(theme,'#F4DED4','#3A2E26')}`,flexShrink:0}}>
      {items.map(item=>(
        <div key={item.id} onClick={()=>onNavigate(item.id)} style={{flex:1,display:'flex',flexDirection:'column',alignItems:'center',gap:4,cursor:'pointer',padding:'4px 0'}}>
          {item.icon(active===item.id?accent:sub,22)}
          <span style={{fontSize:11,fontWeight:active===item.id?600:400,color:active===item.id?accent:sub}}>{item.label}</span>
        </div>
      ))}
    </div>
  );
}

/* ── Profile ── */
function ProfileScreen({ onBack, theme, accent }) {
  const bg = T(theme,'#FFF8F5','#1A120E');
  const fg = T(theme,'#221A15','#EDE0DA');
  const sub = T(theme,'#52443C','#D7C3B9');
  const card = T(theme,'#FFFFFF','#2A1F19');

  const stats = [
    { label:'Check-ins', value:'12' },
    { label:'Favoritos', value:'8' },
    { label:'Rutas', value:'3' },
  ];

  return (
    <div style={{height:'100%',display:'flex',flexDirection:'column',background:bg}}>
      <div style={{padding:'54px 20px 12px',display:'flex',alignItems:'center',gap:12}}>
        <button onClick={onBack} style={{background:'none',border:'none',cursor:'pointer'}}>{Icons.back(fg)}</button>
        <h1 style={{fontSize:20,fontWeight:700,color:fg}}>Mi Perfil</h1>
      </div>

      <div style={{flex:1,overflowY:'auto',padding:'0 20px 20px',display:'flex',flexDirection:'column',gap:16}}>
        {/* Avatar */}
        <div style={{display:'flex',flexDirection:'column',alignItems:'center',gap:12,padding:'20px 0'}}>
          <div style={{width:80,height:80,borderRadius:40,background:`${accent}20`,display:'flex',alignItems:'center',justifyContent:'center'}}>
            {Icons.user(accent,36)}
          </div>
          <div style={{textAlign:'center'}}>
            <h2 style={{fontSize:20,fontWeight:700,color:fg}}>Explorador</h2>
            <p style={{fontSize:14,color:sub}}>explorador@email.com</p>
          </div>
        </div>

        {/* Stats */}
        <div style={{display:'flex',gap:10}}>
          {stats.map((s,i) => (
            <div key={i} style={{flex:1,padding:'16px',borderRadius:16,background:card,textAlign:'center',boxShadow:T(theme,'0 1px 6px rgba(0,0,0,.05)','none')}}>
              <p style={{fontSize:24,fontWeight:700,color:accent}}>{s.value}</p>
              <p style={{fontSize:12,color:sub,marginTop:4}}>{s.label}</p>
            </div>
          ))}
        </div>

        {/* Badges */}
        <div style={{padding:'16px',borderRadius:16,background:card,boxShadow:T(theme,'0 1px 6px rgba(0,0,0,.05)','none')}}>
          <h3 style={{fontSize:15,fontWeight:600,color:fg,marginBottom:12}}>Insignias</h3>
          <div style={{display:'flex',gap:12}}>
            {['🏛️','🗺️','⭐'].map((b,i)=>(
              <div key={i} style={{width:52,height:52,borderRadius:16,background:`${accent}12`,display:'flex',alignItems:'center',justifyContent:'center',fontSize:24}}>{b}</div>
            ))}
            <div style={{width:52,height:52,borderRadius:16,background:T(theme,'#F4DED4','#3A2E26'),display:'flex',alignItems:'center',justifyContent:'center',fontSize:18,color:sub}}>?</div>
          </div>
        </div>

        {/* Menu items */}
        {['Estadísticas','Configuración','Política de Privacidad','Cerrar Sesión'].map((item,i)=>(
          <div key={i} style={{padding:'16px',borderRadius:14,background:card,boxShadow:T(theme,'0 1px 6px rgba(0,0,0,.05)','none'),display:'flex',justifyContent:'space-between',alignItems:'center',cursor:'pointer',color:i===3?'#E53935':fg,fontSize:15,fontWeight:500}}>
            {item}
            <span style={{color:sub,fontSize:18}}>›</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─────────────────── APP ROUTER ─────────────────── */
function App({ theme = 'light', accent = '#D95F2A' }) {
  const [screen, setScreen] = useState('onboarding');
  const [history, setHistory] = useState(['onboarding']);

  function navigate(s) {
    setHistory(prev => [...prev, s]);
    setScreen(s);
  }
  function goBack() {
    setHistory(prev => {
      const next = prev.slice(0, -1);
      setScreen(next[next.length - 1] || 'menu');
      return next;
    });
  }

  switch (screen) {
    case 'onboarding': return <OnboardingScreen onDone={()=>navigate('login')} theme={theme} accent={accent} />;
    case 'login': return <LoginScreen onLogin={()=>navigate('menu')} onSkip={()=>navigate('menu')} theme={theme} accent={accent} />;
    case 'menu': return <MenuScreen onNavigate={navigate} theme={theme} accent={accent} />;
    case 'map': return <MapScreen onNavigate={navigate} onBack={goBack} theme={theme} accent={accent} />;
    case 'placeDetail': return <PlaceDetailScreen onBack={goBack} theme={theme} accent={accent} />;
    case 'profile': return <ProfileScreen onBack={goBack} theme={theme} accent={accent} />;
    case 'favorites': return <MenuScreen onNavigate={navigate} theme={theme} accent={accent} />;
    default: return <MenuScreen onNavigate={navigate} theme={theme} accent={accent} />;
  }
}

window.App = App;
