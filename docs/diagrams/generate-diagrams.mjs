import fs from "node:fs";
import path from "node:path";

const OUT = path.dirname(new URL(import.meta.url).pathname);
const esc = (s) => String(s).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
const style = {
  title: "text;html=1;fontSize=24;fontStyle=1;align=center;verticalAlign=middle;",
  text: "text;html=1;fontSize=12;align=left;verticalAlign=middle;whiteSpace=wrap;",
  action: "rounded=1;arcSize=12;whiteSpace=wrap;html=1;fontSize=12;fillColor=#ffffff;strokeColor=#334155;strokeWidth=1.4;",
  decision: "rhombus;whiteSpace=wrap;html=1;fontSize=12;fillColor=#fff7ed;strokeColor=#9a3412;strokeWidth=1.4;",
  usecase: "ellipse;whiteSpace=wrap;html=1;fontSize=12;fillColor=#ffffff;strokeColor=#334155;strokeWidth=1.4;",
  actor: "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;fontSize=12;strokeColor=#0f172a;",
  boundary: "swimlane;html=1;rounded=0;startSize=34;fontSize=14;fontStyle=1;fillColor=#ffffff;strokeColor=#475569;strokeWidth=1.5;",
  lane: "swimlane;html=1;horizontal=0;startSize=34;fontSize=13;fontStyle=1;fillColor=#f8fafc;strokeColor=#94a3b8;",
  classBox: "swimlane;fontStyle=1;align=center;startSize=28;horizontal=1;html=1;fontSize=11;fillColor=#ffffff;strokeColor=#334155;strokeWidth=1.3;swimlaneLine=1;",
  note: "shape=note;whiteSpace=wrap;html=1;fontSize=11;fillColor=#fffbeb;strokeColor=#a16207;",
  initial: "ellipse;html=1;aspect=fixed;fillColor=#0f172a;strokeColor=#0f172a;",
  final: "ellipse;html=1;aspect=fixed;fillColor=#0f172a;strokeColor=#0f172a;strokeWidth=3;perimeter=ellipsePerimeter;",
};

function page(id, name, width, height) {
  return { id, name, width, height, cells: [], svgs: [] };
}
function vertex(p, id, value, x, y, w, h, kind = "action", parent = "1") {
  p.cells.push(`<mxCell id="${id}" value="${esc(value).replaceAll("\n", "&#xa;")}" style="${style[kind] ?? kind}" vertex="1" parent="${parent}"><mxGeometry x="${x}" y="${y}" width="${w}" height="${h}" as="geometry"/></mxCell>`);
  p.svgs.push({ t: "v", id, value, x, y, w, h, kind });
}
function edge(p, id, source, target, value = "", rel = "flow", points = []) {
  const styles = {
    flow: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;endFill=1;strokeColor=#334155;strokeWidth=1.3;fontSize=11;",
    association: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=none;strokeColor=#334155;strokeWidth=1.2;fontSize=11;",
    dependency: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;dashed=1;endArrow=open;endFill=0;strokeColor=#475569;fontSize=11;",
    include: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;dashed=1;endArrow=open;endFill=0;strokeColor=#334155;fontSize=11;",
    extend: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;dashed=1;endArrow=open;endFill=0;strokeColor=#334155;fontSize=11;",
    composition: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;startArrow=diamond;startFill=1;endArrow=none;strokeColor=#334155;fontSize=11;",
    aggregation: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;startArrow=diamond;startFill=0;endArrow=none;strokeColor=#334155;fontSize=11;",
    implementation: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;dashed=1;endArrow=block;endFill=0;strokeColor=#334155;fontSize=11;",
    protocol: "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;startArrow=open;endArrow=open;startFill=0;endFill=0;strokeColor=#7c3aed;strokeWidth=2;fontSize=11;",
  };
  const pts = points.length ? `<Array as="points">${points.map(([x,y]) => `<mxPoint x="${x}" y="${y}"/>`).join("")}</Array>` : "";
  p.cells.push(`<mxCell id="${id}" value="${esc(value)}" style="${styles[rel]}" edge="1" parent="1" source="${source}" target="${target}"><mxGeometry relative="1" as="geometry">${pts}</mxGeometry></mxCell>`);
  p.svgs.push({ t: "e", id, source, target, value, rel });
}
function diagramXml(p) {
  return `<diagram id="${p.id}" name="${esc(p.name)}"><mxGraphModel dx="${p.width}" dy="${p.height}" grid="1" gridSize="10" guides="1" page="1" pageScale="1" pageWidth="${p.width}" pageHeight="${p.height}" background="#ffffff"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${p.cells.join("")}</root></mxGraphModel></diagram>`;
}
function saveDrawio(file, pages) {
  fs.writeFileSync(path.join(OUT, file), `<mxfile host="app.diagrams.net" modified="2026-07-30T00:00:00.000Z" agent="Codex" version="24.7.17" type="device" compressed="false">${pages.map(diagramXml).join("")}</mxfile>\n`);
}
function svg(p, filename) {
  const nodes = new Map(p.svgs.filter(x => x.t === "v").map(x => [x.id, x]));
  const lines = [];
  const defs = `<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 z" fill="#334155"/></marker><marker id="open" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto"><path d="M1,1 L8,4.5 L1,8" fill="none" stroke="#334155"/></marker></defs>`;
  for (const e of p.svgs.filter(x => x.t === "e")) {
    const a = nodes.get(e.source), b = nodes.get(e.target); if (!a || !b) continue;
    const ax = a.x + a.w / 2, ay = a.y + a.h / 2, bx = b.x + b.w / 2, by = b.y + b.h / 2;
    const dashed = ["dependency","include","extend","implementation"].includes(e.rel) ? ` stroke-dasharray="7 5"` : "";
    const marker = e.rel === "association" || ["composition","aggregation"].includes(e.rel) ? "" : ` marker-end="url(#${e.rel === "flow" ? "arrow" : "open"})"`;
    let route = `M${ax} ${ay} L${bx} ${by}`;
    if (e.rel === "association" && a.kind === "actor") {
      const rail = ax < bx ? 175 : 1260;
      route = `M${ax} ${ay} H${rail} V${by} H${bx}`;
    }
    if (["f9","f14","f19","f29"].includes(e.id)) {
      route = `M${ax} ${ay} H45 V${by} H${bx}`;
    }
    if (e.id === "f26") {
      route = `M${ax} ${ay} H20 V${by} H${bx}`;
    }
    lines.push(`<path d="${route}" fill="none" stroke="${e.rel === "protocol" ? "#7c3aed" : "#64748b"}" stroke-width="${e.rel === "protocol" ? 2 : 1.4}"${dashed}${marker}/>${e.value ? `<text x="${(ax+bx)/2}" y="${(ay+by)/2-5}" text-anchor="middle" font-size="11" fill="#0f172a">${esc(e.value)}</text>` : ""}`);
  }
  const shapes = p.svgs.filter(x => x.t === "v").map(n => {
    const text = n.value.split("\n");
    const tspans = text.map((s,i) => `<tspan x="${n.x+n.w/2}" dy="${i ? 15 : 0}">${esc(s)}</tspan>`).join("");
    if (n.kind === "title" || n.kind === "text") return `<text x="${n.x+n.w/2}" y="${n.y+n.h/2}" text-anchor="middle" font-size="${n.kind==="title"?24:12}" font-weight="${n.kind==="title"?"700":"400"}" fill="#0f172a">${tspans}</text>`;
    if (n.kind === "initial") return `<circle cx="${n.x+n.w/2}" cy="${n.y+n.h/2}" r="${Math.min(n.w,n.h)/2}" fill="#0f172a"/>`;
    if (n.kind === "final") return `<circle cx="${n.x+n.w/2}" cy="${n.y+n.h/2}" r="${Math.min(n.w,n.h)/2}" fill="none" stroke="#0f172a" stroke-width="3"/><circle cx="${n.x+n.w/2}" cy="${n.y+n.h/2}" r="${Math.min(n.w,n.h)/2-5}" fill="#0f172a"/>`;
    if (n.kind === "decision") return `<polygon points="${n.x+n.w/2},${n.y} ${n.x+n.w},${n.y+n.h/2} ${n.x+n.w/2},${n.y+n.h} ${n.x},${n.y+n.h/2}" fill="#fff7ed" stroke="#9a3412" stroke-width="1.4"/><text x="${n.x+n.w/2}" y="${n.y+n.h/2}" text-anchor="middle" font-size="11">${tspans}</text>`;
    if (n.kind === "usecase") return `<ellipse cx="${n.x+n.w/2}" cy="${n.y+n.h/2}" rx="${n.w/2}" ry="${n.h/2}" fill="white" stroke="#334155" stroke-width="1.4"/><text x="${n.x+n.w/2}" y="${n.y+n.h/2}" text-anchor="middle" font-size="11">${tspans}</text>`;
    if (n.kind === "actor") return `<circle cx="${n.x+n.w/2}" cy="${n.y+15}" r="9" fill="none" stroke="#0f172a"/><path d="M${n.x+n.w/2} ${n.y+24}v32m-18-20h36m-18 20l-16 24m16-24l16 24" stroke="#0f172a" fill="none"/><text x="${n.x+n.w/2}" y="${n.y+n.h-2}" text-anchor="middle" font-size="12">${esc(n.value)}</text>`;
    if (n.kind === "boundary" || n.kind === "lane") return `<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" fill="${n.kind==="lane"?"#f8fafc":"white"}" stroke="#64748b"/><text x="${n.x+10}" y="${n.y+22}" font-size="13" font-weight="700">${esc(n.value)}</text>`;
    if (n.kind === "note") return `<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" fill="#fffbeb" stroke="#a16207"/><text x="${n.x+n.w/2}" y="${n.y+18}" text-anchor="middle" font-size="10">${tspans}</text>`;
    const cls = n.kind === "classBox";
    if (cls) {
      const [head, ...body] = text;
      return `<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" fill="white" stroke="#334155" stroke-width="1.3"/><line x1="${n.x}" y1="${n.y+28}" x2="${n.x+n.w}" y2="${n.y+28}" stroke="#334155"/><text x="${n.x+n.w/2}" y="${n.y+18}" text-anchor="middle" font-size="10.5" font-weight="600">${esc(head)}</text><text x="${n.x+n.w/2}" y="${n.y+45}" text-anchor="middle" font-size="10">${body.map((s,i)=>`<tspan x="${n.x+n.w/2}" dy="${i?14:0}">${esc(s)}</tspan>`).join("")}</text>`;
    }
    return `<rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" rx="8" fill="white" stroke="#334155" stroke-width="1.3"/><text x="${n.x+n.w/2}" y="${n.y+18}" text-anchor="middle" font-size="11">${tspans}</text>`;
  });
  const backgrounds = shapes.filter((_, i) => ["boundary", "lane"].includes(p.svgs.filter(x => x.t === "v")[i].kind));
  const foregrounds = shapes.filter((_, i) => !["boundary", "lane"].includes(p.svgs.filter(x => x.t === "v")[i].kind));
  fs.writeFileSync(path.join(OUT, filename), `<svg xmlns="http://www.w3.org/2000/svg" width="${p.width}" height="${p.height}" viewBox="0 0 ${p.width} ${p.height}"><rect width="100%" height="100%" fill="white"/>${defs}${backgrounds.join("")}${lines.join("")}${foregrounds.join("")}</svg>\n`);
}

// Use case diagram
const uc = page("usecase", "Sơ đồ use case", 1400, 990);
vertex(uc,"uct","Mimope.io — Sơ đồ use case",350,18,700,40,"title");
vertex(uc,"player","Player",35,380,80,110,"actor"); vertex(uc,"operator","Operator",1275,385,85,110,"actor");
vertex(uc,"system","Mimope.io",155,75,1090,850,"boundary");
const U=(id,v,x,y,w=190,h=58)=>vertex(uc,id,v,x,y,w,h,"usecase");
U("join","Tham gia trò chơi",210,130); U("nickname","Nhập nickname",500,105,170); U("starter","Chọn sinh vật khởi đầu",755,105,210);
U("control","Điều khiển sinh vật",210,255); U("move","Di chuyển",500,225,160); U("dash","Dash",765,225,140);
U("survive","Sinh tồn",210,385); U("food","Ăn thức ăn",500,350,170); U("water","Bổ sung nước",755,350,170);
U("fight","Chiến đấu với người chơi khác",210,520,240); U("bite","Cắn đối thủ",535,485,170); U("counter","Phản công kẻ săn mồi",790,485,210);
U("evolve","Tiến hóa",210,655,180); U("observe","Theo dõi trạng thái trận đấu",475,655,240); U("hud","Xem HUD",790,610,150); U("board","Xem bảng xếp hạng",1010,610,190); U("map","Xem bản đồ nhỏ",790,710,170); U("replay","Chơi lại sau khi chết",210,800,220); U("health","Kiểm tra trạng thái máy chủ",975,800,225);
["join","control","survive","fight","evolve","observe","replay"].forEach((id,i)=>edge(uc,"pa"+i,"player",id,"","association"));
edge(uc,"op","operator","health","","association");
edge(uc,"i1","join","nickname","«include»","include"); edge(uc,"i2","join","starter","«include»","include");
edge(uc,"i3","control","move","«include»","include"); edge(uc,"x1","dash","control","«extend»","extend");
edge(uc,"i4","survive","food","«include»","include"); edge(uc,"i5","survive","water","«include»","include");
edge(uc,"i6","fight","bite","«include»","include"); edge(uc,"x2","counter","fight","«extend»","extend");
edge(uc,"i7","observe","hud","«include»","include"); edge(uc,"i8","observe","board","«include»","include"); edge(uc,"i9","observe","map","«include»","include");
vertex(uc,"cond","[Player cấp thấp ở phía sau kẻ săn mồi]",785,545,225,48,"note");
saveDrawio("usecase-diagram.drawio",[uc]); svg(uc,"usecase-diagram.svg");

// Application activity
const af = page("application-flow","Luồng hoạt động ứng dụng",1654,1169);
vertex(af,"aft","Mimope.io — Luồng hoạt động ứng dụng",430,15,800,40,"title");
vertex(af,"cl","Client",30,70,520,1040,"lane"); vertex(af,"ws","WebSocket layer",550,70,520,1040,"lane"); vertex(af,"sim","Game loop / simulation",1070,70,550,1040,"lane");
const A=(id,v,x,y,w=210,h=48,k="action")=>vertex(af,id,v,x,y,w,h,k);
A("s","",265,88,20,20,"initial"); A("open","Người chơi mở ứng dụng",150,118,210,38); A("home","Hiển thị HomeScreen",150,166,210,38); A("form","Nhập nickname và chọn starter",140,214,230,38); A("loading","Chuyển sang LoadingScreen",140,262,230,38); A("socket","GameConnection mở WebSocket /ws",135,310,240,38); A("connected","Kết nối thành công?",185,358,140,62,"decision"); A("cerr","Hiển thị lỗi và về HomeScreen",20,430,220,42); A("join","Gửi join",355,430,150,42);
A("validate","Rate-limit, decode; kiểm tra protocol version, nickname, starter",680,480,260,50); A("valid","Thông điệp hợp lệ?",735,540,150,62,"decision"); A("serr","Gửi error",575,612,145,40); A("enqueue","Enqueue Join",875,612,150,40);
A("spawn","Atomic capacity check và spawn",1235,662,230,42); A("capacity","Room còn chỗ?",1270,715,160,62,"decision"); A("welcome","Gửi welcome",880,790,150,40); A("full","Gửi error: room đầy",1400,790,180,40);
A("game","Lưu player ID; load assets; chuyển GameScreen",270,840,250,45); A("input","Thu input; gửi tối đa 20 Hz",40,900,220,42); A("latest","Validate input; lưu latest-wins",660,900,230,42); A("tick","Chạy tick; gửi death nếu có; tạo snapshot và event đã lọc",1220,900,270,42); A("render","Cập nhật Zustand; PixiJS nội suy và render",285,955,255,42); A("dead","Player đã chết?",70,1010,140,62,"decision"); A("death","Nhận death; chuyển DeathScreen",235,1020,220,42); A("again","Chơi lại?",465,1010,130,62,"decision"); A("end","",505,1080,22,22,"final");
edge(af,"f1","s","open"); edge(af,"f2","open","home"); edge(af,"f3","home","form"); edge(af,"f4","form","loading"); edge(af,"f5","loading","socket"); edge(af,"f6","socket","connected");
edge(af,"f7","connected","join","[Có]"); edge(af,"f8","connected","cerr","[Không]"); edge(af,"f9","cerr","home");
edge(af,"f10","join","validate"); edge(af,"f11","validate","valid"); edge(af,"f12","valid","enqueue","[Hợp lệ]"); edge(af,"f13","valid","serr","[Không hợp lệ]"); edge(af,"f14","serr","home");
edge(af,"f15","enqueue","spawn"); edge(af,"f16","spawn","capacity"); edge(af,"f17","capacity","welcome","[Có]"); edge(af,"f18","capacity","full","[Không]"); edge(af,"f19","full","home"); edge(af,"f20","welcome","game");
edge(af,"f21","game","input"); edge(af,"f22","input","latest"); edge(af,"f23","latest","tick"); edge(af,"f24","tick","render"); edge(af,"f25","render","dead"); edge(af,"f26","dead","input","[Không]"); edge(af,"f27","dead","death","[Có]"); edge(af,"f28","death","again"); edge(af,"f29","again","home","[Có]"); edge(af,"f30","again","end","[Không]");
vertex(af,"reconnect","Socket đóng ngoài dự kiến: thử reconnect tối đa 3 lần; thành công quay lại kết nối/join, thất bại báo lỗi và về HomeScreen.",650,1045,500,48,"note");
saveDrawio("application-flowchart.drawio",[af]); svg(af,"application-flowchart.svg");

// Class overview
const co = page("class-overview","Tổng quan client-server",1654,1169);
vertex(co,"cot","Mimope.io — Sơ đồ lớp: Tổng quan client-server",350,15,950,40,"title");
vertex(co,"cp","CLIENT — React / TypeScript / PixiJS",25,70,790,1060,"boundary"); vertex(co,"sp","SERVER — Spring Boot / Java",840,70,790,1060,"boundary");
const C=(p,id,v,x,y,w=210,h=88)=>vertex(p,id,v,x,y,w,h,"classBox");
C(co,"app","«component» App\nscreen: Screen\nđiều phối màn hình",70,120); C(co,"homec","«component» HomeScreen\nnickname, starter",330,110,190); C(co,"loadc","«component» LoadingScreen\nconnect / join",570,110,190); C(co,"gamec","«component» GameScreen\nHUD / leaderboard / minimap",70,260,220); C(co,"deathc","«component» DeathScreen\nchơi lại",330,260,190); C(co,"uist","«component» uiStore\nscreen, nickname, error, death",570,250,190);
C(co,"gamest","«component» gameStore\nlocalPlayerId, players, foods, tick",70,405,220); C(co,"conn","GameConnection\n-state: ConnectionState\n+connect(); +join(); +sendInput()",330,395,220,105); C(co,"proto","«component» protocol.ts\nPROTOCOL_VERSION\ncreateJoinMessage(); parseServerMessage()",590,395,190,105); C(co,"gcanvas","«component» GameCanvas\nPixi lifecycle bridge",70,560,220); C(co,"pixi","PixiGame\n+init(); +onSnapshot\n+fps; +playerPosition",330,550,220,105); C(co,"inputc","InputManager\n+attach(); +detach()\n+snapshot()",590,550,190,105);
C(co,"wsc","WebSocketConfig\n-properties\n+registerWebSocketHandlers()",875,120,220,100); C(co,"wsp","«record» WebSocketProperties\nallowedOriginPatterns",1150,120,210); C(co,"handler","GameWebSocketHandler\n-sessionRegistry; -gameRoom\n+afterConnectionEstablished()",1400,110,200,110); C(co,"client","ClientSession\n-session; -nickname\n+allowMessage(); +isOpen()",875,310,220,110); C(co,"registry","SessionRegistry\n-sessions\n+add(); +remove(); +getAll()",1150,310,210,110); C(co,"decoder","MessageDecoder\n+decode(json)",1400,300,200); C(co,"encoder","MessageEncoder\n+send(); +sendError()",875,490,220); C(co,"validator","NicknameValidator\n+validate(raw)",1150,490,210); C(co,"roomc","GameRoom\n-world; -loop\n+addPlayer(); +queueInput()",1400,475,200,115); C(co,"loopc","GameLoop\n-world; -tickRate\n+start(); +stop()",940,690,220,105); C(co,"worldc","GameWorld\n-players; -foods; -tick\n+tick(); +submit()",1280,680,240,105); C(co,"metrics","SnapshotMetrics\n+record(); +reset()",940,870,220); C(co,"cmdq","GameCommandQueue\n-queue\n+submit(); +drain()",1280,870,240);
edge(co,"c1","app","homec","","association"); edge(co,"c2","app","loadc","","association"); edge(co,"c3","app","gamec","","association"); edge(co,"c4","app","deathc","","association"); edge(co,"c5","app","uist","","dependency"); edge(co,"c6","loadc","conn","","dependency"); edge(co,"c7","gamec","gcanvas","","association"); edge(co,"c8","gcanvas","pixi","1","composition"); edge(co,"c9","pixi","inputc","1","composition"); edge(co,"c10","inputc","conn","gửi input","dependency"); edge(co,"c11","conn","proto","","dependency"); edge(co,"c12","conn","uist","cập nhật","dependency"); edge(co,"c13","conn","gamest","cập nhật","dependency"); edge(co,"c14","pixi","gamest","snapshot/render state","dependency");
edge(co,"s1","wsc","handler","cấu hình","dependency"); edge(co,"s2","wsc","wsp","1","association"); edge(co,"s3","handler","registry","","dependency"); edge(co,"s4","handler","decoder","","dependency"); edge(co,"s5","handler","encoder","","dependency"); edge(co,"s6","handler","validator","","dependency"); edge(co,"s7","handler","roomc","","dependency"); edge(co,"s8","registry","client","1 ◇— 0..*","aggregation"); edge(co,"s9","roomc","loopc","1","composition"); edge(co,"s10","roomc","worldc","1","composition"); edge(co,"s11","roomc","metrics","","dependency"); edge(co,"s12","loopc","worldc","tick()","dependency"); edge(co,"s13","worldc","cmdq","1","composition"); edge(co,"cross","conn","handler","WebSocket JSON protocol v2","protocol");

// Backend core
const bc = page("backend-core","Backend simulation core",1654,1169);
vertex(bc,"bct","Mimope.io — Sơ đồ lớp: Backend simulation core",370,15,900,40,"title");
C(bc,"gw","GameWorld\n-players; -foods; -tick\n+tick(); +submit(); +getVisibleEntities()",680,90,290,115); C(bc,"gq","GameCommandQueue\n-queue\n+submit(); +drain()",70,100,230,100); C(bc,"gc","«interface» GameCommand\n+execute(world)",370,100,230,90);
["Join","Remove","Evolve","ForceKill","GrantXp"].forEach((n,i)=>C(bc,"cmd"+i,`«record» ${n}\n+execute(world)`,50+i*200,260,170,75));
C(bc,"pe","PlayerEntity\n-id; -animal; -xp; -health; -water\n+queueInput(); +applyMovement(); +damage()",1090,90,260,125); C(bc,"fe","FoodEntity\n-instanceId; -definition; -x; -y\n+getFoodId(); +getXp()",1380,90,230,115);
C(bc,"grid","SpatialGrid\n-cellSize; -cells\n+insert(); +queryNearby()",70,430,230,105); C(bc,"spawnsvc","FoodSpawnService\n-maxFood\n+replenishFood()",350,430,230,95); C(bc,"events","WorldEventBuffer\n+clear(); +foodPickups(); +deaths()",630,430,230,95);
const systems=["MovementSystem","WaterSystem","HealthRegenerationSystem","FoodCollisionSystem","PredationSystem","EvolutionSystem"]; systems.forEach((n,i)=>C(bc,"sys"+i,`${n}\n+update()`,50+i*260,650,215,75));
C(bc,"animal","AnimalDefinition\nid, tier, speed, radius, biome\n+byId(); +evolutionOptions()",1040,900,260,110); C(bc,"fooddef","FoodDefinition\nid, xp, radius, minTier, biome\n+byId(); +all()",1340,900,250,110); C(bc,"biome","«enumeration» Biome\nLAND; OCEAN; ARCTIC; FINAL\n+fromProtocol()",730,910,250,105);
edge(bc,"b1","gw","gq","1","composition"); edge(bc,"b2","gq","gc","0..*","aggregation"); for(let i=0;i<5;i++) edge(bc,"impl"+i,"cmd"+i,"gc","","implementation");
edge(bc,"b3","gw","pe","1 ◼— 0..*","composition"); edge(bc,"b4","gw","fe","1 ◼— 0..*","composition"); edge(bc,"b5","gw","grid","1","composition"); edge(bc,"b6","gw","spawnsvc","1","composition"); edge(bc,"b7","gw","events","1","composition");
systems.forEach((_,i)=>edge(bc,"dep"+i,"gw","sys"+i,"điều phối","dependency")); edge(bc,"pdef","pe","animal","1","association"); edge(bc,"fdef","fe","fooddef","1","association"); edge(bc,"ab","animal","biome","1","association"); edge(bc,"fb","fooddef","biome","1","association");
saveDrawio("class-diagram.drawio",[co,bc]); svg(co,"class-diagram-overview.svg"); svg(bc,"class-diagram-backend-core.svg");

// Tick activity
const ta = page("game-tick","Sơ đồ hoạt động của một game tick",1654,1169);
vertex(ta,"tat","Mimope.io — Sơ đồ hoạt động của một game tick",370,15,900,40,"title");
vertex(ta,"gl","GameLoop",25,70,400,1040,"lane"); vertex(ta,"gws","GameWorld và gameplay systems",425,70,800,1040,"lane"); vertex(ta,"gr","GameRoom",1225,70,400,1040,"lane");
const T=(id,v,x,y,w=220,h=45,k="action")=>vertex(ta,id,v,x,y,w,h,k);
T("ts","",205,95,22,22,"initial"); T("dt","Tính dt",110,135); T("inc","Tăng tick; clear WorldEventBuffer",560,135,260); T("drain","Drain GameCommandQueue theo FIFO",560,200,260); T("mut","Thực thi mutation trên game-loop thread",560,265,260); T("move2","MovementSystem",470,340,190); T("water2","WaterSystem",700,340,190); T("regen2","HealthRegenerationSystem",930,340,220); T("grid1","Rebuild SpatialGrid lần một",560,420,260); T("food2","FoodCollisionSystem",470,495,190); T("pred2","PredationSystem",700,495,190); T("evo2","EvolutionSystem",930,495,190); T("replenish","FoodSpawnService.replenishFood",560,575,275); T("grid22","Rebuild SpatialGrid lần hai",560,645,260); T("return","Tick trả về GameLoop",110,645); T("send","Gửi evolution, death, leaderboard và filtered snapshot theo player",1300,730,250,75); T("metric","Ghi metrics",110,840); T("budget","Tick vượt budget?",150,910,150,80,"decision"); T("warn","Log warning",25,1020,150); T("sleep","Sleep thời gian còn lại",250,1020,170); T("next","Tick kế tiếp",535,1020,170); T("te","",740,1030,24,24,"final");
edge(ta,"t1","ts","dt"); edge(ta,"t2","dt","inc"); edge(ta,"t3","inc","drain"); edge(ta,"t4","drain","mut"); edge(ta,"t5","mut","move2"); edge(ta,"t6","move2","water2"); edge(ta,"t7","water2","regen2"); edge(ta,"t8","regen2","grid1"); edge(ta,"t9","grid1","food2"); edge(ta,"t10","food2","pred2"); edge(ta,"t11","pred2","evo2"); edge(ta,"t12","evo2","replenish"); edge(ta,"t13","replenish","grid22"); edge(ta,"t14","grid22","return"); edge(ta,"t15","return","send"); edge(ta,"t16","send","metric"); edge(ta,"t17","metric","budget"); edge(ta,"t18","budget","warn","[Có]"); edge(ta,"t19","budget","sleep","[Không]"); edge(ta,"t20","warn","next"); edge(ta,"t21","sleep","next"); edge(ta,"t22","next","dt","vòng lặp"); edge(ta,"t23","next","te","khi dừng loop");
vertex(ta,"n1","Water cạn → DeathEvent\nFood hợp lệ, chưa consume → XP + FoodPickupEvent\nBite gây chết → DeathEvent\nĐủ XP, chưa nhận option → evolution options",1260,170,320,145,"note");
vertex(ta,"n2","Food consumed membership: HashSet.\nKnockback được defer sau khi resolve bite.\nGrid lần một phục vụ collision; lần hai bảo đảm snapshot nhất quán.",1260,370,320,120,"note");
saveDrawio("game-tick-activity.drawio",[ta]); svg(ta,"game-tick-activity.svg");
