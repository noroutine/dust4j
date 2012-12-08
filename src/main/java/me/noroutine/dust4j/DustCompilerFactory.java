package me.noroutine.dust4j;

/**
 * Factory interface for obtaining {@link DustCompiler} instance.
 * The class specified as <pre>compilerFactory</pre> init-param value should implement this interface
 *
 * @see DustCompiler
 * @see DefaultDustCompilerFactory
 * @author Oleksii Khilkevych
 * @since dust4j 0.1
 */
public interface DustCompilerFactory {

    /**
     * Factory method to obtain {@link DustCompiler} instance
     *
     * @return compiler instance
     */
    DustCompiler createDustCompiler();
}
