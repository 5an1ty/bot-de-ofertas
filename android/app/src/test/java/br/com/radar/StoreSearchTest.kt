package br.com.radar
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
class StoreSearchTest {
 @Test fun recognizesNames() {
  assertEquals("KaBuM!",StoreSearch.resolve(" kabum ")?.name)
  assertEquals("Terabyte",StoreSearch.resolve("terabyte")?.name)
  assertEquals("Amazon Brasil",StoreSearch.resolve("amazon")?.name)
  assertEquals("Mercado Livre",StoreSearch.resolve("mercado livre")?.name)
  assertEquals("Pichau",StoreSearch.resolve("www.pichau.com.br")?.name)
 }
 @Test fun rejectsUnknownDestinations() {
  for(name in listOf("localhost","127.0.0.1","minhaloja","pichau.evil.com","WhatsApp")) assertNull(StoreSearch.resolve(name))
 }
 @Test fun queryCannotChangeDestination() {
  for(store in StoreSearch.stores) {
   val uri=URI(StoreSearch.searchUrl(store,"RX 7600 & #? / teste"))
   assertEquals("https",uri.scheme);assertEquals(store.host,uri.host);assertNull(uri.fragment)
  }
 }
}
