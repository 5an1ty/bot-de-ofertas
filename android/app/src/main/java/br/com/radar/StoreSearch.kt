package br.com.radar

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.Jsoup

/** Only known public search endpoints. Unknown names never become arbitrary network URLs. */
object StoreSearch {
 data class Store(val name:String,val host:String,val search:String)
 val stores=listOf(Store("KaBuM!","www.kabum.com.br","https://www.kabum.com.br/busca/"),Store("Pichau","www.pichau.com.br","https://www.pichau.com.br/search?q="),Store("Terabyte","www.terabyteshop.com.br","https://www.terabyteshop.com.br/busca?str="),Store("Amazon Brasil","www.amazon.com.br","https://www.amazon.com.br/s?k="),Store("Mercado Livre","lista.mercadolivre.com.br","https://lista.mercadolivre.com.br/"))
 fun normalize(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").replace(Regex("[^a-z0-9]"),"")
 fun resolve(name:String):Store? { val n=normalize(name);return stores.firstOrNull { normalize(it.name)==n || normalize(it.host)==n || (n=="amazon" && it.name=="Amazon Brasil") || (n=="kabum" && it.name=="KaBuM!") } }
 fun searchUrl(store:Store,query:String)=store.search+URLEncoder.encode(query,"UTF-8").replace("+","%20")
 fun check(store:Store,product:JSONObject):String {
  val queries=mutableListOf(product.getString("name"));product.optJSONArray("aliases")?.let{a->for(i in 0 until a.length()) if(a.optString(i).isNotBlank()) queries.add(a.getString(i))}
  val titles=linkedSetOf<String>()
  for(query in queries.distinct().take(3)) {
   val c=URI(searchUrl(store,query)).toURL().openConnection() as HttpURLConnection
   try {
    c.connectTimeout=8000;c.readTimeout=8000;c.instanceFollowRedirects=false;c.setRequestProperty("User-Agent","Radar/0.2 (consulta manual de produtos)")
    val code=c.responseCode
    if(code!=200)return when(code){403,429->"Acesso bloqueado pela loja (HTTP $code). Tente a busca no navegador.";in 300..399->"A loja redirecionou a consulta; abra no navegador.";else->"Consulta falhou: HTTP $code"}
    val text=c.inputStream.bufferedReader().use{reader -> val buffer=CharArray(2000000);var total=0;while(total<buffer.size){val count=reader.read(buffer,total,buffer.size-total);if(count<0)break;total+=count};String(buffer,0,total)}
    if(text.contains("captcha",true))return "Verificação de acesso exigida pela loja. Abra no navegador."
    val doc=Jsoup.parse(text)
    fun inspect(value:Any?) {
     when(value) {
      is JSONArray -> for(i in 0 until value.length()) inspect(value.opt(i))
      is JSONObject -> {
       if(value.optString("@type")=="Product") {
        val title=value.optString("name")
        val words=product.getString("name").lowercase().split(Regex("\\s+"))
        val excluded=product.optJSONArray("excluded")?:JSONArray()
        val variants=product.optJSONArray("variants")?:JSONArray()
        val match=words.all{title.lowercase().contains(it)} && (0 until excluded.length()).none{title.contains(excluded.getString(it),true)} && (0 until variants.length()).all{title.contains(variants.getString(it),true)}
        if(match)titles.add(title)
       }
       value.keys().forEach{inspect(value.opt(it))}
      }
     }
    }
    for(script in doc.select("script[type=application/ld+json]"))try{ val raw=script.data().trim();inspect(if(raw.startsWith("["))JSONArray(raw) else JSONObject(raw)) }catch(_:Exception){}
   } finally {c.disconnect()}
  }
  return if(titles.isEmpty()) "Consulta realizada; sem produtos estruturados compatíveis. Isso não confirma ausência de ofertas." else "${titles.size} anúncio(s) candidato(s): ${titles.take(3).joinToString("; ")}. Preço, estoque e condições não confirmados; confira na loja."
 }
}
