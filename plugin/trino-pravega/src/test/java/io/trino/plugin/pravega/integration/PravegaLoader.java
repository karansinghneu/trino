/*
 * Copyright (c) Pravega Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Note: This file contains some code from PrestoDb (majority of the "ResultsSession" implementation)
 * (rev a8968160e1840ac67a5f63def27d31c0ef0acde7)
 * https://github.com/prestodb/presto/blob/0.247/presto-kafka/src/test/java/com/facebook/presto/kafka/util/KafkaLoader.java
 */
package io.trino.plugin.pravega.integration;

import io.trino.Session;
import io.trino.client.Column;
import io.trino.client.QueryData;
import io.trino.client.QueryStatusInfo;
import io.trino.spi.type.TimeZoneKey;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;
import io.trino.spi.type.Varchars;
import io.trino.server.testing.TestingTrinoServer;
import io.trino.spi.TrinoWarning;
import io.trino.testing.AbstractTestingTrinoClient;
import io.trino.testing.ResultsSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.pravega.client.ClientConfig;
import io.pravega.client.EventStreamClientFactory;
import io.pravega.client.admin.StreamManager;
import io.pravega.client.stream.EventStreamWriter;
import io.pravega.client.stream.EventWriterConfig;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.Serializer;
import io.pravega.client.stream.StreamConfiguration;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TimeType.TIME;
import static io.trino.spi.type.TimeWithTimeZoneType.TIME_WITH_TIME_ZONE;
import static io.trino.spi.type.TimestampType.TIMESTAMP;
import static io.trino.spi.type.TimestampWithTimeZoneType.TIMESTAMP_WITH_TIME_ZONE;
//import static io.trino.type.DateTimeOperators.parseTimeLiteral;
import static io.trino.type.DateTimes.parseTimestampWithTimeZone;
//import static io.trino.type.DateTimes.parseTimestampWithoutTimeZone;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public class PravegaLoader
        extends AbstractTestingTrinoClient<Void>
{
    private static final DateTimeFormatter ISO8601_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static class MapSerializer
            implements Serializer<ImmutableMap<String, Object>>
    {
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public ByteBuffer serialize(ImmutableMap<String, Object> map)
        {
            try {
                String s = objectMapper.writeValueAsString(map);
                return ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8));
            }
            catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ImmutableMap<String, Object> deserialize(ByteBuffer byteBuffer)
        {
            return null; // not needed during ingestion
        }
    }

    private final EventStreamClientFactory factory;

    private final EventStreamWriter<ImmutableMap<String, Object>> writer;

    private final Random random = new Random();

    public PravegaLoader(URI controller,
                         StreamManager streamManager,
                         String schema,
                         String stream,
                         TestingTrinoServer prestoServer,
                         Session defaultSession)
    {
        super(prestoServer, defaultSession);

        if (!streamManager.checkScopeExists(schema)) {
            streamManager.createScope(schema);
        }

        StreamConfiguration dataStreamConfig = StreamConfiguration.builder()
                .scalingPolicy(ScalingPolicy.fixed(10))
                .build();
        streamManager.createStream(schema, stream, dataStreamConfig);

        this.factory = EventStreamClientFactory.withScope(
                schema, ClientConfig.builder().controllerURI(controller).build());

        this.writer = factory.createEventWriter(stream,
                new MapSerializer(),
                EventWriterConfig.builder().automaticallyNoteTime(true).build());
    }

    @Override
    public ResultsSession<Void> getResultSession(Session session)
    {
        requireNonNull(session, "session is null");
        return new PravegaLoadingSession(session);
    }

    private class PravegaLoadingSession
            implements ResultsSession<Void>
    {
        private final AtomicReference<List<Type>> types = new AtomicReference<>();

        private final TimeZoneKey timeZoneKey;

        private PravegaLoadingSession(Session session)
        {
            this.timeZoneKey = session.getTimeZoneKey();
        }

//        @Override
//        public void setWarnings(List<TrinoWarning> warnings) {}

        @Override
        public void addResults(QueryStatusInfo statusInfo, QueryData data)
        {
            if (types.get() == null && statusInfo.getColumns() != null) {
                types.set(getTypes(statusInfo.getColumns()));
            }

            if (data.getData() != null) {
                checkState(types.get() != null, "Data without types received!");
                List<Column> columns = statusInfo.getColumns();
                for (List<Object> fields : data.getData()) {
                    ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
                    for (int i = 0; i < fields.size(); i++) {
                        Type type = types.get().get(i);
                        Object value = convertValue(fields.get(i), type);
                        if (value != null) {
                            builder.put(columns.get(i).getName(), value);
                        }
                    }

                    writer.writeEvent(String.valueOf(random.nextInt(999)), builder.build());
                }
            }
        }

        @Override
        public Void build(Map<String, String> setSessionProperties, Set<String> resetSessionProperties)
        {
            return null;
        }

        private Object convertValue(Object value, Type type)
        {
            if (value == null) {
                return null;
            }

            if (BOOLEAN.equals(type) || isVarcharType(type)) {
                return value;
            }
            if (BIGINT.equals(type)) {
                return ((Number) value).longValue();
            }
            if (INTEGER.equals(type)) {
                return ((Number) value).intValue();
            }
            if (DOUBLE.equals(type)) {
                return ((Number) value).doubleValue();
            }
            if (DATE.equals(type)) {
                return value;
            }
//            if (TIME.equals(type)) {
//                return ISO8601_FORMATTER.format(Instant.ofEpochMilli(parseTimeLiteral(timeZoneKey, (String) value)));
//            }
//            if (TIMESTAMP.equals(type)) {
//                return ISO8601_FORMATTER.format(Instant.ofEpochMilli(parseTimestampWithoutTimeZone(timeZoneKey, (String) value)));
//            }
//            if (TIME_WITH_TIME_ZONE.equals(type) || TIMESTAMP_WITH_TIME_ZONE.equals(type)) {
//                return ISO8601_FORMATTER.format(Instant.ofEpochMilli(unpackMillisUtc(parseTimestampWithTimeZone(timeZoneKey, (String) value))));
//            }
            throw new AssertionError("unhandled type: " + type);
        }
    }

    private static boolean isVarcharType(Type type)
    {
        return type instanceof VarcharType;
    }

    @Override
    public void close()
    {
        try {
            writer.close();
            factory.close();
        }
        finally {
            super.close();
        }
    }
}
