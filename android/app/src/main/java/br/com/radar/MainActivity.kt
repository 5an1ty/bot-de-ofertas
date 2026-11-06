package br.com.radar

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs=getSharedPreferences("radar",MODE_PRIVATE)
  val incoming=if(intent.action==Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
  setContent {
   var tab by remember { mutableStateOf("Produtos") }
   var products by remember { mutableStateOf(prefs.getString("products","[]")!!) }
   var sources by remember { mutableStateOf(prefs.getString("sources","[]")!!) }
   var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
   var tolerance by remember { mutableStateOf("0") }; var percent by remember { mutableStateOf(false) }
   var excluded by remember { mutableStateOf("XT,computador,desktop") }
   var aliases by remember { mutableStateOf("") }; var variants by remember { mutableStateOf("") }
   var selected by remember { mutableStateOf(setOf<Long>()) }; var editing by remember { mutableStateOf<Long?>(null) }
   var sourceName by remember { mutableStateOf("") }; var sourceUrl by remember { mutableStateOf("") }
   var url by remember { mutableStateOf(prefs.getString("url","")!!) }
   var token by remember { mutableStateOf(prefs.getString("token","")!!) }
   var checking by remember { mutableStateOf(false) }
   var reports by remember { mutableStateOf(prefs.getString("reports", "")!!) }
   var advanced by remember { mutableStateOf(false) }
   var message by remember { mutableStateOf("") }
   var transfer by remember { mutableStateOf("") }
   var shared by remember { mutableStateOf(incoming ?: prefs.getString("share","")!!) }
   fun persist(key:String,value:String) { prefs.edit().putString(key,value).apply() }
   fun list(raw:String):List<JSONObject> { val a=JSONArray(raw); return (0 until a.length()).map { a.getJSONObject(it) } }
   fun money(raw:String):Long = BigDecimal(raw.trim().replace(",",".")).movePointRight(2).longValueExact().also { require(it>=0) }
   fun format(value:Long)=NumberFormat.getCurrencyInstance(Locale("pt","BR")).format(BigDecimal(value).movePointLeft(2))
   MaterialTheme(shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(24.dp)),colorScheme=darkColorScheme(primary=Color(0xFFC3A0FF),onPrimary=Color(0xFF260956),primaryContainer=Color(0xFF382157),background=Color(0xFF0D0A16),surface=Color(0xFF151020),surfaceVariant=Color(0xFF221A32),secondary=Color(0xFFB3A5CD),outline=Color(0xFF574666))) {
    Surface(Modifier.fillMaxSize()) {
     LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      item { Spacer(Modifier.height(24.dp)); Text("RADAR / OFERTAS",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge); Text("Seu próximo achado.",style=MaterialTheme.typography.headlineLarge); Text("Seus produtos. Suas fontes. Seu limite.",color=MaterialTheme.colorScheme.secondary) }
      item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       Text("Consulta sob demanda",style=MaterialTheme.typography.titleMedium)
       Text("Verifique os produtos ativos em todas as fontes cadastradas e ativas. Não há monitoramento em segundo plano.",style=MaterialTheme.typography.bodySmall)
       Button(enabled=!checking,onClick={
        val ps=list(products).filter{it.optBoolean("active",true)}; val ss=list(sources).filter{it.optBoolean("active",true)}
        if(ps.isEmpty() || ss.isEmpty()) {message="Cadastre um produto ativo e uma fonte para verificar."} else {
         checking=true; reports="Iniciando consultas…"; val output=StringBuilder()
         Thread {
          try { for(source in ss) { val store=StoreSearch.resolve(source.getString("name"))
           for(product in ps) {
            val result=if(store==null) "Fonte não reconhecida; consulta automática indisponível." else try {StoreSearch.check(store,product)} catch(_:Exception){"Falha de conexão. Verifique sua internet ou tente novamente."}
            output.append("${source.getString("name")} · ${product.getString("name")}\n$result\n\n")
            val snapshot=output.toString();runOnUiThread{reports=snapshot}
           }
          } } finally {runOnUiThread{checking=false;reports="Verificação: "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date())+"\n\n"+output;persist("reports",reports)}}
         }.start()
        }
       },modifier=Modifier.fillMaxWidth()) { Text(if(checking) "Verificando fontes…" else "Verificar agora") }
       if(checking) LinearProgressIndicator(Modifier.fillMaxWidth())
      } } }
      item { if(reports.isNotBlank()) Card(Modifier.fillMaxWidth()) { Text(reports,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall) } }
      item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Produtos","Fontes","Ajustes").forEach { t -> FilterChip(selected=tab==t,onClick={tab=t;message=""},label={Text(t)}) } } }
      item { if(message.isNotBlank()) Text(message) }
      if(tab=="Produtos") {
       item { Text(if(editing==null) "Novo produto" else "Editar produto",style=MaterialTheme.typography.titleLarge) }
       item { OutlinedTextField(name,{name=it},label={Text("Nome do produto")},modifier=Modifier.fillMaxWidth()) }
       item { OutlinedTextField(price,{price=it},label={Text("Até X reais")},prefix={Text("R$ ")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth()); Text("Valor base. A tolerância abaixo é somada a esse valor.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary) }
       item { OutlinedTextField(tolerance,{tolerance=it},label={Text(if(percent) "Tolerância (%)" else "Tolerância extra (R$)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth()) }
       item { Row { Checkbox(percent,{percent=it}); Text("Tolerância percentual") } }
       item { TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Ocultar filtros avançados" else "Palavras-chave e variantes ›")} }
       if(advanced) item { OutlinedTextField(aliases,{aliases=it},label={Text("Nomes alternativos")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(excluded,{excluded=it},label={Text("Excluir termos")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(variants,{variants=it},label={Text("Variantes obrigatórias")},modifier=Modifier.fillMaxWidth()) }
       items(list(sources)) { source -> Row { Checkbox(source.getLong("id") in selected,{checked -> selected=if(checked) selected+source.getLong("id") else selected-source.getLong("id")}); Text(source.getString("name")) } }
       item { Button(onClick={try {
        require(name.isNotBlank()) { "Informe o nome" }
        val target=money(price); val tol=if(percent) tolerance.toLong().also { require(it>=0) } else money(tolerance)
        val obj=JSONObject().put("id",editing?:System.currentTimeMillis()).put("name",name).put("target",target).put("tolerance",tol).put("mode",if(percent) "percentual" else "reais").put("active",true)
        for((k,v) in listOf("aliases" to aliases,"excluded" to excluded,"variants" to variants)) obj.put(k,JSONArray(v.split(',').map{it.trim()}.filter{it.isNotEmpty()}))
        obj.put("sources",JSONArray(selected.toList()))
        products=JSONArray(list(products).filter{it.getLong("id")!=editing}+obj).toString(); persist("products",products); editing=null; name=""; message="Cadastro salvo neste aparelho."
       } catch(e:Exception) { message="Dados inválidos: confira nome e valores." }}) { Text("Salvar produto") } }
       items(list(products)) { p -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(p.getString("name"),style=MaterialTheme.typography.titleLarge)
        val limit=p.getLong("target")+(if(p.optString("mode")=="percentual") p.getLong("target")*p.getLong("tolerance")/100 else p.getLong("tolerance"))
        Text("Avisar até ${format(limit)} · ${if(p.optBoolean("active",true)) "ativo" else "pausado"}")
        Row {
         TextButton(onClick={editing=p.getLong("id"); name=p.getString("name"); price=BigDecimal(p.getLong("target")).movePointLeft(2).toPlainString(); percent=p.optString("mode")=="percentual"; tolerance=if(percent) p.getLong("tolerance").toString() else BigDecimal(p.getLong("tolerance")).movePointLeft(2).toPlainString(); aliases=(p.optJSONArray("aliases")?:JSONArray()).let{a->(0 until a.length()).joinToString(","){a.getString(it)}}; excluded=(p.optJSONArray("excluded")?:JSONArray()).let{a->(0 until a.length()).joinToString(","){a.getString(it)}}; variants=(p.optJSONArray("variants")?:JSONArray()).let{a->(0 until a.length()).joinToString(","){a.getString(it)}}; selected=(p.optJSONArray("sources")?:JSONArray()).let{a->(0 until a.length()).map{a.getLong(it)}.toSet()}}) { Text("Editar") }
         TextButton(onClick={products=JSONArray(list(products).map{if(it.getLong("id")==p.getLong("id")) it.put("active",!it.optBoolean("active",true)) else it}).toString(); persist("products",products)}) { Text("Pausar/ativar") }
         TextButton(onClick={products=JSONArray(list(products).filter{it.getLong("id")!=p.getLong("id")}).toString(); persist("products",products)}) { Text("Excluir") }
        }
       } } }
      } else if(tab=="Fontes") {
       item { Text("Digite uma loja para reconhecer seu endereço. O resultado da conexão aparece em Verificar agora.") }
       item { OutlinedTextField(sourceName,{sourceName=it;sourceUrl=StoreSearch.resolve(it)?.let{s->"https://"+s.host}?:""},label={Text("Nome da loja")},supportingText={Text(StoreSearch.resolve(sourceName)?.let{"${it.name} reconhecida automaticamente"}?:"KaBuM!, Pichau, Terabyte, Amazon ou Mercado Livre")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(sourceUrl,{sourceUrl=it},label={Text("Endereço (opcional)")}); Button(onClick={if(sourceName.isNotBlank()) { sources=JSONArray(list(sources)+JSONObject().put("id",System.currentTimeMillis()).put("name",sourceName).put("url",sourceUrl).put("active",true)).toString(); persist("sources",sources); sourceName="" }}) { Text("Cadastrar fonte") } }
       items(list(sources)) { s -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(s.getString("name"),style=MaterialTheme.typography.titleMedium); Text(StoreSearch.resolve(s.getString("name"))?.let{"${it.host} · pronta para tentativa de consulta"}?:"Fonte não reconhecida",style=MaterialTheme.typography.bodySmall); TextButton(onClick={sources=JSONArray(list(sources).filter{it.getLong("id")!=s.getLong("id")}).toString(); persist("sources",sources)}){Text("Excluir")} } } }
      } else {
       item { Text("Serviço de monitoramento",style=MaterialTheme.typography.titleLarge); OutlinedTextField(url,{url=it;persist("url",it)},label={Text("URL HTTPS do backend")}); OutlinedTextField(token,{token=it;persist("token",it)},label={Text("Token do serviço")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation()) }
       item { Button(onClick={Thread { try { val base=URI(url); require(base.scheme=="https" && base.host!=null); val c=URI(url.trimEnd('/')+"/health").toURL().openConnection() as HttpURLConnection; c.connectTimeout=10000;c.readTimeout=10000;c.setRequestProperty("Authorization","Bearer $token"); val result=c.inputStream.bufferedReader().use{it.readText()};c.disconnect();runOnUiThread{message="Serviço: $result"} } catch(e:Exception){runOnUiThread{message="Não foi possível acessar o serviço HTTPS."}} }.start()}) { Text("Verificar conexão") } }
       item { Text("Compartilhamento manual",style=MaterialTheme.typography.titleLarge); OutlinedTextField(shared,{shared=it},label={Text("Texto recebido · preço não verificado")}); Button(onClick={persist("share",shared); message="Texto salvo. Não equivale a oferta conferida."}) { Text("Guardar texto") }; Text("Leitura automática de grupos e notificações de ofertas ainda indisponíveis.") }
       item { Button(onClick={transfer=JSONObject().put("version",1).put("products",JSONArray(products)).put("sources",JSONArray(sources)).toString(2)}) { Text("Exportar cadastros") }; OutlinedTextField(transfer,{transfer=it},label={Text("JSON para copiar ou importar")},modifier=Modifier.fillMaxWidth()); Button(onClick={try {val data=JSONObject(transfer);require(data.getInt("version")==1); val ps=data.getJSONArray("products");val ss=data.getJSONArray("sources");for(i in 0 until ps.length()){val p=ps.getJSONObject(i);require(p.getString("name").isNotBlank() && p.getLong("target")>=0 && p.getLong("tolerance")>=0);p.getLong("id")};for(i in 0 until ss.length()){ss.getJSONObject(i).getString("name");ss.getJSONObject(i).getLong("id")};products=ps.toString();sources=ss.toString();persist("products",products);persist("sources",sources);message="Cadastros importados."}catch(e:Exception){message="Arquivo inválido; cadastros preservados."}}) {Text("Importar (substitui cadastros)")} }
      }
      item { Spacer(Modifier.height(32.dp));Text("Radar 0.2.0 · versão de teste") }
     }
    }
   }
  }
 }
}
