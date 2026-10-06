package noname.fm.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import noname.fm.utils.MusicCollection;
import noname.fm.utils.TrackInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * Created by dfeodot on 10/11/2017.
 */
@Controller
public class TrackInfoController {

    @Autowired
    MusicCollection mCollection;

    private int[] returnedTracks = null;

    @RequestMapping("/nextTrack")
    @ResponseBody
    public int getNextTrack(){
        int length= mCollection.getCollection().size();
        if(returnedTracks == null){
            returnedTracks = new int[length];
        }
        int index = 0;
        //TODO: this will loop forever and ever at some point
//        do{
            index = (int) (ThreadLocalRandom.current().nextDouble() * length);
//        }
//        while(returnedTracks[index] != 0);
//        returnedTracks[index] = 1;
        return index;
    }

    @RequestMapping(value = "/trackInfo", produces = "application/json")
    @ResponseBody
    public TrackInfoUI getTrackInfo(@RequestParam(value="id") int id){

        if (id < 0 || id >= mCollection.getCollection().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        TrackInfo track = mCollection.getTrackInfo( id );
        String cover = "/cover?id="+id;
        String playLink = "/listen?id="+id;
        String waveform = "/waveform?id="+id;
        return new TrackInfoUI(track.getAlbum(), track.getArtist(), playLink, cover, waveform, track.getTitle());
    }

    private class TrackInfoUI {

        private String album;
        private String artist;
        //this should be the link to call to play a track
        private String audioTrack;
        private String picture;
        private String waveform;
        private String title;

        public TrackInfoUI(String album, String artist, String audioTrack, String cover, String waveform, String title) {
            this.album = album;
            this.artist = artist;
            this.audioTrack = audioTrack;
            this.picture = cover;
            this.waveform = waveform;
            this.title = title;
        }

        public String getAlbum() {
            return album;
        }

        public String getArtist() {
            return artist;
        }

        public String getAudioTrack() {
            return audioTrack;
        }

        public String getPicture() {
            return picture;
        }

        public String getWaveform() {
            return waveform;
        }

        public String getTitle() {
            return title;
        }
    }
}
