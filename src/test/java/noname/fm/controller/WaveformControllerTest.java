package noname.fm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.List;

import noname.fm.utils.MusicCollection;
import noname.fm.utils.TrackInfo;
import noname.fm.utils.WaveformException;
import noname.fm.utils.WaveformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WaveformController.class)
class WaveformControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MusicCollection collection;

    @MockitoBean
    WaveformService waveformService;

    @BeforeEach
    void setUp() {
        TrackInfo track = Mockito.mock(TrackInfo.class);
        when(track.getPath()).thenReturn("track.mp3");
        when(collection.getCollection()).thenReturn(List.of(track, track));
        when(collection.getTrackInfo(1)).thenReturn(track);
    }

    @Test
    void negativeIdIs404() throws Exception {
        mvc.perform(get("/waveform").param("id", "-1")).andExpect(status().isNotFound());
    }

    @Test
    void idEqualToSizeIs404() throws Exception {
        mvc.perform(get("/waveform").param("id", "2")).andExpect(status().isNotFound());
    }

    @Test
    void validIdReturnsJsonBody() throws Exception {
        byte[] body = "{\"bins\":[1,2,3]}".getBytes();
        when(waveformService.getWaveformJson(any(File.class))).thenReturn(body);
        mvc.perform(get("/waveform").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().bytes(body));
    }

    @Test
    void serviceFailureIs500() throws Exception {
        when(waveformService.getWaveformJson(any(File.class))).thenThrow(new WaveformException("bad"));
        mvc.perform(get("/waveform").param("id", "1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().bytes(new byte[0]));
    }
}
