package org.leavesmc.leaves.protocol.servux;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServuxProtocol {

    public static final String PROTOCOL_ID = "servux";
    public static final Logger LOGGER = LoggerFactory.getLogger(PROTOCOL_ID.toUpperCase());
    /**
     * MiniHUD / Litematica / Tweakeroo 26.2 require this prefix:
     * {@code servux-fabric-<minecraftVersionId>}.
     */
    public static final String SERVUX_STRING = "servux-fabric-" + SharedConstants.getCurrentVersion().id() + "-leaves";

    @Contract("_ -> new")
    public static Identifier id(String path) {
        return Identifier.tryBuild(PROTOCOL_ID, path);
    }

    public static void skipRemaining(@NotNull FriendlyByteBuf buf) {
        if (buf.isReadable()) {
            buf.skipBytes(buf.readableBytes());
        }
    }

    public static @NotNull CompoundTag readNbtOrEmpty(FriendlyByteBuf buf) {
        if (!buf.isReadable()) {
            return new CompoundTag();
        }
        int index = buf.readerIndex();
        try {
            CompoundTag nbt = buf.readNbt();
            return nbt != null ? nbt : new CompoundTag();
        } catch (Exception e) {
            buf.readerIndex(index);
            skipRemaining(buf);
            return new CompoundTag();
        }
    }
}
