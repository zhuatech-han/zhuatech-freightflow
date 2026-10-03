#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""在可丢弃数据库中验证真实运输及结算HTTP流程；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, json, uuid, urllib.request, urllib.error, http.cookiejar, os, datetime, struct, zlib, concurrent.futures
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);p.add_argument('--env',required=True);p.add_argument('--allow-test-writes',action='store_true');p.add_argument('--state');a=p.parse_args()
if not a.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable database.')
env=dict(line.split('=',1) for line in Path(a.env).read_text().splitlines() if '=' in line and not line.startswith('#'))
base=a.base.rstrip('/');suffix=uuid.uuid4().hex[:8];count=0
class Client:
    """隔离测试的会话与CSRF，拒绝将凭证输出到日志；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self):self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.token=None
    def call(self,path,body=None,method=None,status=200):
        global count
        if body is not None and not self.token:self.token=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if body is not None:headers[self.token['header']]=self.token['token']
        req=urllib.request.Request(base+('' if path.startswith('/actuator') else '/api')+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method or ('GET' if body is None else 'POST'))
        try:r=self.opener.open(req,timeout=30)
        except urllib.error.HTTPError as e:r=e
        raw=r.read().decode('utf-8-sig');assert r.status==status,(path,r.status,raw)
        count+=1
        try:return json.loads(raw)
        except json.JSONDecodeError:return raw
    def login(self,user,pw):self.call('/auth/login',{'username':user,'password':pw});self.token=None
    def proof(self,shipment,png,status=200):
        global count
        if not self.token:self.token=self.call('/auth/csrf')
        boundary='ff'+uuid.uuid4().hex
        payload=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="TEST-POD.png"\r\nContent-Type: image/png\r\n\r\n').encode()+png+f'\r\n--{boundary}--\r\n'.encode()
        req=urllib.request.Request(base+f'/api/shipments/{shipment}/proofs',data=payload,headers={'Content-Type':'multipart/form-data; boundary='+boundary,self.token['header']:self.token['token']})
        try:r=self.opener.open(req,timeout=30)
        except urllib.error.HTTPError as e:r=e
        raw=r.read().decode();assert r.status==status,(r.status,raw);count+=1;return json.loads(raw)
admin=Client();admin.login(env.get('ADMIN_USERNAME','admin'),env['ADMIN_PASSWORD']);assert admin.call('/actuator/health')['status']=='UP'
roles=admin.call('/lists/roles?size=100')['items']
def user(name,role):
    v={'username':name,'displayName':'TEST '+name,'password':'Aa9'+uuid.uuid4().hex,'roleId':next(x['id'] for x in roles if role in x['name']),'departmentId':1,'enabled':True}
    ident=admin.call('/admin/users',v)['id'];c=Client();c.login(name,v['password']);return v,ident,c
custdata,custacc,client=user('customer'+suffix,'Customer');driverdata,driveracc,driver=user('driver'+suffix,'Driver');fdata,facct,finance=user('finance'+suffix,'Finance');ddata,dacct,dispatch=user('dispatch'+suffix,'Dispatcher');otherdata,otheracc,other=user('other'+suffix,'Customer')
def master(kind,code,name,**kwargs):return admin.call('/master/'+kind,{'code':code+'-'+suffix,'name':name,'departmentId':1,'enabled':True,**kwargs})['id']
customer=master('customers','TEST-P','TEST 托运客户 / Consignor',accountId=custacc);foreign=master('customers','TEST-F','TEST 外部客户 / Foreign',accountId=otheracc)
carrier=master('carriers','TEST-C','TEST 承运商 / Carrier');vehicle=master('vehicles','TEST-V','TEST 厢式货车 / Box truck',carrierId=carrier,maxWeightKg='10000',maxVolumeM3='50',maxPieces=100);person=master('drivers','TEST-D','TEST 驾驶员 / Driver',carrierId=carrier,accountId=driveracc,licenseNumber='TEST-LICENCE',licenseExpires='2027-10-01')
now=datetime.datetime.now(datetime.timezone.utc).replace(second=0,microsecond=0)
def iso(t):return t.isoformat().replace('+00:00','Z')
def sb(**kw):return {'customerId':customer,'origin':'TEST 上海装货点 / Shanghai pickup','destination':'TEST 苏州卸货点 / Suzhou delivery','consignee':'TEST 收货人 / Receiver','consigneePhone':'','goods':'TEST 机械配件 / Machine parts','pieces':8,'weightKg':'850','volumeM3':'4.2','requestKey':uuid.uuid4().hex,**kw}
def tb(**kw):return {'carrierId':carrier,'driverId':person,'vehicleId':vehicle,'plannedStart':iso(now-datetime.timedelta(hours=1)),'plannedEnd':iso(now+datetime.timedelta(hours=8)),'carrierFee':'600','requestKey':uuid.uuid4().hex,'note':'TEST 验收车次 / Acceptance trip',**kw}
shipbody=sb(customerId=999999);s=client.call('/shipments',shipbody)['id'];assert client.call('/shipments',shipbody)['id']==s
assert admin.call('/shipments/'+str(s))['shipment']['customerId']==customer
other.call('/shipments/'+str(s),status=403)
client.call('/trips',tb(),status=403)
t=dispatch.call('/trips',tb())['id'];dispatch.call('/trips',tb(),status=409)
def detail(kind,id,c=admin):return c.call(f'/{kind}/{id}')[{'shipments':'shipment','trips':'trip','invoices':'invoice'}[kind]]
def cmd(kind,id,**kw):return {'revision':detail(kind,id)['revision'],'requestKey':uuid.uuid4().hex,**kw}
def act(kind,id,action,c=admin,status=200,**kw):return c.call(f'/{kind}/{id}/{action}',cmd(kind,id,**kw),status=status)
act('shipments',s,'quote',dispatch,freight='1000');act('trips',t,'add',dispatch,shipmentId=s)
act('trips',t,'accept',admin,403);act('trips',t,'depart',driver,409);act('trips',t,'dispatch',dispatch);act('trips',t,'accept',driver);act('trips',t,'depart',driver)
assert 'freight' not in detail('shipments',s,driver) and 'carrierFee' not in detail('trips',t,driver)
act('trips',t,'close',dispatch,409)
act('shipments',s,'deliver',driver,409,receiver='TEST',receivedPieces=8)
# A valid generated test PNG; raw evidence is stored in the database and independently access checked.
def chunk(k,v):return struct.pack('>I',len(v))+k+v+struct.pack('>I',zlib.crc32(k+v)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,24,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\x00'+b'\xa0\xb8\xd5'*32)*24))+chunk(b'IEND',b'')
proof=driver.proof(s,png);assert driver.proof(s,png)['id']==proof['id'];client.proof(s,png,403)
# Protected binary evidence is readable by the consignor, but not an unrelated customer.
for c,status in [(client,200),(other,403)]:
    try:r=c.opener.open(base+'/api/proofs/'+str(proof['id']))
    except urllib.error.HTTPError as e:r=e
    assert r.status==status;r.read();count+=1
act('shipments',s,'deliver',driver,409,receiver='TEST',receivedPieces=7)
issue=act('shipments',s,'issue',driver,note='TEST 卸货延迟 / Unloading delay')
act('shipments',s,'deliver',driver,receiver='TEST 签收 / Receiver',receivedPieces=8)
act('shipments',s,'confirm-delivery',dispatch,409)
dispatch.call('/issues/'+str(issue['id'])+'/resolve',{'requestKey':uuid.uuid4().hex,'revision':issue['revision'],'note':'TEST 已协调卸货 / Unloading resolved'})
act('shipments',s,'confirm-delivery',driver,403);act('shipments',s,'confirm-delivery',dispatch)
expense={'tripId':t,'type':'TOLL','amount':'20','reference':'TEST-TOLL','note':'TEST 实报通行费 / Toll expense','requestKey':uuid.uuid4().hex}
e=driver.call('/expenses',expense);assert driver.call('/expenses',expense)['id']==e['id'];act('trips',t,'close',dispatch,409)
finance.call('/expenses/'+str(e['id'])+'/review',{'revision':e['revision'],'requestKey':uuid.uuid4().hex,'approved':True,'note':'TEST 独立复核 / Independent review'});act('trips',t,'close',dispatch)
today=(now+datetime.timedelta(hours=8)).date().isoformat()
ar=finance.call('/invoices',{'kind':'RECEIVABLE','customerId':customer,'shipmentIds':[s],'dueDate':today,'requestKey':uuid.uuid4().hex})['id'];ap=finance.call('/invoices',{'kind':'PAYABLE','tripId':t,'dueDate':today,'requestKey':uuid.uuid4().hex})['id'];assert detail('invoices',ap)['total']==620
other.call('/invoices/'+str(ar),status=403);client.call('/invoices/'+str(ap),status=403);driver.call('/lists/invoices',status=403)
act('invoices',ar,'pay',finance,409,amount='1001',method='BANK',reference='TEST-OVER')
parallel=[]
for n in range(2):c=Client();c.login(fdata['username'],fdata['password']);c.call('/auth/csrf');parallel.append(c)
commands=[cmd('invoices',ar,amount='400',method='BANK',reference='TEST-CONCURRENT-'+str(n)) for n in range(2)]
def contested(pair):
    c,v=pair
    try:c.call(f'/invoices/{ar}/pay',v);return 200,v
    except AssertionError as e:assert e.args[0][1]==409,e.args;return 409,v
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(contested,zip(parallel,commands)))
assert sorted(x[0] for x in results)==[200,409];winner=next(v for code,v in results if code==200);finance.call(f'/invoices/{ar}/pay',winner)
assert detail('invoices',ar)['paid']==400
act('invoices',ar,'pay',finance,amount='600',method='BANK',reference='TEST-FULL')
source=finance.call('/invoices/'+str(ar))['entries'][0]['id'];v=cmd('invoices',ar,sourceId=source,amount='100',reference='TEST-REVERSAL',note='TEST 部分冲正 / Partial reversal');finance.call(f'/invoices/{ar}/reverse',v);finance.call(f'/invoices/{ar}/reverse',v)
act('invoices',ar,'reverse',finance,409,sourceId=source,amount='301',reference='TEST-OVER',note='TEST 超额 / Excess')
act('invoices',ap,'pay',finance,amount='620',method='BANK',reference='TEST-CARRIER')
r=finance.call('/reports?from='+today+'&to='+today);assert r['revenue']==1000 and r['cost']==620 and r['profit']==380 and r['receipts']==900 and r['disbursements']==620 and r['netCash']==280 and r['receivables']==100,r
assert 'zhuatech' not in finance.call('/reports.csv?from='+today+'&to='+today)
Client().call('/lists/shipments',status=401);dispatch.call('/reports?from='+today+'&to='+today,status=403)
# Import is atomic: a later invalid row rolls back the earlier valid row.
before=admin.call('/lists/shipments?size=100')['total'];admin.call('/shipments/import',{'requestKey':uuid.uuid4().hex,'items':[sb(),sb(pieces=0)]},status=400);assert admin.call('/lists/shipments?size=100')['total']==before
# Leave genuine test workflow records for browser QA, all explicitly TEST-labelled.
newship=client.call('/shipments',sb(goods='TEST 浏览器验收 / Browser acceptance'))['id'];newtrip=dispatch.call('/trips',tb())['id'];client.call('/lists/users',status=403)
if a.state:
    data={'base':base,'admin':{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_PASSWORD']},'customer':custdata,'driver':driverdata,'finance':fdata,'dispatch':ddata,'shipment':s,'trip':t,'ar':ar,'ap':ap,'newShipment':newship,'newTrip':newtrip,'customerId':customer,'carrier':carrier,'vehicle':vehicle,'driverId':person,'today':today,'proof':proof['id']}
    fd=os.open(a.state,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f:json.dump(data,f)
    os.chmod(a.state,0o600);Path(a.state+'.png').write_bytes(png)
print(f'PASS: {count} real HTTP assertions; driver execution, photo POD, independent reviews, AR/AP, partial payments, linked reversal, concurrent payment, scope and atomic import')
