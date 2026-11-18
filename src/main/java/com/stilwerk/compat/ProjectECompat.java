package com.stilwerk.compat;

import java.lang.reflect.Method;
import java.math.BigInteger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional ProjectE integration without a compile-time dependency: survival costs may be paid from the
 * player's transmutation EMC for items the player has learned, and overflow refunds turn into EMC.
 */
public final class ProjectECompat {
    private ProjectECompat() {}

    private static final Logger LOG = LoggerFactory.getLogger("stilwerk/projecte");
    private static boolean tried;
    private static EntityCapability<Object, Void> knowledge;
    private static Method getEmc, setEmc, syncEmc, hasKnowledge, getValue;
    private static Object emcProxy;

    /** True when ProjectE is installed and its knowledge and EMC APIs were found. */
    @SuppressWarnings("unchecked")
    public static synchronized boolean available() {
        if (tried) return knowledge != null;
        tried = true;
        if (!ModList.get().isLoaded("projecte")) return false;
        try {
            Class<?> k = Class.forName("moze_intel.projecte.api.capabilities.IKnowledgeProvider");
            getEmc = k.getMethod("getEmc");
            setEmc = k.getMethod("setEmc", BigInteger.class);
            syncEmc = k.getMethod("syncEmc", ServerPlayer.class);
            hasKnowledge = k.getMethod("hasKnowledge", ItemStack.class);
            Class<?> proxy = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");
            emcProxy = proxy.getField("INSTANCE").get(null);
            getValue = proxy.getMethod("getValue", ItemLike.class);
            knowledge = (EntityCapability<Object, Void>) (EntityCapability<?, Void>) EntityCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("projecte", "knowledge"), k);
            LOG.info("ProjectE detected: survival costs can be paid with EMC");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("ProjectE present but its API could not be reached; EMC payment disabled", e);
            knowledge = null;
            return false;
        }
    }

    private static Object provider(ServerPlayer p) {
        return available() ? p.getCapability(knowledge) : null;
    }

    /** ProjectE's EMC value of an item, 0 without ProjectE or without a value. */
    public static long value(ItemLike item) {
        if (!available()) return 0;
        try {
            return (long) getValue.invoke(emcProxy, item);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** The player's transmutation EMC, ZERO without ProjectE. */
    public static BigInteger emc(ServerPlayer p) {
        Object provider = provider(p);
        if (provider == null) return BigInteger.ZERO;
        try {
            return (BigInteger) getEmc.invoke(provider);
        } catch (ReflectiveOperationException e) {
            return BigInteger.ZERO;
        }
    }

    public static boolean setEmc(ServerPlayer p, BigInteger value) {
        Object provider = provider(p);
        if (provider == null) return false;
        try {
            setEmc.invoke(provider, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** True when the player has learned the item in the transmutation table. */
    public static boolean knows(ServerPlayer p, Item item) {
        Object provider = provider(p);
        if (provider == null) return false;
        try {
            return (boolean) hasKnowledge.invoke(provider, new ItemStack(item));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** The player's EMC account, or null without ProjectE. */
    public static @org.jetbrains.annotations.Nullable com.stilwerk.core.EmcBank bank(ServerPlayer p) {
        if (!available()) return null;
        return new com.stilwerk.core.EmcBank() {
            @Override public BigInteger emc() { return ProjectECompat.emc(p); }
            @Override public void setEmc(BigInteger value) { ProjectECompat.setEmc(p, value); }
            @Override public boolean knows(Item item) { return ProjectECompat.knows(p, item); }
            @Override public long value(Item item) { return ProjectECompat.value(item); }
            @Override public void sync() { ProjectECompat.sync(p); }
        };
    }

    /** Sends the new EMC to the client (ProjectE's own packet). */
    public static void sync(ServerPlayer p) {
        Object provider = provider(p);
        if (provider == null || p.connection == null) return;
        try {
            syncEmc.invoke(provider, p);
        } catch (ReflectiveOperationException e) {
            LOG.debug("EMC sync to {} failed", p.getScoreboardName(), e);
        }
    }
}
