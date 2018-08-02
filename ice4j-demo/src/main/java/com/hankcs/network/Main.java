package com.hankcs.network;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ice4j.Transport;
import org.ice4j.ice.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.List;

@SuppressWarnings("all")
public class Main {

    private static Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Throwable {

        String streamName = "stream-name";

        if (args.length < 1) {
            System.exit(-99);
        }

        int i = Integer.valueOf(args[0]);

        logger.trace("is controlling ? " + (i == 1));
        Agent agent = new Agent();
        agent.setTrickling(false);
        IceMediaStream stream = agent.createMediaStream(streamName);

        agent.createComponent(stream, Transport.UDP,
                10000, 10000, 20000);
        agent.setControlling(i == 1);
        agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);
        agent.addStateChangeListener(listener);
//        agent.setTa(10000);
        String sdp = SdpUtils.createSDPDescription(agent);
        logger.debug("local sdp\n" + sdp);

        logger.info("Paste remote SDP here. Enter an empty line to proceed:");
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));

        StringBuilder buff = new StringBuilder();
        read(reader, buff);
        String remoteSdp = buff.toString();
        SdpUtils.parseSDP(agent, remoteSdp);

        logger.warn("remote user name: " + agent.generateRemoteUserName("stream-name"));
        logger.warn(agent.getStream("stream-name").getRemoteUfrag());

//        System.exit(-9);

        logger.debug("startConnectivityEstablishment....");

        agent.startConnectivityEstablishment();

        synchronized (listener) {
            listener.wait();
        }

        final DatagramSocket socket = getDatagramSocket(agent, streamName);
        final SocketAddress remoteAddress = getRemotePeerSocketAddress(agent, streamName);
        System.out.println(socket.toString());
        new Thread(() -> {
            while (true) {
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf,
                            buf.length);
                    socket.receive(packet);
                    System.out.println(packet.getAddress() + ":" + packet.getPort() + " says: "
                            + new String(packet.getData(), 0, packet.getLength()));

                    if (i != 1) {
                        DatagramPacket packet2 = new DatagramPacket(buf, buf.length);
                        packet2.setSocketAddress(remoteAddress);
                        socket.send(packet2);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        if (i == 1) {
            new Thread(() -> {
                try {
                    BufferedReader reader2 = new BufferedReader(new InputStreamReader(System.in));
                    String line;
                    // 从键盘读取
                    while ((line = reader2.readLine()) != null) {
                        line = line.trim();
                        if (line.length() == 0) {
                            break;
                        }
                        byte[] buf = (line).getBytes();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        packet.setSocketAddress(remoteAddress);
                        socket.send(packet);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }).start();
        }
    }

    public static DatagramSocket getDatagramSocket(Agent agent, String streamName) throws Throwable {
        LocalCandidate localCandidate = agent
                .getSelectedLocalCandidate(streamName);
        LocalCandidate candidate = localCandidate;
        return candidate.getDatagramSocket();
    }

    private static SocketAddress getRemotePeerSocketAddress(Agent agent, String streamName) {
        RemoteCandidate remoteCandidate = agent
                .getSelectedRemoteCandidate(streamName);
        return remoteCandidate.getTransportAddress();
    }


    private static final PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            Object state = evt.getNewValue();

            logger.info("Agent entered the " + state + " state.");
            if (state == IceProcessingState.COMPLETED) {
                Agent agent = (Agent) evt.getSource();
                List<IceMediaStream> streams = agent.getStreams();

                for (IceMediaStream stream : streams) {
                    logger.info("Stream name: " + stream.getName());
                    List<Component> components = stream.getComponents();
                    for (Component c : components) {
                        logger.info("Component of stream:" + c.getName()
                                + ",selected of pair:" + c.getSelectedPair());
                    }
                }

                logger.info("Printing the completed check lists:");
                for (IceMediaStream stream : streams) {
                    logger.info("Check list for  stream: " + stream.getName());
                    logger.info("nominated check list:" + stream.getCheckList());
                }
                synchronized (this) {
                    this.notifyAll();
                }
            } else if (state == IceProcessingState.TERMINATED) {
                logger.info("ice processing TERMINATED...");
            } else if (state == IceProcessingState.FAILED) {
                logger.info("ice processing FAILED...");
                ((Agent) evt.getSource()).free();
                System.exit(-1);
            }
        }
    };


    public static void read(BufferedReader reader, StringBuilder buff) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                break;
            }
            buff.append(line);
            buff.append("\r\n");
        }
    }
}
/*
 *
 *
v=0
o=ice4j.org 0 0 IN null null
s=-
t=0 0
a=ice-options:trickle
a=ice-ufrag:b0slc1cjtg0psg
a=ice-pwd:178p64slnp35j8iqi1n5nmutvf
m=stream-name 10000 RTP/AVP 0
c=IN 118.178.236.183 IP4
a=mid:stream-name
a=candidate:1 1 udp 2130706431 118.178.236.183 10000 typ host
 *
 */