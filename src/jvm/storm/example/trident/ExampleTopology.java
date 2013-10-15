package storm.example.trident;
/**
 * File: OAMTopology.java
**/

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.collect.Lists;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.generated.StormTopology;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import org.json.JSONObject;
import org.json.JSONException;
import storm.trident.TridentTopology;
import storm.trident.operation.BaseFunction;
import storm.trident.operation.TridentCollector;
import storm.trident.tuple.TridentTuple;
import storm.kafka.trident.TransactionalTridentKafkaSpout;
import storm.kafka.trident.TridentKafkaConfig;
import storm.kafka.HostPort;
//import storm.kafka.KafkaConfig.BrokerHosts;
import storm.kafka.SpoutConfig;
import storm.kafka.ZkHosts;

/**
 * Trident Topology for OAM. This topology consumes abuse instances from Kafka
 * and performs an RDNS lookup on the ones related to IP. It then writes the
 * results to a flat file that is processed by a perl script.
 *
 * We will probably do more with the topology in the future.
 *
**/
public class ExampleTopology {
    /**
     * Class constructor: Note this is a Utility class so it should not be
     * instantiated.
    **/
    protected ExampleTopology() {
        // prevents calls from subclass
        throw new UnsupportedOperationException();
    }

    /**
     * Parses JSON published by Kafka.
    **/
    public static class ParseJSON extends BaseFunction {

        /**
         * Constant: HTTP Code 200.
        **/
        private static final int HTTP_CODE_200 = 200;
        /**
         * Takes a tuple adds the RDNS and emits a new tuple.
         *
         * @param tuple an TridentTuple that contains fields in JSON format
         * @param collector the TridentCollector
        **/
        @Override
        public final void execute(
            final TridentTuple tuple,
            final TridentCollector collector
        ) {
            byte[] bytes = tuple.getBinary(0);
            try {
            	
            	System.out.println("running JSON");
                String decoded = new String(bytes);
                JSONObject json = new JSONObject(decoded);
                collector.emit(new Values(
                      json.getString("name")
                    , json.getInt("type")
                ));
            } catch (JSONException e) {
                System.err.println("Caught JSONException: " + e.getMessage());
            }
        }
    }
    /**
     * The WriteCSV class takes a TridentTuple and prints the fields to a flat
     * file.
    **/
    public static class WriteCSV extends BaseFunction {
        /** Constant: The file path where the file will be saved. **/
        public static final String FILE_PATH
            = "";
        /** Constant: The file name to save to. **/
        public static final String FILE_NAME = "example_out.csv";
        /** Constant: The number of fields in the csv doc. **/
        public static final int NUM_FIELDS = 2;
        /** Constant: The source where the instance came from. **/
        public static final int FIELD_NAME       = 0;
        /** Constant: The type of instance (custid or ip). **/
        public static final int FIELD_TYPE         = 1;

        @Override
        public final void execute(
            final TridentTuple tuple,
            final TridentCollector collector
        ) {
            try {
            	
            	System.out.println("running CVS");
                CSVWriter writer = new CSVWriter(
                      new FileWriter(FILE_NAME, true), ',');
                String[] fields = new String[NUM_FIELDS];
                fields[FIELD_NAME]
                    = tuple.getStringByField("name");
                fields[FIELD_TYPE]
                    = tuple.getIntegerByField("type").toString();

                writer.writeNext(fields);
                writer.close();
                collector.emit(
                    new Values(tuple.getStringByField("name")));
            } catch (IOException e) {
                System.err.println("Caught IOException: " + e.getMessage());
            }
        }
    }

    /**
     * Wrapper for the Topology.
     * The spout and bolts are iniated in this method.
     *
     * @return topology
    **/
    public static StormTopology buildTopology() {
        try {
            HostPort hostport = new HostPort(
                  getEnvVar("KAFKA_DOMAIN")
                , Integer.parseInt(getEnvVar("KAFKA_PORT")));

//            TridentKafkaConfig.StaticHosts hosts =
//                new TridentKafkaConfig.StaticHosts(
//                      Lists.newArrayList(hostport)
//                    , 1
//                );


//          TridentKafkaConfig.StaticHosts hosts =
//              new TridentKafkaConfig.StaticHosts(
//                    Lists.newArrayList(hostport)
//                  , 1
//              );
            
            String zookeepers = "localhost:2181";
            ZkHosts zhost = new ZkHosts(zookeepers,"/brokers");
            
            TridentKafkaConfig config = new TridentKafkaConfig(
                  zhost
                , getEnvVar("KAFKA_TOPIC")
            );
            
            config.forceStartOffsetTime(-2);
            
            TransactionalTridentKafkaSpout spout =
                new TransactionalTridentKafkaSpout(config);

            TridentTopology topology = new TridentTopology();
            topology.newStream("spout1", spout)
                .each(new Fields("bytes"), new ParseJSON(),
                    new Fields(
                        "name",
                        "type"
                ))
                .each(new Fields(
                    "name",
                    "type"),
                    new WriteCSV(),
                    new Fields("result_id")
                );

            return topology.build();
        } catch (IOException e) {
            System.err.println("Caught IOException: " + e.getMessage());
        }
        return null;
    }

    /**
     * Constructor for the OAMTopology.
     *
     * @param args any needed arguments
     * @throws Exception if an input is not correct.
    **/
    public static final void main(final String[] args) throws Exception {
        Config conf = new Config();
 //       conf.setDebug(true);
        conf.setMaxSpoutPending(1);
        
        System.out.println(getEnvVar("KAFKA_PORT"));
        
        LocalCluster cluster = new LocalCluster();
        cluster.submitTopology("ExampleTrident", conf, buildTopology());
    }

   /**
    * Check to make sure the environmental variable is set and return the
    * value.
    *
    * @param env variable to get the value of.
    * @return Environmental variable value.
    * @throws IOException if row length is incorrect
   **/
    private static String getEnvVar(final String env) throws IOException {
        String value = System.getenv(env);
        if (value == null) {
            throw new IOException(env + " environment variable is not set.");
        }

        return value;
    }

}

