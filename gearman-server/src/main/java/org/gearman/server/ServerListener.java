package org.gearman.server;

import org.gearman.server.codec.Decoder;
import org.gearman.server.codec.Encoder;
import org.gearman.server.persistence.RedisQueue;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.concurrent.Executors;

/**
 * Created with IntelliJ IDEA.
 * User: jewart
 * Date: 12/1/12
 * Time: 12:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class ServerListener {
    private final int port;
    private final Logger LOG = LoggerFactory.getLogger(ServerListener.class);
    private final JobStore jobStore;
    private final String hostName;
    private final DefaultChannelGroup channelGroup;
    private final ServerChannelFactory serverFactory;

    public ServerListener(int port)
    {
        this.channelGroup = new DefaultChannelGroup(this + "-channelGroup");
        this.serverFactory =  new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
                                                                Executors.newCachedThreadPool());
        this.port = port;
        this.jobStore = new JobStore(new RedisQueue());

        if(jobStore != null)
        {
            jobStore.loadAllJobs();
        }

        String host;

        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "localhost";
        }

        this.hostName = host;
    }


    public boolean start()
    {
        ServerBootstrap bootstrap = new ServerBootstrap(serverFactory);

        // Set up the pipeline factory.
        ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("encoder", Encoder.getInstance());
                pipeline.addLast("decoder", new Decoder());
                pipeline.addLast("handler", new PacketHandler(jobStore, hostName, channelGroup));
                return pipeline;
            }
        };

        // Bind and start to accept incoming connections.
        bootstrap.setOption("reuseAddress", true);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        bootstrap.setPipelineFactory(pipelineFactory);

        Channel channel = bootstrap.bind(new InetSocketAddress(this.port));
        if (!channel.isBound()) {
            this.stop();
            return false;
        }

        this.channelGroup.add(channel);
        return true;

    }

    public void stop() {
        if (this.channelGroup != null) {
            this.channelGroup.close();
        }
        if (this.serverFactory != null) {
            this.serverFactory.releaseExternalResources();
        }
    }

}