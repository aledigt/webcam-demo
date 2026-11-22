package io.github.aledigt.evisionshop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStartTest {
  @Test fun scriptOnlyFillsMissingSpeechRecognition() {
    val script = DocumentStart.SCRIPT
    assertTrue(script.contains("window.SpeechRecognition || window.webkitSpeechRecognition"))
    assertTrue(script.contains("window.webkitSpeechRecognition = SpeechRecognition"))
    assertFalse(script.contains("http://"))
    assertFalse(script.contains("https://"))
    assertFalse(script.contains("eval("))
    assertFalse(script.contains("</script>"))
  }

  @Test fun injectsBeforePageScripts() {
    val html = "<html><head><title>t</title></head><body><script>new SpeechRecognition()</script></body></html>"
    val out = DocumentStart.inject(html, "window.__shim=1;")
    assertTrue(out.indexOf("window.__shim=1;") > out.indexOf("<head>"))
    assertTrue(out.indexOf("window.__shim=1;") < out.indexOf("new SpeechRecognition"))
  }

  @Test fun injectsWhenHeadIsMissing() {
    val out = DocumentStart.inject("<html><body></body></html>", "window.__shim=1;")
    assertTrue(out.startsWith("<script>window.__shim=1;</script>"))
  }
}
