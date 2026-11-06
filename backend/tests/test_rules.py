import os, tempfile, unittest
from backend import server as s
class Rules(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory(); s.DB=self.tmp.name+'/test.db'
  self.p=dict(name='RX 7600',target=170000,tolerance=10000,mode='reais')
  self.o=dict(title='Radeon RX 7600 8 GB',price=180000,stock='confirmado',verified=True,price_kind='total')
 def tearDown(self): self.tmp.cleanup()
 def test_limit(self):
  self.assertTrue(s.qualifies(self.p,self.o)); self.o['price']+=1; self.assertFalse(s.qualifies(self.p,self.o))
 def test_percent(self):
  self.p.update(mode='percentual',tolerance=10); self.o['price']=187000; self.assertTrue(s.qualifies(self.p,self.o))
 def test_variants(self):
  for title in ['RX 7600 XT','Computador RX 7600','PC RX 7600','RX 6600']:
   self.o['title']=title; self.assertFalse(s.qualifies(self.p,self.o))
 def test_uncertain(self):
  for key,value in [('stock','esgotado'),('verified',False),('price_kind','parcela'),('unverified_conditions',True)]:
   offer={**self.o,key:value}; self.assertFalse(s.qualifies(self.p,offer))
 def test_persistence_edit(self):
  saved=s.save('products',self.p); saved['name']='RTX 4060'; s.save('products',saved)
  self.assertEqual(len(s.rows('products')),1); self.assertEqual(s.rows('products')[0]['name'],'RTX 4060')
 def test_money(self):
  self.p['target']=1.5
  with self.assertRaises(ValueError): s.save('products',self.p)
 def test_source_not_connected(self):
  self.assertEqual(s.save('sources',{'name':'Loja'})['status'],'indisponível')
if __name__=='__main__': unittest.main()
