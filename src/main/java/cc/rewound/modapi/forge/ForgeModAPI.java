package cc.rewound.modapi.forge;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import cc.rewound.modapi.RewoundModAPI;
import cc.rewound.modapi.RewoundModAPIImplementation;
import cc.rewound.modapi.packet.EventPacket;
import cc.rewound.modapi.packet.RewoundPacket;
import cc.rewound.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundGameFinishPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundGameRejoinPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundGameStartPacket;
import cc.rewound.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import cc.rewound.modapi.serializer.PacketSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = ForgeModAPI.MODID, version = ForgeModAPI.VERSION, clientSideOnly = true, name = "Rewound Mod API")
public class ForgeModAPI implements RewoundModAPIImplementation {
    public static final String MODID = "rewound_mod_api";
    public static final String VERSION = "${version}";
    private static final Logger LOGGER = LogManager.getLogger("RewoundModAPI");
    private static final boolean DEBUG_MODE = Boolean.getBoolean("cc.rewound.modapi.debug");

    // We store a local reference to the net handler, so it's instantly available from the moment we connect
    private NetHandlerPlayClient netHandler;
    private boolean onRewound;

    @Mod.EventHandler
    public void init(FMLPostInitializationEvent event) {
        RewoundModAPI.getInstance().setModImplementation(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        netHandler = (NetHandlerPlayClient) event.handler;
        event.manager.channel().pipeline().addBefore("packet_handler", "rewound_mod_api_packet_handler", RewoundPacketHandler.INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        netHandler = null;
        onRewound = false;
    }

    @Override
    public void onInit() {
        RewoundModAPI.getInstance().createHandler(ClientboundHelloPacket.class, packet -> onRewound = true);
        MinecraftForge.EVENT_BUS.register(this);

        if (DEBUG_MODE) {
            LOGGER.info("Debug mode is enabled!");
            registerDebug();
        }
    }

    @Override
    public boolean sendPacket(RewoundPacket packet) {
        if (netHandler == null) {
            return false;
        }

        if (!isConnectedToRewound()) {
            return false;
        }

        if (!netHandler.getNetworkManager().isChannelOpen()) {
            LOGGER.warn("Attempted to send packet while channel is closed!");
            netHandler = null;
            return false;
        }

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        PacketSerializer serializer = new PacketSerializer(buf);
        packet.write(serializer);
        netHandler.addToSendQueue(new C17PacketCustomPayload(packet.getIdentifier(), buf));
        return true;
    }

    @Override
    public boolean isConnectedToRewound() {
        return onRewound;
    }

    @ChannelHandler.Sharable
    private static class RewoundPacketHandler extends SimpleChannelInboundHandler<Packet<?>> {
        private static final RewoundPacketHandler INSTANCE = new RewoundPacketHandler();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Packet<?> msg) {
            if (!(msg instanceof S3FPacketCustomPayload)) {
                ctx.fireChannelRead(msg);
                return;
            }

            S3FPacketCustomPayload packet = (S3FPacketCustomPayload) msg;
            String identifier = packet.getChannelName();
            if (!RewoundModAPI.getInstance().getRegistry().isRegistered(identifier)) {
                ctx.fireChannelRead(msg);
                return;
            }

            PacketBuffer buffer = packet.getBufferData();
            buffer.retain();
            ctx.fireChannelRead(msg);
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    RewoundModAPI.getInstance().handle(identifier, new PacketSerializer(buffer));
                } catch (Exception e) {
                    LOGGER.warn("Failed to handle packet {}", identifier, e);
                } finally {
                    buffer.release();
                }
            });
        }
    }

    private static void registerDebug() {
        // Register events
        subscribeDebug(ClientboundLocationPacket.class);
        subscribeDebug(ClientboundGameStartPacket.class);
        subscribeDebug(ClientboundGameFinishPacket.class);
        subscribeDebug(ClientboundGameRejoinPacket.class);
    }

    private static <T extends EventPacket> void subscribeDebug(Class<T> event) {
        RewoundModAPI.getInstance().subscribeToEventPacket(event);
        RewoundModAPI.getInstance().createHandler(event, packet -> LOGGER.info("Received event {}", packet))
                .onError(error -> LOGGER.error("Received error response for event {}: {}", event.getSimpleName(), error));
    }
}
