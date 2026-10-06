package noname.fm.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.datatype.Artwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Created by dfeodot on 9/18/2017.
 */
@Service
public class MusicCollection {

    @Value("${archive.dir}")
    private String rootPath;

    @Value("${default.cover}")
    private File artF;

    private final List<TrackInfo> collection = new ArrayList();
    private final List<Artwork> artCollection = new ArrayList();
    private Artwork art;

    //funny enough, the @Value thing is called after the constructor
    //doing a post contruct to have all the things
    //JAVA is hard

    //this now scan the folder in the propertiest and build the list
    //pretty smart
    @PostConstruct
    public void readMusicCollection() throws IOException {

        art = Artwork.createArtworkFromFile( artF );
        File rootDir = new File( rootPath );

        readFiles(rootDir);

    }

    private void readFiles(File rootDir) {

        File[] fileList = rootDir.listFiles();
        for (File file : fileList) {
            if(file.isDirectory()){
                readFiles( file );
            }
            else {
                try {
                    MP3File mp3File = (MP3File) AudioFileIO.read( file );
                    Tag tag = mp3File.getTag();
                    //do you even check for null?!
                    TrackInfo track = null;
                    if (tag != null) {
                        track = new TrackInfo(
                                tag.getFirst( FieldKey.ARTIST ),
                                tag.getFirst( FieldKey.ALBUM ),
                                tag.getFirst( FieldKey.TITLE ),
                                tag.getFirst( FieldKey.YEAR ),
                                tag.getFirst( FieldKey.TRACK ),
                                tag.getFirstArtwork(),
                                file.getAbsolutePath()
                        );
                        artCollection.add( tag.getFirstArtwork() );
                    } else {
                        track = new TrackInfo( "Unknown Artist",
                                "Unknown Album",
                                "Unknown Track",
                                "1999",
                                "1",
                                art,
                                file.getAbsolutePath() );
                        artCollection.add( art );
                    }


                    collection.add( track );


                    //TODO: wish I know how to handle errors
                } catch (CannotReadException e) {
                    //most likely not an mp3 file
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TagException e) {
                    e.printStackTrace();
                } catch (ReadOnlyFileException e) {
                    e.printStackTrace();
                } catch (InvalidAudioFrameException e) {
                    e.printStackTrace();
                } catch (Exception e){
                    //wavs and shit
                    e.printStackTrace();
                }

            }
        }
    }

    public byte[] getArtAsBytes(int i) {
        return artCollection.get( i ).getBinaryData();
    }

    public TrackInfo getTrackInfo(int i) {
        return collection.get( i );
    }

    public List<TrackInfo> getCollection() {
        return collection;
    }
}
