package io.github.aledigt.evisionshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
  @Test fun shopAndCloudStayInTheApp() {
    assertEquals(NavigationPolicy.Decision.LOAD_IN_APP, NavigationPolicy.decide("https://shop.evision.tech/", true))
    assertEquals(NavigationPolicy.Decision.LOAD_IN_APP, NavigationPolicy.decide("https://shop.evision.tech/index.html?add_device=1", true))
    assertEquals(NavigationPolicy.Decision.LOAD_IN_APP, NavigationPolicy.decide("https://SHOP.evision.tech/path", true))
    assertEquals(
      NavigationPolicy.Decision.LOAD_IN_APP,
      NavigationPolicy.decide("https://cloud.evision.tech/?key=ABC&redirect_uri=https://shop.evision.tech/", true)
    )
  }

  @Test fun otherHttpsOpensOutsideOnlyFromTheMainFrame() {
    val yandex = "https://dialogs.yandex.ru/store/skills/213c4e96-moj-domofon"
    assertEquals(NavigationPolicy.Decision.OPEN_EXTERNAL, NavigationPolicy.decide(yandex, true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide(yandex, false))
    assertEquals(NavigationPolicy.Decision.OPEN_EXTERNAL, NavigationPolicy.decide("https://shop.evision.tech.evil.com/", true))
    assertEquals(NavigationPolicy.Decision.OPEN_EXTERNAL, NavigationPolicy.decide("https://media.evision.tech/", true))
  }

  @Test fun unsafeSchemesAndCredentialsAreBlocked() {
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("http://shop.evision.tech/", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("http://cloud.evision.tech/x", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("javascript:alert(1)", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("file:///sdcard/secret.txt", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("content://settings/secure", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("intent://scan/#Intent;scheme=zxing;end", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("data:text/html,hi", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("https://user:pass@shop.evision.tech/", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide(" https://shop.evision.tech/", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide(null, true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("", true))
  }

  @Test fun mailAndPhoneLeaveTheApp() {
    assertEquals(NavigationPolicy.Decision.OPEN_EXTERNAL, NavigationPolicy.decide("mailto:shop@evision.tech", true))
    assertEquals(NavigationPolicy.Decision.OPEN_EXTERNAL, NavigationPolicy.decide("tel:+79990000000", true))
    assertEquals(NavigationPolicy.Decision.BLOCK, NavigationPolicy.decide("mailto:shop@evision.tech", false))
  }

  @Test fun backUsesWebHistoryThenLeaves() {
    assertEquals(NavigationPolicy.BackAction.WEBVIEW_BACK, NavigationPolicy.backAction(true))
    assertEquals(NavigationPolicy.BackAction.FINISH, NavigationPolicy.backAction(false))
  }

  @Test fun onlyCameraCaptureIsGrantedToThePage() {
    val grant = NavigationPolicy.grantableWebResources(
      listOf(
        NavigationPolicy.VIDEO_CAPTURE,
        "android.webkit.resource.AUDIO_CAPTURE",
        "android.webkit.resource.PROTECTED_MEDIA_ID"
      )
    )
    assertEquals(setOf(NavigationPolicy.VIDEO_CAPTURE), grant)
    assertTrue(NavigationPolicy.grantableWebResources(listOf("android.webkit.resource.AUDIO_CAPTURE")).isEmpty())
  }

  @Test fun shopDocumentIsOnlyTheEntryPage() {
    assertTrue(NavigationPolicy.isShopDocument("https://shop.evision.tech/"))
    assertTrue(NavigationPolicy.isShopDocument("https://shop.evision.tech/index.html?add_device=1"))
    assertFalse(NavigationPolicy.isShopDocument("https://shop.evision.tech/sw.js"))
    assertFalse(NavigationPolicy.isShopDocument("https://cloud.evision.tech/"))
    assertFalse(NavigationPolicy.isShopDocument("http://shop.evision.tech/"))
  }

  @Test fun downloadNamesStayInsideOneFileSegment() {
    assertEquals("orders.csv", NavigationPolicy.suggestedDownloadName("attachment; filename=\"orders.csv\"", "https://cloud.evision.tech/export"))
    assertEquals("заказ.csv", NavigationPolicy.suggestedDownloadName("attachment; filename*=UTF-8''%D0%B7%D0%B0%D0%BA%D0%B0%D0%B7.csv", "https://cloud.evision.tech/export"))
    assertEquals("download", NavigationPolicy.suggestedDownloadName(null, "https://shop.evision.tech/"))
    val traversal = NavigationPolicy.suggestedDownloadName("filename=\"../../etc/passwd\"", "https://shop.evision.tech/a")
    assertEquals("etc_passwd", traversal)
    assertFalse(traversal.contains("/"))
    assertFalse(traversal.contains(".."))
  }
}
