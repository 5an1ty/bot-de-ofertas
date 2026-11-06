import json,threading,tempfile,unittest,urllib.request,urllib.error
from http.server import ThreadingHTTPServer
from backend import server as s
class HTTPTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();s.DB=self.tmp.name+'/db';self.http=ThreadingHTTPServer(('127.0.0.1',0),s.Handler)
  self.thread=threading.Thread(target=self.http.serve_forever,daemon=True);self.thread.start();self.base='http://127.0.0.1:'+str(self.http.server_port)
 def tearDown(self): self.http.shutdown();self.http.server_close();self.tmp.cleanup()
 def req(self,path,data=None,method=None):
  request=urllib.request.Request(self.base+path,data=json.dumps(data).encode() if data is not None else None,method=method)
  with urllib.request.urlopen(request) as r:return json.load(r)
 def test_crud_export(self):
  p=self.req('/products',{'name':'RX 7600','target':170000,'tolerance':10000});p['active']=False;self.req('/products',p)
  self.assertFalse(self.req('/products')[0]['active']);self.assertEqual(self.req('/export')['products'][0],p)
  self.req('/products/'+str(p['id']),method='DELETE');self.assertEqual(self.req('/products'),[])
 def test_bad_request(self):
  with self.assertRaises(urllib.error.HTTPError) as ctx:self.req('/products',{'name':'Test','target':1.1,'tolerance':0})
  self.assertEqual(ctx.exception.code,400)
 def test_health(self): self.assertFalse(self.req('/health')['remote_monitoring'])
