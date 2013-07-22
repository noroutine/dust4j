package me.noroutine.dust4j;

/**
 * Interface that any dust.js compiler should implement to be plugged into dust4j
 *
 * @see DustCompilerFactory
 * @see RhinoDustCompiler
 * @author Oleksii Khilkevych
 * @since dust4j 0.1
 */
public interface DustCompiler {

    /**
     *
     * @param templateName  dust.js template name
     * @param template  template source code
     * @return compiled dust.js template
     * @throws Exception    for any failure
     */
    String compile(String templateName, String template) throws Exception;
}
