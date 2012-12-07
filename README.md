dust4j
======

Dust.js is the very nice and ultra-fast JavaScript templating library, that was chosen and adopted by LinkedIn from over 20 others.

This project provides extensible, zero-dependency and non-intrusive support for automatic on-the-fly compilation for Dust.js templates for any Java Web Application that adheres Java Servlet specification.

Features
--------

* no dependencies, except Java 6+ and Servlet API
* independent of any frameworks
* provides setters for integration with Spring's DelegatingFilterProxy
* extensible, you can configure and implement your own `DustCompilerFactory`
* integrated internal cache and ETag support
* configurable

Dust.js on-the-fly server-side compilation for your Java web applications

Requirements: none, except plain Java SDK 6+ and Servlet API

How to use?
-----------

Add dust4j as dependency to your Maven project

        <dependency>
            <groupId>me.noroutine</groupId>
            <artifactId>dust4j</artifactId>
            <version>0.1</version>
        </dependency>

Add DustCompilingFilter to your web.xml

        <filter>
            <filter-name>dustCompilingFilter</filter-name>
            <filter-class>me.noroutine.dust4j.DustCompilingFilter</filter-class>
        </filter>

        <filter-mapping>
            <filter-name>dustCompilingFilter</filter-name>
            <url-pattern>/*</url-pattern>
        </filter-mapping>

Setup your framework to return templates when URL ending with .dust.js are requested. Say, in Spring MVC 3.0, with Tiles, it'd be something like this:

* Controller code:

        @Controller
        @RequestMapping("/")
        public class HomeController {

            // first mapping is main, the second is useful for debugging, designing or client-side compilation
            // only URLs ending with .dust.js will trigger the compilation
            @RequestMapping({"/template/{template}.dust.js", "/template/{template}.dust"})
            public String getAddTemplate(@PathVariable String template) {
                return "template." + template;
            }

        }


* definition in tiles-defs.xml:

        ...
        <definition name="template.*" template="/WEB-INF/jsp/templates/{1}.jsp" />
        ...

Now, if you gonna ask your container for URL like `http://localhost:8080/fancyapp/template/dustjs_demo.dust.js` it will hit the JSP of your Dust.js template, that template will be evaluated by JSP engine and will be automatically compiled and client will get already compiled template

Compiled templates register themselves with client-side dust.js library under unique name. This name is generated automatically from request URL relative to application context, stripping down `.dust.js` suffix

For example for `http://localhost:8080/fancyapp/template/dustjs_demo.dust.js` template name will be 'template/dustjs_demo'. You can however change this to your needs.

On your web page, you need to include dust.js libraries

    <script type="text/javascript" src="/fancyapp/resources/js/vendor/dust-core-1.1.1.js"></script>
    <script type="text/javascript" src="/fancyapp/resources/js/vendor/dust-helpers-1.1.0.js"></script>

and your template

    <script type="text/javascript" defer="defer" src="/fancyapp/template/dustjs_demo.dust.js?version=6b3a3c4a3585142e39272dc3a31493b5b847e7a9&cache=true"></script>

Now, you're ready to use the template, which is registered with name `template/dustjs_demo`


Filter configuration options
----------------------------

<table>
<thead>
<th>init-param name</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
<tr>
<td>cache</td><td>Boolean</td><td>true</td><td>Enable/disable internal cache</td>
</tr>
<tr>
<td>eTag</td><td>Boolean</td><td>true</td><td>Enable/disable ETag support</td>
</tr>
<tr>
<td>compilerFactory</td><td>String</td><td>me.noroutine.dust4j.DefaultDustCompilerFactory</td><td>Factory for obtaining DustCompiler instance</td>
</tr>
<tr>
<td>templateNameRegex</td><td>Regular Expression</td><td>/(.*).dust.js</td><td>Regex to apply to relative part of requests to generate template names
</tr>
</table>


Overriding cache
----------------

Filter has cache enabled by default, as compilation takes significant time and slows downs container to commit the response. You can disable it, but this is not generally recommended.
If you have cache enabled and want to go through the cache to the compiler for debugging or whatever reasons, just add `cache=false` to your template URL.

ETag support
------------

Filter has built-in support for ETag for controlling client-side caching. To use it, you need to add `version` parameter to URL with ETag string you want to check. This string will be checked agains the If-None-Match header, if any, and in case of mismatch, the template will be recompiled.
Maintaining consistency of version parameter values is project-specific and is out of scope of this project.