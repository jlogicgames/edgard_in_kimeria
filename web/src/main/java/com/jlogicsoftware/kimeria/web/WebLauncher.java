package com.jlogicsoftware.kimeria.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.jlogicsoftware.kimeria.DisplayControl;
import com.jlogicsoftware.kimeria.KimeriaGame;
import org.teavm.jso.JSBody;

public class WebLauncher {
    public static void main(String[] args) {
        launch(false);
    }

    static void launch(boolean debugMode) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        // Fill the canvas and resize with the browser window, same intent as
        // the desktop launcher's resizable window.
        config.width = 0;
        config.height = 0;
        // Back the canvas with physical pixels (D57): width x devicePixelRatio, CSS size unchanged. Without this an
        // iPhone (ratio 3) draws a 390 px wide canvas that the browser then stretches 3x, smearing pixel art and text.
        config.usePhysicalPixels = true;
        prepareMobilePage();
        new WebApplication(new KimeriaGame(hasTouchScreen(), debugMode, new PageFullscreen()), config);
    }

    @JSBody(script = "return navigator.maxTouchPoints > 0 || 'ontouchstart' in window;")
    private static native boolean hasTouchScreen();

    /** The browser's Fullscreen API on the whole page; absent on iPhone Safari, where {@link #available()} is false. */
    private static final class PageFullscreen implements DisplayControl {
        @Override public boolean available() { return fullscreenAvailable(); }
        @Override public boolean active() { return fullscreenActive(); }
        @Override public void toggle() { toggleFullscreen(); }
        @Override public float pixelRatio() { return (float) Math.max(1, devicePixelRatio()); }
    }

    /** The same ratio the backend sizes the canvas with ({@code devicePixelRatio || 1}). */
    @JSBody(script = "return window.devicePixelRatio || 1;")
    private static native double devicePixelRatio();

    @JSBody(script = "var d = document, e = d.documentElement;"
        + "return !!((d.fullscreenEnabled && e.requestFullscreen) || (d.webkitFullscreenEnabled && e.webkitRequestFullscreen));")
    private static native boolean fullscreenAvailable();

    @JSBody(script = "var d = document; return !!(d.fullscreenElement || d.webkitFullscreenElement);")
    private static native boolean fullscreenActive();

    @JSBody(script = "var d = document, e = d.documentElement;"
        + "if (d.fullscreenElement || d.webkitFullscreenElement) {"
        + "  (d.exitFullscreen || d.webkitExitFullscreen).call(d);"
        + "} else {"
        + "  var r = (e.requestFullscreen || e.webkitRequestFullscreen).call(e);"
        + "  if (r && r.catch) r.catch(function () {});"
        + "}")
    private static native void toggleFullscreen();

    /**
     * Page-level setup for phones (#226). Everything is feature-checked and failure-tolerant, so a browser without
     * a given API just skips that part:
     * <ul>
     * <li>viewport meta without pinch-zoom, and the iOS "Add to Home Screen" metadata (standalone mode, black status
     *     bar, title, manifest); the release build also bakes the same tags into index.html, because iOS reads
     *     them from the static document when the shortcut is made;</li>
     * <li>CSS: the page is exactly the <em>visible</em> viewport ({@code 100dvh}, not iOS's {@code 100vh}, which
     *     includes the area under the toolbars and hid half of the game in landscape), no scrolling, no
     *     rubber-band, no touch gestures on the canvas;</li>
     * <li>re-sends a window {@code resize} after {@code orientationchange} and visual-viewport changes so the
     *     canvas follows the toolbars appearing and hiding;</li>
     * <li>Screen Wake Lock: requested on the first touch (it needs a visible page), re-requested when the page
     *     becomes visible again, because the browser drops it whenever the page is hidden. Held while the page
     *     is visible, including menus; unsupported browsers (older iOS) simply auto-lock as before.</li>
     * </ul>
     */
    @JSBody(script = "try {"
        + "var d = document, w = window, head = d.head;"
        + "var meta = function (n, c) { if (!d.querySelector('meta[name=\"' + n + '\"]')) { var m = d.createElement('meta'); m.name = n; m.content = c; head.appendChild(m); } };"
        + "var vp = d.querySelector('meta[name=\"viewport\"]');"
        + "if (!vp) { vp = d.createElement('meta'); vp.name = 'viewport'; head.appendChild(vp); }"
        + "vp.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';"
        + "meta('apple-mobile-web-app-capable', 'yes'); meta('mobile-web-app-capable', 'yes');"
        + "meta('apple-mobile-web-app-status-bar-style', 'black'); meta('apple-mobile-web-app-title', 'Edgard in Kimeria');"
        + "var st = d.createElement('style');"
        + "st.textContent = 'html,body{position:fixed;inset:0;width:100%;height:100vh;height:100dvh;margin:0;padding:0;overflow:hidden;overscroll-behavior:none;background:#000;"
        + "-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent}"
        + "body{height:100vh;height:100dvh}canvas{display:block;touch-action:none}';"
        + "head.appendChild(st);"
        + "var nudge = function () { w.dispatchEvent(new Event('resize')); };"
        + "w.addEventListener('orientationchange', function () { setTimeout(nudge, 250); });"
        + "if (w.visualViewport) w.visualViewport.addEventListener('resize', nudge);"
        + "d.addEventListener('gesturestart', function (e) { e.preventDefault(); });"
        + "var lock = null;"
        + "var wake = function () {"
        + "  if (!navigator.wakeLock || lock || d.visibilityState !== 'visible') return;"
        + "  navigator.wakeLock.request('screen').then(function (l) { lock = l; l.addEventListener('release', function () { lock = null; }); }, function () {});"
        + "};"
        + "d.addEventListener('visibilitychange', wake);"
        + "['touchend', 'pointerup', 'click', 'keydown'].forEach(function (t) { d.addEventListener(t, wake, {passive: true}); });"
        + "} catch (e) {}")
    private static native void prepareMobilePage();
}
