package network.thunder.core.mesh;

import io.netty.channel.ChannelHandlerContext;
import network.thunder.core.communication.Message;
import network.thunder.core.communication.Type;
import network.thunder.core.communication.nio.P2PContext;
import network.thunder.core.communication.objects.p2p.DataObject;
import network.thunder.core.communication.objects.p2p.gossip.InvObject;
import network.thunder.core.communication.objects.subobjects.AuthenticationObject;
import network.thunder.core.etc.Tools;
import network.thunder.core.etc.crypto.CryptoTools;
import org.bitcoinj.core.ECKey;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

public class Node {

    public static final int THRESHHOLD_INVENTORY_AMOUNT_TO_SEND = 32;
    //From the gossip handler upwards nodes have their own connection object
    public Connection conn;
    public boolean justFetchNewIpAddresses = false;
    public P2PContext context;
    protected ECKey pubKeyTempClient;
    protected ECKey pubKeyTempServer;
    private String host;
    private int port;
    private boolean connected = false;
    private ChannelHandlerContext nettyContext;
    private byte[] pubkey;
    private boolean isAuth;
    private boolean sentAuth;
    private boolean authFinished;
    private boolean isReady;
    private boolean hasOpenChannel;
    private ArrayList<byte[]> inventoryList = new ArrayList<>();
    private OnConnectionCloseListener onConnectionCloseListener;

    public Node (String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Node (ResultSet set) throws SQLException {
        this.host = set.getString("host");
        this.port = set.getInt("port");
    }

    public Node (byte[] pubkey) {
        this.pubkey = pubkey;
    }

    public Node () {
    }

    public boolean allowsAuth () {
        return !isAuth;
    }

    public void closeConnection () {
        if (onConnectionCloseListener != null) {
            onConnectionCloseListener.onClose();
        }
        try {
            this.nettyContext.close();
        } catch (Exception e) {
        }
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Node node = (Node) o;

        return Arrays.equals(pubkey, node.pubkey);

    }

    public void finishAuth () {
        authFinished = true;
    }

    public AuthenticationObject getAuthenticationObject (ECKey keyServer, ECKey keyClientTemp) throws NoSuchProviderException, NoSuchAlgorithmException {

        byte[] data = new byte[keyServer.getPubKey().length + keyClientTemp.getPubKey().length];
        System.arraycopy(keyServer.getPubKey(), 0, data, 0, keyServer.getPubKey().length);
        System.arraycopy(keyClientTemp.getPubKey(), 0, data, keyServer.getPubKey().length, keyClientTemp.getPubKey().length);

        AuthenticationObject obj = new AuthenticationObject();
        obj.pubkeyServer = keyServer.getPubKey();
        obj.signature = CryptoTools.createSignature(keyServer, data);

        sentAuth = true;
        if (this.isAuth) {
            this.authFinished = true;
        }
        return obj;
    }

    public String getHost () {
        return host;
    }

    public void setHost (String host) {
        this.host = host;
    }

    public ChannelHandlerContext getNettyContext () {
        return nettyContext;
    }

    public void setNettyContext (ChannelHandlerContext nettyContext) {
        this.nettyContext = nettyContext;
    }

    public int getPort () {
        return port;
    }

    public void setPort (int port) {
        this.port = port;
    }

    public ECKey getPubKeyTempClient () {
        return pubKeyTempClient;
    }

    public void setPubKeyTempClient (ECKey pubKeyTempClient) {
        this.pubKeyTempClient = pubKeyTempClient;
    }

    public ECKey getPubKeyTempServer () {
        return pubKeyTempServer;
    }

    public void setPubKeyTempServer (ECKey pubKeyTempServer) {
        this.pubKeyTempServer = pubKeyTempServer;
    }

    public boolean hasSentAuth () {
        return sentAuth;
    }

    @Override
    public int hashCode () {
        return pubkey != null ? Arrays.hashCode(pubkey) : 0;
    }

    public boolean isAuth () {
        return isAuth;
    }

    public boolean isAuthFinished () {
        return authFinished;
    }

    public boolean isConnected () {
        return connected;
    }

    public void setConnected (boolean connected) {
        this.connected = connected;
    }

    public void newInventoryList (ArrayList<DataObject> objectList) {
        for (DataObject object : objectList) {
            byte[] hash = object.getObject().getHash();
            if (!Tools.arrayListContainsByteArray(this.inventoryList, hash)) {
                this.inventoryList.add(hash);
            }
        }

        if (this.inventoryList.size() > THRESHHOLD_INVENTORY_AMOUNT_TO_SEND) {
            InvObject invObject = new InvObject();
            invObject.inventoryList = this.inventoryList;
            this.nettyContext.writeAndFlush(new Message(invObject, Type.GOSSIP_INV));
            this.inventoryList.clear();
        }
    }

    public boolean processAuthentication (AuthenticationObject authentication, ECKey pubkeyClient, ECKey pubkeyServerTemp) throws NoSuchProviderException, NoSuchAlgorithmException {

        //TODO: Check whether the pubkeyClient is actually the pubkey we are expecting

        byte[] data = new byte[pubkeyClient.getPubKey().length + pubkeyServerTemp.getPubKey().length];
        System.arraycopy(pubkeyClient.getPubKey(), 0, data, 0, pubkeyClient.getPubKey().length);
        System.arraycopy(pubkeyServerTemp.getPubKey(), 0, data, pubkeyClient.getPubKey().length, pubkeyServerTemp.getPubKey().length);

        CryptoTools.verifySignature(pubkeyClient, data, authentication.signature);

        isAuth = true;
        if (sentAuth) {
            authFinished = true;
        }
        return true;
    }

    public void sendFailure (ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new Message(null, Type.FAILURE));
    }

    public void setOnConnectionCloseListener (OnConnectionCloseListener onConnectionCloseListener) {
        this.onConnectionCloseListener = onConnectionCloseListener;
    }

    public interface OnConnectionCloseListener {
        public void onClose ();
    }
}
