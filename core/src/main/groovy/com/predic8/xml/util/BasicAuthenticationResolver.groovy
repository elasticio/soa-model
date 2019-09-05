/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.xml.util

import com.predic8.schema.Import as SchemaImport
import com.predic8.wsdl.Import as WsdlImport
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.Credentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.params.ConnRoutePNames
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BasicAuthenticationResolver extends ResourceResolver {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthenticationResolver.class)

    def baseDir = ''
    def username = ''
    def password = ''
    def proxyHost
    def proxyPort

    def resolve(input, baseDir) {
        this.baseDir = baseDir
        this.resolve(input)
    }

    def resolve(input) {
        if ( input instanceof SchemaImport ) {
            if ( !input.schemaLocation ) return
            input = input.schemaLocation
        }

        if ( input instanceof WsdlImport ) {
            if ( !input.location ) return
            input = input.location
        }

        if ( input instanceof InputStream )  {
            log.debug("resolving from reader, baseDir: $baseDir")
            return input;
        }

        if(input instanceof Reader) {
            throw new RuntimeException("Please use an InputStream instead of Reader!")
        }

        log.debug("resolving: $input, baseDir: $baseDir")

        if(input instanceof File){
            return new FileInputStream(input)
        }
        if (! input instanceof String)
            throw new RuntimeException("Do not know how to resolve $input")

        if(input.startsWith('file')){
            def url = new URL(input)
            return new FileInputStream(url.getPath())
        } else if(input.startsWith('http') || input.startsWith('https')) {
            return resolveViaHttp(input)
        } else {
            if(baseDir && (baseDir.startsWith('http') || baseDir.startsWith('https'))){
                return resolveViaHttp(baseDir + input)
            } else if(baseDir) {
                return new FileInputStream(baseDir+input)
            }
            else {
                def file = new File(input)
                return new FileInputStream(file.getAbsolutePath())
            }
        }
    }

    private request(url) {
        HttpClient client = new DefaultHttpClient();
        HttpClientContext context
        if ( username ) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password))

            context = HttpClientContext.create()
            context.setCredentialsProvider(credsProvider)
        }
        if ( proxyHost ) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpGet method = new HttpGet(url)
        method.setHeader("User-Agent", "SOA Model (see http://membrane-soa.org)")
        HttpResponse response = context != null ? client.execute(method, context) : client.execute(method)
        if(response.statusLine.statusCode != 200) {
            def rde = new ResourceDownloadException("could not get resource $url by HTTP")
            rde.status = response.statusLine.statusCode
            rde.url = url
            method.releaseConnection()
            throw rde
        }
        response
    }

    protected resolveViaHttp(url) {
        /*def con = request(url)
        def res = con.getResponseBodyAsStream()
        //con.releaseConnection()
        res*/
        new StringReader(resolveAsString(url))
    }

    public resolveAsString(url) {
        try{
            HttpResponse con = request(url)
            EntityUtils.toString(con.entity)
        } catch (ResourceDownloadException e) {
            throw e
        } catch (Exception e) {
            throw new ResourceDownloadException(rootCause : e, url : url)
        }
    }

}
