package noname.fm.controller;

import java.io.File;

import noname.fm.utils.MusicCollection;
import noname.fm.utils.TrackInfo;
import noname.fm.utils.WaveformException;
import noname.fm.utils.WaveformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the overview waveform of a track as {"bins":[...]}.
 */
@RestController
public class WaveformController {

    private static final Logger LOG = LoggerFactory.getLogger(WaveformController.class);

    @Autowired
    MusicCollection mCollection;

    @Autowired
    WaveformService waveformService;

    @GetMapping("/waveform")
    public ResponseEntity<byte[]> waveform(@RequestParam("id") int id) {
        if (id < 0 || id >= mCollection.getCollection().size()) {
            return ResponseEntity.notFound().build();
        }
        TrackInfo track = mCollection.getTrackInfo(id);
        try {
            byte[] body = waveformService.getWaveformJson(new File(track.getPath()));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .cacheControl(CacheControl.noCache())
                    .body(body);
        } catch (WaveformException e) {
            LOG.warn("Waveform failed for {}: {}", track.getPath(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
