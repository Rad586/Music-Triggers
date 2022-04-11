package mods.thecomputerizer.musictriggers;

import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.FolderPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class packFolder implements RepositorySource {
    private static final FileFilter RESOURCEPACK_FILTER = (p_10398_) -> {
        boolean flag = p_10398_.isFile() && p_10398_.getName().endsWith(".zip");
        boolean flag1 = p_10398_.isDirectory() && (new File(p_10398_, "pack.mcmeta")).isFile();
        return flag || flag1;
    };
    private final File folder;
    private final PackSource packSource;

    public packFolder(File p_10386_, PackSource p_10387_) {
        this.folder = p_10386_;
        this.packSource = p_10387_;
    }

    public void loadPacks(@NotNull Consumer<Pack> consumer, Pack.@NotNull PackConstructor constructor) {
        if (!this.folder.isDirectory()) {
            this.folder.mkdirs();
        }

        File[] afile = this.folder.listFiles(RESOURCEPACK_FILTER);
        if (afile != null) {
            for(File file1 : afile) {
                Pack pack = Pack.create("Music Triggers Songs", true, this.createSupplier(file1), constructor, Pack.Position.TOP, this.packSource);
                if (pack != null) {
                    consumer.accept(pack);
                }
            }

        }
    }

    private Supplier<PackResources> createSupplier(File p_10389_) {
        return p_10389_.isDirectory() ? () -> new FolderPackResources(p_10389_) : () -> new FilePackResources(p_10389_);
    }
}
