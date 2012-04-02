package ca.redtoad.wstest;

import org.apache.catalina.websocket.MessageInbound;
import org.apache.catalina.websocket.StreamInbound;
import org.apache.catalina.websocket.WebSocketServlet;
import org.apache.catalina.websocket.WsOutbound;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

public class WSTestServlet extends WebSocketServlet {
    private static final long serialVersionUID = 1L;

    private static final long STATUS_DELAY = 5000;
    private final Timer statusTimer = new Timer(WSTestServlet.class.getSimpleName() + " StatusTimer");
    private final AtomicInteger connectionIds = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, TestMessageInbound> connections = new ConcurrentHashMap<Integer, TestMessageInbound>();
    private static Logger logger = Logger.getLogger(WSTestServlet.class.getName());

    @Override
    public void init() throws ServletException {
        super.init();
        statusTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                status();
            }
        }, STATUS_DELAY, STATUS_DELAY);
    }

    private void status() {
        broadcast(String.format("current status: %d active connections", connections.size()));
    }

    private void broadcast(String message) {
        logger.info(String.format("broadcast(%s)", message));
        for (TestMessageInbound connection : getConnections()) {
            try {
                CharBuffer response = CharBuffer.wrap(message);
                connection.getWsOutbound().writeTextMessage(response);
            } catch (IOException ignore) {
            }
        }
    }

    private Collection<TestMessageInbound> getConnections() {
        return Collections.unmodifiableCollection(connections.values());
    }

    @Override
    public void destroy() {
        super.destroy();
        if (statusTimer != null) {
            statusTimer.cancel();
        }
    }

    @Override
    protected StreamInbound createWebSocketInbound(String subProtocol) {
        logger.info("createWebSocketInbound");
        return new TestMessageInbound(connectionIds.incrementAndGet());
    }

    private final class TestMessageInbound extends MessageInbound {
        private final int id;

        private TestMessageInbound(int id) {
            this.id = id;
        }

        @Override
        protected void onOpen(WsOutbound outbound) {
            logger.info("onOpen " + id);
            connections.put(id, this);
            broadcast(String.format("client %d joined", id));
        }

        @Override
        protected void onClose(int status) {
            logger.info("onClose " + id);
            connections.remove(id);
            broadcast(String.format("client %d left", id));
        }

        @Override
        protected void onBinaryMessage(ByteBuffer message) throws IOException {
            logger.info("onBinaryMessage " + id);
            throw new UnsupportedOperationException("Binary message not supported.");
        }

        @Override
        protected void onTextMessage(CharBuffer charBuffer) throws IOException {
            logger.info("onTextMessage " + id);
            broadcast(String.format("client %d says %s", id, charBuffer.toString()));
        }
    }
}
