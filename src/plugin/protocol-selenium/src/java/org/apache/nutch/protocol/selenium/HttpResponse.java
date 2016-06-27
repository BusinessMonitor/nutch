/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nutch.protocol.selenium;

import com.google.common.base.Predicate;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.metadata.SpellCheckedMetadata;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.storage.WebPage;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class HttpResponse implements Response {

    private static final Logger LOG = LoggerFactory.getLogger(HttpResponse.class);
    private Http http;
    private URL url;
    private byte[] content;
    private int code;
    private Metadata headers = new SpellCheckedMetadata();

    /**
     * The nutch configuration
     */
    private Configuration conf = null;

    public HttpResponse(Http http, URL url, WebPage page, Configuration conf) throws UnsupportedEncodingException {

        this.conf = conf;
        this.http = http;
        this.url = url;

        getCapabilities(conf);
        
        String externalForm = url.toExternalForm();
        if(externalForm.startsWith("file://") && !externalForm.startsWith("file:///")) {
            try {
                url = new URL(url.toExternalForm().replace("file://", "file:///"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }



        final WebDriver driver = getDriver(getCapabilities(conf));
        LOG.info("Fetching URL " + url);
        try {
            int timeout = http.getTimeout();

            // This should be extracted to a HTTPRenderBase class or similar
            int sleep = conf.getInt("http.min.render", 1500);

            driver.manage().timeouts().pageLoadTimeout(sleep, TimeUnit.MILLISECONDS);

            driver.get(url.toString());
            // Wait for the page to load, timeout after 3 seconds
            Thread.sleep(Math.min(sleep, timeout));
            getContent(driver);
            LOG.info("Successfully fetched URL " + url);
        } catch (InterruptedException e) {
            LOG.warn("WebDriver was interrupted before trying to fetch response", e);
        } catch(TimeoutException e) {
            getContent();
            LOG.warn("WebDriver timed out, returning original content", e);
        } finally {
            driver.close();
        }
    }

    private void getContent(WebDriver driver) throws UnsupportedEncodingException {
        String innerHtml = driver.findElement(By.tagName("html")).getAttribute("innerHTML");
        code = 200;
        content = innerHtml.getBytes("UTF-8");
    }

    private WebDriver getDriver(DesiredCapabilities capabilities) {
        String hubHost = System.getProperty("selenium.hub.host", "localhost");
        String hubPort = System.getProperty("selenium.hub.port", "4444");

        try {
            return new RemoteWebDriver(new URL("http://" + hubHost + ":" + hubPort + "/wd/hub"), capabilities);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private DesiredCapabilities getCapabilities(Configuration conf) {
        DesiredCapabilities capabilities = DesiredCapabilities.firefox();
        capabilities.setBrowserName("firefox");
        capabilities.setJavascriptEnabled(true);
        return capabilities;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public Metadata getHeaders() {
        return headers;
    }

    @Override
    public byte[] getContent() {
        return content;
    }
}
