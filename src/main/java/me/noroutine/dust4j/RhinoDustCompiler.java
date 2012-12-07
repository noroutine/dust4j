package me.noroutine.dust4j;

import javax.script.*;
import java.io.Reader;

/**
 * @author Oleksii Khilkevych
 * @since 07.12.12
 */

public class RhinoDustCompiler implements DustCompiler {

    private Invocable invocable;
    private Object dust;

    public RhinoDustCompiler(Reader dustJs) throws ScriptException {
        ScriptEngine engine = getJavaScriptEngine();
        this.invocable = (Invocable) engine;
        engine.eval(dustJs);
        this.dust = engine.get("dust");
    }

    @Override
    public String compile(String templateName, String template) throws Exception {
        return invocable.invokeMethod(dust, "compile", template, templateName).toString();
    }

    private ScriptEngine getJavaScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager();
        return manager.getEngineByName("JavaScript");
    }
}
