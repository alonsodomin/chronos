/*
 * Copyright 2016 Antonio Alonso Dominguez
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
 */

package io.quckoo.serialization.kryo;

import static com.esotericsoftware.kryo.util.Util.*;

import java.time.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/** Serializers for org.threeten.bp.*, are added as default serializers.
 *
 * Serializers are all private for now because they're not expected to be somehow used/extended/accessed by the user. If there
 * should be a case where this is needed it can be changed - for now the public api should be kept as spall as possible.
 *
 * Implementation note: All serialization is inspired by oracles org.threeten.bp.Ser. */
public final class TimeSerializers {

    public static void addDefaultSerializers (Kryo kryo) {
        if (isClassAvailable("org.threeten.bp.Duration")) kryo.addDefaultSerializer(Duration.class, new DurationSerializer());
        if (isClassAvailable("org.threeten.bp.Instant")) kryo.addDefaultSerializer(Instant.class, new InstantSerializer());
        if (isClassAvailable("org.threeten.bp.LocalDate")) kryo.addDefaultSerializer(LocalDate.class, new LocalDateSerializer());
        if (isClassAvailable("org.threeten.bp.LocalTime")) kryo.addDefaultSerializer(LocalTime.class, new LocalTimeSerializer());
        if (isClassAvailable("org.threeten.bp.LocalDateTime"))
            kryo.addDefaultSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        if (isClassAvailable("org.threeten.bp.ZoneOffset")) kryo.addDefaultSerializer(ZoneOffset.class, new ZoneOffsetSerializer());
        if (isClassAvailable("org.threeten.bp.ZoneId")) kryo.addDefaultSerializer(ZoneId.class, new ZoneIdSerializer());
        if (isClassAvailable("org.threeten.bp.OffsetTime")) kryo.addDefaultSerializer(OffsetTime.class, new OffsetTimeSerializer());
        if (isClassAvailable("org.threeten.bp.OffsetDateTime"))
            kryo.addDefaultSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        if (isClassAvailable("org.threeten.bp.ZonedDateTime"))
            kryo.addDefaultSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
        if (isClassAvailable("org.threeten.bp.Year")) kryo.addDefaultSerializer(Year.class, new YearSerializer());
        if (isClassAvailable("org.threeten.bp.YearMonth")) kryo.addDefaultSerializer(YearMonth.class, new YearMonthSerializer());
        if (isClassAvailable("org.threeten.bp.MonthDay")) kryo.addDefaultSerializer(MonthDay.class, new MonthDaySerializer());
        if (isClassAvailable("org.threeten.bp.Period")) kryo.addDefaultSerializer(Period.class, new PeriodSerializer());
    }

    private static class DurationSerializer extends Serializer<Duration> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, Duration duration) {
            out.writeLong(duration.getSeconds());
            out.writeInt(duration.getNano(), true);
        }

        public Duration read (Kryo kryo, Input in, Class<Duration> type) {
            long seconds = in.readLong();
            int nanos = in.readInt(true);
            return Duration.ofSeconds(seconds, nanos);
        }
    }

    private static class InstantSerializer extends Serializer<Instant> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, Instant instant) {
            out.writeLong(instant.getEpochSecond(), true);
            out.writeInt(instant.getNano(), true);
        }

        public Instant read (Kryo kryo, Input in, Class<Instant> type) {
            long seconds = in.readLong(true);
            int nanos = in.readInt(true);
            return Instant.ofEpochSecond(seconds, nanos);
        }
    }

    private static class LocalDateSerializer extends Serializer<LocalDate> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, LocalDate date) {
            write(out, date);
        }

        static void write (Output out, LocalDate date) {
            out.writeInt(date.getYear(), true);
            out.writeByte(date.getMonthValue());
            out.writeByte(date.getDayOfMonth());
        }

        public LocalDate read (Kryo kryo, Input in, Class<LocalDate> type) {
            return read(in);
        }

        static LocalDate read (Input in) {
            int year = in.readInt(true);
            int month = in.readByte();
            int dayOfMonth = in.readByte();
            return LocalDate.of(year, month, dayOfMonth);
        }
    }

    private static class LocalDateTimeSerializer extends Serializer<LocalDateTime> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, LocalDateTime dateTime) {
            LocalDateSerializer.write(out, dateTime.toLocalDate());
            LocalTimeSerializer.write(out, dateTime.toLocalTime());
        }

        public LocalDateTime read (Kryo kryo, Input in, Class<LocalDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            return LocalDateTime.of(date, time);
        }
    }

    private static class LocalTimeSerializer extends Serializer<LocalTime> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, LocalTime time) {
            write(out, time);
        }

        static void write (Output out, LocalTime time) {
            if (time.getNano() == 0) {
                if (time.getSecond() == 0) {
                    if (time.getMinute() == 0) {
                        out.writeByte(~time.getHour());
                    } else {
                        out.writeByte(time.getHour());
                        out.writeByte(~time.getMinute());
                    }
                } else {
                    out.writeByte(time.getHour());
                    out.writeByte(time.getMinute());
                    out.writeByte(~time.getSecond());
                }
            } else {
                out.writeByte(time.getHour());
                out.writeByte(time.getMinute());
                out.writeByte(time.getSecond());
                out.writeInt(time.getNano(), true);
            }
        }

        public LocalTime read (Kryo kryo, Input in, Class<LocalTime> type) {
            return read(in);
        }

        static LocalTime read (Input in) {
            int hour = in.readByte();
            int minute = 0;
            int second = 0;
            int nano = 0;
            if (hour < 0) {
                hour = ~hour;
            } else {
                minute = in.readByte();
                if (minute < 0) {
                    minute = ~minute;
                } else {
                    second = in.readByte();
                    if (second < 0) {
                        second = ~second;
                    } else {
                        nano = in.readInt(true);
                    }
                }
            }
            return LocalTime.of(hour, minute, second, nano);
        }
    }

    private static class ZoneOffsetSerializer extends Serializer<ZoneOffset> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, ZoneOffset obj) {
            write(out, obj);
        }

        static void write (Output out, ZoneOffset obj) {
            final int offsetSecs = obj.getTotalSeconds();
            int offsetByte = offsetSecs % 900 == 0 ? offsetSecs / 900 : 127; // compress to -72 to +72
            out.writeByte(offsetByte);
            if (offsetByte == 127) {
                out.writeInt(offsetSecs);
            }
        }

        public ZoneOffset read (Kryo kryo, Input in, Class<ZoneOffset> type) {
            return read(in);
        }

        static ZoneOffset read (Input in) {
            int offsetByte = in.readByte();
            return (offsetByte == 127 ? ZoneOffset.ofTotalSeconds(in.readInt()) : ZoneOffset.ofTotalSeconds(offsetByte * 900));
        }
    }

    private static class ZoneIdSerializer extends Serializer<ZoneId> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, ZoneId obj) {
            write(out, obj);
        }

        static void write (Output out, ZoneId obj) {
            out.writeString(obj.getId());
        }

        public ZoneId read (Kryo kryo, Input in, Class<ZoneId> type) {
            return read(in);
        }

        static ZoneId read (Input in) {
            String id = in.readString();
            return ZoneId.of(id);
        }
    }

    private static class OffsetTimeSerializer extends Serializer<OffsetTime> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, OffsetTime obj) {
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneOffsetSerializer.write(out, obj.getOffset());
        }

        public OffsetTime read (Kryo kryo, Input in, Class<OffsetTime> type) {
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneOffset offset = ZoneOffsetSerializer.read(in);
            return OffsetTime.of(time, offset);
        }
    }

    private static class OffsetDateTimeSerializer extends Serializer<OffsetDateTime> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, OffsetDateTime obj) {
            LocalDateSerializer.write(out, obj.toLocalDate());
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneOffsetSerializer.write(out, obj.getOffset());
        }

        public OffsetDateTime read (Kryo kryo, Input in, Class<OffsetDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneOffset offset = ZoneOffsetSerializer.read(in);
            return OffsetDateTime.of(date, time, offset);
        }
    }

    private static class ZonedDateTimeSerializer extends Serializer<ZonedDateTime> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, ZonedDateTime obj) {
            LocalDateSerializer.write(out, obj.toLocalDate());
            LocalTimeSerializer.write(out, obj.toLocalTime());
            ZoneIdSerializer.write(out, obj.getZone());
        }

        public ZonedDateTime read (Kryo kryo, Input in, Class<ZonedDateTime> type) {
            LocalDate date = LocalDateSerializer.read(in);
            LocalTime time = LocalTimeSerializer.read(in);
            ZoneId zone = ZoneIdSerializer.read(in);
            return ZonedDateTime.of(date, time, zone);
        }
    }

    private static class YearSerializer extends Serializer<Year> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, Year obj) {
            out.writeInt(obj.getValue(), true);
        }

        public Year read (Kryo kryo, Input in, Class<Year> type) {
            return Year.of(in.readInt(true));
        }
    }

    private static class YearMonthSerializer extends Serializer<YearMonth> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, YearMonth obj) {
            out.writeInt(obj.getYear(), true);
            out.writeByte(obj.getMonthValue());
        }

        public YearMonth read (Kryo kryo, Input in, Class<YearMonth> type) {
            int year = in.readInt(true);
            byte month = in.readByte();
            return YearMonth.of(year, month);
        }
    }

    private static class MonthDaySerializer extends Serializer<MonthDay> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, MonthDay obj) {
            out.writeByte(obj.getMonthValue());
            out.writeByte(obj.getDayOfMonth());
        }

        public MonthDay read (Kryo kryo, Input in, Class<MonthDay> type) {
            byte month = in.readByte();
            byte day = in.readByte();
            return MonthDay.of(month, day);
        }
    }

    private static class PeriodSerializer extends Serializer<Period> {
        {
            setImmutable(true);
        }

        public void write (Kryo kryo, Output out, Period obj) {
            out.writeInt(obj.getYears(), true);
            out.writeInt(obj.getMonths(), true);
            out.writeInt(obj.getDays(), true);
        }

        public Period read (Kryo kryo, Input in, Class<Period> type) {
            int years = in.readInt(true);
            int months = in.readInt(true);
            int days = in.readInt(true);
            return Period.of(years, months, days);
        }
    }

}
