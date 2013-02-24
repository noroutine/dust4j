package me.noroutine.dust4j;

import javax.script.*;
import java.io.Reader;

/**
 * JavaScript-based compiler, that uses dust.js library to compile templates.
 * Default implementation of {@link DustCompiler}.
 *
 * @author Oleksii Khilkevych
 * @since dust4j 0.1
 */

public class RhinoDustCompiler implements DustCompiler {

    private Invocable invocable;
    private Object dust;

    /**
     * @param dustJs    reader that dust.js library would be read from
     * @throws ScriptException  would be thrown if there is any problem with JavaScript code
     */
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
