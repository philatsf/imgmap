package com.signalfx.imgmap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;

import javax.imageio.ImageIO;

import com.signalfx.endpoint.SignalFxEndpoint;
import com.signalfx.endpoint.SignalFxReceiverEndpoint;
import com.signalfx.metrics.auth.StaticAuthToken;
import com.signalfx.metrics.connection.HttpDataPointProtobufReceiverFactory;
import com.signalfx.metrics.errorhandler.OnSendErrorHandler;
import com.signalfx.metrics.flush.AggregateMetricSender;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.DataPoint;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.Datum;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.Dimension;
import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers.MetricType;


public class ToHeatMap {
    private final static String SFXURL = "https://mon-ingest.signalfx.com";
    private final static String SFXTOKEN = "PUT IN YOUR TOKEN";

    
    public static void main(String[] args) {
        File imageFile = getImageFile(args);
        try {
            sendImageAsTS(getImage(imageFile), getSender(), imageFile.getName());
        } catch (IOException e) {
            fatal("Error sending datapoints to SignalFx.");
            e.printStackTrace();
        }
    }

    private static BufferedImage getImage(File f) {
        try {
            return ImageIO.read(f);
        } catch (IOException e) {
            fatal("Failed to read image file " + f.getName());
            e.printStackTrace();
        }
        return null;
    }
    
    private static File getImageFile(String[] args) {
        if (args.length < 1) {
            fatal("Missing file name.");
        }
        
        File imgFile = new File(args[0]);
        if (!imgFile.exists()) {
            fatal("File " + imgFile.getAbsolutePath() + " does not exist.");
        }
        return imgFile;
    }
    
    private static AggregateMetricSender getSender() {
        URL hostUrl = null;
        try {
            hostUrl = new URL(SFXURL);
        } catch (MalformedURLException mue) {
            fatal("Bad SignalFx Ingest URL: " + SFXURL);
            mue.printStackTrace();
            return null;
        }
        
        SignalFxReceiverEndpoint endpoint = new SignalFxEndpoint(hostUrl.getProtocol(),
                                                                 hostUrl.getHost(),
                                                                 hostUrl.getPort());

        HttpDataPointProtobufReceiverFactory factory = 
                new HttpDataPointProtobufReceiverFactory(endpoint).setVersion(2);
        
        StaticAuthToken token = new StaticAuthToken(SFXTOKEN);
        
        return new AggregateMetricSender("PixelSender", factory, token,
                Collections.<OnSendErrorHandler>emptyList());
    }
    
    private static void fatal(String msg) {
        System.err.println("Error: " + msg);
        System.exit(1);
    }
    
    private static void sendImageAsTS(BufferedImage image, AggregateMetricSender sender, 
                                      String fileName) throws IOException {
        long time = System.currentTimeMillis();
        AggregateMetricSender.Session session = sender.createSession();
        Dimensions dimensions = new Dimensions(fileName, image.getWidth(), image.getHeight());
        try {
            for (int row=0, cnt=0; row < image.getHeight(); row++) {
                for (int col=0; col < image.getWidth(); col++, cnt++) {
                    Datum datum = SignalFxProtocolBuffers.Datum.newBuilder()
                                   .setIntValue(image.getRGB(col, row))
                                   .build();
                    
                    DataPoint dp = SignalFxProtocolBuffers.DataPoint.newBuilder()
                                    .setMetricType(MetricType.GAUGE)
                                    .setTimestamp(time)
                                    .setMetric("pixel")
                                    .setValue(datum)
                                    .addDimensions(dimensions.image)
                                    .addDimensions(dimensions.size)
                                    .addDimensions(dimensions.getPosition(cnt))
                                    .build();
                    session.setDatapoint(dp);
                }
            }
        } finally {
            session.close();
        }
    }

    final static class Dimensions {
        final static int [] sizeTable = { 9, 99, 999, 9999, 99999, 999999, 9999999,
                                          99999999, 999999999, Integer.MAX_VALUE };
        final static DateFormat dateFormat = new SimpleDateFormat("MMddyyyy:HH:mm:ss");
        Dimension image = null;
        Dimension size = null;
        private int maxDigits = 0;
        
        Dimensions(String image, int width, int height) {
            this.image = SignalFxProtocolBuffers.Dimension.newBuilder()
                          .setKey("image")
                          .setValue(image).build();
            this.size = SignalFxProtocolBuffers.Dimension.newBuilder()
                          .setKey("size")
                          .setValue("" + width + "x" + height).build();
            this.maxDigits = stringSize(width * height);
        }
        
        Dimension getPosition(int pos) {
            return SignalFxProtocolBuffers.Dimension.newBuilder()
                    .setKey("position")
                    .setValue(getAlpha(pos)).build();
        }

        static int stringSize(int x) {
            for (int i=0; ; i++) {
                if (x <= sizeTable[i]) {
                    return i+1;
                }
            }
        }
        
        private String getAlpha(int num) {
            int numDigits = stringSize(num);
            String digits = Integer.toString(num);
            for (int i=0; numDigits + i < maxDigits; i++) {
                digits = "0" + digits;
            }
            return digits;
        }
    }
    
}
