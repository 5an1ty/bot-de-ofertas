"""Radar local monitoring service. Money is always integer centavos."""
import json, os, sqlite3, threading, time, unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
DB = os.getenv('RADAR_DB', 'radar.db')
LOCK = threading.RLock()
UNAVAILABLE = ['KaBuM!', 'Pichau', 'Terabyte', 'Amazon Brasil', 'Mercado Livre', 'WhatsApp']
def connection():
    c = sqlite3.connect(DB)
    c.execute('CREATE TABLE IF NOT EXISTS records (kind TEXT, id INTEGER, data TEXT, PRIMARY KEY(kind,id))')
    return c

def rows(kind):
    with connection() as c:
        return [json.loads(r[0]) for r in c.execute('SELECT data FROM records WHERE kind=? ORDER BY id', (kind,))]

def save(kind, data):
    if kind == 'products':
        if not str(data.get('name','')).strip(): raise ValueError('Nome obrigatório')
        for field in ('target','tolerance'):
            if type(data.get(field)) is not int or data[field] < 0: raise ValueError('Preços devem ser centavos inteiros não negativos')
        if data.get('mode','reais') not in ('reais','percentual'): raise ValueError('Tolerância inválida')
    if kind == 'sources':
        if not data.get('name'): raise ValueError('Nome obrigatório')
        data['status'] = 'indisponível'
        data['error'] = 'Conector sem validação real; salvar endereço não estabelece conexão.'
    with LOCK, connection() as c:
        if not data.get('id'):
            data['id'] = c.execute('SELECT COALESCE(MAX(id),0)+1 FROM records WHERE kind=?',(kind,)).fetchone()[0]
        c.execute('INSERT OR REPLACE INTO records VALUES (?,?,?)',(kind,data['id'],json.dumps(data)))
    return data

def normalized(s):
    return ''.join(c for c in unicodedata.normalize('NFKD',s.lower()) if not unicodedata.combining(c))

def qualifies(product, offer):
    title = normalized(offer['title'])
    names = [product['name']] + product.get('aliases',[])
    if not any(all(word in title.split() for word in normalized(n).split()) for n in names): return False
    if any(normalized(word) in title for word in product.get('excluded',[])): return False
    if '7600' in normalized(product['name']) and 'xt' not in normalized(product['name']).split():
        if any(word in title.split() for word in ['xt','computador','pc','desktop']): return False
    if any(normalized(v) not in title for v in product.get('variants',[])): return False
    limit = product['target'] + (product['target'] * product['tolerance'] // 100 if product.get('mode') == 'percentual' else product['tolerance'])
    return (product.get('active',True) and type(offer.get('price')) is int and 0 < offer['price'] <= limit
            and offer.get('stock') == 'confirmado' and offer.get('verified') is True
            and offer.get('price_kind') == 'total' and not offer.get('unverified_conditions'))

class UnavailableConnector:
    def search(self, product):
        raise RuntimeError('Integração indisponível: acesso externo bloqueado ou autenticação não configurada')
CONNECTORS = {name: UnavailableConnector() for name in UNAVAILABLE}
STATE = {'last_run':None, 'remote_monitoring':False, 'status':'sem conectores operacionais'}
def monitor():
    while True:
        STATE['last_run'] = int(time.time())
        for product in rows('products'):
            if not product.get('active',True): continue
            for source in rows('sources'):
                if source['id'] not in product.get('sources',[]) or not source.get('active',True): continue
                try: CONNECTORS.get(source['name'],UnavailableConnector()).search(product)
                except RuntimeError as e:
                    source['error']=str(e); save('sources',source)
        time.sleep(max(60,int(os.getenv('RADAR_INTERVAL_SECONDS','900'))))

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def respond(self, code, data):
        raw=json.dumps(data,ensure_ascii=False).encode(); self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8'); self.send_header('Content-Length',str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def authorized(self):
        token=os.getenv('RADAR_TOKEN','')
        return not token or self.headers.get('Authorization') == 'Bearer '+token
    def do_GET(self):
        if not self.authorized(): return self.respond(401,{'error':'Não autorizado'})
        kind=self.path.strip('/')
        if kind=='health': return self.respond(200,STATE)
        if kind=='export': return self.respond(200,{'version':1, **{k:rows(k) for k in ['products','sources','offers','shares']}})
        if kind in ['products','sources','offers','shares']: return self.respond(200,rows(kind))
        self.respond(404,{'error':'Não encontrado'})
    def do_POST(self):
        if not self.authorized(): return self.respond(401,{'error':'Não autorizado'})
        try:
            size=int(self.headers.get('Content-Length','0'))
            if size>1048576: raise ValueError('Limite de 1 MB')
            data=json.loads(self.rfile.read(size)); kind=self.path.strip('/')
            if kind in ['products','sources','shares']: return self.respond(200,save(kind,data))
            self.respond(404,{'error':'Não encontrado'})
        except (ValueError,TypeError,KeyError) as e: self.respond(400,{'error':str(e)})
    def do_DELETE(self):
        if not self.authorized(): return self.respond(401,{'error':'Não autorizado'})
        try:
            kind, ident=self.path.strip('/').split('/')
            if kind not in ['products','sources','shares']: raise ValueError('Tipo inválido')
            with connection() as c: c.execute('DELETE FROM records WHERE kind=? AND id=?',(kind,int(ident)))
            self.respond(200,{'deleted':True})
        except ValueError as e: self.respond(400,{'error':str(e)})
if __name__=='__main__':
    threading.Thread(target=monitor,daemon=True).start()
    ThreadingHTTPServer((os.getenv('RADAR_HOST','127.0.0.1'),int(os.getenv('PORT','8080'))),Handler).serve_forever()
