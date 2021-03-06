/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search;

import org.apache.lucene.document.InetAddressPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.geo.GeoHashUtils;
import org.elasticsearch.common.io.stream.NamedWriteable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.common.joda.FormatDateTimeFormatter;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.index.mapper.ip.IpFieldMapper;
import org.elasticsearch.index.mapper.ip.LegacyIpFieldMapper;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;

/** A formatter for values as returned by the fielddata/doc-values APIs. */
public interface DocValueFormat extends NamedWriteable {

    String format(long value);

    String format(double value);

    String format(BytesRef value);

    long parseLong(String value, boolean roundUp, Callable<Long> now);

    double parseDouble(String value, boolean roundUp, Callable<Long> now);

    public static final DocValueFormat RAW = new DocValueFormat() {

        @Override
        public String getWriteableName() {
            return "raw";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }

        @Override
        public String format(long value) {
            return Long.toString(value);
        }

        @Override
        public String format(double value) {
            return Double.toString(value);
        }

        @Override
        public String format(BytesRef value) {
            return Term.toString(value);
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            double d = Double.parseDouble(value);
            if (roundUp) {
                d = Math.ceil(d);
            } else {
                d = Math.floor(d);
            }
            return Math.round(d);
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            return Double.parseDouble(value);
        }
    };

    public static final class DateTime implements DocValueFormat {

        public static final String NAME = "date_time";

        final FormatDateTimeFormatter formatter;
        final DateTimeZone timeZone;
        private final DateMathParser parser;

        public DateTime(FormatDateTimeFormatter formatter, DateTimeZone timeZone) {
            this.formatter = Objects.requireNonNull(formatter);
            this.timeZone = Objects.requireNonNull(timeZone);
            this.parser = new DateMathParser(formatter);
        }

        public DateTime(StreamInput in) throws IOException {
            this(Joda.forPattern(in.readString()), DateTimeZone.forID(in.readString()));
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(formatter.format());
            out.writeString(timeZone.getID());
        }

        @Override
        public String format(long value) {
            return formatter.printer().withZone(timeZone).print(value);
        }

        @Override
        public String format(double value) {
            return format((long) value);
        }

        @Override
        public String format(BytesRef value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            return parser.parse(value, now, roundUp, timeZone);
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            return parseLong(value, roundUp, now);
        }
    }

    public static final DocValueFormat GEOHASH = new DocValueFormat() {

        @Override
        public String getWriteableName() {
            return "geo_hash";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }

        @Override
        public String format(long value) {
            return GeoHashUtils.stringEncode(value);
        }

        @Override
        public String format(double value) {
            return format((long) value);
        }

        @Override
        public String format(BytesRef value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            throw new UnsupportedOperationException();
        }
    };

    public static final DocValueFormat BOOLEAN = new DocValueFormat() {

        @Override
        public String getWriteableName() {
            return "bool";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }

        @Override
        public String format(long value) {
            return java.lang.Boolean.valueOf(value != 0).toString();
        }

        @Override
        public String format(double value) {
            return java.lang.Boolean.valueOf(value != 0).toString();
        }

        @Override
        public String format(BytesRef value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            throw new UnsupportedOperationException();
        }
    };

    public static final DocValueFormat IP = new DocValueFormat() {

        @Override
        public String getWriteableName() {
            return "ip";
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }

        @Override
        public String format(long value) {
            return LegacyIpFieldMapper.longToIp(value);
        }

        @Override
        public String format(double value) {
            return format((long) value);
        }

        @Override
        public String format(BytesRef value) {
            byte[] bytes = Arrays.copyOfRange(value.bytes, value.offset, value.offset + value.length);
            InetAddress inet = InetAddressPoint.decode(bytes);
            return NetworkAddress.format(inet);
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            // TODO: throw exception in 6.0
            return LegacyIpFieldMapper.ipToLong(value);
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            return parseLong(value, roundUp, now);
        }
    };

    public static final class Decimal implements DocValueFormat {

        public static final String NAME = "decimal";
        private static final DecimalFormatSymbols SYMBOLS = new DecimalFormatSymbols(Locale.ROOT);

        final String pattern;
        private final NumberFormat format;

        public Decimal(String pattern) {
            this.pattern = pattern;
            this.format = new DecimalFormat(pattern, SYMBOLS);
        }

        public Decimal(StreamInput in) throws IOException {
            this(in.readString());
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(pattern);
        }

        @Override
        public String format(long value) {
            return format.format(value);
        }

        @Override
        public String format(double value) {
            return format.format(value);
        }

        @Override
        public String format(BytesRef value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long parseLong(String value, boolean roundUp, Callable<Long> now) {
            Number n;
            try {
                n = format.parse(value);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            if (format.isParseIntegerOnly()) {
                return n.longValue();
            } else {
                double d = n.doubleValue();
                if (roundUp) {
                    d = Math.ceil(d);
                } else {
                    d = Math.floor(d);
                }
                return Math.round(d);
            }
        }

        @Override
        public double parseDouble(String value, boolean roundUp, Callable<Long> now) {
            Number n;
            try {
                n = format.parse(value);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            return n.doubleValue();
        }

    }
}
