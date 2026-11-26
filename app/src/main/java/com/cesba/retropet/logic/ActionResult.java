package com.cesba.retropet.logic;
public class ActionResult {
    public final boolean ok; public final String message;
    public ActionResult(boolean ok, String message) { this.ok = ok; this.message = message; }
    public static ActionResult ok(String m) { return new ActionResult(true, m); }
    public static ActionResult fail(String m) { return new ActionResult(false, m); }
}
