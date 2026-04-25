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
    private final GenericObjectPool<Socket> pool;

    public ThalesNodePool(ThalesNode node, LbProperties props) {
        this.node = node;

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
        Socket socket = null;
        try {
            socket = pool.borrowObject();
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(rawCommand);
            out.flush();

            return readResponse(in);
        } catch (Exception e) {
            node.recordError();
            if (socket != null) {
                pool.invalidateObject(socket);
                socket = null;
            }
            throw e;
        } finally {
            node.decrementActive();
            if (socket != null) {
                pool.returnObject(socket);
            }
        }
    }

    private byte[] readResponse(InputStream in) throws Exception {
        // Read 2-byte length header
        byte[] lenBuf = new byte[2];
        readFully(in, lenBuf, 2);
        int bodyLen = ((lenBuf[0] & 0xFF) << 8) | (lenBuf[1] & 0xFF);

        byte[] body = new byte[bodyLen];
        readFully(in, body, bodyLen);

        byte[] full = new byte[2 + bodyLen];
        full[0] = lenBuf[0];
        full[1] = lenBuf[1];
        System.arraycopy(body, 0, full, 2, bodyLen);
        return full;
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
