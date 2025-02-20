package net.nayrus.noteblockmaster.composer;

import libs.felnull.fnnbs.NBS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.nayrus.noteblockmaster.NoteBlockMaster;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SongFileManager {

    public static final List<UUID> registeredSongs = new ArrayList<>();
    public static Path SONG_DIR;
    public static Path CACHE_DIR;

    @Nullable
    public static NBS loadNBSFile(String name){
        Path filePath = SONG_DIR.resolve(name+ ".nbs");
        if (Files.exists(filePath)) {
            try {
                return NBS.load(Files.newInputStream(filePath));
            } catch (IOException e) {
                NoteBlockMaster.LOGGER.error(e.getLocalizedMessage());
            }
        } else {
            NoteBlockMaster.LOGGER.warn("File not found: {}", filePath.toAbsolutePath());
        }
        return null;
    }

    public static void validateAndLoadCache(){
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(CACHE_DIR)){
            List<UUID> not_validated = new ArrayList<>(List.copyOf(SongFileManager.registeredSongs));
            for(Path file : stream){
                String name = file.getFileName().toString();
                try{
                    UUID id = UUID.fromString(name.substring(0,36));
                    if(registeredSongs.contains(id)) not_validated.remove(id);
                    else{
                        registeredSongs.add(id);
                        not_validated.removeIf(uuid -> uuid.compareTo(id) == 0);
                    }
                }catch(IllegalArgumentException e){
                    NoteBlockMaster.LOGGER.warn("File {} is in the cache directory but is not a valid UUID", name);
                }
            }
            for(UUID id : not_validated){
                if(!SongCache.SERVER_CACHE.saveIfPresent(id)) registeredSongs.remove(id);
            }
        } catch (IOException e) {
            NoteBlockMaster.LOGGER.error("Could not validate the caching directory {}", e.getLocalizedMessage());
        }
    }

    public static void safeCachedSong(SongData song) throws IOException {
        Path filePath = CACHE_DIR.resolve(song.getID()+".snbt");
        if(!Files.exists(filePath)) NbtIo.write(song.save(new CompoundTag()), filePath);
    }

    public static @Nullable SongData loadCachedSong(UUID id){
        Path filePath = CACHE_DIR.resolve(id+".snbt");
        if (Files.exists(filePath)) {
            try{
                if(!(NbtIo.read(filePath) instanceof CompoundTag tag)) return null;
                return SongData.load(tag);
            } catch (IOException e) {
                NoteBlockMaster.LOGGER.warn("Could not open cached song file of {}", id);
                return null;
            }
        }else return null;
    }

    public static void deleteCachedSong(UUID id) throws IOException {
        Path filePath = CACHE_DIR.resolve(id+".snbt");
        if(Files.exists(filePath)) Files.delete(filePath);
    }


}
