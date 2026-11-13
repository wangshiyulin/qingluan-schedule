// NODE_PATH=/tmp/academic-js-tests/node_modules node app/src/test/resources/import/capture.test.cjs
const {JSDOM}=require('jsdom'), fs=require('fs'),path=require('path'),assert=require('assert/strict');
const script=fs.readFileSync(path.resolve(__dirname,'../../../main/assets/import/capture.js'),'utf8');
const tick=()=>new Promise(r=>setTimeout(r,40));
function boot(html='<body></body>',url='https://school.example/semester') {
  const dom=new JSDOM(html,{url,runScripts:'outside-only'});const w=dom.window;
  w.TextDecoder=TextDecoder;w.TextEncoder=TextEncoder;
  class XHR { constructor(){this.listeners=[];this.responseType='';}addEventListener(t,f){if(t==='load')this.listeners.push(f)}send(){this.originalCalls=(this.originalCalls||0)+1;this.listeners.forEach(f=>f());return 42} }
  w.XMLHttpRequest=XHR;return {dom,w,start:()=>w.eval(script)};
}
(async()=>{
  {
    const b=boot('<body data-token="TOP_SECRET"><input value="PASSWORD_SECRET"><script>SECRET_SCRIPT</script><div>课表</div></body>');let requests=0;
    const payload={password:'PASSWORD_SECRET',token:'TOP_SECRET',data:{teachers:[{id:'t1',name:'陈老师',phone:'PHONE_SECRET'}],rooms:[{id:'r1',name:'400446—004'}],courses:[{courseName:'离散数学',id:'c1',day:2,startNode:1,step:3,weeks:[8,9,10],teacherId:'t1',classroomId:'r1',password:'PASSWORD_SECRET'},{courseName:'继承教师',id:'c2',teacher:{name:'张老师',phone:'INHERITED_SECRET'},meetings:[{day:1,startNode:1,weeks:[1]}]}]}};
    b.w.fetch=async()=>{requests++;return new Response(JSON.stringify(payload),{headers:{'content-type':'application/json'}})};b.start();
    const response=await b.w.fetch('/api/courses');assert.equal((await response.json()).password,'PASSWORD_SECRET');await tick();
    b.w.__qingluanAcademic.request();const capture=b.w.__qingluanAcademic.snapshot();const encoded=JSON.stringify(capture);
    assert.equal(requests,1,'observer must never replay requests');
    assert(capture.records.some(r=>r.courseName==='离散数学'));
    assert(capture.records.some(r=>r.__academicDictionary==='teacher' && r.name==='陈老师'));
    for(const secret of ['PASSWORD_SECRET','TOP_SECRET','PHONE_SECRET','SECRET_SCRIPT','INHERITED_SECRET'])assert(!encoded.includes(secret),secret);
    b.dom.window.close();
  }
  {
    const b=boot('<body><div id="course"></div></body>');b.w.fetch=async()=>new Response('{}');b.start();
    const xhr=new b.w.XMLHttpRequest();xhr.responseText=JSON.stringify({courses:[{name:'计算机系统基础',day:5,startPeriod:1,endPeriod:2,weeks:[11],teacher:'王老师',room:'A301'}]});
    assert.equal(xhr.send(),42);assert.equal(xhr.originalCalls,1);
    b.w.document.getElementById('course').__vueParentComponent={props:{course:{name:'隐藏数据课程',teacher:{name:'李老师',phone:'PRIVATE_PHONE'}},day:3,startNode:6,step:2,weeks:[11],location:'B302'}};
    b.w.__qingluanAcademic.request();const rows=b.w.__qingluanAcademic.snapshot().records;
    assert(rows.some(r=>r.name==='计算机系统基础' && r.teacher==='王老师'));
    assert(rows.some(r=>r.courseName==='隐藏数据课程' && r.teacher==='李老师'));
    assert(!JSON.stringify(rows).includes('PRIVATE_PHONE'));b.dom.window.close();
  }
  {
    const b=boot('<body><iframe src="https://embedded.example/table"></iframe></body>');b.w.fetch=async()=>new Response('{}');
    const child={postMessage:()=>{}};Object.defineProperty(b.w.document.querySelector('iframe'),'contentWindow',{value:child});b.start();
    const id=b.w.__qingluanAcademic.request();
    function message(source,origin,data){b.w.dispatchEvent(new b.w.MessageEvent('message',{source,origin,data}))}
    message({},'https://attacker.example',{qingluanAcademic:1,type:'records',rows:[{courseName:'不应读取',day:1,startNode:1}]});
    assert(!b.w.__qingluanAcademic.snapshot().records.some(r=>r.courseName==='不应读取'));
    message(child,'https://embedded.example',{qingluanAcademic:1,type:'document',id,frame:'outer',pending:1,html:'<table>outer</table>'});
    message(child,'https://embedded.example',{qingluanAcademic:1,type:'document',id,frame:'inner',pending:0,html:'<table>inner</table>'});
    const result=b.w.__qingluanAcademic.snapshot();assert.equal(result.documents.length,3);assert.equal(result.incomplete,false);b.dom.window.close();
  }
  {
    const b=boot();let response;
    b.w.fetch=async()=>response=new Response(new ReadableStream({start(c){for(let i=0;i<6;i++)c.enqueue(new Uint8Array(1024*1024));c.close()}}),{headers:{'content-type':'application/json'}});
    b.start();await b.w.fetch('/api/large');await tick();await tick();
    assert(b.w.__qingluanAcademic.snapshot().warnings.some(w=>w.includes('过大')));await response.body.cancel();b.dom.window.close();
  }
  {
    const b=boot('<body></body>','http://insecure.example');b.start();assert.equal(b.w.__qingluanAcademic,undefined);b.dom.window.close();
  }
  console.log('5 capture checks passed: read-only fetch/XHR, secret filtering, framework metadata, frame provenance/relay, bounded streams/HTTPS.');
})().catch(e=>{console.error(e);process.exitCode=1});
