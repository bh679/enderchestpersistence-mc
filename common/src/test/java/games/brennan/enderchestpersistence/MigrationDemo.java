package games.brennan.enderchestpersistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/**
 * A runnable walk-through of the launcher-migration scenario, against real directories and real
 * gzipped-NBT stash files on disk. Run it with {@code ./gradlew :common:migrationDemo}.
 *
 * <p>This is the same production code the mod runs — {@link EcpConfig#readStoreMode},
 * {@link StoreLocation#init}, {@link EnderChestStore#file},
 * {@link EnderChestStore#seedFromInstanceIfNeeded} — driven over a throwaway directory tree instead
 * of a launcher instance, so the whole migration can be watched happening rather than asserted.
 * Nothing outside the working directory is touched.</p>
 *
 * <p>Lives in the test source set, so it never ships in the mod jar.</p>
 */
public final class MigrationDemo {

    private static final UUID PLAYER = UUID.fromString("7f3c1a90-0000-4000-8000-0000deadbeef");

    private MigrationDemo() {}

    public static void main(String[] args) throws IOException {
        Path work = Path.of(args.length > 0 ? args[0] : System.getProperty("java.io.tmpdir") + "/ecp-migration-demo");
        deleteTree(work);

        Path curseforge = work.resolve("CurseForge/instances/DungeonTrain/config");
        Path prism = work.resolve("PrismLauncher/instances/DungeonTrain/.minecraft/config");
        Path appData = work.resolve("Library/Application Support");
        Files.createDirectories(curseforge);
        Files.createDirectories(prism);
        Files.createDirectories(appData);

        banner("SETUP — a CurseForge instance on the old (pre-0.3.0) build");
        Path legacyStash = curseforge.resolve(StoreLocation.DIR_NAME).resolve(PLAYER + ".dat");
        Files.createDirectories(legacyStash.getParent());
        writeStash(legacyStash);
        System.out.println("  wrote a real gzipped-NBT stash, " + Files.size(legacyStash) + " bytes");
        System.out.println("    " + rel(work, legacyStash));
        System.out.println("  contents: " + describeStash(legacyStash));

        banner("STEP 1 — that same instance updates to 0.3.0 and starts");
        Files.writeString(curseforge.resolve(EcpConfig.FILE_NAME), EcpConfig.defaultFileContents(),
                StandardCharsets.UTF_8);
        StoreMode mode = EcpConfig.readStoreMode(curseforge);
        System.out.println("  config says store-location=" + mode.configValue());
        StoreLocation.reset();
        StoreLocation.init(mode, false, curseforge, appData);
        System.out.println("  store resolved to: " + rel(work, EnderChestStore.file(PLAYER)));
        boolean carried = EnderChestStore.seedFromInstanceIfNeeded(PLAYER);
        System.out.println("  carried across:    " + carried);
        report(work, "  after the update", EnderChestStore.file(PLAYER), legacyStash);

        banner("STEP 2 — THE REPORTED BUG: the player switches to Prism and restores only their worlds");
        System.out.println("  the new instance's config folder is empty:");
        System.out.println("    " + rel(work, prism) + " -> " + listing(prism));
        StoreLocation.reset();
        StoreLocation.init(EcpConfig.readStoreMode(prism), false, prism, appData);
        System.out.println("  store resolved to: " + rel(work, EnderChestStore.file(PLAYER)));
        boolean found = Files.isRegularFile(EnderChestStore.file(PLAYER));
        System.out.println("  chest found:       " + found);
        if (found) {
            System.out.println("  contents:          " + describeStash(EnderChestStore.file(PLAYER)));
        }

        banner("STEP 3 — what the OLD build would have done in step 2 (store-location=inside)");
        StoreLocation.reset();
        StoreLocation.init(StoreMode.INSIDE, false, prism, appData);
        System.out.println("  store resolved to: " + rel(work, EnderChestStore.file(PLAYER)));
        System.out.println("  chest found:       " + Files.isRegularFile(EnderChestStore.file(PLAYER))
                + "   <-- this is what the player hit");

        banner("STEP 4 — a dedicated server on the same machine stays in its own instance");
        StoreLocation.reset();
        StoreLocation.init(StoreMode.OUTSIDE, true, curseforge, appData);
        System.out.println("  store resolved to: " + rel(work, EnderChestStore.file(PLAYER)));

        banner("RESULT");
        System.out.println(found
                ? "  PASS — the chest survived the launcher migration."
                : "  FAIL — the chest did not survive the migration.");
        System.out.println("\n  working tree left at " + work + ":");
        tree(work);
        StoreLocation.reset();
        if (!found) {
            System.exit(1);
        }
    }

    /** A stash in the store's real on-disk shape: root compound of slot key -> list of item tags. */
    private static void writeStash(Path file) throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("survival", items("minecraft:diamond", 64, "minecraft:netherite_ingot", 3));
        root.put("survival|hard", items("minecraft:enchanted_golden_apple", 8, "minecraft:elytra", 1));
        NbtIo.writeCompressed(root, file);
    }

    private static ListTag items(String idA, int countA, String idB, int countB) {
        ListTag list = new ListTag();
        list.add(item(0, idA, countA));
        list.add(item(1, idB, countB));
        return list;
    }

    private static CompoundTag item(int slot, String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Slot", (byte) slot);
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    private static String describeStash(Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        StringBuilder out = new StringBuilder();
        for (String key : root.getAllKeys().stream().sorted().toList()) {
            ListTag list = root.getList(key, Tag.TAG_COMPOUND);
            if (!out.isEmpty()) out.append("; ");
            out.append(key).append("=[");
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                if (i > 0) out.append(", ");
                out.append(item.getInt("count")).append("x ").append(item.getString("id"));
            }
            out.append(']');
        }
        return out.toString();
    }

    private static void report(Path work, String label, Path store, Path original) throws IOException {
        System.out.println(label + ":");
        System.out.println("    machine store   " + rel(work, store) + "  -> "
                + (Files.isRegularFile(store) ? describeStash(store) : "MISSING"));
        System.out.println("    instance copy   " + rel(work, original) + "  -> "
                + (Files.isRegularFile(original) ? "still present (left in place deliberately)" : "GONE"));
    }

    private static String listing(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            var names = entries.map(p -> p.getFileName().toString()).sorted().toList();
            return names.isEmpty() ? "(empty)" : String.join(", ", names);
        }
    }

    private static String rel(Path work, Path path) {
        return work.relativize(path).toString();
    }

    private static void tree(Path work) throws IOException {
        try (var paths = Files.walk(work)) {
            paths.filter(Files::isRegularFile).sorted().forEach(p -> {
                try {
                    System.out.println("    " + rel(work, p) + "  (" + Files.size(p) + " bytes)");
                } catch (IOException e) {
                    System.out.println("    " + rel(work, p));
                }
            });
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
