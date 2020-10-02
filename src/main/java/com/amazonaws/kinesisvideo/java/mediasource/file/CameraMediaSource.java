package com.amazonaws.kinesisvideo.java.mediasource.file;

import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.internal.mediasource.OnStreamDataAvailable;
import com.amazonaws.kinesisvideo.java.logging.SysOutLogChannel;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFileMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.java.mediasource.file.ImageFrameSource;

import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_AN_HOUR;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_MILLISECOND;
import static com.amazonaws.kinesisvideo.producer.Time.HUNDREDS_OF_NANOS_IN_A_SECOND;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.processing.face.detection.DetectedFace;
import org.openimaj.image.processing.face.detection.HaarCascadeDetector;
import org.openimaj.math.geometry.shape.Rectangle;

public class CameraMediaSource implements MediaSource {
    private  String streamName = "";

    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long HUNDREDS_OF_NANOS_IN_MS = 10 * 1000;
    private static final long FRAME_DURATION_20_MS = 20L;
    private static final boolean NOT_ADAPTIVE = false;
    private static final boolean KEYFRAME_FRAGMENTATION = true;
    private static final boolean SDK_GENERATES_TIMECODES = false;
    private static final boolean RELATIVE_FRAGMENT_TIMECODES = false;
    private static final String NO_KMS_KEY_ID = null;
    private static final int VERSION_ZERO = 0;
    private static final long MAX_LATENCY_ZERO = 0L;
    private static final long NO_RETENTION = 0L;
    private static final boolean REQUEST_FRAGMENT_ACKS = true;
    private static final boolean RECOVER_ON_FAILURE = true;
    private static final long DEFAULT_GOP_DURATION = 2000L * HUNDREDS_OF_NANOS_IN_A_SECOND;
    private static final int DEFAULT_BITRATE = 2000000;
    private static final int DEFAULT_TIMESCALE = 10000;
    private static final int FRAMERATE_30 = 30;
    private static final int FRAME_RATE_25 = 25;
    private static final boolean USE_FRAME_TIMECODES = true;
    private static final boolean ABSOLUTE_TIMECODES = true;
    private static final boolean RELATIVE_TIMECODES = false;
    private static final boolean RECALCULATE_METRICS = true;
    /**
     * Default buffer duration for a stream
     */
    public static final long DEFAULT_BUFFER_DURATION_IN_SECONDS = 40;

    /**
     * Default replay duration for a stream
     */
    public static final long DEFAULT_REPLAY_DURATION_IN_SECONDS = 20;

    /**
     * Default connection staleness detection duration.
     */
    public static final long DEFAULT_STALENESS_DURATION_IN_SECONDS = 20;
    
	private CameraMediaSourceConfiguration cameraMediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private CameraFrameSource cameraFrameSource;
    private int frameIndex;
    Webcam webcam = null;
    
    public CameraMediaSource( String streamName) {
      this.streamName = streamName;
    }
    
    public void setupWebCam(Webcam webcam) {
    	this.webcam = webcam;
    	Dimension size = WebcamResolution.VGA.getSize();
        webcam.setViewSize(size);
        webcam.open(true);
    }

	@Override
	public MediaSourceState getMediaSourceState() {
		// TODO Auto-generated method stub
		return mediaSourceState;
	}

	@Override
	public MediaSourceConfiguration getConfiguration() {
		// TODO Auto-generated method stub
		return cameraMediaSourceConfiguration;
	}

	@Override
	public void initialize(MediaSourceSink mediaSourceSink) throws KinesisVideoException {
		// TODO Auto-generated method stub
		this.mediaSourceSink = mediaSourceSink;
	}

	@Override
	public void configure(MediaSourceConfiguration configuration) {
		// TODO Auto-generated method stub
		
		if (!(configuration instanceof CameraMediaSourceConfiguration)) {
            throw new IllegalStateException("Configuration must be an instance of OpenCvMediaSourceConfiguration");
        }
        this.cameraMediaSourceConfiguration = (CameraMediaSourceConfiguration) configuration;
        this.frameIndex = 0;
		
	}

	@Override
	public void start() throws KinesisVideoException {
		// TODO Auto-generated method stub
		mediaSourceState = MediaSourceState.RUNNING;
		cameraFrameSource = new CameraFrameSource(cameraMediaSourceConfiguration, webcam);
		cameraFrameSource.onBytesAvailable(createKinesisVideoFrameAndPushToProducer());
		cameraFrameSource.start();
		
	}

	private OnStreamDataAvailable createKinesisVideoFrameAndPushToProducer() {
		// TODO Auto-generated method stub
        return new OnStreamDataAvailable() {
            @Override
            public void onFrameDataAvailable(final ByteBuffer data) {
                final long currentTimeMs = System.currentTimeMillis();

                final int flags = FRAME_FLAG_KEY_FRAME;

                if (data != null) {
                    final KinesisVideoFrame frame = new KinesisVideoFrame(
                            frameIndex++,
                            flags,
                            currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                            currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                            FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_MS,
                            data);

                    if (frame.getSize() == 0) {
                        return;
                    }

                    putFrame(frame);
                    
                } else {
                	System.out.println("Data not received from frame");
                }

            }
        };
	}
	
    private void putFrame(final KinesisVideoFrame kinesisVideoFrame) {
        try {
            mediaSourceSink.onFrame(kinesisVideoFrame);
        } catch (final KinesisVideoException ex) {
            throw new RuntimeException(ex);
        }
    }

    
    private boolean isKeyFrame() {
        return frameIndex % 22 == 0;
    }
    
	@Override
	public void stop() throws KinesisVideoException {
		// TODO Auto-generated method stub
        if (cameraFrameSource != null) {
        	cameraFrameSource.stop();
        }

        mediaSourceState = MediaSourceState.STOPPED;
        webcam.close();
		
	}

	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void free() throws KinesisVideoException {
		// TODO Auto-generated method stub
		
	}

  @Override
  public StreamInfo getStreamInfo() throws KinesisVideoException {
    // Need to fix-up the content type as the Console playback only accepts video/h264 and will fail
    // if the mime type is video/avc which is the default in Android.
    String contentType = cameraMediaSourceConfiguration.getEncoderMimeType();
    if (contentType.equals("video/avc")) {
      contentType = "video/h264";
    }

    return new StreamInfo(VERSION_ZERO, streamName,
        StreamInfo.StreamingType.STREAMING_TYPE_REALTIME, contentType, NO_KMS_KEY_ID,
        cameraMediaSourceConfiguration.getRetentionPeriodInHours() * HUNDREDS_OF_NANOS_IN_AN_HOUR, NOT_ADAPTIVE,
        MAX_LATENCY_ZERO, DEFAULT_GOP_DURATION * HUNDREDS_OF_NANOS_IN_A_MILLISECOND,
        KEYFRAME_FRAGMENTATION, SDK_GENERATES_TIMECODES, cameraMediaSourceConfiguration.getIsAbsoluteTimecode(),
        REQUEST_FRAGMENT_ACKS, RECOVER_ON_FAILURE,
        StreamInfo.codecIdFromContentType(cameraMediaSourceConfiguration.getEncoderMimeType()),
        StreamInfo.createTrackName(cameraMediaSourceConfiguration.getEncoderMimeType()), cameraMediaSourceConfiguration.getBitRate(),
        cameraMediaSourceConfiguration.getFrameRate(),
        DEFAULT_BUFFER_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
        DEFAULT_REPLAY_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
        DEFAULT_STALENESS_DURATION_IN_SECONDS * HUNDREDS_OF_NANOS_IN_A_SECOND,
        cameraMediaSourceConfiguration.getTimeScale() / 100, RECALCULATE_METRICS,
        cameraMediaSourceConfiguration.getCodecPrivateData(), getTags(), cameraMediaSourceConfiguration.getNalAdaptationFlags());
  }
  
  private static Tag[] getTags() {
    final List<Tag> tagList = new ArrayList<Tag>();
    tagList.add(new Tag("device", "Test Device"));
    tagList.add(new Tag("stream", "Test Stream"));
    return tagList.toArray(new Tag[0]);
}

  @Override
  public MediaSourceSink getMediaSourceSink() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public StreamCallbacks getStreamCallbacks() {
    // TODO Auto-generated method stub
    return null;
  }

}
