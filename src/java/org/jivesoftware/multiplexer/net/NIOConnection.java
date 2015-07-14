/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.multiplexer.net;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.CompressionFilter;
import org.apache.mina.filter.SSLFilter;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.ConnectionCloseListener;
import org.jivesoftware.multiplexer.PacketDeliverer;
import org.jivesoftware.multiplexer.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.KeyStore;

/**
 * Implementation of {@link Connection} inteface specific for NIO connections when using
 * the MINA framework.<p>
 *
 * MINA project can be found at <a href="http://mina.apache.org">here</a>. 
 *
 * @author Gaston Dombiak
 */
public class NIOConnection implements Connection {

    /**
     * The utf-8 charset for decoding and encoding XMPP packet streams.
     */
    public static final String CHARSET = "UTF-8";
    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    private Session session;
    private IoSession ioSession;

    private ConnectionCloseListener closeListener;

    /**
     * Deliverer to use when the connection is closed or was closed when delivering
     * a packet.
     */
    private PacketDeliverer backupDeliverer;
    private boolean flashClient = false;
    private int majorVersion = 1;
    private int minorVersion = 0;
    private String language = null;

    // TODO Uso el #checkHealth????
    /**
     * TLS policy currently in use for this connection.
     */
    private TLSPolicy tlsPolicy = TLSPolicy.optional;

    /**
     * Compression policy currently in use for this connection.
     */
    private CompressionPolicy compressionPolicy = CompressionPolicy.disabled;
    private CharsetEncoder encoder;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    public NIOConnection(IoSession session, PacketDeliverer packetDeliverer) {
        this.ioSession = session;
        this.backupDeliverer = packetDeliverer;
        encoder = Charset.forName(CHARSET).newEncoder();
    }

    public boolean validate() {
        if (isClosed()) {
            return false;
        }
        deliverRawText(" ");
        return !isClosed();
    }

    public void registerCloseListener(ConnectionCloseListener listener, Object ignore) {
        if (closeListener != null) {
            throw new IllegalStateException("Close listener already configured");
        }
        if (isClosed()) {
            listener.onConnectionClose(session);
        }
        else {
            closeListener = listener;
        }
    }

    public void removeCloseListener(ConnectionCloseListener listener) {
        if (closeListener == listener) {
            closeListener = null;
        }
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        return ((InetSocketAddress) ioSession.getRemoteAddress()).getAddress();
    }

    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    public void close() {
        boolean wasClosed = false;
        synchronized (this) {
            if (!isClosed()) {
                try {
                    deliverRawText(flashClient ? "</flash:stream>" : "</stream:stream>", false);
                } catch (Exception e) {
                    // Ignore
                }
                closeConnection();
                wasClosed = true;
            }
        }
        if (wasClosed) {
            notifyCloseListeners();
        }
    }

    public void systemShutdown() {
        deliverRawText("<stream:error><system-shutdown " +
                "xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");
        close();
    }

    /**
     * Forces the connection to be closed immediately no matter if closing the socket takes
     * a long time. This method should only be called from {@link SocketSendingTracker} when
     * sending data over the socket has taken a long time and we need to close the socket, discard
     * the connection and its ioSession.
     */
    private void forceClose() {
        closeConnection();
        // Notify the close listeners so that the SessionManager can send unavailable
        // presences if required.
        notifyCloseListeners();
    }

    private void closeConnection() {
        ioSession.close();
    }

    /**
     * Notifies all close listeners that the connection has been closed.
     * Used by subclasses to properly finish closing the connection.
     */
    private void notifyCloseListeners() {
        if (closeListener != null) {
            try {
                closeListener.onConnectionClose(session);
            } catch (Exception e) {
                Log.error("Error notifying listener: " + closeListener, e);
            }
        }
    }

    public void init(Session owner) {
        session = owner;
    }

    public boolean isClosed() {
        if (session == null) {
            return !ioSession.isConnected();
        }
        return session.getStatus() == Session.STATUS_CLOSED && !ioSession.isConnected();
    }

    public boolean isSecure() {
        return ioSession.getFilterChain().contains("tls");
    }

    public void deliver(String stanza) {
        if (isClosed()) {
            XMPPPacketReader xmppReader = new XMPPPacketReader();
            xmppReader.setXPPFactory(factory);
            try {
                Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
                backupDeliverer.deliver(doc);
            } catch (Exception e) {
                Log.error("Error parsing stanza: " + stanza, e);
            }
        }
        else {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                buffer.putString(stanza, encoder);
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                //System.out.println("SENT: " + doc.asXML());
                ioSession.write(buffer);
            }
            catch (Exception e) {
                Log.debug("Error delivering packet" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                XMPPPacketReader xmppReader = new XMPPPacketReader();
                xmppReader.setXPPFactory(factory);
                try {
                    Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
                    backupDeliverer.deliver(doc);
                } catch (Exception e) {
                    Log.error("Error parsing stanza: " + stanza, e);
                }
            }
        }
    }

    public void deliverRawText(String text) {
        // Deliver the packet in asynchronous mode
        deliverRawText(text, true);
    }

    public void deliverRawText(String text, boolean asynchronous) {
        if (!isClosed()) {
            ByteBuffer buffer = ByteBuffer.allocate(text.length());
            buffer.setAutoExpand(true);

            boolean errorDelivering = false;
            try {
                //Charset charset = Charset.forName(CHARSET);
                //buffer.putString(text, charset.newEncoder());
                buffer.put(text.getBytes(CHARSET));
                if (flashClient) {
                    buffer.put((byte) '\0');
                }
                buffer.flip();
                if (asynchronous) {
                    ioSession.write(buffer);
                }
                else {
                    // Send stanza and wait for ACK (using a 2 seconds default timeout)
                    boolean ok =
                            ioSession.write(buffer).join(JiveGlobals.getIntProperty("connection.ack.timeout", 2000));
                    if (!ok) {
                        Log.warn("No ACK was received when sending stanza to: " + this.toString());
                    }
                }
            }
            catch (Exception e) {
                Log.debug("Error delivering raw text" + "\n" + this.toString(), e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
            }
        }
    }

    public void startTLS(boolean clientMode, String remoteServer) throws Exception {
        KeyStore ksKeys = SSLConfig.getKeyStore();
        String keypass = SSLConfig.getKeyPassword();

        KeyStore ksTrust = SSLConfig.getTrustStore();
        String trustpass = SSLConfig.getTrustPassword();

        // KeyManager's decide which key material to use.
        KeyManager[] km = SSLJiveKeyManagerFactory.getKeyManagers(ksKeys, keypass);

        // TrustManager's decide whether to allow connections.
        TrustManager[] tm = SSLJiveTrustManagerFactory.getTrustManagers(ksTrust, trustpass);
        // TODO Set proper value when s2s is supported
        boolean needClientAuth = false;
        if (clientMode || needClientAuth) {
            // Check if we can trust certificates presented by the server
            tm = new TrustManager[]{new ServerTrustManager(remoteServer, ksTrust)};
        }

        SSLContext tlsContext = SSLContext.getInstance("TLS");

        tlsContext.init(km, tm, null);

        SSLFilter filter = new SSLFilter(tlsContext);
        filter.setUseClientMode(clientMode);
        if (needClientAuth) {
            // Only REQUIRE client authentication if we are fully verifying certificates
            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true) &&
                    JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true) &&
                    !JiveGlobals
                            .getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false))
            {
                filter.setNeedClientAuth(true);
            }
            else {
                // Just indicate that we would like to authenticate the client but if client
                // certificates are self-signed or have no certificate chain then we are still
                // good
                filter.setWantClientAuth(true);
            }
        }

        ioSession.getFilterChain().addAfter("org.apache.mina.common.ExecutorThreadModel", "tls", filter);
        ioSession.setAttribute(SSLFilter.DISABLE_ENCRYPTION_ONCE, Boolean.TRUE);
        if (!clientMode) {
            // Indicate the client that the server is ready to negotiate TLS
            deliverRawText("<proceed xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>");
        }
    }

    public void startCompression() {
        IoFilterChain chain = ioSession.getFilterChain();
        String baseFilter = "org.apache.mina.common.ExecutorThreadModel";
        if (chain.contains("tls")) {
            baseFilter = "tls";
        }
        chain.addAfter(baseFilter, "compression", new CompressionFilter(CompressionFilter.COMPRESSION_MAX));
    }

    public boolean isFlashClient() {
        return flashClient;
    }

    public void setFlashClient(boolean flashClient) {
        this.flashClient = flashClient;
    }

    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanaguage(String language) {
        this.language = language;
    }

    public boolean isCompressed() {
        return ioSession.getFilterChain().contains("compression");
    }

    public CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    public void setCompressionPolicy(CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    public TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    public void setTlsPolicy(TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    public String toString() {
        return super.toString() + " MINA Session: " + ioSession;
    }
}
