package com.isc.hsm.transparentlb.node;

import com.isc.hsm.transparentlb.config.LbProperties;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;

public class ThalesNodePool {

    private static final Logger log = LoggerFactory.getLogger(ThalesNodePool.class);

    private final ThalesNode node;
    private final LbProperties props;
    private final GenericObjectPool<Socket> pool;

    public ThalesNodePool(ThalesNode node, LbProperties props) {
        this.node = node;
        this.props = props;

        LbProperties.PoolConfig pc = props.getPool();
        GenericObjectPoolConfig<Socket> cfg = new GenericObjectPoolConfig<>();
        cfg.setMaxTotal(pc.getMaxTotal());
        cfg.setMinIdle(pc.getMinIdle());
        cfg.setMaxWait(Duration.ofMillis(pc.getMaxWaitMs()));
        cfg.setTestOnBorrow(true);
        cfg.setTestWhileIdle(true);

        this.pool = new GenericObjectPool<>(
            new ThalesSocketFactory(node.getHost(), node.getPort(),
                pc.getConnectTimeoutMs(), pc.getSocketTimeoutMs()),
            cfg
        );
    }

    /**
     * Sends rawCommand bytes to this Thales node and returns the raw response bytes.
     * The Thales protocol uses a 2-byte big-endian length prefix.
     */
    public byte[] send(byte[] rawCommand) throws Exception {
        node.incrementActive();
        node.recordRequest();
        // Fresh socket per request — payShield closes the connection after each response
        // so persistent pooled sockets cause stale-connection failures under load.
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(props.getPool().getSocketTimeoutMs());
            socket.setTcpNoDelay(true);
            socket.connect(new java.net.InetSocketAddress(node.getHost(), node.getPort()),
                    props.getPool().getConnectTimeoutMs());

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // eznet-tcp2jms strips the 2-byte length prefix before publishing to JMS;
            // payShield expects it on the wire, so we reattach it here.
            int len = rawCommand.length;
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
            out.write(rawCommand);
            out.flush();

            return readResponse(in);
        } catch (Exception e) {
            node.recordError();
            throw e;
        } finally {
            node.decrementActive();
        }
    }

    private byte[] readResponse(InputStream in) throws Exception {
        // Read 2-byte length header then body.
        // Return only the body — eznet-tcp2jms re-adds the 2-byte TCP framing when
        // sending back to the TCP client, so including it here would double-prefix.
        byte[] lenBuf = new byte[2];
        readFully(in, lenBuf, 2);
        int bodyLen = ((lenBuf[0] & 0xFF) << 8) | (lenBuf[1] & 0xFF);

        byte[] body = new byte[bodyLen];
        readFully(in, body, bodyLen);
        return body;
    }

    private void readFully(InputStream in, byte[] buf, int len) throws Exception {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, read, len - read);
            if (n < 0) throw new Exception("Connection closed by HSM node " + node.getId());
            read += n;
        }
    }

    public void close() {
        pool.close();
    }

    public ThalesNode getNode() {
        return node;
    }

    public int getNumActive() {
        return pool.getNumActive();
    }

    public int getNumIdle() {
        return pool.getNumIdle();
    }
}
