"""Reversible App Tester setup for the isolated sandbox purchase package only.

Never uninstalls, clears or launches the ordinary DishNest app. Private recovery
state lives outside the exFAT checkout on the publisher's protected NTFS folder.
"""
import argparse,base64,hashlib,json,os,re,subprocess,time,sys
from pathlib import Path
from datetime import datetime,timezone
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
sys.stdout.reconfigure(encoding='utf-8')
PACKAGE='com.dishnest.app.purchasetest'
PRIVATE=Path(os.environ['LOCALAPPDATA'])/'DishNest/publisher-private'
STATE=PRIVATE/'purchase-test-recovery.json'
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
def digest(data):return hashlib.sha256(data).hexdigest()
def device():
    lines=subprocess.check_output([str(ADB),'devices'],text=True).splitlines()
    devices=[line.split()[0] for line in lines if re.match(r'^\S+\s+device\s*$',line)]
    if len(devices)!=1:raise ValueError('Exactly one authorized Fire required')
    return devices[0]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['setup','restore','snapshot','tap','start','tester','record','catalog','scroll','confirm-settings','screen','tap-screen']);parser.add_argument('--text');parser.add_argument('--row');parser.add_argument('--case');parser.add_argument('--premium',choices=['active','inactive']);parser.add_argument('--screenshot',action='store_true');parser.add_argument('--direction',choices=['down','up'],default='down');parser.add_argument('--original-user',choices=['1','2','3']);parser.add_argument('--original-autorenew',choices=['Off','On']);parser.add_argument('--original-login',choices=['Logged In','Logged Out']);parser.add_argument('--x',type=int);parser.add_argument('--y',type=int);parser.add_argument('--package',choices=[PACKAGE,'com.amazon.sdktestclient']);args=parser.parse_args()
    serial=device()
    def adb(*parts,check=True):return subprocess.run([str(ADB),'-s',serial,*parts],capture_output=True,check=check,timeout=30).stdout
    def ordinary_hashes():
        paths=adb('exec-out','run-as','com.dishnest.app','find','.','-type','f').decode().splitlines()
        return {path:digest(adb('exec-out','run-as','com.dishnest.app','cat',path)) for path in paths if path.startswith(('./databases/','./shared_prefs/','./files/'))}
    def unlocked():
        policy=adb('shell','dumpsys','window','policy').decode(errors='replace')
        if re.search(r'isStatusBarKeyguard=true|mIsShowing=true',policy):raise ValueError('Unlock Fire; secure lock cannot be bypassed')
    def snapshot():
        unlocked();path='/data/local/tmp/dishnest-purchase-window.xml'
        for attempt in range(4):
            result=adb('shell','uiautomator','dump',path)
            if b'dumped to:' in result:
                try:return ET.fromstring(adb('exec-out','cat',path))
                except ET.ParseError:pass
            # Give Fire's accessibility bridge time to detach between failed dumps.
            if attempt < 3:time.sleep(2 ** attempt)
        raise ValueError('No valid Fire UI hierarchy')
    if args.action in ('screen','tap-screen'):
        # Manual visual fallback when Fire's accessibility root is unavailable.
        # The operator must inspect each captured image before choosing coordinates.
        # It never records a passing test; normal signed-status assertions still do.
        unlocked()
        windows=adb('shell','dumpsys','window','windows').decode(errors='replace')
        focused=re.search(r'mCurrentFocus=[^\n]* u\d+ ([a-zA-Z0-9_.]+)/',windows)
        allowed=(PACKAGE,'com.amazon.sdktestclient')
        if not focused or focused[1] not in allowed:raise ValueError('Only isolated test windows may be captured or tapped')
        pixels=adb('exec-out','screencap','-p')
        if args.action=='tap-screen':
            import struct
            if args.package!=focused[1] or args.x is None or args.y is None:raise ValueError('Explicit observed test-window package and coordinates required')
            if adb('shell','getprop','debug.amazon.sandboxmode').decode().strip()!='debug':raise ValueError('Sandbox routing required for manual test taps')
            if not pixels.startswith(b'\x89PNG\r\n\x1a\n'):raise ValueError('No valid current test screenshot')
            width,height=struct.unpack('>II',pixels[16:24])
            if not (0<=args.x<width and 0<=args.y<height):raise ValueError('Coordinates outside the observed test screen')
            adb('shell','input','tap',str(args.x),str(args.y))
            windows=adb('shell','dumpsys','window','windows').decode(errors='replace')
            after=re.search(r'mCurrentFocus=[^\n]* u\d+ ([a-zA-Z0-9_.]+)/',windows)
            if not after or after[1] not in allowed:raise ValueError('Refusing a screenshot outside isolated test windows')
            pixels=adb('exec-out','screencap','-p')
        target=ROOT/'build/fire-test-screen.png';target.parent.mkdir(exist_ok=True);target.write_bytes(pixels)
        print(json.dumps({'focusPackage':focused[1],'screenPath':str(target),'testPassed':False}))
    elif args.action=='setup':
        unlocked()
        if not all([args.original_user,args.original_autorenew,args.original_login]):raise ValueError('Read App Tester settings first; supply all three original-setting arguments for exact restoration')
        if STATE.exists() and not json.loads(STATE.read_text(encoding='utf-8')).get('restored'):raise ValueError('Restore the existing purchase test session first')
        if not PRIVATE.is_dir():raise ValueError('Publisher protected storage required')
        mode=adb('shell','getprop','debug.amazon.sandboxmode').decode().strip()
        overlay=adb('shell','appops','get','com.amazon.sdktestclient','SYSTEM_ALERT_WINDOW').decode()
        if 'allow' not in overlay:raise ValueError('App Tester overlay permission is not already allowed; no permission changed')
        listed=adb('shell','ls','/sdcard/amazon.sdktester.json',check=False)
        original=adb('exec-out','cat','/sdcard/amazon.sdktester.json') if b'amazon.sdktester.json' in listed else None
        merged=json.loads(original) if original else {}
        merged.update(json.loads((ROOT/'billing-server/amazon.sdktester.json').read_text(encoding='utf-8')))
        state={'startedAt':datetime.now(timezone.utc).isoformat(),'originalMode':mode,'originalCatalog':base64.b64encode(original).decode() if original is not None else None,'ordinaryHashes':ordinary_hashes(),'restored':False,'originalTesterSettings':{'user':'Amazon User '+args.original_user,'login':args.original_login,'autoRenew':args.original_autorenew}}
        STATE.write_text(json.dumps(state),encoding='utf-8')
        fixture=PRIVATE/'purchase-test-catalog.json';fixture.write_text(json.dumps(merged),encoding='utf-8')
        adb('push',str(fixture),'/sdcard/amazon.sdktester.json')
        adb('shell','setprop','debug.amazon.sandboxmode','debug')
        if adb('shell','getprop','debug.amazon.sandboxmode').decode().strip()!='debug':raise ValueError('Sandbox routing not verified')
        print(json.dumps({'sandboxRouting':True,'catalogMerged':True,'ordinaryAppModified':False,'recoveryStateProtected':True}))
    elif args.action=='catalog':
        state=json.loads(STATE.read_text(encoding='utf-8'))
        if state.get('restored'):raise ValueError('Active recoverable sandbox session required')
        merged=json.loads(adb('exec-out','cat','/sdcard/amazon.sdktester.json'))
        merged.update(json.loads((ROOT/'billing-server/amazon.sdktester.json').read_text(encoding='utf-8')))
        fixture=PRIVATE/'purchase-test-catalog.json';fixture.write_text(json.dumps(merged),encoding='utf-8')
        adb('push',str(fixture),'/sdcard/amazon.sdktester.json')
        print('Updated only isolated DishNest catalog fixtures; original catalog backup retained')
    elif args.action=='restore':
        state=json.loads(STATE.read_text(encoding='utf-8'))
        if state.get('restored'):raise ValueError('Session already restored')
        if not state.get('testerUserRestored') or not state.get('testerSubscriptionSettingsRestored'):raise ValueError('Verify original App Tester account and subscription settings before restoration')
        adb('shell','am','force-stop',PACKAGE)
        original=state['originalCatalog']
        if original is None:adb('shell','rm','-f','/sdcard/amazon.sdktester.json')
        else:
            payload=base64.b64decode(original);fixture=PRIVATE/'purchase-test-restore.json';fixture.write_bytes(payload)
            adb('push',str(fixture),'/sdcard/amazon.sdktester.json')
            if digest(adb('exec-out','cat','/sdcard/amazon.sdktester.json'))!=digest(payload):raise ValueError('Catalog restoration failed')
        mode=state['originalMode'] or 'none';adb('shell','setprop','debug.amazon.sandboxmode',mode)
        if adb('shell','getprop','debug.amazon.sandboxmode').decode().strip()!=mode:raise ValueError('Sandbox property restoration failed')
        adb('shell','cmd','deviceidle','tempwhitelist','-r','com.amazon.sdktestclient')
        preserved=ordinary_hashes()==state['ordinaryHashes']
        state.update(restored=True,ordinaryDataHashesUnchanged=preserved,restoredAt=datetime.now(timezone.utc).isoformat())
        STATE.write_text(json.dumps(state),encoding='utf-8')
        report={'restoredAt':state['restoredAt'],'packageId':PACKAGE,'apkSha256':digest((ROOT/'app/build/outputs/apk/purchaseTest/app-purchaseTest.apk').read_bytes()),'controllerSha256':digest(Path(__file__).read_bytes()),'catalogRestored':True,'sandboxPropertyRestored':True,'testerUserRestored':True,'testerSubscriptionSettingsRestored':True,'temporaryAllowanceRemoved':True,'overlayPermissionChanged':False,'ordinaryDataHashesUnchanged':preserved,'ordinaryAppModified':False,'serialIdentifierStored':False,'testReceiptIdentifiersStored':False}
        (ROOT/'artifacts/purchase-test-restoration.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps(report))
        if not preserved:raise ValueError('Ordinary data changed independently; inspect locally before claiming preservation')
    elif args.action=='record':
        unlocked()
        if not args.case or not re.fullmatch(r'[a-z0-9-]+',args.case) or not args.premium:raise ValueError('Case and expected premium state required')
        if args.screenshot:
            visible=snapshot()
            if not any(n.get('package')==PACKAGE for n in visible.iter('node')):raise ValueError('Only isolated purchase app screenshots permitted')
            (ROOT/'artifacts/screens'/('purchase-test-'+args.case+'.png')).write_bytes(adb('exec-out','screencap','-p'))
        adb('shell','run-as',PACKAGE,'rm','-f','files/purchase-test-status.json')
        adb('shell','am','start','-W','-n',PACKAGE+'/com.dishnest.app.PurchaseStatusActivity')
        status=None
        for _ in range(20):
            output=adb('exec-out','run-as',PACKAGE,'cat','files/purchase-test-status.json',check=False)
            try:status=json.loads(output);break
            except (ValueError,UnicodeDecodeError):time.sleep(.3)
        if not status:raise ValueError('No current purchase status report')
        adb('shell','am','start','-W','--activity-clear-top','--activity-single-top','-n',PACKAGE+'/com.dishnest.app.MainActivity')
        assert status['debug'] and status['billingEnabled'] and status['environment']=='sandbox' and status['keyValid'] and status['sandboxEndpoint'] and status['sdkMode']=='SANDBOX'
        assert status['premium']==(args.premium=='active'), 'Premium state differs from expected result'
        from build_evidence import input_hashes
        apk=ROOT/'app/build/outputs/apk/purchaseTest/app-purchaseTest.apk'
        report_path=ROOT/'artifacts/amazon-purchase-device.json'
        report=json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else {'packageId':PACKAGE,'cases':[],'apkSha256':digest(apk.read_bytes()),'sourceSha256':input_hashes(),'realMoneyCharged':False,'liveAppTesting':False,'productionReleaseTested':False,'ordinaryAppModified':False,'serialIdentifierStored':False,'receiptIdentifiersStored':False,'setupNotes':['API-28 Fire lacks DRM permission definition; test variant uses system-owned Amazon IAP signature permission for DRM callback','App Tester receives a temporary 120-second background allowance; no permanent power whitelist change']}
        if report['apkSha256']!=digest(apk.read_bytes()) or report['sourceSha256']!=input_hashes():
            report.setdefault('historicalRuns',[]).append({key:report[key] for key in ['apkSha256','sourceSha256','cases']})
            report.update(apkSha256=digest(apk.read_bytes()),sourceSha256=input_hashes(),cases=[])
        report['cases']=[case for case in report['cases'] if case['case']!=args.case]
        report['cases'].append({'case':args.case,'checkedAt':datetime.now(timezone.utc).isoformat(),'fixtureSha256':digest((ROOT/'billing-server/amazon.sdktester.json').read_bytes()),'expectedPremium':args.premium,'status':status,'passed':True})
        report_path.write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(json.dumps({'case':args.case,'passed':True,'premium':status['premium'],'sdkMode':status['sdkMode']}))
    elif args.action in ('tester','start'):
        unlocked()
        if args.action=='start':
            if adb('shell','getprop','debug.amazon.sandboxmode').decode().strip()!='debug':raise ValueError('Sandbox routing required')
            adb('shell','am','start','-W','--activity-clear-top','--activity-single-top','-n',PACKAGE+'/com.dishnest.app.MainActivity')
        else:adb('shell','am','start','-W','-n','com.amazon.sdktestclient/.MainActivity')
        print('Opened '+('App Tester' if args.action=='tester' else PACKAGE))
    else:
        root=snapshot();nodes=list(root.iter('node'))
        if args.action=='snapshot':
            if args.text:nodes=[n for n in nodes if n.get('text')==args.text or n.get('content-desc')==args.text]
            print(json.dumps([{'text':'<test identifier omitted>' if any(term in n.get('resource-id','') for term in ('textview_user_id','receipt_id')) and not n.get('resource-id','').endswith('_label') else n.get('text'),'description':n.get('content-desc'),'id':n.get('resource-id'),'package':n.get('package'),'enabled':n.get('enabled'),'checked':n.get('checked'),'clickable':n.get('clickable'),'bounds':n.get('bounds')} for n in nodes if n.get('text') or n.get('content-desc')],ensure_ascii=False))
        elif args.action=='confirm-settings':
            state=json.loads(STATE.read_text(encoding='utf-8'))
            if state.get('restored'):raise ValueError('No active recovery session')
            expected=state['originalTesterSettings']
            if any(n.get('text')==expected['user'] and n.get('checked')=='true' for n in nodes) and any(n.get('text')==expected['login'] and n.get('checked')=='true' for n in nodes):state['testerUserRestored']=True
            labels=[n for n in nodes if n.get('text')=='Auto Renewing Subscription']
            parents={child:parent for parent in root.iter() for child in parent}
            if len(labels)==1:
                ancestor=labels[0]
                while ancestor in parents:
                    ancestor=parents[ancestor]
                    values=[n for n in ancestor.iter('node') if n.get('resource-id')=='android:id/text1']
                    if len(values)==1:
                        state['testerSubscriptionSettingsRestored']=values[0].get('text')==expected['autoRenew'];break
            STATE.write_text(json.dumps(state),encoding='utf-8')
            print(json.dumps({key:state.get(key,False) for key in ['testerUserRestored','testerSubscriptionSettingsRestored']}))
        elif args.action=='scroll':
            matches=[n for n in nodes if n.get('scrollable')=='true' and n.get('package') in (PACKAGE,'com.amazon.sdktestclient')]
            if len(matches)!=1:raise ValueError('Expected one observed test scroll container')
            bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',matches[0].get('bounds',''))
            if not bounds:raise ValueError('No scroll bounds')
            x1,y1,x2,y2=map(int,bounds.groups());x=(x1+x2)//2;top=y1+(y2-y1)//4;bottom=y2-(y2-y1)//4
            first,last=(bottom,top) if args.direction=='down' else (top,bottom)
            adb('shell','input','swipe',str(x),str(first),str(x),str(last),'300')
            print('Scrolled observed test container')
        else:
            if not args.text:raise ValueError('--text required')
            matches=[n for n in nodes if (n.get('text')==args.text or n.get('content-desc')==args.text) and n.get('enabled')=='true']
            if args.row:
                labels=[n for n in nodes if n.get('text')==args.row]
                if len(labels)!=1:raise ValueError('Expected one observed row label')
                parents={child:parent for parent in root.iter() for child in parent}
                ancestor=labels[0]
                while ancestor in parents:
                    ancestor=parents[ancestor]
                    scoped=[n for n in matches if n in list(ancestor.iter('node'))]
                    if len(scoped)==1:matches=scoped;break
            if len(matches)!=1:raise ValueError('Expected exactly one observed text match')
            node=matches[0]
            if node.get('package') not in (PACKAGE,'com.amazon.sdktestclient'):raise ValueError('Refusing a tap outside isolated test packages')
            # Refuse real Amazon checkout: only the App Tester package can confirm a simulated purchase.
            bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.get('bounds',''))
            if not bounds:raise ValueError('No visible bounds')
            x1,y1,x2,y2=map(int,bounds.groups())
            if x2<=x1 or y2<=y1:raise ValueError('Control outside visible window')
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            print('Tapped observed test control')
if __name__=='__main__':
    # Share the existing native-QA mutex so separate controllers cannot race the UI.
    import ctypes
    from ctypes import wintypes
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.CreateMutexW.argtypes=[ctypes.c_void_p,wintypes.BOOL,wintypes.LPCWSTR]
    kernel.CreateMutexW.restype=wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD]
    kernel.ReleaseMutex.argtypes=[wintypes.HANDLE]
    kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handle=kernel.CreateMutexW(None,False,'Local\\DishNestFireQa')
    if not handle:raise OSError('Cannot acquire native-QA mutex')
    acquired=kernel.WaitForSingleObject(handle,0) in (0,128)
    try:
        if not acquired:raise RuntimeError('Another Fire QA controller is active; run device actions sequentially')
        main()
    finally:
        if acquired:kernel.ReleaseMutex(handle)
        kernel.CloseHandle(handle)
