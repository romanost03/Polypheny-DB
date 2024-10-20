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
 */

package org.polypheny.db.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.function.Deterministic;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.type.entity.PolyBoolean;
import org.polypheny.db.type.entity.PolyList;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.graph.GraphObject;
import org.polypheny.db.type.entity.graph.GraphPropertyHolder;
import org.polypheny.db.type.entity.graph.PolyDictionary;
import org.polypheny.db.type.entity.graph.PolyEdge;
import org.polypheny.db.type.entity.graph.PolyEdge.EdgeDirection;
import org.polypheny.db.type.entity.graph.PolyGraph;
import org.polypheny.db.type.entity.graph.PolyNode;
import org.polypheny.db.type.entity.graph.PolyPath;
import org.polypheny.db.type.entity.numerical.PolyDouble;
import org.polypheny.db.type.entity.relational.PolyMap;
import org.polypheny.db.type.entity.spatial.GeometryTopologicalException;
import org.polypheny.db.type.entity.spatial.PolyGeometry;
import org.polypheny.db.type.entity.spatial.PolyPoint;

import static java.lang.Math.toRadians;
import static org.polypheny.db.functions.spatial.GeoDistanceFunctions.EARTH_RADIUS_M;
import static org.polypheny.db.type.entity.spatial.PolyGeometry.WGS_84;
import static org.polypheny.db.type.entity.spatial.PolyGeometry.WGS_84_3D;


@Deterministic
@Slf4j
public class CypherFunctions {

    private CypherFunctions() {
        // empty on purpose
    }


    /**
     * Matches all suitable paths for the given graph.
     *
     * @param graph the full graph
     * @param comp the reference path, which is matched
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyPath> pathMatch( PolyGraph graph, PolyPath comp ) {
        return Linq4j.asEnumerable( graph.extract( comp ) );
    }


    /**
     * Matches all suitable nodes for the given graph.
     *
     * @param graph the full graph
     * @param node the node to match
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyNode> nodeMatch( PolyGraph graph, PolyNode node ) {
        return Linq4j.asEnumerable( graph.extract( node ) );
    }


    /**
     * Extracts all nodes as {@link Enumerable} of the given graph.
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyNode> nodeExtract( PolyGraph graph ) {
        return Linq4j.asEnumerable( graph.getNodes().values() );
    }


    /**
     * Creates a new graph from the provided nodes and edges.
     *
     * @param nodes collection of nodes
     * @param edges collection of edges
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyValue[]> toGraph( Enumerable<PolyNode[]> nodes, Enumerable<PolyEdge[]> edges ) {
        Map<PolyString, PolyNode> ns = new HashMap<>();
        for ( PolyNode[] node : nodes ) {
            ns.put( node[0].id, node[0] );
        }

        Map<PolyString, PolyEdge> es = new HashMap<>();
        for ( PolyEdge[] edge : edges ) {
            es.put( edge[0].id, edge[0] );
        }
        List<PolyValue[]> values = new ArrayList<>();
        values.add( new PolyValue[]{ new PolyGraph( PolyMap.of( ns ), PolyMap.of( es ) ) } );
        return Linq4j.asEnumerable( values );
    }


    /**
     * Transforms the relational normalized edges into {@link PolyEdge}s
     *
     * @param edge the normalized edges
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyEdge[]> toEdge( Enumerable<PolyValue[]> edge ) {
        List<PolyEdge[]> edges = new ArrayList<>();

        PolyString oldId = null;
        PolyString oldSourceId = null;
        PolyString oldTargetId = null;
        Set<PolyString> oldLabels = new HashSet<>();
        Map<PolyString, PolyValue> oldProps = new HashMap<>();

        for ( PolyValue[] value : edge ) {
            PolyString id = value[0].asString();
            PolyString label = value[1].asString();
            PolyString sourceId = value[2].asString();
            PolyString targetId = value[3].asString();
            // id is 4
            PolyString key = value[5] == null ? PolyString.of( null ) : value[5].asString();
            PolyString val = value[6] == null ? PolyString.of( null ) : value[6].asString();

            if ( !id.isNull() && !id.equals( oldId ) ) {
                if ( oldId != null && !oldId.isNull() ) {
                    edges.add( new PolyEdge[]{ new PolyEdge( oldId, new PolyDictionary( oldProps ), PolyList.of( oldLabels ), oldSourceId, oldTargetId, EdgeDirection.LEFT_TO_RIGHT, null ) } );
                }
                oldId = id;
                oldLabels = new HashSet<>();
                oldSourceId = sourceId;
                oldTargetId = targetId;
                oldProps = new HashMap<>();
            }
            oldLabels.add( label );

            if ( !key.isNull() ) {
                // id | key | value | source | target
                // 13 | null| null | 12      | 10 ( no key value present )
                oldProps.put( key, val );
            }
        }

        if ( oldId != null && !oldId.isNull() ) {
            edges.add( new PolyEdge[]{ new PolyEdge( oldId, new PolyDictionary( oldProps ), PolyList.of( oldLabels ), oldSourceId, oldTargetId, EdgeDirection.LEFT_TO_RIGHT, null ) } );
        }

        return Linq4j.asEnumerable( edges );
    }


    /**
     * Transforms the relational normalized edges into {@link PolyEdge}s
     *
     * @param node the normalized nodes
     */
    @SuppressWarnings("unused")
    public static Enumerable<PolyNode[]> toNode( Enumerable<PolyValue[]> node ) {
        List<PolyNode[]> nodes = new ArrayList<>();

        PolyString oldId = null;
        Set<PolyString> oldLabels = new HashSet<>();
        Map<PolyString, PolyValue> oldProps = new HashMap<>();

        for ( PolyValue[] value : node ) {
            PolyString id = value[0].asString();
            PolyString label = value[1].asString();
            // id is 2
            PolyString key = value[3] == null ? PolyString.of( null ) : value[3].asString();
            PolyString val = value[4] == null ? PolyString.of( null ) : value[4].asString();

            if ( !id.isNull() && !id.equals( oldId ) ) {
                if ( oldId != null ) {
                    nodes.add( new PolyNode[]{ new PolyNode( oldId, new PolyDictionary( oldProps ), PolyList.of( oldLabels ), null ) } );
                }
                oldId = id;
                oldLabels = new HashSet<>();
                oldProps = new HashMap<>();
            }
            if ( !label.isNull() && !label.value.equals( "$" ) ) {
                // eventually no labels
                oldLabels.add( label );
            }
            if ( !key.isNull() ) {
                // eventually no properties present
                oldProps.put( key, val );
            }
        }

        if ( oldId != null && !oldId.isNull() ) {
            nodes.add( new PolyNode[]{ new PolyNode( oldId, new PolyDictionary( oldProps ), PolyList.of( oldLabels ), null ) } );
        }

        return Linq4j.asEnumerable( nodes );
    }


    /**
     * Returns if the given node has the given key.
     *
     * @param node the target node
     * @param property the key to check
     */
    @SuppressWarnings("unused")
    public static PolyBoolean hasProperty( PolyNode node, PolyValue property ) {
        return PolyBoolean.of( node.properties.containsKey( property.asString() ) );
    }


    /**
     * Returns if the given node has the given label.
     *
     * @param node the target node
     * @param label the label to check
     */
    @SuppressWarnings("unused")
    public static PolyBoolean hasLabel( PolyNode node, PolyValue label ) {
        return PolyBoolean.of( node.labels.contains( label.asString() ) );
    }


    /**
     * Extracts the given variable from a provided path e.g. ()-[e]-(), e -> e
     *
     * @param path the path containing the element with the given variable
     * @param variableName the name of the given variable
     */
    @SuppressWarnings("unused")
    public static GraphObject extractFrom( PolyPath path, PolyString variableName ) {
        return path.get( variableName );
    }


    /**
     * Extracts a given property from a graph element.
     *
     * @param holder the target from which the property is extracted
     * @param key the key of the property to extract
     * @return the property value
     */
    @SuppressWarnings("unused")
    public static PolyValue extractProperty( GraphPropertyHolder holder, PolyString key ) {
        if ( holder.getProperties().containsKey( key ) ) {
            return holder.getProperties().get( key );
        }
        return null;
    }


    @SuppressWarnings("unused")
    public static PolyValue extractProperties( GraphPropertyHolder holder ) {
        return holder.getProperties();
    }


    @SuppressWarnings("unused")
    public static PolyString extractId( GraphPropertyHolder holder ) {
        return holder.getId();
    }


    /**
     * Returns a single label of a given graph element.
     *
     * @param holder the target from which the label is extracted
     */
    @SuppressWarnings("unused")
    public static PolyString extractLabel( GraphPropertyHolder holder ) {
        return holder.getLabels().get( 0 );
    }


    /**
     * Returns all labels of a given graph element.
     *
     * @param holder the target from which the labels are extracted
     */
    @SuppressWarnings("unused")
    public static List<PolyString> extractLabels( GraphPropertyHolder holder ) {
        return holder.getLabels();
    }


    /**
     * Transform the object into a list by either wrapping it or leaving it as is if it already is.
     *
     * @param obj the object to transform
     */
    @SuppressWarnings("unused")
    public static PolyList<?> toList( PolyValue obj ) {
        if ( obj == null ) {
            return PolyList.of();
        }

        if ( obj.isList() ) {
            return obj.asList();
        } else if ( obj.isString() ) {
            try {
                return (PolyList<?>) PolyValue.fromJson( obj.asString().value );
            } catch ( Exception ignored ) {
                // ignore
            }
        }
        return PolyList.of( obj );
    }


    /**
     * Replaces the left and right side of the given edge with the provided elements.
     *
     * @param edge the edge to adjust
     * @param left the left element of the edge
     * @param right the right element of the edge
     * @return the adjusted edge, where both elements are replaced
     */
    @SuppressWarnings("unused")
    public static PolyEdge adjustEdge( PolyEdge edge, PolyNode left, PolyNode right ) {
        // If isVariable is true, the node is only a placeholder and the id should not be replaced
        return edge.from( left.isVariable() ? null : left.id, right.isVariable() ? null : right.id );
    }


    /**
     * Sets a property value for a given key.
     * If the key does not exist it gets created, if it does, it gets replaced.
     *
     * @param target the graph element to modify
     * @param key the key of the property to replace
     * @param value the value to replace it by
     * @return the adjusted graph element
     */
    @SuppressWarnings("unused")
    public static GraphPropertyHolder setProperty( GraphPropertyHolder target, PolyString key, PolyValue value ) {
        target.properties.put( key, value );
        return target;
    }


    /**
     * Sets all given labels for the target graph element.
     *
     * @param target the graph element to modify
     * @param labels the labels to set
     * @param replace if the labels a replaced completely or only added
     * @return the adjusted graph element
     */
    @SuppressWarnings("unused")
    public static GraphPropertyHolder setLabels( GraphPropertyHolder target, List<PolyString> labels, PolyBoolean replace ) {
        if ( replace.value ) {
            target.labels.clear();
        }
        target.setLabels( PolyList.of( labels ) );
        return target;
    }


    /**
     * Sets all given properties for the given graph element.
     *
     * @param target the graph element to modify
     * @param keys the keys, which are set
     * @param values the values for the keys
     * @param replace if the properties are replaced completely or only added and replaced
     * @return the modified graph element
     */
    @SuppressWarnings("unused")
    public static GraphPropertyHolder setProperties( GraphPropertyHolder target, List<PolyString> keys, List<PolyValue> values, PolyBoolean replace ) {
        if ( replace.value ) {
            target.properties.clear();
        }

        int i = 0;
        for ( PolyString key : keys ) {
            target.properties.put( key, values.get( i ) );
            i++;
        }
        return target;
    }


    /**
     * Removes the given labels for graph object.
     *
     * @param target the graph element to modify
     * @param labels the labels to remove
     * @return the modified graph element
     */
    @SuppressWarnings("unused")
    public static GraphPropertyHolder removeLabels( GraphPropertyHolder target, List<String> labels ) {
        target.labels.removeAll( labels.stream().map( PolyString::of ).toList() );
        return target;
    }


    /**
     * Removes a property from a given graph element.
     *
     * @param target the graph element to modify
     * @param key the key of the property to remove
     * @return the modified graph element
     */
    @SuppressWarnings("unused")
    public static GraphPropertyHolder removeProperty( GraphPropertyHolder target, String key ) {
        target.properties.remove( PolyString.of( key ) );
        return target;
    }


    public static PolyBoolean like( PolyValue b0, PolyValue b1 ) {
        if ( b0.isString() && b1.isString() ) {
            return Functions.like( b0.asString(), b1.asString() );
        }
        return PolyBoolean.FALSE;
    }


    /**
     * 5 possible argument names with values:
     * - 3 coordinates
     * - 2 options
     */
    @SuppressWarnings("unused")
    public static PolyGeometry point(
            PolyValue argName1,
            PolyValue argValue1,
            PolyValue argName2,
            PolyValue argValue2,
            PolyValue argName3,
            PolyValue argValue3,
            PolyValue argName4,
            PolyValue argValue4,
            PolyValue argName5,
            PolyValue argValue5 ) {
        Map<PolyValue, PolyValue> map = new HashMap<>();
        if ( argName1 != null && argValue1 != null ) {
            map.put( argName1, argValue1 );
        }
        if ( argName2 != null && argValue2 != null ) {
            map.put( argName2, argValue2 );
        }
        if ( argName3 != null && argValue3 != null ) {
            map.put( argName3, argValue3 );
        }
        if ( argName4 != null && argValue4 != null ) {
            map.put( argName4, argValue4 );
        }
        if ( argName5 != null && argValue5 != null ) {
            map.put( argName5, argValue5 );
        }
        return point( PolyMap.of( map ) );
    }


    @SuppressWarnings("unused")
    public static PolyGeometry point( PolyValue map ) {
        if ( !map.isMap() ) {
            throw new GenericRuntimeException( "point() expects a map." );
        }

        PolyMap<PolyValue, PolyValue> polyMap = map.asMap();
        PolyString x = new PolyString( "x" );
        PolyString y = new PolyString( "y" );
        PolyString z = new PolyString( "z" );
        PolyString longitude = new PolyString( "longitude" );
        PolyString latitude = new PolyString( "latitude" );
        PolyString height = new PolyString( "height" );
        PolyString srid = new PolyString( "srid" );
        PolyString crs = new PolyString( "crs" );

        // In Cypher, it is possible to define the following four SRIDs
        //  7203: cartesian with 2 coordinates (no relation to ESPG:7203) -> 0
        //  9157: cartesian with 3 coordinates (no relation to ESPG:9157) -> 0
        //  4326: spherical with 2 coordinates                            -> 4326
        //  4979: spherical with 3 coordinates                            -> 4979
        int SRID = 0;
        Coordinate coordinate = new Coordinate();

        if ( polyMap.containsKey( x ) && polyMap.containsKey( y ) ) {
            if ( polyMap.get( x ).isNull() ) {
                return null;
            }
            coordinate.setX( convertPolyValueToDouble( polyMap.get( x ) ) );
            if ( polyMap.get( y ).isNull() ) {
                return null;
            }
            coordinate.setY( convertPolyValueToDouble( polyMap.get( y ) ) );
            if ( polyMap.containsKey( z ) ) {
                if ( polyMap.get( z ).isNull() ) {
                    return null;
                }
                coordinate.setZ( convertPolyValueToDouble( polyMap.get( z ) ) );
            }

            if ( polyMap.containsKey( srid ) ) {
                SRID = switch ( polyMap.get( srid ).asInteger().intValue() ) {
                    case WGS_84 -> WGS_84;
                    case WGS_84_3D -> WGS_84_3D;
                    default -> 0;
                };
            } else if ( polyMap.containsKey( crs ) ) {
                SRID = switch ( polyMap.get( crs ).asString().value ) {
                    case "WGS-84-2D" -> WGS_84;
                    case "WGS-84-3D" -> WGS_84_3D;
                    default -> 0;
                };
            }

        } else if ( polyMap.containsKey( longitude ) && polyMap.containsKey( latitude ) ) {
            if ( polyMap.get( longitude ).isNull() ) {
                return null;
            }
            coordinate.setX( convertPolyValueToDouble( polyMap.get( longitude ) ) );
            if ( polyMap.get( latitude ).isNull() ) {
                return null;
            }
            coordinate.setY( convertPolyValueToDouble( polyMap.get( latitude ) ) );
            if ( polyMap.containsKey( height ) ) {
                if ( polyMap.get( height ).isNull() ) {
                    return null;
                }
                coordinate.setZ( convertPolyValueToDouble( polyMap.get( height ) ) );
                SRID = WGS_84_3D;
            } else {
                SRID = WGS_84;
            }
        }
        GeometryFactory geometryFactory = new GeometryFactory( new PrecisionModel(), SRID );
        return PolyGeometry.of( geometryFactory.createPoint( coordinate ) );
    }


    @SuppressWarnings("unused")
    public static PolyDouble distance( PolyValue p1, PolyValue p2 ) {
        PolyPoint g1 = p1.asGeometry().asPoint();
        PolyPoint g2 = p2.asGeometry().asPoint();

        if ( !Objects.equals( g1.getSRID(), g2.getSRID() ) ) {
            throw new GenericRuntimeException( "Cannot compute point.distance(%s, %s) because of different SRIDs.".formatted( g1, g2 ) );
        }
        Integer srid = g1.getSRID();

        try {
            if ( g1.hasZ() && g2.hasZ() ) {
                if ( srid == 0 ) {
                    return new PolyDouble(
                            Math.sqrt( Math.pow( g2.getX() - g1.getX(), 2 ) + Math.pow( g2.getY() - g1.getY(), 2 ) + Math.pow( g2.getZ() - g1.getZ(), 2 ) )
                    );
                } else if ( srid == WGS_84_3D ) {
                    // See https://github.com/neo4j/neo4j/blob/5.20/community/values/src/main/java/org/neo4j/values/storable/CRSCalculator.java
                    double greatCircleDistance = getGreatCircleDistance( g1, g2 );
                    double avgHeight = (g1.getZ() + g2.getZ()) / 2;
                    // Note: Neo4j uses a different earth radius of 6378140.0, which is why the same calculation
                    //       in Neo4j does not yield (exactly) the same results.
                    double distance2D = (EARTH_RADIUS_M + avgHeight) * greatCircleDistance;
                    double heightDifference = g1.getZ() - g2.getZ();
                    return new PolyDouble( Math.sqrt( Math.pow( distance2D, 2 ) + Math.pow( heightDifference, 2 ) ) );
                }
            } else {
                return new PolyDouble( g1.distance( g2 ) );
            }
        } catch ( GeometryTopologicalException e ) {
            throw new GenericRuntimeException( e );
        }

        throw new GenericRuntimeException( "This should not be possible!" );
    }
    

    /**
     * Use same logic as Neo4j to calculate the spherical distance.
     * See: <a href="https://github.com/neo4j/neo4j/blob/5.20/community/values/src/main/java/org/neo4j/values/storable/CRSCalculator.java">GitHub</a>
     */
    private static double getGreatCircleDistance( PolyPoint g1, PolyPoint g2 ) {
        double lat1 = toRadians( g1.getY() );
        double lat2 = toRadians( g2.getY() );
        double latDifference = lat2 - lat1;
        double lonDifference = toRadians( g2.getX() - g1.getX() );
        double alpha = Math.pow( Math.sin( latDifference / 2 ), 2 ) +
                Math.cos( lat1 ) * Math.cos( lat2 ) * Math.pow( Math.sin( lonDifference / 2 ), 2 );
        return 2.0 * Math.atan2( Math.sqrt( alpha ), Math.sqrt( 1 - alpha ) );
    }


    @SuppressWarnings("unused")
    public static PolyBoolean withinBBox( PolyValue point, PolyValue bbox ) {
        PolyGeometry g = point.asGeometry();
        PolyGeometry gBBox = bbox.asGeometry();
        return new PolyBoolean( g.within( gBBox ) );
    }


    private static double convertPolyValueToDouble( PolyValue value ) {
        // This should be sufficient, as all numerical values from Cypher are stored as BigDecimal.
        if ( value.isString() ) {
            return Double.parseDouble( value.toString() );
        }
        if ( value.isDouble() ) {
            return value.asDouble().doubleValue();
        }

        assert value.isBigDecimal() : "Extend method to handle other numerical data types.";
        return Objects.requireNonNull( value.asBigDecimal().getValue() ).doubleValue();
    }

}
