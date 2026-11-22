package io.github.aledigt.evisionshop

/**
 * Заглушка webkitSpeechRecognition.
 * В shop.evision.tech вызов `new SpeechRecognition()` идёт безусловно,
 * а в Android WebView этого API обычно нет: без заглушки падает весь скрипт,
 * включая камеру. Если браузерный API уже есть, скрипт ничего не подменяет.
 * Голосовой ввод при заглушке не работает; кнопка микрофона на сайте скрыта.
 */
object DocumentStart {
  val SCRIPT: String = """
    (function () {
      if (window.SpeechRecognition || window.webkitSpeechRecognition) return;
      function SpeechRecognition() {}
      SpeechRecognition.prototype.start = function () {
        var self = this;
        setTimeout(function () {
          if (typeof self.onerror === 'function') self.onerror({ error: 'service-not-allowed' });
          if (typeof self.onend === 'function') self.onend();
        }, 0);
      };
      SpeechRecognition.prototype.stop = function () {};
      SpeechRecognition.prototype.abort = function () {};
      window.SpeechRecognition = window.webkitSpeechRecognition = SpeechRecognition;
    })();
  """.trimIndent()

  fun inject(html: String, script: String = SCRIPT): String {
    val tag = "<script>" + script.replace("</", "<\\/") + "</script>"
    val head = Regex("(?i)<head\\b[^>]*>").find(html) ?: return tag + html
    val at = head.range.last + 1
    return html.substring(0, at) + tag + html.substring(at)
  }
}
