package network.thunder.core.communication.layer.middle.peerseed;

import network.thunder.core.communication.ClientObject;
import network.thunder.core.communication.layer.Message;
import network.thunder.core.communication.layer.middle.peerseed.messages.PeerSeedGetMessage;
import network.thunder.core.communication.layer.middle.peerseed.messages.PeerSeedMessageFactory;
import network.thunder.core.communication.layer.middle.peerseed.messages.PeerSeedSendMessage;
import network.thunder.core.communication.layer.MessageExecutor;
import network.thunder.core.communication.layer.middle.broadcasting.types.P2PDataObject;
import network.thunder.core.communication.layer.middle.broadcasting.types.PubkeyIPObject;
import network.thunder.core.helper.callback.results.PeerSeedResult;
import network.thunder.core.communication.layer.ContextFactory;
import network.thunder.core.communication.processor.ConnectionIntent;
import network.thunder.core.helper.events.LNEventHelper;
import network.thunder.core.communication.layer.middle.peerseed.messages.PeerSeedMessage;
import network.thunder.core.database.DBHandler;
import network.thunder.core.etc.Tools;
import network.thunder.core.communication.ServerObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by matsjerratsch on 22/01/2016.
 */
public class PeerSeedProcessorImpl extends PeerSeedProcessor {

    DBHandler dbHandler;
    ClientObject node;
    ServerObject serverObject;

    PeerSeedMessageFactory messageFactory;
    LNEventHelper eventHelper;

    MessageExecutor messageExecutor;

    public PeerSeedProcessorImpl (ContextFactory contextFactory, DBHandler dbHandler, ClientObject node) {
        this.messageFactory = contextFactory.getPeerSeedMessageFactory();
        this.dbHandler = dbHandler;
        this.eventHelper = contextFactory.getEventHelper();
        this.node = node;
        this.serverObject = contextFactory.getServerSettings();
    }

    @Override
    public void onInboundMessage (Message message) {
        consumeMessage((PeerSeedMessage) message);
    }

    @Override
    public boolean consumesInboundMessage (Object object) {
        return (object instanceof PeerSeedMessage);
    }

    @Override
    public boolean consumesOutboundMessage (Object object) {
        return false;
    }

    @Override
    public void onLayerActive (MessageExecutor messageExecutor) {
        this.messageExecutor = messageExecutor;
        if (node.isServer) {
            messageExecutor.sendNextLayerActive();
        } else {
            if (node.intent == ConnectionIntent.GET_IPS) {
                messageExecutor.sendMessageUpwards(messageFactory.getPeerSeedGetMessage());
            } else {
                messageExecutor.sendNextLayerActive();
            }
        }
    }

    private void consumeMessage (PeerSeedMessage message) {
        if (message instanceof PeerSeedGetMessage) {

            List<PubkeyIPObject> ipObjects = dbHandler.getIPObjects();

            if (ipObjects.size() > PEERS_TO_SEND) {
                ipObjects = Tools.getRandomSubList(ipObjects, PEERS_TO_SEND);
            }

            Message response = messageFactory.getPeerSeedSendMessage(ipObjects);
            messageExecutor.sendMessageUpwards(response);
        } else if (message instanceof PeerSeedSendMessage) {
            PeerSeedSendMessage sendMessage = (PeerSeedSendMessage) message;
            List<PubkeyIPObject> list = removeOurIPFromList(sendMessage.ipObjectList);
            dbHandler.insertIPObjects(P2PDataObject.generaliseList(list));
            fireIPEvents(list);

            //TODO We might always want to close here, given that we only ever get here if intent = GET_IPS
            if (!node.isServer && node.intent == ConnectionIntent.GET_IPS) {
                node.resultCallback.execute(new PeerSeedResult());
                messageExecutor.closeConnection();
            }
        }
    }

    private void fireIPEvents (List<PubkeyIPObject> list) {
        for (PubkeyIPObject ip : list) {
            eventHelper.onReceivedIP(ip);
        }
    }

    private List<PubkeyIPObject> removeOurIPFromList (List<PubkeyIPObject> list) {
        List<PubkeyIPObject> toRemove = new ArrayList<>();
        for (PubkeyIPObject ip : list) {
            if (Arrays.equals(ip.pubkey, serverObject.pubKeyServer.getPubKey())) {
                toRemove.add(ip);
            }
        }
        list.removeAll(toRemove);
        return list;
    }
}