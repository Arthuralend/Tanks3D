package com.arthur.tanks3d;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements InputManager.InputDeviceListener {
    private WebView web;
    private boolean ready = false;
    private final List<Integer> pads = new ArrayList<>();           // gamepad device ids, in connection order
    private final Map<Integer, String> stickDir = new HashMap<>();  // last stick/hat direction per device
    private final Map<Integer, Boolean> trigger = new HashMap<>();
    private InputManager im;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF0B0F14);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { ready = true; sendPads(); }
        });
        setContentView(web);
        hideBars();
        im = (InputManager) getSystemService(INPUT_SERVICE);
        im.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        scanPads();
        web.loadUrl("file:///android_asset/game/index.html");
    }

    private void hideBars() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); web.resumeTimers(); hideBars(); }
    @Override protected void onPause() { js("TV.hide()"); web.onPause(); super.onPause(); }
    @Override protected void onDestroy() { im.unregisterInputDeviceListener(this); web.destroy(); super.onDestroy(); }

    /* ---------- gamepads ---------- */
    private static boolean isPad(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int src = d.getSources();
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    private void scanPads() {
        for (int id : InputDevice.getDeviceIds()) if (isPad(InputDevice.getDevice(id)) && !pads.contains(id)) pads.add(id);
    }
    private int padIndex(InputDevice d) {
        if (!isPad(d)) return -1;
        int id = d.getId();
        if (!pads.contains(id)) { pads.add(id); sendPads(); }
        return pads.indexOf(id);
    }
    private void sendPads() {
        if (!ready) return;
        StringBuilder sb = new StringBuilder();
        if (pads.isEmpty()) sb.append("🎮 Геймпады не подключены. Играть можно пультом, а для игры вдвоём подключите геймпад по Bluetooth.");
        else {
            for (int i = 0; i < pads.size(); i++) {
                InputDevice d = InputDevice.getDevice(pads.get(i));
                String n = d == null ? "геймпад" : d.getName().replace("'", "").replace("<", "").replace("\\", "");
                if (i > 0) sb.append("<br>");
                sb.append(i == 0 ? "🎮 " : "").append("Геймпад ").append(i + 1).append(": <b>").append(n).append("</b>");
                if (pads.size() >= 2) sb.append(i == 0 ? " → игрок I" : (i == 1 ? " → игрок II (вдвоём)" : ""));
                else sb.append(" → игрок I, вдвоём — игрок II (пульт — игрок I)");
            }
        }
        js("TV.setPads(" + pads.size() + ",'" + sb + "')");
    }
    @Override public void onInputDeviceAdded(int id) { if (isPad(InputDevice.getDevice(id)) && !pads.contains(id)) { pads.add(id); sendPads(); } }
    @Override public void onInputDeviceRemoved(int id) {
        if (pads.remove((Integer) id)) {
            String d = stickDir.remove(id);
            if (d != null) js("TV.key(" + pads.size() + ",'" + d + "',false)");
            sendPads();
        }
    }
    @Override public void onInputDeviceChanged(int id) { }

    private void js(String code) { if (ready) web.evaluateJavascript(code, null); }
    private void key(int dev, String k, boolean down) { js("TV.key(" + dev + ",'" + k + "'," + down + ")"); }

    /* ---------- buttons / remote ---------- */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (e.getAction() == KeyEvent.ACTION_MULTIPLE) return true;
        switch (code) {
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER:
                return super.dispatchKeyEvent(e);
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down && e.getRepeatCount() == 0 && ready) {
                web.evaluateJavascript("TV.back()", r -> { if (r != null && r.contains("exit")) finish(); });
            }
            return true;
        }
        if (down && e.getRepeatCount() > 0) return true;
        int dev = padIndex(e.getDevice());
        String k = null;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: k = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: k = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: k = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: k = "right"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y: case KeyEvent.KEYCODE_BUTTON_R1: case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_BUTTON_L2:
                k = "fire"; break;
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_P:
                if (down) js("TV.start()"); return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_C:
                if (down) js("TV.cam()"); return true;
        }
        if (k != null) key(dev, k, down);
        return true;
    }

    /* ---------- sticks, hats, triggers ---------- */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        int src = e.getSource();
        if (((src & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) || e.getAction() != MotionEvent.ACTION_MOVE)
            return super.dispatchGenericMotionEvent(e);
        InputDevice d = e.getDevice();
        int dev = padIndex(d);
        if (dev < 0) dev = 0;
        int id = e.getDeviceId();
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        float x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y);
        String dir = null;
        if (Math.abs(hx) > .5f || Math.abs(hy) > .5f) dir = Math.abs(hx) > Math.abs(hy) ? (hx > 0 ? "right" : "left") : (hy > 0 ? "down" : "up");
        else if (Math.max(Math.abs(x), Math.abs(y)) > .5f) dir = Math.abs(x) > Math.abs(y) ? (x > 0 ? "right" : "left") : (y > 0 ? "down" : "up");
        String prev = stickDir.get(id);
        if (dir == null ? prev != null : !dir.equals(prev)) {
            if (prev != null) key(dev, prev, false);
            if (dir != null) key(dev, dir, true);
            if (dir == null) stickDir.remove(id); else stickDir.put(id, dir);
        }
        float t = Math.max(Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)),
                Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)));
        boolean on = t > .5f, was = Boolean.TRUE.equals(trigger.get(id));
        if (on != was) { trigger.put(id, on); key(dev, "fire", on); }
        return true;
    }
}
