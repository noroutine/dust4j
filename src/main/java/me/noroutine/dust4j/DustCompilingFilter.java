package me.noroutine.dust4j;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main workhorse of dust4j, the filter that intercepts the JSP/Servlet output stream and feeds the content of it to instance of {@link DustCompiler}
 *
 * This allows to use JSP or any other framework to create dynamic templates.
 * Supports simple session-based cache and eTag support to control client-side caching of compiled templates
 *
 * <h3>Setup</h3>
 * <p>
 * To setup filter in your application, add the definitions to yout web.xml:
 *
 * <pre>&nbsp;{@code
 *
 * <filter>
 *     <filter-name>dustCompilingFilter</filter-name>
 *     <filter-class>me.noroutine.dust4j.DustCompilingFilter</filter-class>
 * </filter>
 *
 * <filter-mapping>
 *     <filter-name>dustCompilingFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 *
 * Note that you should use /* as url-pattern, due to the fact that *.dust.js doesn't work as one would expect.
 * So filter will apply for any URL, but it will only compile those that match the <code>templateNameRegex</code>,
 * which is by default any URL that ends with .dust.js
 * </p>
 *
 * <h3>Configuration</h3>
 * <p>
 * This filter supports the following parameters:
 *
 * <table>
 * <thead>
 * <th>init-param name</th>
 * <th>Type</th>
 * <th>Default</th>
 * <th>Description</th>
 * <tr>
 * <td>cache</td><td>boolean</td><td>true</td><td>Enable/disable internal cache</td>
 * </tr>
 * <tr>
 * <td>eTag</td><td>boolean</td><td>false</td><td>Enable/disable ETag support</td>
 * </tr>
 * <tr>
 * <td>compilerFactory</td><td>string</td><td>me.noroutine.dust4j.DefaultDustCompilerFactory</td><td>Canonical name of factory for obtaining DustCompiler instance. Should implement DustCompilerFactory interface</td>
 * </tr>
 * <tr>
 * <td>templateNameRegex</td><td>regular expression</td><td>/(.*).dust.js$</td><td>Regex to apply to relative part of requests to generate template names. Should contain one and only matching group that will be used to infer template name
 * </tr>
 * </table>
 * </p>
 * <h3>Using with Spring (and possibly others?)</h3>
 * <p>
 * Filter has a set of setters to use it as a component in DI framework like Spring.
 * Spring provides a proxy servlet filter that can delegate filter processing to Spring-managed bean.
 *
 * To configure this, define your filter like this:
 * <pre>&nbsp;{@code
 *
 * <filter>
 *     <filter-name>dustCompilingFilter</filter-name>
 *     <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
 * </filter>
 *
 * <filter-mapping>
 *     <filter-name>dustCompilingFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }</pre>
 *
 * And define a bean with id <code>dustCompilingFilter</code>in your applicationContext.xml (<strong>NOT</strong> dispatcher servlet context)
 * <pre>&nbsp;{@code
 *
 * <bean name="dustCompilingFilter" class="me.noroutine.dust4j.DustCompilingFilter">
 *    <property name="compilerFactory">
 *        <bean class="me.noroutine.dust4j.DefaultDustCompilerFactory" />
 *    </property>
 *    <property name="cacheEnabled" value="true" />
 *    <property name="ETagEnabled" value="true" />
 * </bean>
 * }</pre>
 * </p>
 *
 * @author Oleksii Khilkevych
 * @since dust4j 0.1
 */

public class DustCompilingFilter implements Filter {

    private static final Logger log = Logger.getLogger(DustCompilingFilter.class.getCanonicalName());

    private static final String DUST_TEMPLATE_CACHE_ATTR = "com.noroutine.dust4j.dustTemplateCache";

    private static final String DEFAULT_NAME_REGEX = "/(.*).dust.js$";

    // init-param keys
    private static final String PARAM_COMPILER_FACTORY = "compilerFactory";
    private static final String PARAM_CACHE = "cache";
    private static final String PARAM_ETAG = "eTag";
    private static final String PARAM_NAME_REGEX = "templateNameRegex";

    private DustCompilerFactory compilerFactory = new DefaultDustCompilerFactory();

    private DustCompiler compiler;

    private String templateNameRegex = DEFAULT_NAME_REGEX;

    private boolean cacheEnabled = true;

    private boolean eTagEnabled = false;

    public DustCompilingFilter() {
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String appCtx = filterConfig.getServletContext().getContextPath();

        if (filterConfig.getInitParameter(PARAM_CACHE) != null) {
            cacheEnabled = Boolean.valueOf(filterConfig.getInitParameter(PARAM_CACHE));
        }

        if (filterConfig.getInitParameter(PARAM_ETAG) != null) {
            eTagEnabled = Boolean.valueOf(filterConfig.getInitParameter(PARAM_ETAG));
        }

        if (filterConfig.getInitParameter(PARAM_NAME_REGEX) != null) {
            templateNameRegex = filterConfig.getInitParameter(PARAM_NAME_REGEX);
        }

        try {
            Class<? extends DustCompilerFactory> compilerFactoryClass;
            if (filterConfig.getInitParameter(PARAM_COMPILER_FACTORY) != null) {
                compilerFactoryClass = (Class<? extends DustCompilerFactory>) Class.forName(filterConfig.getInitParameter(PARAM_COMPILER_FACTORY));
                compilerFactory = compilerFactoryClass.newInstance();
            }

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) resp;

            String appCtx = request.getSession().getServletContext().getContextPath();
            String uri = DustCompilingFilter.getURL(request);
            String uriRegex = getTemplateNameRegex(appCtx, templateNameRegex);

            if (uri.matches(uriRegex)) {
                if (this.compiler == null) {
                    this.compiler = this.compilerFactory.createDustCompiler();
                }

                if (this.compiler == null) {
                    log.log(Level.SEVERE, "Failed to obtain compiler instance, skipping for this request");
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
                    String templateName = uri.replaceFirst(uriRegex, "$1");
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
                    response.setContentLength(template.getBytes("UTF-8").length);

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

    private String getErrorTemplate(String templateName) {
        return "(function(){dust.register(\"" + templateName + "\",body_0);function body_0(chk,ctx){return chk.write(\"Failed to compile template\");}return body_0;})();";
    }

    private static String getTemplateNameRegex(String appCtx, String relativeRegex) {
        StringBuilder sb = new StringBuilder("^").append(appCtx);
        if (relativeRegex != null) {
            sb.append(relativeRegex.startsWith("^") ? relativeRegex.substring(1) : relativeRegex);
        } else {
            sb.append(DEFAULT_NAME_REGEX);
        }
        return sb.toString();
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public void setETagEnabled(boolean eTagEnabled) {
        this.eTagEnabled = eTagEnabled;
    }

    public void setCompilerFactory(DustCompilerFactory compilerFactory) {
        if (compilerFactory == null) {
            throw new IllegalArgumentException("compilerFactory bust be not null");
        }
        this.compilerFactory = compilerFactory;
        this.compiler = null;
    }

    public void setTemplateNameRegex(String templateNameRegex) {
        this.templateNameRegex = templateNameRegex;
    }

    public static String getURL(HttpServletRequest req) {
        String contextPath = req.getContextPath();
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();
        String queryString = req.getQueryString();

        StringBuffer url =  new StringBuffer();
        url.append(contextPath).append(servletPath);

        if (pathInfo != null) {
            url.append(pathInfo);
        }
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
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

