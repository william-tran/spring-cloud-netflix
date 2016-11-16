/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.eureka;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.protocol.HttpContext;

/**
 * Adapts a version 4.3+ {@link SSLConnectionSocketFactory} to a pre 4.3
 * {@link SSLSocketFactory}. This allows {@link HttpClient}s built using the
 * deprecated pre 4.3 APIs to use SSL improvements from 4.3, e.g. SNI.
 * 
 * @author William Tran
 *
 */
class SSLSocketFactoryAdapter extends SSLSocketFactory {

    private final SSLConnectionSocketFactory factory;

    SSLSocketFactoryAdapter(SSLConnectionSocketFactory factory) {
        // super's dependencies are dummies, and will delegate all calls to the
        // to the overridden methods
        super(DummySSLSocketFactory.INSTANCE, DummyX509HostnameVerifier.INSTANCE);
        this.factory = factory;
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        return factory.createSocket(context);
    }

    @Override
    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        return factory.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
    }

    @Override
    public Socket createLayeredSocket(
            final Socket socket,
            final String target,
            final int port,
            final HttpContext context) throws IOException {
        return factory.createLayeredSocket(socket, target, port, context);
    }

    private static class DummySSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private static final DummySSLSocketFactory INSTANCE = new DummySSLSocketFactory();

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    private static class DummyX509HostnameVerifier implements X509HostnameVerifier {
        private static final DummyX509HostnameVerifier INSTANCE = new DummyX509HostnameVerifier();

        @Override
        public boolean verify(String hostname, SSLSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, SSLSocket ssl) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, X509Certificate cert) throws SSLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
            throw new UnsupportedOperationException();
        }

    }

}
