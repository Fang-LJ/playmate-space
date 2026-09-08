#!/usr/bin/env python3
"""Real HTTP + MySQL standalone-book regression. Requires a local mock-login server.
Usage: PLAYMATE_API_BASE_URL=http://127.0.0.1:18080 python3 scripts/book-smoke-test.py
Creates only uniquely named test accounts/books; does not modify existing user books.
"""
import concurrent.futures
import json
import os
import time
import urllib.request
import urllib.error
from decimal import Decimal

BASE = os.environ.get('PLAYMATE_API_BASE_URL', 'http://127.0.0.1:18080')
stamp = str(time.time_ns())
passed = 0

def api(path, token=None, data=None, method=None, success=True):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE+path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method or ('POST' if data is not None else 'GET'))
    try:
        with urllib.request.urlopen(req, timeout=20) as response: body = json.load(response, parse_float=Decimal)
    except urllib.error.HTTPError as error:
        body = json.load(error, parse_float=Decimal)
    if success is True: assert body['code'] == 'SUCCESS', (path, body)
    if success is False: assert body['code'] != 'SUCCESS', (path, body)
    return body.get('data') if success is True else body

def check(name):
    global passed
    passed += 1
    print('[PASS]', name, flush=True)

def login(name):
    result = api('/api/auth/wx-login', data={'mockOpenid': 'book-smoke-'+name+'-'+stamp, 'nickname': '算账测试'+name})
    return result['token'], result['userId']

A, user_a = login('A'); B, user_b = login('B'); C, user_c = login('C'); D, user_d = login('D')
create = {'name': '独立算账测试 '+stamp, 'names': ['小明','小红'], 'clientRequestId': stamp}
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    created = list(pool.map(lambda _: api('/api/books', A, create), range(2)))
assert created[0]['bookId'] == created[1]['bookId']
book = created[0]; book_id = book['bookId']; path = f'/api/books/{book_id}'
dash = api(path,A); me, ming, hong = [m['memberId'] for m in dash['members']]
assert len(dash['members']) == 3 and dash['totalAmount'] == 0
check('并发创建幂等；无需好友登录即可创建手动成员')
api(path,B,success=False); api(path,success=False)
preview = api('/api/book-invites/'+book['shareCode'])
assert 'totalAmount' not in preview and 'book' not in preview
assert len(preview['members']) == 2
check('非成员不可查看账目；公开邀请不泄露金额和凭证')

def expense(title,amount,payer,participants,mode='EQUAL',request=None):
    return {'title': title,'amount':amount,'category':'FOOD','payerMemberId':payer,
            'expenseTime':'2026-09-08T18:00:00','splitMode':mode,'shares':participants,
            'clientRequestId':request or title+'-'+stamp}
def selected(*ids): return [{'memberId': i} for i in ids]

dinner = expense('晚饭','300.00',me,selected(me,ming,hong))
e = api(path+'/expenses',A,dinner)
api(path+'/expenses',B,dinner,success=False)
with concurrent.futures.ThreadPoolExecutor(4) as pool:
    retry = list(pool.map(lambda _: api(path+'/expenses',A,dinner),range(4)))
assert all(r['expenseId']==e['expenseId'] for r in retry)
api(path+'/expenses',A,expense('奶茶','36.00',ming,selected(me,ming)))
api(path+'/expenses',A,expense('打车','30.00',hong,selected(ming,hong)))
dash = api(path,A)
assert dash['totalAmount'] == 366 and dash['expenseCount']==3
assert {m['memberId']:m['netAmount'] for m in dash['members']} == {me:182,ming:-97,hong:-85}
assert [s['amount'] for s in dash['suggestions']]==[97,85]
check('不同付款人和分摊人合并准确；消费并发重试不重复入账')

for invalid in [expense('负数','-1',me,selected(me)),expense('重复成员','10',me,selected(me,me)),expense('不平衡','10',me,[{'memberId':me,'shareAmount':'9'}],'CUSTOM'),expense('零比例','10',me,[{'memberId':me,'splitRatio':'0'}],'PROPORTIONAL')]:
    api(path+'/expenses',A,invalid,success=False)
other = api('/api/books',A,{'name':'跨账本测试 '+stamp,'names':[],'clientRequestId':'other-'+stamp})
other_member = api(f"/api/books/{other['bookId']}",A)['myMemberId']
api(path+'/expenses',A,expense('外部付款人','10',other_member,selected(me)),success=False)
api(path+'/expenses',A,expense('外部分摊人','10',me,selected(other_member)),success=False)
check('拒绝非法金额、重复成员、不平衡分摊及跨账本付款/分摊')

for mode, parts in [('EQUAL',selected(me,ming,hong)),('PROPORTIONAL',[{'memberId':me,'splitRatio':'1'},{'memberId':ming,'splitRatio':'2'},{'memberId':hong,'splitRatio':'3'}])]:
    small=api(path+'/expenses',A,expense(mode,'0.01',me,parts,mode))
    assert sum(Decimal(s['shareAmount']) for s in small['shares']) == Decimal('.01')
    api(path+f"/expenses/{small['expenseId']}/void",A,{'expectedVersion':small['version']})
assert api(path,A)['totalAmount']==366
check('均摊/比例尾差精确到分，作废后不参与结算')

claim_payload={'code':book['shareCode'],'memberId':ming}
assert api('/api/books/join',B,claim_payload)['status']=='PENDING'
assert api('/api/books/join-status?code='+book['shareCode'],B)['status']=='PENDING'
assert api('/api/books/join',B,claim_payload)['status']=='PENDING'
assert api('/api/books/join',C,claim_payload)['status']=='PENDING'
api(path,B,success=False)
dash=api(path,A); assert len(dash['claims'])==2
for claim in dash['claims']: api(path+f"/claims/{claim['claimId']}",D,{'approve':True},success=False)
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    decisions=list(pool.map(lambda claim: api(path+f"/claims/{claim['claimId']}",A,{'approve':True},success=None),dash['claims']))
assert sum(r['code']=='SUCCESS' for r in decisions)==1
new_dash=api(path,A); linked=next(m for m in new_dash['members'] if m['memberId']==ming)
member_token = B if linked['userId']==user_b else C
loser_token = C if member_token==B else B
assert len(new_dash['members'])==3 and new_dash['totalAmount']==366
assert new_dash['suggestions']==dash['suggestions']
assert api('/api/books/join',member_token,claim_payload)['status']=='JOINED'
assert api('/api/books/join-status?code='+book['shareCode'],member_token)['status']=='JOINED'
assert api('/api/books/join-status?code='+book['shareCode'],loser_token)['status']=='REJECTED'
check('关联需创建者审批；并发认领只有一人成功，成员ID和历史分摊不变')

api(path+'/expenses',member_token,expense('违规代记','1',me,selected(me)),success=False)
api(path+f"/expenses/{e['expenseId']}",member_token,{**dinner,'version':1},method='PUT',success=False)
api(path+'/members',member_token,{'nickname':'越权成员'},success=False)
api(path+'/state',member_token,{'status':'ARCHIVED','version':new_dash['book']['version']},success=False)
member_exp=api(path+'/expenses',member_token,expense('本人付款','5',ming,selected(ming)))
api(path+f"/expenses/{member_exp['expenseId']}/void",member_token,{'expectedVersion':1})
check('普通成员可记自己的付款，但不能代记、改他人账目或管理账本')

before=api(path,A)
api('/api/books/join',D,{'code':book['shareCode']})
after=api(path,A)
assert after['memberCount']==4 and after['suggestions']==before['suggestions']
assert len(api(path+f"/expenses/{e['expenseId']}",A)['shares'])==3
check('邀请新成员不会自动重分摊历史消费')

current=api(path+f"/expenses/{e['expenseId']}",A)
update={**dinner,'title':'晚饭更新','version':current['version']}
with concurrent.futures.ThreadPoolExecutor(2) as pool:
    updates=list(pool.map(lambda _: api(path+f"/expenses/{e['expenseId']}",A,update,method='PUT',success=None),range(2)))
assert sum(r['code']=='SUCCESS' for r in updates)==1
api(path+f"/expenses/{e['expenseId']}/void",A,{'expectedVersion':1},success=False)
check('并发编辑只成功一次；旧版本作废被拒绝')

api(path+f'/members/{ming}/state',A,{'status':'INACTIVE'})
api(path,member_token,success=False)
api('/api/books/join',member_token,claim_payload,success=False)
assert api(path,A)['totalAmount']==366
api(path+f'/members/{ming}/state',A,{'status':'ACTIVE'})
assert api(path,member_token)['myMemberId']==ming
check('停用阻止访问与重新加入；恢复后历史身份和余额不变')

before=api(path,A)
api(path+'/state',A,{'status':'ARCHIVED','version':before['book']['version']})
api(path+'/expenses',A,expense('归档后写入','1',me,selected(me)),success=False)
api(path+'/members',A,{'nickname':'归档后成员'},success=False)
api('/api/books/join',loser_token,{'code':book['shareCode']},success=False)
assert api(path,A)['totalAmount']==366
assert api(path+f"/expenses/{e['expenseId']}",A)['canEdit'] is False
api(path+'/state',A,{'status':'OPEN','version':before['book']['version']},success=False)
current=api(path,A)
api(path+'/state',A,{'status':'OPEN','version':current['book']['version']})
check('归档只读且不清空余额；重新打开需要最新版本')

for i in range(31): api(path+'/expenses',A,expense(f'分页{i}','1',me,selected(me)))
page1=api(path+'/expenses?page=1',A);page2=api(path+'/expenses?page=2',A)
assert len(page1['items'])==30 and page1['hasMore'] and len(page2['items'])==4 and not page2['hasMore']
assert not ({i['expenseId'] for i in page1['items']} & {i['expenseId'] for i in page2['items']})
check('消费分页完整且无重复')

for target in [book_id,other['bookId']]:
    current=api(f'/api/books/{target}',A)
    api(f'/api/books/{target}/state',A,{'status':'ARCHIVED','version':current['book']['version']})
assert any(b['bookId']==book_id for b in api('/api/books?status=ARCHIVED',A)['items'])
assert all(b['bookId']!=book_id for b in api('/api/books?status=OPEN',A)['items'])
check('账本首页按进行中/归档筛选并展示本人净额')
print(f'{passed} integration scenarios passed')
