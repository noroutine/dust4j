package me.noroutine.dust4j;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Oleksii Khilkevych
 * @since 16.10.12
 */

public class DustCompilingFilter implements Filter {

    private static final Logger log = Logger.getLogger(DustCompilingFilter.class.getCanonicalName());

    private static final String DUST_TEMPLATE_CACHE_ATTR = "com.noroutine.dust4j.dustTemplateCache";

    private static final String DEFAULT_DUST_SUFFIX = ".dust.js";
    private static final Class<? extends DustCompilerFactory> DEFAULT_COMPILER_FACTORY_CLASS = DefaultDustCompilerFactory.class;

    // init-param keys
    private static final String PARAM_COMPILER_FACTORY = "compilerFactory";
    private static final String PARAM_CACHE = "cache";
    private static final String PARAM_ETAG = "eTag";
    private static final String PARAM_NAME_REGEX = "templateNameRegex";

    private Class<? extends DustCompilerFactory> compilerFactoryClass;

    private DustCompilerFactory compilerFactory;

    private DustCompiler compiler;

    private String templateNameRegex;

    private boolean cacheEnabled;

    private boolean eTagEnabled;

    public DustCompilingFilter() {
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String appCtx = filterConfig.getServletContext().getContextPath();

        if (filterConfig.getInitParameter(PARAM_CACHE) != null) {
            cacheEnabled = Boolean.valueOf(filterConfig.getInitParameter(PARAM_CACHE));
        } else {
            cacheEnabled = true;
        }

        if (filterConfig.getInitParameter(PARAM_ETAG) != null) {
            eTagEnabled = Boolean.valueOf(filterConfig.getInitParameter(PARAM_ETAG));
        } else {
            eTagEnabled = true;
        }

        templateNameRegex = getTemplateNameRegex(appCtx, filterConfig.getInitParameter(PARAM_NAME_REGEX));


        try {
            if (filterConfig.getInitParameter(PARAM_COMPILER_FACTORY) != null) {
                compilerFactoryClass = (Class<? extends DustCompilerFactory>) Class.forName(filterConfig.getInitParameter(PARAM_COMPILER_FACTORY));
            } else {
                // if no factory specified, check for compiler
                compilerFactoryClass = DEFAULT_COMPILER_FACTORY_CLASS;
            }

            compilerFactory = compilerFactoryClass.newInstance();
            compiler = compilerFactory.createDustCompiler();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) resp;

            if (request.getRequestURI().matches(templateNameRegex)) {
                if (this.compiler == null) {
                    log.log(Level.SEVERE, "Dust.js is not setup correctly, skipping for this request");
                } else {
                    boolean cache = Boolean.valueOf(request.getParameter("cache"));
                    String version = request.getParameter("version");

                    // Check If-None-Match vs version
                    String clientETag = request.getHeader("If-None-Match");
                    if (cache && eTagEnabled && version != null && clientETag != null && version.equals(clientETag)) {
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return;
                    }

                    PrintWriter out = response.getWriter();
                    Map<String, String> templateCache = getDustTemplateCache(request);
                    String templateName = getTemplateName(request.getRequestURI());
                    String template;

                    if (cache && cacheEnabled && templateCache.containsKey(templateName)) {
                        log.info("Template cache hit for " + templateName);
                        template = templateCache.get(templateName);
                    } else {
                        CharResponseWrapper responseWrapper = new CharResponseWrapper(response);
                        chain.doFilter(req, responseWrapper);
                        String dustTemplate = responseWrapper.toString();

                        log.info("Compiling output with dust.js");
                        try {
                            long startTimeMs = System.currentTimeMillis();
                            template = compiler.compile(templateName, dustTemplate);
                            long compileTimeMs = System.currentTimeMillis() - startTimeMs;
                            log.info("Compiling time for " + templateName + " (ms): " + compileTimeMs);
                            if (cache && cacheEnabled) {
                                templateCache.put(templateName, template);
                            } else {
                                log.info("Template cache is disabled, this will slow down template load significantly");
                            }
                        } catch (Exception e) {
                            log.log(Level.SEVERE, "Failed to compile template " + templateName, e);
                            template = getErrorTemplate(templateName);
                        }
                    }

                    response.setContentType("application/json");
                    response.setContentLength(template.length());

                    // set ETag
                    if (cache && eTagEnabled && version != null) {
                        response.setHeader("ETag", version);
                    }

                    out.write(template);
                    out.close();
                    return;
                }
            }
        }
        chain.doFilter(req, resp);
    }

    @Override
    public void destroy() {

    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getDustTemplateCache(HttpServletRequest request) {
        Map<String, String> cache = (Map<String, String>) request.getSession().getAttribute(DUST_TEMPLATE_CACHE_ATTR);
        if (cache == null) {
            cache = new HashMap<String, String>();
            request.getSession().setAttribute(DUST_TEMPLATE_CACHE_ATTR, cache);
        }
        return cache;
    }

    private String getTemplateName(String templateUri) {
        return templateUri.replaceFirst(templateNameRegex, "$1");
    }

    private String getErrorTemplate(String templateName) {
        return "(function(){dust.register(\"" + templateName + "\",body_0);function body_0(chk,ctx){return chk.write(\"Failed to compile template\");}return body_0;})();";
    }

    private static String getTemplateNameRegex(String appCtx, String relativeRegex) {
        StringBuilder sb = new StringBuilder("^").append(appCtx);
        if (relativeRegex != null) {
            sb.append(relativeRegex.startsWith("^") ? relativeRegex.substring(1) : relativeRegex);
        } else {
            if (appCtx.charAt(appCtx.length() - 1) != '/') {
                sb.append("/");
            }
            sb.append("(.*)").append(DEFAULT_DUST_SUFFIX).append("$");
        }
        return sb.toString();
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void setETagEnabled(boolean eTagEnabled) {
        this.eTagEnabled = eTagEnabled;
    }

    public void setCompiler(DustCompiler compiler) {
        this.compiler = compiler;
    }

    public void setTemplateNameRegex(String templateNameRegex) {
        this.templateNameRegex = templateNameRegex;
    }

}

class CharResponseWrapper extends HttpServletResponseWrapper {

    private ByteArrayOutputStream out;

    private PrintWriter printWriter;
    private ServletOutputStream servletOutputStream;

    public String toString() {
        return out.toString();
    }

    public CharResponseWrapper(HttpServletResponse response) {
        super(response);
        this.out = new ByteArrayOutputStream();
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (printWriter != null) {
            throw new IllegalStateException("getWriter() was already called");
        }

        if (servletOutputStream == null) {
            servletOutputStream = createServletOutputStream();
        }

        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws UnsupportedEncodingException {
        if (servletOutputStream != null) {
            throw new IllegalStateException("getOutputStream() was already called");
        }

        if (printWriter == null) {
            printWriter = createPrintWriter();
        }

        return printWriter;
    }

    private PrintWriter createPrintWriter() throws UnsupportedEncodingException {
        return new PrintWriter(new OutputStreamWriter(out, "UTF-8"));
    }

    private ServletOutputStream createServletOutputStream() {
        return new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }
        };
    }
}

