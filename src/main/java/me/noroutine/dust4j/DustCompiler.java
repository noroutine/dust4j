package me.noroutine.dust4j;

/**
 * @author Oleksii Khilkevych
 * @since 16.10.12
 */

public interface DustCompiler {
    String compile(String templateName, String template) throws Exception;
}
