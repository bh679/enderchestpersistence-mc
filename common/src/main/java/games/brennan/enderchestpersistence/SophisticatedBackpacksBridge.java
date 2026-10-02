package games.brennan.enderchestpersistence;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * {@link BackpackContents.Storage} over Sophisticated Backpacks' world-level {@code BackpackStorage},
 * reached by reflection so this mod neither depends on it nor breaks without it — and so the same
 * jar works on Fabric, where it does not exist.
 *
 * <p>Reads and writes go through the private {@code backpackContents} map when it is reachable, since
 * its semantics are plain: absent means "this world has never seen the backpack". The public
 * {@code getOrCreateBackpackContents} / {@code setBackpackContents} are the fallback. Any failure
 * logs one WARN and disables the bridge until the game restarts; the Ender Chest itself is unaffected.</p>
 *
 * <p>Server thread only — {@code BackpackStorage.get()} answers with the client copy anywhere else.</p>
 */
public final class SophisticatedBackpacksBridge implements BackpackContents.Storage {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String STORAGE_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage";

    private static volatile BackpackContents.Storage instance;

    private final Method storageGetter;
    private final Field contents;
    private final Method getOrCreate;
    private final Method set;
    private volatile boolean broken;

    private SophisticatedBackpacksBridge(Method get, Field contents, Method getOrCreate, Method set) {
        this.storageGetter = get;
        this.contents = contents;
        this.getOrCreate = getOrCreate;
        this.set = set;
    }

    /** The bridge if Sophisticated Backpacks is loaded, else {@link BackpackContents#NONE}. */
    public static BackpackContents.Storage get() {
        BackpackContents.Storage s = instance;
        if (s == null) {
            s = resolve();
            instance = s;
        }
        return s;
    }

    private static BackpackContents.Storage resolve() {
        Class<?> cls;
        try {
            cls = Class.forName(STORAGE_CLASS, false, SophisticatedBackpacksBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return BackpackContents.NONE;
        }
        try {
            Method get = cls.getMethod("get");
            Field contents = null;
            try {
                Field f = cls.getDeclaredField("backpackContents");
                f.setAccessible(true);
                if (Map.class.isAssignableFrom(f.getType())) contents = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall back to the public methods
            }
            Method getOrCreate = method(cls, "getOrCreateBackpackContents", UUID.class);
            Method set = method(cls, "setBackpackContents", UUID.class, CompoundTag.class);
            if (contents == null && (getOrCreate == null || set == null)) {
                LOGGER.warn("[EnderChestPersistence] Sophisticated Backpacks is loaded but its storage API was"
                        + " not recognised — backpack contents in the Ender Chest will not carry across worlds.");
                return BackpackContents.NONE;
            }
            LOGGER.info("[EnderChestPersistence] Sophisticated Backpacks detected — backpack contents in the"
                    + " Ender Chest will be kept with it.");
            return new SophisticatedBackpacksBridge(get, contents, getOrCreate, set);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("[EnderChestPersistence] could not hook Sophisticated Backpacks storage ({}) — backpack"
                    + " contents in the Ender Chest will not carry across worlds.", e.toString());
            return BackpackContents.NONE;
        }
    }

    private static Method method(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public CompoundTag get(UUID id) {
        if (broken) return null;
        try {
            Object storage = storageGetter.invoke(null);
            if (storage == null) return null;
            if (contents != null) {
                Object tag = ((Map<?, ?>) contents.get(storage)).get(id);
                return tag instanceof CompoundTag c ? c : null;
            }
            Object tag = getOrCreate.invoke(storage, id);
            return tag instanceof CompoundTag c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void put(UUID id, CompoundTag tag) {
        if (broken) return;
        try {
            Object storage = storageGetter.invoke(null);
            if (storage == null) return;
            if (contents != null) {
                ((Map<UUID, CompoundTag>) contents.get(storage)).put(id, tag);
            } else {
                set.invoke(storage, id, tag);
            }
            if (storage instanceof SavedData data) data.setDirty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private void fail(Throwable e) {
        broken = true;
        LOGGER.warn("[EnderChestPersistence] Sophisticated Backpacks storage access failed ({}) — backpack"
                + " contents will not be carried until the game restarts.", e.toString());
    }
}
