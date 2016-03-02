/**
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.examples.streams;

import io.confluent.examples.streams.utils.GenericAvroDeserializer;
import io.confluent.examples.streams.utils.GenericAvroSerializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.KTable;

import java.util.Properties;

/**
 * From the changelog stream of user profile data to compute the number of users
 * with complete profiles per region for regions having at least 10M users with
 * complete profiles. "Completeness" is defined as being at least 200 characters total.
 *
 * NOTE: this program works with Java 8 with lambda expression only.
 */
public class UserRegionExample {

    public static void main(String[] args) throws Exception {
        Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.JOB_ID_CONFIG, "streams-user-region-example");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        streamsConfiguration.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, "localhost:2181");
        streamsConfiguration.put(StreamsConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        streamsConfiguration.put(StreamsConfig.VALUE_SERIALIZER_CLASS_CONFIG, GenericAvroSerializer.class);
        streamsConfiguration.put(StreamsConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        streamsConfiguration.put(StreamsConfig.VALUE_DESERIALIZER_CLASS_CONFIG, GenericAvroDeserializer.class);

        final Serializer<Long> longSerializer = new LongSerializer();
        final Deserializer<Long> longDeserializer = new LongDeserializer();

        KStreamBuilder builder = new KStreamBuilder();

        // read the source stream
        KTable<String, GenericRecord> profile = builder.table("UserProfile");

        // aggregate the user counts of by region
        KTable<String, Long> regionCount = profile
                // filter out incomplete profiles with less than 200 characters
                .filter((userId, record) -> ((String) record.get("experience")).getBytes().length > 200)
                // count by region, we can set null to all serdes to use defaults
                .count((userId, record) ->  new KeyValue<>((String) record.get("region"), record),
                    null, null, longSerializer, null, null, longDeserializer, "CountsByRegion")
                // filter out regions with less than 10M users
                .filter((regionName, count) -> count > 10 * 1000 * 1000);

        // write to the result topic, we need to override the value serializer to for type long
        regionCount.to("LargeCountsByRegion", null, longSerializer);

        KafkaStreams streams = new KafkaStreams(builder, streamsConfiguration);
        streams.start();
    }

}