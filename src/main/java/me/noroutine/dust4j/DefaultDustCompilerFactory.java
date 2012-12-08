package me.noroutine.dust4j;

import javax.script.ScriptException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Default implementation of {@link DustCompilerFactory}
 *
 * @author Oleksii Khilkevych
 * @since dust4j 0.1
 */

public class DefaultDustCompilerFactory implements DustCompilerFactory {

    private static final String DUST_FULL = "com/linkedin/dustjs/dust-full-1.1.1.min.js";

    public DefaultDustCompilerFactory() {
    }

    @Override
    public DustCompiler createDustCompiler() {
        Reader dustJsReader = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(DUST_FULL));
        try {
            return new RhinoDustCompiler(dustJsReader);
        } catch (ScriptException se) {
            throw new RuntimeException(se);
        }
    }

}
