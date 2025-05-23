/*
 * Copyright 2019-2024 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.type;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.polypheny.db.util.Util;
import org.polypheny.db.util.temporal.TimeUnit;


/**
 * Enumeration of the type names which can be used to construct a SQL type.
 * Rationale for this class's existence (instead of just using the standard java.sql.Type ordinals):
 *
 * <ul>
 * <li>{@link java.sql.Types} does not include all SQL2003 data-types;</li>
 * <li>PolyType provides a type-safe enumeration;</li>
 * <li>PolyType provides a place to hang extra information such as whether the type carries precision and scale.</li>
 * </ul>
 */
@Getter
public enum PolyType {
    BOOLEAN(
            PrecScale.NO_NO,
            false,
            Types.BOOLEAN,
            PolyTypeFamily.BOOLEAN ),

    TINYINT(
            PrecScale.NO_NO,
            false,
            Types.TINYINT,
            PolyTypeFamily.NUMERIC ),

    SMALLINT(
            PrecScale.NO_NO,
            false,
            Types.SMALLINT,
            PolyTypeFamily.NUMERIC ),

    INTEGER(
            PrecScale.NO_NO,
            false,
            Types.INTEGER,
            PolyTypeFamily.NUMERIC ),

    BIGINT(
            PrecScale.NO_NO,
            false,
            Types.BIGINT,
            PolyTypeFamily.NUMERIC ),

    DECIMAL(
            PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
            false,
            Types.DECIMAL,
            PolyTypeFamily.NUMERIC ),

    FLOAT(
            PrecScale.NO_NO,
            false,
            Types.FLOAT,
            PolyTypeFamily.NUMERIC ),

    REAL(
            PrecScale.NO_NO,
            false,
            Types.REAL,
            PolyTypeFamily.NUMERIC ),

    DOUBLE(
            PrecScale.NO_NO,
            false,
            Types.DOUBLE,
            PolyTypeFamily.NUMERIC ),

    DATE(
            PrecScale.NO_NO,
            false,
            Types.DATE,
            PolyTypeFamily.DATE ),

    TIME(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.TIME,
            PolyTypeFamily.TIME ),

    TIMESTAMP(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.TIMESTAMP,
            PolyTypeFamily.TIMESTAMP ),

    INTERVAL(
            PrecScale.NO_NO,
            false,
            Types.OTHER,
            PolyTypeFamily.INTERVAL_TIME ),


    CHAR(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.CHAR,
            PolyTypeFamily.CHARACTER ),

    VARCHAR(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.VARCHAR,
            PolyTypeFamily.CHARACTER ),

    TEXT(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.VARCHAR,
            PolyTypeFamily.CHARACTER ),

    BINARY(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.BINARY,
            PolyTypeFamily.BINARY ),

    VARBINARY(
            PrecScale.NO_NO | PrecScale.YES_NO,
            false,
            Types.VARBINARY,
            PolyTypeFamily.BINARY ),

    NULL(
            PrecScale.NO_NO,
            true,
            Types.NULL,
            PolyTypeFamily.NULL ),

    ANY(
            PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.ANY ),

    SYMBOL(
            PrecScale.NO_NO,
            true,
            Types.OTHER,
            null ),

    MULTISET(
            PrecScale.NO_NO,
            false,
            Types.ARRAY,
            PolyTypeFamily.MULTISET ),

    ARRAY(
            PrecScale.NO_NO,
            false,
            Types.ARRAY,
            PolyTypeFamily.ARRAY ),

    MAP(
            PrecScale.NO_NO,
            false,
            Types.OTHER,
            PolyTypeFamily.MAP ),

    DOCUMENT(
            PrecScale.NO_NO,
            false,
            Types.STRUCT,
            PolyTypeFamily.DOCUMENT ),

    GRAPH(
            PrecScale.NO_NO,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.GRAPH ),

    NODE(
            PrecScale.NO_NO,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.GRAPH ),

    EDGE(
            PrecScale.NO_NO,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.GRAPH ),

    PATH(
            PrecScale.NO_NO,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.GRAPH ),

    DISTINCT(
            PrecScale.NO_NO,
            false,
            Types.DISTINCT,
            null ),

    STRUCTURED(
            PrecScale.NO_NO,
            false,
            Types.STRUCT,
            null ),

    ROW(
            PrecScale.NO_NO,
            false,
            Types.STRUCT,
            null ),

    OTHER(
            PrecScale.NO_NO,
            false,
            Types.OTHER,
            null ),

    USER_DEFINED_TYPE(
            PrecScale.NO_NO,
            true,
            Types.OTHER,
            null
    ),

    CURSOR(
            PrecScale.NO_NO,
            false,
            ExtraPolyTypes.REF_CURSOR,
            PolyTypeFamily.CURSOR ),

    COLUMN_LIST(
            PrecScale.NO_NO,
            false,
            Types.OTHER + 2,
            PolyTypeFamily.COLUMN_LIST ),

    DYNAMIC_STAR(
            PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES,
            true,
            Types.JAVA_OBJECT,
            PolyTypeFamily.ANY ),

    GEOMETRY(
            PrecScale.NO_NO,
            true,
            ExtraPolyTypes.GEOMETRY,
            PolyTypeFamily.GEO ),

    FILE(
            PrecScale.NO_NO,
            true,
            Types.BINARY,
            PolyTypeFamily.MULTIMEDIA
    ),

    IMAGE(
            PrecScale.NO_NO,
            true,
            Types.BINARY,
            PolyTypeFamily.MULTIMEDIA
    ),

    VIDEO(
            PrecScale.NO_NO,
            true,
            Types.BINARY,
            PolyTypeFamily.MULTIMEDIA
    ),

    AUDIO(
            PrecScale.NO_NO,
            true,
            Types.BINARY,
            PolyTypeFamily.MULTIMEDIA
    ),
    JSON(
            PrecScale.NO_NO,
            true,
            Types.VARCHAR,
            PolyTypeFamily.CHARACTER
    );


    public static final int MAX_DATETIME_PRECISION = 3;

    // Minimum and default interval precisions are  defined by SQL2003
    // Maximum interval precisions are implementation dependent, but must be at least the default value
    public static final int DEFAULT_INTERVAL_START_PRECISION = 2;
    public static final int DEFAULT_INTERVAL_FRACTIONAL_SECOND_PRECISION = 6;
    public static final int MIN_INTERVAL_START_PRECISION = 1;
    public static final int MIN_INTERVAL_FRACTIONAL_SECOND_PRECISION = 1;
    public static final int MAX_INTERVAL_START_PRECISION = 10;
    public static final int MAX_INTERVAL_FRACTIONAL_SECOND_PRECISION = 9;
    public static final int MAX_DECIMAL_PRECISION = 64;

    // Cached map of enum values
    private static final Map<String, PolyType> VALUES_MAP = Util.enumConstants( PolyType.class );

    // categorizations used by SqlTypeFamily definitions

    // you probably want to use JDK 1.5 support for treating enumeration as collection instead; this is only here to support
    // SqlTypeFamily.ANY
    public static final List<PolyType> ALL_TYPES =
            ImmutableList.of(
                    BOOLEAN, INTEGER, VARCHAR, JSON, DATE, TIME, TIMESTAMP, NULL, DECIMAL, ANY, CHAR, BINARY, VARBINARY, FILE, IMAGE, VIDEO, AUDIO,
                    TINYINT, SMALLINT, BIGINT, REAL, DOUBLE, SYMBOL, INTERVAL, FLOAT, MULTISET, DISTINCT, STRUCTURED, ROW, CURSOR, COLUMN_LIST );

    public static final List<PolyType> BOOLEAN_TYPES = ImmutableList.of( BOOLEAN );

    public static final List<PolyType> BINARY_TYPES = ImmutableList.of( BINARY, VARBINARY );

    public static final List<PolyType> INT_TYPES = ImmutableList.of( TINYINT, SMALLINT, INTEGER, BIGINT );

    public static final List<PolyType> EXACT_TYPES = combine( INT_TYPES, ImmutableList.of( DECIMAL ) );

    public static final List<PolyType> APPROX_TYPES = ImmutableList.of( FLOAT, REAL, DOUBLE );

    public static final List<PolyType> NUMERIC_TYPES = combine( EXACT_TYPES, APPROX_TYPES );

    public static final List<PolyType> FRACTIONAL_TYPES = combine( APPROX_TYPES, ImmutableList.of( DECIMAL ) );

    public static final List<PolyType> CHAR_TYPES = ImmutableList.of( CHAR, VARCHAR, JSON, TEXT );

    public static final List<PolyType> STRING_TYPES = combine( CHAR_TYPES, BINARY_TYPES );

    public static final List<PolyType> DATETIME_TYPES = ImmutableList.of( DATE, TIME, TIMESTAMP );

    public static final List<PolyType> DOCUMENT_TYPES = ImmutableList.of( MAP, ARRAY, DOCUMENT );

    public static final List<PolyType> JSON_TYPES = combine( DOCUMENT_TYPES, STRING_TYPES );

    public static final List<PolyType> GRAPH_TYPES = ImmutableList.of( GRAPH, ARRAY, NODE, EDGE, PATH );

    public static final List<PolyType> COLLECTION_TYPES = ImmutableList.of( ARRAY );

    public static final List<PolyType> BLOB_TYPES = ImmutableList.of( FILE, AUDIO, IMAGE, VIDEO );

    public static final List<PolyType> INTERVAL_TYPES = List.of( INTERVAL );

    private static final Map<Integer, PolyType> JDBC_TYPE_TO_NAME =
            ImmutableMap.<Integer, PolyType>builder()
                    .put( Types.TINYINT, TINYINT )
                    .put( Types.SMALLINT, SMALLINT )
                    .put( Types.BIGINT, BIGINT )
                    .put( Types.INTEGER, INTEGER )
                    .put( Types.NUMERIC, DECIMAL ) // REVIEW
                    .put( Types.DECIMAL, DECIMAL )

                    .put( Types.FLOAT, FLOAT )
                    .put( Types.REAL, REAL )
                    .put( Types.DOUBLE, DOUBLE )

                    .put( Types.CHAR, CHAR )
                    .put( Types.VARCHAR, VARCHAR )

                    // TODO: provide real support for these eventually
                    .put( ExtraPolyTypes.NCHAR, CHAR )
                    .put( ExtraPolyTypes.NVARCHAR, VARCHAR )

                    .put( Types.LONGVARCHAR, VARCHAR )
                    .put( Types.LONGNVARCHAR, VARCHAR )

                    .put( Types.BINARY, BINARY )
                    .put( Types.VARBINARY, VARBINARY )

                    .put( Types.DATE, DATE )
                    .put( Types.TIME, TIME )
                    .put( Types.TIMESTAMP, TIMESTAMP )
                    .put( Types.BIT, BOOLEAN )
                    .put( Types.BOOLEAN, BOOLEAN )
                    .put( Types.DISTINCT, DISTINCT )
                    .put( Types.STRUCT, STRUCTURED )
                    .put( Types.ARRAY, ARRAY )
                    .build();

    /**
     * Bitwise-or of flags indicating allowable precision/scale combinations.
     */
    private final int signatures;

    /**
     * Returns true if not of a "pure" standard sql type. "Inpure" types are {@link #ANY}, {@link #NULL} and {@link #SYMBOL}
     */
    private final boolean special;
    /**
     * -- GETTER --
     */
    private final int jdbcOrdinal;
    /**
     * -- GETTER --
     * Gets the SqlTypeFamily containing this PolyType.
     */
    private final PolyTypeFamily family;


    PolyType( int signatures, boolean special, int jdbcType, PolyTypeFamily family ) {
        this.signatures = signatures;
        this.special = special;
        this.jdbcOrdinal = jdbcType;
        this.family = family;
    }


    /**
     * Looks up a type name from its name.
     *
     * @return Type name, or null if not found
     */
    public static PolyType get( String name ) {
        return VALUES_MAP.get( name );
    }


    public boolean allowsNoPrecNoScale() {
        return (signatures & PrecScale.NO_NO) != 0;
    }


    public boolean allowsPrecNoScale() {
        return (signatures & PrecScale.YES_NO) != 0;
    }


    public boolean allowsPrec() {
        return allowsPrecScale( true, true ) || allowsPrecScale( true, false );
    }


    public boolean allowsScale() {
        return allowsPrecScale( true, true );
    }


    /**
     * Returns whether this type can be specified with a given combination of precision and scale. For example,
     *
     * <ul>
     * <li><code>Varchar.allowsPrecScale(true, false)</code> returns <code>true</code>, because the VARCHAR type allows a precision parameter, as in <code>VARCHAR(10)</code>.</li>
     * <li><code>Varchar.allowsPrecScale(true, true)</code> returns <code>true</code>, because the VARCHAR type does not allow a precision and a scale parameter, as in <code>VARCHAR(10, 4)</code>.</li>
     * <li><code>allowsPrecScale(false, true)</code> returns <code>false</code> for every type.</li>
     * </ul>
     *
     * @param precision Whether the precision/length field is part of the type specification
     * @param scale Whether the scale field is part of the type specification
     * @return Whether this combination of precision/scale is valid
     */
    public boolean allowsPrecScale( boolean precision, boolean scale ) {
        int mask = precision
                ? (scale ? PrecScale.YES_YES : PrecScale.YES_NO)
                : (scale ? 0 : PrecScale.NO_NO);
        return (signatures & mask) != 0;
    }


    private static List<PolyType> combine( List<PolyType> list0, List<PolyType> list1 ) {
        return ImmutableList.<PolyType>builder()
                .addAll( list0 )
                .addAll( list1 )
                .build();
    }


    /**
     * @return default scale for this type if supported, otherwise -1 if scale is either unsupported or must be specified explicitly
     */
    public int getDefaultScale() {
        return switch ( this ) {
            case DECIMAL -> 0;
            case INTERVAL -> DEFAULT_INTERVAL_FRACTIONAL_SECOND_PRECISION;
            default -> -1;
        };
    }


    /**
     * Gets the PolyType corresponding to a JDBC type.
     *
     * @param jdbcType the JDBC type of interest
     * @return corresponding PolyType, or null if the type is not known
     */
    public static PolyType getNameForJdbcType( int jdbcType ) {
        return JDBC_TYPE_TO_NAME.get( jdbcType );
    }


    /**
     * Returns the limit of this datatype. For example,
     *
     * <table border="1">
     * <caption>Datatype limits</caption>
     * <tr>
     * <th>Datatype</th>
     * <th>sign</th>
     * <th>limit</th>
     * <th>beyond</th>
     * <th>precision</th>
     * <th>scale</th>
     * <th>Returns</th>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>true</td>
     * <td>true</td>
     * <td>false</td>
     * <td>-1</td>
     * <td>-1</td>
     * <td>2147483647 (2 ^ 31 -1 = MAXINT)</td>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>true</td>
     * <td>true</td>
     * <td>true</td>
     * <td>-1</td>
     * <td>-1</td>
     * <td>2147483648 (2 ^ 31 = MAXINT + 1)</td>
     * </tr>
     * <tr>
     * <td>Integer</td>
     * <td>false</td>
     * <td>true</td>
     * <td>false</td>
     * <td>-1</td>
     * <td>-1</td>
     * <td>-2147483648 (-2 ^ 31 = MININT)</td>
     * </tr>
     * <tr>
     * <td>Boolean</td>
     * <td>true</td>
     * <td>true</td>
     * <td>false</td>
     * <td>-1</td>
     * <td>-1</td>
     * <td>TRUE</td>
     * </tr>
     * <tr>
     * <td>Varchar</td>
     * <td>true</td>
     * <td>true</td>
     * <td>false</td>
     * <td>10</td>
     * <td>-1</td>
     * <td>'ZZZZZZZZZZ'</td>
     * </tr>
     * </table>
     *
     * @param sign If true, returns upper limit, otherwise lower limit
     * @param limit If true, returns value at or near to overflow; otherwise value at or near to underflow
     * @param beyond If true, returns the value just beyond the limit, otherwise the value at the limit
     * @param precision Precision, or -1 if not applicable
     * @param scale Scale, or -1 if not applicable
     * @return Limit value
     */
    Object getLimit( boolean sign, Limit limit, boolean beyond, int precision, int scale ) {
        assert allowsPrecScale( precision != -1, scale != -1 ) : this;
        if ( limit == Limit.ZERO ) {
            if ( beyond ) {
                return null;
            }
            sign = true;
        }
        Calendar calendar;

        switch ( this ) {
            case BOOLEAN:
                return switch ( limit ) {
                    case ZERO -> false;
                    case UNDERFLOW -> null;
                    case OVERFLOW -> {
                        if ( beyond || !sign ) {
                            yield null;
                        } else {
                            yield true;
                        }
                    }
                };

            case TINYINT:
                return getNumericLimit( 2, 8, sign, limit, beyond );

            case SMALLINT:
                return getNumericLimit( 2, 16, sign, limit, beyond );

            case INTEGER:
                return getNumericLimit( 2, 32, sign, limit, beyond );

            case BIGINT:
                return getNumericLimit( 2, 64, sign, limit, beyond );

            case DECIMAL:
                BigDecimal decimal = getNumericLimit( 10, precision, sign, limit, beyond );
                if ( decimal == null ) {
                    return null;
                }

                // Decimal values must fit into 64 bits. So, the maximum value of a DECIMAL(19, 0) is 2^63 - 1, not 10^19 - 1.
                if ( limit == Limit.OVERFLOW ) {
                    final BigDecimal other = (BigDecimal) BIGINT.getLimit( sign, limit, beyond, -1, -1 );
                    if ( decimal.compareTo( other ) == (sign ? 1 : -1) ) {
                        decimal = other;
                    }
                }

                // Apply scale.
                if ( scale == 0 ) {
                    // do nothing
                } else if ( scale > 0 ) {
                    decimal = decimal.divide( BigDecimal.TEN.pow( scale ) );
                } else {
                    decimal = decimal.multiply( BigDecimal.TEN.pow( -scale ) );
                }
                return decimal;

            case CHAR:
            case JSON:
            case VARCHAR:
                if ( !sign ) {
                    return null; // this type does not have negative values
                }
                StringBuilder buf = new StringBuilder();
                return switch ( limit ) {
                    case ZERO -> buf.toString();
                    case UNDERFLOW -> {
                        if ( beyond ) {
                            // There is no value between the empty string and the smallest non-empty string.
                            yield null;
                        }
                        buf.append( "a" );
                        yield buf.toString();
                    }
                    case OVERFLOW -> {
                        buf.append( "Z".repeat( Math.max( 0, precision ) ) );
                        if ( beyond ) {
                            buf.append( "Z" );
                        }
                        yield buf.toString();
                    }
                };
            case BINARY:
            case VARBINARY:
                if ( !sign ) {
                    return null; // this type does not have negative values
                }
                return switch ( limit ) {
                    case ZERO -> new byte[0];
                    case UNDERFLOW -> {
                        if ( beyond ) {
                            // There is no value between the empty string and the smallest value.
                            yield null;
                        }
                        yield new byte[]{ 0x00 };
                    }
                    case OVERFLOW -> {
                        byte[] bytes = new byte[precision + (beyond ? 1 : 0)];
                        Arrays.fill( bytes, (byte) 0xff );
                        yield bytes;
                    }
                };

            case DATE:
                calendar = Util.calendar();
                switch ( limit ) {
                    case ZERO:

                        // The epoch.
                        calendar.set( Calendar.YEAR, 1970 );
                        calendar.set( Calendar.MONTH, 0 );
                        calendar.set( Calendar.DAY_OF_MONTH, 1 );
                        break;
                    case UNDERFLOW:
                        return null;
                    case OVERFLOW:
                        if ( beyond ) {
                            // It is impossible to represent an invalid year as a date literal. SQL dates are represented
                            // as 'yyyy-mm-dd', and 1 <= yyyy <= 9999 is valid. There is no year 0: the year before 1AD is 1BC,
                            // so SimpleDateFormat renders the day before 0001-01-01 (AD) as 0001-12-31 (BC), which looks
                            // like a valid date.
                            return null;
                        }

                        // "SQL:2003 6.1 <data type> Access Rules 6" says that year is between 1 and 9999, and days/months
                        // are the valid Gregorian calendar values for these years.
                        if ( sign ) {
                            calendar.set( Calendar.YEAR, 9999 );
                            calendar.set( Calendar.MONTH, 11 );
                            calendar.set( Calendar.DAY_OF_MONTH, 31 );
                        } else {
                            calendar.set( Calendar.YEAR, 1 );
                            calendar.set( Calendar.MONTH, 0 );
                            calendar.set( Calendar.DAY_OF_MONTH, 1 );
                        }
                        break;
                }
                calendar.set( Calendar.HOUR_OF_DAY, 0 );
                calendar.set( Calendar.MINUTE, 0 );
                calendar.set( Calendar.SECOND, 0 );
                return calendar;

            case TIME:
                if ( !sign ) {
                    return null; // this type does not have negative values
                }
                if ( beyond ) {
                    return null; // invalid values are impossible to represent
                }
                calendar = Util.calendar();
                return switch ( limit ) {
                    case ZERO -> {
                        // The epoch.
                        calendar.set( Calendar.HOUR_OF_DAY, 0 );
                        calendar.set( Calendar.MINUTE, 0 );
                        calendar.set( Calendar.SECOND, 0 );
                        calendar.set( Calendar.MILLISECOND, 0 );
                        yield calendar;
                    }
                    case UNDERFLOW -> null;
                    case OVERFLOW -> {
                        calendar.set( Calendar.HOUR_OF_DAY, 23 );
                        calendar.set( Calendar.MINUTE, 59 );
                        calendar.set( Calendar.SECOND, 59 );
                        int millis = (precision >= 3) ? 999
                                : ((precision == 2) ? 990
                                        : ((precision == 1) ? 900
                                                : 0));
                        calendar.set( Calendar.MILLISECOND, millis );
                        yield calendar;
                    }
                };
            case TIMESTAMP:
                calendar = Util.calendar();
                return switch ( limit ) {
                    case ZERO -> {
                        // The epoch.
                        calendar.set( Calendar.YEAR, 1970 );
                        calendar.set( Calendar.MONTH, 0 );
                        calendar.set( Calendar.DAY_OF_MONTH, 1 );
                        calendar.set( Calendar.HOUR_OF_DAY, 0 );
                        calendar.set( Calendar.MINUTE, 0 );
                        calendar.set( Calendar.SECOND, 0 );
                        calendar.set( Calendar.MILLISECOND, 0 );
                        yield calendar;
                    }
                    case UNDERFLOW -> null;
                    case OVERFLOW -> {
                        if ( beyond ) {
                            // It is impossible to represent an invalid year as a date literal. SQL dates are represented
                            // as 'yyyy-mm-dd', and 1 <= yyyy <= 9999 is valid. There is no year 0: the year before
                            // 1AD is 1BC, so SimpleDateFormat renders the day before 0001-01-01 (AD) as 0001-12-31 (BC),
                            // which looks like a valid date.
                            yield null;
                        }

                        // "SQL:2003 6.1 <data type> Access Rules 6" says that year is between 1 and 9999, and days/months
                        // are the valid Gregorian calendar values for these years.
                        if ( sign ) {
                            calendar.set( Calendar.YEAR, 9999 );
                            calendar.set( Calendar.MONTH, 11 );
                            calendar.set( Calendar.DAY_OF_MONTH, 31 );
                            calendar.set( Calendar.HOUR_OF_DAY, 23 );
                            calendar.set( Calendar.MINUTE, 59 );
                            calendar.set( Calendar.SECOND, 59 );
                            int millis =
                                    (precision >= 3) ? 999
                                            : ((precision == 2) ? 990
                                                    : ((precision == 1) ? 900
                                                            : 0));
                            calendar.set( Calendar.MILLISECOND, millis );
                        } else {
                            calendar.set( Calendar.YEAR, 1 );
                            calendar.set( Calendar.MONTH, 0 );
                            calendar.set( Calendar.DAY_OF_MONTH, 1 );
                            calendar.set( Calendar.HOUR_OF_DAY, 0 );
                            calendar.set( Calendar.MINUTE, 0 );
                            calendar.set( Calendar.SECOND, 0 );
                            calendar.set( Calendar.MILLISECOND, 0 );
                        }
                        yield calendar;
                    }
                };
            default:
                throw Util.unexpected( this );
        }
    }


    /**
     * Returns the minimum precision (or length) allowed for this type, or -1 if precision/length are not applicable
     * for this type.
     *
     * @return Minimum allowed precision
     */
    public int getMinPrecision() {
        return switch ( this ) {
            case DECIMAL, JSON, VARCHAR, CHAR, VARBINARY, BINARY, TIME, TIMESTAMP -> 1;
            case INTERVAL -> MIN_INTERVAL_START_PRECISION;
            default -> -1;
        };
    }


    /**
     * Returns the minimum scale (or fractional second precision in the case of intervals) allowed for this type, or -1 if
     * precision/length are not applicable for this type.
     *
     * @return Minimum allowed scale
     */
    public int getMinScale() {
        return switch ( this ) {
            // TODO: Minimum numeric scale for decimal
            case INTERVAL -> MIN_INTERVAL_FRACTIONAL_SECOND_PRECISION;
            default -> -1;
        };
    }


    /**
     * Returns {@code HOUR} for {@code HOUR TO SECOND} and {@code HOUR}, {@code SECOND} for {@code SECOND}.
     */
    public TimeUnit getStartUnit() {
        return switch ( this ) {
            case INTERVAL -> TimeUnit.MONTH;
            default -> throw new AssertionError( this );
        };
    }


    /**
     * Returns {@code SECOND} for both {@code HOUR TO SECOND} and {@code SECOND}.
     */
    public TimeUnit getEndUnit() {
        return switch ( this ) {
            case INTERVAL -> TimeUnit.MILLISECOND;
            default -> throw new AssertionError( this );
        };
    }


    public boolean isYearMonth() {
        return this == INTERVAL;
    }


    /**
     * Limit.
     */
    private enum Limit {
        ZERO, UNDERFLOW, OVERFLOW
    }


    private BigDecimal getNumericLimit( int radix, int exponent, boolean sign, Limit limit, boolean beyond ) {
        return switch ( limit ) {
            case OVERFLOW -> {
                // 2-based schemes run from -2^(N-1) to 2^(N-1)-1 e.g. -128 to +127
                // 10-based schemas run from -(10^N-1) to 10^N-1 e.g. -99 to +99
                final BigDecimal bigRadix = BigDecimal.valueOf( radix );
                if ( radix == 2 ) {
                    --exponent;
                }
                BigDecimal decimal = bigRadix.pow( exponent );
                if ( sign || (radix != 2) ) {
                    decimal = decimal.subtract( BigDecimal.ONE );
                }
                if ( beyond ) {
                    decimal = decimal.add( BigDecimal.ONE );
                }
                if ( !sign ) {
                    decimal = decimal.negate();
                }
                yield decimal;
            }
            case UNDERFLOW -> beyond ? null : (sign ? BigDecimal.ONE : BigDecimal.ONE.negate());
            case ZERO -> BigDecimal.ZERO;
        };
    }


    /**
     * @return name of this type
     */
    public String getName() {
        return toString();
    }


    /**
     * The set of types that are allowed for field in an entity (e.g. columns in a table).
     *
     * @return allowed field types
     */
    public static Set<PolyType> allowedFieldTypes() {
        return ImmutableSet.of( BOOLEAN, TINYINT, SMALLINT, INTEGER, JSON, BIGINT, DECIMAL, REAL, DOUBLE, DATE, TIME, TIMESTAMP, VARCHAR, TEXT, FILE, IMAGE, VIDEO, AUDIO );
    }


    /**
     * Returns the type name in string form. Does not include precision, scale or whether nulls are allowed.
     * Example: "DECIMAL" not "DECIMAL(7, 2)"; "INTEGER" not "JavaType(int)".
     */
    public String getTypeName() {
        return this.toString();
    }


    /**
     * Flags indicating precision/scale combinations.
     * <p>
     * Note: for intervals:
     *
     * <ul>
     * <li>precision = start (leading field) precision</li>
     * <li>scale = fractional second precision</li>
     * </ul>
     */
    private interface PrecScale {

        int NO_NO = 1;
        int YES_NO = 2;
        int YES_YES = 4;

    }
}

