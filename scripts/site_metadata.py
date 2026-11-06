import hashlib,json,sys,datetime,pathlib
p=pathlib.Path(sys.argv[1]); data={'version':'0.1.0','date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'url':'https://github.com/5an1ty/bot-de-ofertas/releases/download/v0.1.0/radar-0.1.0-test.apk'}
pathlib.Path('site/release.json').write_text(json.dumps(data))
