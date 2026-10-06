package noname.fm.utils;

/**
 * Thrown when a waveform cannot be computed for a track.
 */
public class WaveformException extends RuntimeException {

    public WaveformException(String message) {
        super(message);
    }

    public WaveformException(String message, Throwable cause) {
        super(message, cause);
    }
}
