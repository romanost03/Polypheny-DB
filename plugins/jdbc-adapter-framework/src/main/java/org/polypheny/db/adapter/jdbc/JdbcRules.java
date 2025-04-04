/*
 * Copyright 2019-2025 The Polypheny Project
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

package org.polypheny.db.adapter.jdbc;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.tree.Expression;
import org.polypheny.db.adapter.jdbc.rel2sql.SqlImplementor;
import org.polypheny.db.adapter.jdbc.rel2sql.SqlImplementor.Result;
import org.polypheny.db.algebra.AbstractAlgNode;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgCollationTraitDef;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgWriter;
import org.polypheny.db.algebra.InvalidAlgException;
import org.polypheny.db.algebra.SingleAlg;
import org.polypheny.db.algebra.convert.ConverterRule;
import org.polypheny.db.algebra.core.Aggregate;
import org.polypheny.db.algebra.core.AggregateCall;
import org.polypheny.db.algebra.core.AlgFactories;
import org.polypheny.db.algebra.core.Calc;
import org.polypheny.db.algebra.core.CorrelationId;
import org.polypheny.db.algebra.core.Filter;
import org.polypheny.db.algebra.core.Intersect;
import org.polypheny.db.algebra.core.Join;
import org.polypheny.db.algebra.core.JoinAlgType;
import org.polypheny.db.algebra.core.Minus;
import org.polypheny.db.algebra.core.Project;
import org.polypheny.db.algebra.core.SemiJoin;
import org.polypheny.db.algebra.core.Sort;
import org.polypheny.db.algebra.core.Union;
import org.polypheny.db.algebra.core.Values;
import org.polypheny.db.algebra.core.relational.RelModify;
import org.polypheny.db.algebra.logical.relational.LogicalRelValues;
import org.polypheny.db.algebra.metadata.AlgMdCollation;
import org.polypheny.db.algebra.metadata.AlgMdUtil;
import org.polypheny.db.algebra.metadata.AlgMetadataQuery;
import org.polypheny.db.algebra.operators.OperatorName;
import org.polypheny.db.algebra.polyalg.arguments.BooleanArg;
import org.polypheny.db.algebra.polyalg.arguments.EntityArg;
import org.polypheny.db.algebra.polyalg.arguments.ListArg;
import org.polypheny.db.algebra.polyalg.arguments.PolyAlgArgs;
import org.polypheny.db.algebra.polyalg.arguments.RexArg;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.nodes.Function;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.plan.AlgOptCost;
import org.polypheny.db.plan.AlgOptRule;
import org.polypheny.db.plan.AlgOptRuleCall;
import org.polypheny.db.plan.AlgPlanner;
import org.polypheny.db.plan.AlgTrait;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.plan.volcano.AlgSubset;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexMultisetUtil;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexOver;
import org.polypheny.db.rex.RexProgram;
import org.polypheny.db.rex.RexUtil;
import org.polypheny.db.rex.RexVisitorImpl;
import org.polypheny.db.schema.document.DocumentRules;
import org.polypheny.db.schema.trait.ModelTrait;
import org.polypheny.db.schema.types.ModifiableTable;
import org.polypheny.db.sql.language.SqlAggFunction;
import org.polypheny.db.sql.language.SqlDialect;
import org.polypheny.db.sql.language.SqlFunction;
import org.polypheny.db.sql.language.fun.SqlItemOperator;
import org.polypheny.db.tools.AlgBuilderFactory;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.ImmutableBitSet;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.Quadruple;
import org.polypheny.db.util.Triple;
import org.polypheny.db.util.UnsupportedRexCallVisitor;
import org.polypheny.db.util.ValidatorUtil;
import org.polypheny.db.util.trace.PolyphenyDbTrace;
import org.slf4j.Logger;


/**
 * Rules and algebra operators for {@link JdbcConvention} calling convention.
 */
public class JdbcRules {

    private JdbcRules() {
    }


    protected static final Logger LOGGER = PolyphenyDbTrace.getPlannerTracer();


    public static List<AlgOptRule> rules( JdbcConvention out ) {
        return rules( out, AlgFactories.LOGICAL_BUILDER );
    }


    public static List<AlgOptRule> rules( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
        return ImmutableList.of(
                new JdbcToEnumerableConverterRule( out, algBuilderFactory ),
                new JdbcJoinRule( out, algBuilderFactory ),
                new JdbcCalcRule( out, algBuilderFactory ),
                new JdbcProjectRule( out, algBuilderFactory ),
                new JdbcFilterRule( out, algBuilderFactory ),
                new JdbcAggregateRule( out, algBuilderFactory ),
                new JdbcSortRule( out, algBuilderFactory ),
                new JdbcUnionRule( out, algBuilderFactory ),
                new JdbcIntersectRule( out, algBuilderFactory ),
                new JdbcMinusRule( out, algBuilderFactory ),
                new JdbcTableModificationRule( out, algBuilderFactory ),
                new JdbcValuesRule( out, algBuilderFactory ) );
    }


    /**
     * Abstract base class for rule that converts to JDBC.
     */
    abstract static class JdbcConverterRule extends ConverterRule {

        protected final JdbcConvention out;


        <R extends AlgNode> JdbcConverterRule(
                Class<R> clazz,
                Predicate<? super R> predicate,
                AlgTrait<?> in,
                JdbcConvention out,
                AlgBuilderFactory algBuilderFactory,
                String description ) {
            super( clazz, predicate, in, out, algBuilderFactory, description );
            this.out = out;
        }

    }


    /**
     * Rule that converts a join to JDBC.
     */
    public static class JdbcJoinRule extends JdbcConverterRule {


        /**
         * Creates a JdbcJoinRule.
         */
        public JdbcJoinRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super(
                    Join.class,
                    join -> (
                            (!geoFunctionInJoin( join ) || supportsGeoFunctionInJoin( out.dialect, join ))
                                    && out.dialect.supportsJoin( join )),
                    Convention.NONE,
                    out,
                    algBuilderFactory,
                    "JdbcJoinRule." + out );
        }


        private static boolean geoFunctionInJoin( Join join ) {
            CheckingGeoFunctionVisitor visitor = new CheckingGeoFunctionVisitor();
            for ( RexNode node : join.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean supportsGeoFunctionInJoin( SqlDialect dialect, Join join ) {
            CheckingGeoFunctionSupportVisitor visitor = new CheckingGeoFunctionSupportVisitor( dialect );
            for ( RexNode node : join.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.supportsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            if ( alg instanceof SemiJoin ) {
                // It's not possible to convert semi-joins. They have fewer columns than regular joins.
                return null;
            }
            return convert( (Join) alg, true );
        }


        /**
         * Converts a {@code Join} into a {@code JdbcJoin}.
         *
         * @param join Join operator to convert
         * @param convertInputTraits Whether to convert input to {@code join}'s JDBC convention
         * @return A new JdbcJoin
         */
        public AlgNode convert( Join join, boolean convertInputTraits ) {
            final List<AlgNode> newInputs = new ArrayList<>();
            for ( AlgNode input : join.getInputs() ) {
                if ( convertInputTraits && input.getConvention() != getOutTrait() ) {
                    input = convert( input, input.getTraitSet().replace( out ) );
                }
                newInputs.add( input );
            }
            if ( convertInputTraits && !canJoinOnCondition( join.getCondition() ) ) {
                return null;
            }
            if ( containsAggregateSubquery( join.getLeft() ) || containsAggregateSubquery( join.getRight() ) ) {
                return null;
            }
            try {
                return new JdbcJoin(
                        join.getCluster(),
                        join.getTraitSet().replace( out ),
                        newInputs.get( 0 ),
                        newInputs.get( 1 ),
                        join.getCondition(),
                        join.getVariablesSet(),
                        join.getJoinType() );
            } catch ( InvalidAlgException e ) {
                LOGGER.debug( e.toString() );
                return null;
            }
        }


        private boolean containsAggregateSubquery( AlgNode input ) {
            return input instanceof Aggregate || (input instanceof AlgSubset subset && subset.getOriginal() instanceof Aggregate);
        }


        /**
         * Returns whether a condition is supported by {@link JdbcJoin}.
         * <p>
         * Corresponds to the capabilities of {@link SqlImplementor#convertConditionToSqlNode}.
         *
         * @param node Condition
         * @return Whether condition is supported
         */
        private boolean canJoinOnCondition( RexNode node ) {
            final List<RexNode> operands;
            switch ( node.getKind() ) {
                case AND, OR, GEO:
                    operands = ((RexCall) node).getOperands();
                    for ( RexNode operand : operands ) {
                        if ( !canJoinOnCondition( operand ) ) {
                            return false;
                        }
                    }
                    return true;

                case EQUALS:
                case IS_NOT_DISTINCT_FROM:
                case NOT_EQUALS:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUAL:
                case LESS_THAN:
                case LESS_THAN_OR_EQUAL:
                    operands = ((RexCall) node).getOperands();
                    if ( (operands.get( 0 ) instanceof RexIndexRef) && (operands.get( 1 ) instanceof RexIndexRef) ) {
                        return true;
                    }
                    // fall through

                default:
                    return false;
            }
        }


    }


    /**
     * Join operator implemented in JDBC convention.
     */
    public static class JdbcJoin extends Join implements JdbcAlg {

        /**
         * Creates a JdbcJoin.
         */
        public JdbcJoin(
                AlgCluster cluster,
                AlgTraitSet traitSet,
                AlgNode left,
                AlgNode right,
                RexNode condition,
                Set<CorrelationId> variablesSet,
                JoinAlgType joinType ) throws InvalidAlgException {
            super( cluster, traitSet, left, right, condition, variablesSet, joinType );
        }


        public static JdbcJoin create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            Triple<RexNode, Set<CorrelationId>, JoinAlgType> extracted = extractArgs( args );
            try {
                return new JdbcJoin( cluster, children.get( 0 ).getTraitSet(), children.get( 0 ), children.get( 1 ), extracted.left, extracted.middle, extracted.right );
            } catch ( InvalidAlgException e ) {
                throw new RuntimeException( e );
            }
        }


        @Override
        public JdbcJoin copy( AlgTraitSet traitSet, RexNode condition, AlgNode left, AlgNode right, JoinAlgType joinType, boolean semiJoinDone ) {
            try {
                return new JdbcJoin( getCluster(), traitSet, left, right, condition, variablesSet, joinType );
            } catch ( InvalidAlgException e ) {
                // Semantic error not possible. Must be a bug. Convert to internal error.
                throw new AssertionError( e );
            }
        }


        @Override
        public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
            // We always "build" the
            Optional<Double> rowCount = mq.getTupleCount( this );
            if ( rowCount.isEmpty() ) {
                return planner.getCostFactory().makeInfiniteCost();
            }

            return planner.getCostFactory().makeCost( rowCount.get(), 0, 0 );
        }


        @Override
        public double estimateTupleCount( AlgMetadataQuery mq ) {
            final double leftRowCount = left.estimateTupleCount( mq );
            final double rightRowCount = right.estimateTupleCount( mq );
            return Math.max( leftRowCount, rightRowCount );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link Calc} to an {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcCalc}.
     */
    private static class JdbcCalcRule extends JdbcConverterRule {

        /**
         * Creates a JdbcCalcRule.
         */
        private JdbcCalcRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Calc.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcCalcRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Calc calc = (Calc) alg;

            // If there's a multiset, let FarragoMultisetSplitter work on it first.
            if ( RexMultisetUtil.containsMultiset( calc.getProgram() ) ) {
                return null;
            }

            return new JdbcCalc(
                    alg.getCluster(),
                    alg.getTraitSet().replace( out ),
                    convert( calc.getInput(), calc.getTraitSet().replace( out ) ),
                    calc.getProgram() );
        }

    }


    /**
     * Calc operator implemented in JDBC convention.
     *
     * @see Calc
     */
    public static class JdbcCalc extends SingleAlg implements JdbcAlg {

        private final RexProgram program;


        public JdbcCalc( AlgCluster cluster, AlgTraitSet traitSet, AlgNode input, RexProgram program ) {
            super( cluster, traitSet, input );
            assert getConvention() instanceof JdbcConvention;
            this.program = program;
            this.rowType = program.getOutputRowType();
        }


        public static JdbcCalc create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            return new JdbcCalc( cluster, children.get( 0 ).getTraitSet(), children.get( 0 ),
                    Calc.getProgramFromArgs( args, children.get( 0 ), cluster.getRexBuilder() ) );
        }


        @Override
        public AlgWriter explainTerms( AlgWriter pw ) {
            return program.explainCalc( super.explainTerms( pw ) );
        }


        @Override
        public double estimateTupleCount( AlgMetadataQuery mq ) {
            return AlgMdUtil.estimateFilteredRows( getInput(), program, mq );
        }


        @Override
        public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
            double dRows = mq.getTupleCount( this ).orElse( Double.MAX_VALUE );
            double dCpu = mq.getTupleCount( getInput() ).orElse( Double.MAX_VALUE ) * program.getExprCount();
            double dIo = 0;
            return planner.getCostFactory().makeCost( dRows, dCpu, dIo );
        }


        @Override
        public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
            return new JdbcCalc( getCluster(), traitSet, sole( inputs ), program );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }


        @Override
        public String algCompareString() {
            return this.getClass().getSimpleName() + "$" +
                    input.algCompareString() + "$" +
                    (program != null ? program.toString() : "") + "&";
        }

    }


    /**
     * Rule to convert a {@link Project} to an {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcProject}.
     */
    public static class JdbcProjectRule extends JdbcConverterRule {

        /**
         * Creates a JdbcProjectRule.
         */
        public JdbcProjectRule( final JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Project.class, project -> supports( out, project ),
                    Convention.NONE, out, algBuilderFactory, JdbcProjectRule.class.getSimpleName() + "." + out );
        }


        private static boolean supports( JdbcConvention out, Project project ) {
            return (out.dialect.supportsWindowFunctions()
                    || !RexOver.containsOver( project.getProjects(), null ))
                    && !userDefinedFunctionInProject( project )
                    && !knnFunctionInProject( project )
                    && !multimediaFunctionInProject( project )
                    && !contains( project, List.of( OperatorName.INITCAP ) )
                    && (!geoFunctionInProject( project ) || supportsGeoFunction( out.dialect, project ))
                    && !DocumentRules.containsJson( project )
                    && !DocumentRules.containsDocument( project )
                    && !UnsupportedRexCallVisitor.containsModelItem( project.getProjects() )
                    && out.dialect.supportsProject( project )
                    && (out.dialect.supportsNestedArrays() || !itemOperatorInProject( project ));

        }


        private static boolean contains( Project project, List<OperatorName> operatorNames ) {
            for ( RexNode node : project.getChildExps() ) {
                if ( node instanceof RexCall call ) {
                    if ( operatorNames.contains( call.op.getOperatorName() ) ) {
                        return true;
                    }
                }
            }
            return false;
        }


        private static boolean userDefinedFunctionInProject( Project project ) {
            CheckingUserDefinedFunctionVisitor visitor = new CheckingUserDefinedFunctionVisitor();
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsUserDefinedFunction() ) {
                    return true;
                }
            }
            return false;
        }


        // TODO js(knn): Make sure this is not just a hotfix.
        private static boolean knnFunctionInProject( Project project ) {
            CheckingKnnFunctionVisitor visitor = new CheckingKnnFunctionVisitor();
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsKnnFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean multimediaFunctionInProject( Project project ) {
            CheckingMultimediaFunctionVisitor visitor = new CheckingMultimediaFunctionVisitor();
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsMultimediaFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean supportsGeoFunction( SqlDialect dialect, Project project ) {
            CheckingGeoFunctionSupportVisitor visitor = new CheckingGeoFunctionSupportVisitor( dialect );
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.supportsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean geoFunctionInProject( AlgNode project ) {
            CheckingGeoFunctionVisitor visitor = new CheckingGeoFunctionVisitor();
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean itemOperatorInProject( Project project ) {
            CheckingItemOperatorVisitor visitor = new CheckingItemOperatorVisitor();
            for ( RexNode node : project.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsItemOperator() ) {
                    return true;
                }
            }
            return false;
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Project project = (Project) alg;

            return new JdbcProject(
                    alg.getCluster(),
                    alg.getTraitSet().replace( out ),
                    convert( project.getInput(), project.getInput().getTraitSet().replace( out ) ),
                    project.getProjects(),
                    project.getTupleType() );
        }

    }


    /**
     * Implementation of {@link Project} in {@link JdbcConvention jdbc calling convention}.
     */
    public static class JdbcProject extends Project implements JdbcAlg {

        public JdbcProject( AlgCluster cluster, AlgTraitSet traitSet, AlgNode input, List<? extends RexNode> projects, AlgDataType rowType ) {
            super( cluster, traitSet, input, projects, rowType );
            assert getConvention() instanceof JdbcConvention;
        }


        public static JdbcProject create( final AlgNode input, final List<? extends RexNode> projects, AlgDataType rowType ) {
            final AlgCluster cluster = input.getCluster();
            final AlgMetadataQuery mq = cluster.getMetadataQuery();
            final AlgTraitSet traitSet = input.getTraitSet().replaceIfs( AlgCollationTraitDef.INSTANCE, () -> AlgMdCollation.project( mq, input, projects ) );
            return new JdbcProject( cluster, traitSet, input, projects, rowType );
        }


        static AlgNode create( AlgNode child, List<? extends RexNode> projects, List<String> fieldNames ) {
            final AlgCluster cluster = child.getCluster();
            final AlgDataType rowType = RexUtil.createStructType( cluster.getTypeFactory(), projects, fieldNames, ValidatorUtil.F_SUGGESTER );
            return create( child, projects, rowType );
        }


        public static AlgNode create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            ListArg<RexArg> projects = args.getListArg( 0, RexArg.class );
            return create( children.get( 0 ), projects.map( RexArg::getNode ), projects.map( RexArg::getAlias ) );
        }


        @Override
        public JdbcProject copy( AlgTraitSet traitSet, AlgNode input, List<RexNode> projects, AlgDataType rowType ) {
            return new JdbcProject( getCluster(), traitSet, input, projects, rowType );
        }


        @Override
        public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
            return super.computeSelfCost( planner, mq ).multiplyBy( JdbcConvention.COST_MULTIPLIER );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link Filter} to an {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcFilter}.
     */
    public static class JdbcFilterRule extends JdbcConverterRule {


        /**
         * Creates a JdbcFilterRule.
         */
        public JdbcFilterRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Filter.class,
                    filter -> (
                            !userDefinedFunctionInFilter( filter )
                                    && !containUnsupportedArray( filter, out )
                                    && !knnFunctionInFilter( filter )
                                    && !multimediaFunctionInFilter( filter )
                                    && (!geoFunctionInFilter( filter ) || supportsGeoFunctionInFilter( out.dialect, filter ))
                                    && !DocumentRules.containsJson( filter )
                                    && !DocumentRules.containsDocument( filter )
                                    && out.dialect.supportsFilter( filter )
                                    && (out.dialect.supportsNestedArrays() || (!itemOperatorInFilter( filter ) && isStringComparableArrayType( filter )))),
                    Convention.NONE, out, algBuilderFactory, JdbcFilterRule.class.getSimpleName() + "." + out );
        }


        private static boolean containUnsupportedArray( Filter filter, JdbcConvention out ) {
            if ( out.dialect.supportsNestedArrays() ) {
                return false;
            }
            UnsupportedIdentifierVisitor visitor = new UnsupportedIdentifierVisitor( type -> type.getPolyType() == PolyType.ARRAY && !out.dialect.supportsNestedArrays() );
            filter.getCondition().accept( visitor );
            return visitor.isContainsUnsupportedIdentifier();
        }


        private static boolean userDefinedFunctionInFilter( Filter filter ) {
            CheckingUserDefinedFunctionVisitor visitor = new CheckingUserDefinedFunctionVisitor();
            filter.getCondition().accept( visitor );
            return visitor.containsUserDefinedFunction();
        }


        private static boolean knnFunctionInFilter( Filter filter ) {
            CheckingKnnFunctionVisitor visitor = new CheckingKnnFunctionVisitor();
            for ( RexNode node : filter.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsKnnFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean multimediaFunctionInFilter( Filter filter ) {
            CheckingMultimediaFunctionVisitor visitor = new CheckingMultimediaFunctionVisitor();
            for ( RexNode node : filter.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsMultimediaFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean geoFunctionInFilter( Filter filter ) {
            CheckingGeoFunctionVisitor visitor = new CheckingGeoFunctionVisitor();
            for ( RexNode node : filter.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean supportsGeoFunctionInFilter( SqlDialect dialect, Filter filter ) {
            CheckingGeoFunctionSupportVisitor visitor = new CheckingGeoFunctionSupportVisitor( dialect );
            for ( RexNode node : filter.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.supportsGeoFunction() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean itemOperatorInFilter( Filter filter ) {
            CheckingItemOperatorVisitor visitor = new CheckingItemOperatorVisitor();
            for ( RexNode node : filter.getChildExps() ) {
                node.accept( visitor );
                if ( visitor.containsItemOperator() ) {
                    return true;
                }
            }
            return false;
        }


        private static boolean isStringComparableArrayType( Filter filter ) {
            for ( AlgDataTypeField dataTypeField : filter.getTupleType().getFields() ) {
                if ( dataTypeField.getType().getPolyType() == PolyType.ARRAY ) {
                    switch ( dataTypeField.getType().getComponentType().getPolyType() ) {
                        case BOOLEAN:
                        case TINYINT:
                        case SMALLINT:
                        case INTEGER:
                        case BIGINT:
                        case CHAR:
                        case VARCHAR:
                            break;
                        default:
                            return false;
                    }
                }
            }
            return true;
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Filter filter = (Filter) alg;

            return new JdbcFilter(
                    alg.getCluster(),
                    alg.getTraitSet().replace( out ).replace( ModelTrait.RELATIONAL ),
                    convert( filter.getInput(), filter.getInput().getTraitSet().replace( out ) ),
                    filter.getCondition() );
        }


        private static class UnsupportedIdentifierVisitor extends RexVisitorImpl<Void> {


            private final java.util.function.Function<AlgDataType, Boolean> isApplicable;
            @Getter
            private boolean containsUnsupportedIdentifier = false;


            protected UnsupportedIdentifierVisitor( java.util.function.Function<AlgDataType, Boolean> isApplicable ) {
                super( true );
                this.isApplicable = isApplicable;
            }


            @Override
            public Void visitIndexRef( RexIndexRef inputRef ) {
                if ( isApplicable.apply( inputRef.type ) ) {
                    containsUnsupportedIdentifier = true;
                }
                return super.visitIndexRef( inputRef );
            }

        }

    }


    /**
     * Implementation of {@link Filter} in {@link JdbcConvention jdbc calling convention}.
     */
    public static class JdbcFilter extends Filter implements JdbcAlg {

        public JdbcFilter( AlgCluster cluster, AlgTraitSet traitSet, AlgNode input, RexNode condition ) {
            super( cluster, traitSet, input, condition );
            assert getConvention() instanceof JdbcConvention;
        }


        public static JdbcFilter create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            RexArg condition = args.getArg( "condition", RexArg.class );
            return new JdbcFilter( cluster, children.get( 0 ).getTraitSet(), children.get( 0 ), condition.getNode() );
        }


        @Override
        public JdbcFilter copy( AlgTraitSet traitSet, AlgNode input, RexNode condition ) {
            return new JdbcFilter( getCluster(), traitSet, input, condition );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link org.polypheny.db.algebra.core.Aggregate} to a
     * {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcAggregate}.
     */
    public static class JdbcAggregateRule extends JdbcConverterRule {

        /**
         * Creates a JdbcAggregateRule.
         */
        public JdbcAggregateRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Aggregate.class, out.dialect::supportsAggregate, Convention.NONE, out, algBuilderFactory, "JdbcAggregateRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Aggregate agg = (Aggregate) alg;
            if ( agg.getGroupSets().size() != 1 ) {
                // GROUPING SETS not supported; see
                // [POLYPHENYDB-734] Push GROUPING SETS to underlying SQL via JDBC adapter
                return null;
            }
            final AlgTraitSet traitSet = agg.getTraitSet().replace( out );
            try {
                return new JdbcAggregate(
                        alg.getCluster(),
                        traitSet,
                        convert( agg.getInput(), out ),
                        agg.indicator,
                        agg.getGroupSet(),
                        agg.getGroupSets(),
                        agg.getAggCallList() );
            } catch ( InvalidAlgException e ) {
                LOGGER.debug( e.toString() );
                return null;
            }
        }

    }


    /**
     * Returns whether this JDBC data source can implement a given aggregate function.
     */
    private static boolean canImplement( SqlAggFunction aggregation, SqlDialect sqlDialect ) {
        return sqlDialect.supportsAggregateFunction( aggregation.getKind() );
    }


    /**
     * Aggregate operator implemented in JDBC convention.
     */
    public static class JdbcAggregate extends Aggregate implements JdbcAlg {

        public JdbcAggregate(
                AlgCluster cluster,
                AlgTraitSet traitSet,
                AlgNode input,
                boolean indicator,
                ImmutableBitSet groupSet,
                List<ImmutableBitSet> groupSets,
                List<AggregateCall> aggCalls )
                throws InvalidAlgException {
            super( cluster, traitSet, input, indicator, groupSet, groupSets, aggCalls );
            assert getConvention() instanceof JdbcConvention;
            assert this.groupSets.size() == 1 : "Grouping sets not supported";
            assert !this.indicator;
            final SqlDialect dialect = ((JdbcConvention) getConvention()).dialect;
            for ( AggregateCall aggCall : aggCalls ) {
                if ( !canImplement( (SqlAggFunction) aggCall.getAggregation(), dialect ) ) {
                    throw new InvalidAlgException( "cannot implement aggregate function " + aggCall.getAggregation() );
                }
            }
        }


        public static JdbcAggregate create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            Triple<ImmutableBitSet, List<ImmutableBitSet>, List<AggregateCall>> extracted = extractArgs( args );
            try {
                return new JdbcAggregate( cluster, children.get( 0 ).getTraitSet(), children.get( 0 ), false,
                        extracted.left, extracted.middle, extracted.right );
            } catch ( InvalidAlgException e ) {
                throw new RuntimeException( e );
            }
        }


        @Override
        public JdbcAggregate copy(
                AlgTraitSet traitSet,
                AlgNode input,
                boolean indicator,
                ImmutableBitSet groupSet,
                List<ImmutableBitSet> groupSets,
                List<AggregateCall> aggCalls ) {
            try {
                return new JdbcAggregate( getCluster(), traitSet, input, indicator, groupSet, groupSets, aggCalls );
            } catch ( InvalidAlgException e ) {
                // Semantic error not possible. Must be a bug. Convert to internal error.
                throw new AssertionError( e );
            }
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link Sort} to an {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcSort}.
     */
    public static class JdbcSortRule extends JdbcConverterRule {

        /**
         * Creates a JdbcSortRule.
         */
        public JdbcSortRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Sort.class, out.dialect::supportsSort, Convention.NONE, out, algBuilderFactory, "JdbcSortRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            return convert( (Sort) alg, true );
        }


        /**
         * Converts a {@code Sort} into a {@code JdbcSort}.
         *
         * @param sort Sort operator to convert
         * @param convertInputTraits Whether to convert input to {@code sort}'s JDBC convention
         * @return A new JdbcSort
         */
        public AlgNode convert( Sort sort, boolean convertInputTraits ) {
            final AlgTraitSet traitSet = sort.getTraitSet().replace( out );

            final AlgNode input;
            if ( convertInputTraits ) {
                final AlgTraitSet inputTraitSet = sort.getInput().getTraitSet().replace( out );
                input = convert( sort.getInput(), inputTraitSet );
            } else {
                input = sort.getInput();
            }

            return new JdbcSort( sort.getCluster(), traitSet, input, sort.getCollation(), sort.offset, sort.fetch );
        }

    }


    /**
     * Sort operator implemented in JDBC convention.
     */
    public static class JdbcSort extends Sort implements JdbcAlg {

        public JdbcSort( AlgCluster cluster, AlgTraitSet traitSet, AlgNode input, AlgCollation collation, RexNode offset, RexNode fetch ) {
            super( cluster, traitSet, input, collation, null, offset, fetch );
            assert getConvention() instanceof JdbcConvention;
            assert getConvention() == input.getConvention();
        }


        public static JdbcSort create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            Triple<AlgCollation, RexNode, RexNode> extracted = extractArgs( args );
            return new JdbcSort( cluster, children.get( 0 ).getTraitSet(), children.get( 0 ), extracted.left, extracted.middle, extracted.right );
        }


        @Override
        public JdbcSort copy( AlgTraitSet traitSet, AlgNode newInput, AlgCollation newCollation, ImmutableList<RexNode> nodes, RexNode offset, RexNode fetch ) {
            return new JdbcSort( getCluster(), traitSet, newInput, newCollation, offset, fetch );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert an {@link org.polypheny.db.algebra.core.Union} to a
     * {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcUnion}.
     */
    public static class JdbcUnionRule extends JdbcConverterRule {

        /**
         * Creates a JdbcUnionRule.
         */
        public JdbcUnionRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Union.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcUnionRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Union union = (Union) alg;
            final AlgTraitSet traitSet = union.getTraitSet().replace( out );
            return new JdbcUnion( alg.getCluster(), traitSet, AlgOptRule.convertList( union.getInputs(), out ), union.all );
        }

    }


    /**
     * Union operator implemented in JDBC convention.
     */
    public static class JdbcUnion extends Union implements JdbcAlg {

        public JdbcUnion( AlgCluster cluster, AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            super( cluster, traitSet, inputs, all );
        }


        public static JdbcUnion create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            return new JdbcUnion( cluster, children.get( 0 ).getTraitSet(), children, args.getArg( "all", BooleanArg.class ).toBool() );
        }


        @Override
        public JdbcUnion copy( AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            return new JdbcUnion( getCluster(), traitSet, inputs, all );
        }


        @Override
        public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
            return super.computeSelfCost( planner, mq ).multiplyBy( .1 );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link org.polypheny.db.algebra.core.Intersect} to a
     * {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcIntersect}.
     */
    public static class JdbcIntersectRule extends JdbcConverterRule {

        /**
         * Creates a JdbcIntersectRule.
         */
        private JdbcIntersectRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Intersect.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcIntersectRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Intersect intersect = (Intersect) alg;
            if ( intersect.all ) {
                return null; // INTERSECT ALL not implemented
            }
            final AlgTraitSet traitSet = intersect.getTraitSet().replace( out );
            return new JdbcIntersect( alg.getCluster(), traitSet, AlgOptRule.convertList( intersect.getInputs(), out ), false );
        }

    }


    /**
     * Intersect operator implemented in JDBC convention.
     */
    public static class JdbcIntersect extends Intersect implements JdbcAlg {

        public JdbcIntersect( AlgCluster cluster, AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            super( cluster, traitSet, inputs, all );
            assert !all;
        }


        public static JdbcIntersect create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            return new JdbcIntersect( cluster, children.get( 0 ).getTraitSet(), children, args.getArg( "all", BooleanArg.class ).toBool() );
        }


        @Override
        public JdbcIntersect copy( AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            return new JdbcIntersect( getCluster(), traitSet, inputs, all );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule to convert a {@link Minus} to a {@link org.polypheny.db.adapter.jdbc.JdbcRules.JdbcMinus}.
     */
    public static class JdbcMinusRule extends JdbcConverterRule {

        /**
         * Creates a JdbcMinusRule.
         */
        private JdbcMinusRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( Minus.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcMinusRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final Minus minus = (Minus) alg;
            if ( minus.all ) {
                return null; // EXCEPT ALL not implemented
            }
            final AlgTraitSet traitSet = alg.getTraitSet().replace( out );
            return new JdbcMinus( alg.getCluster(), traitSet, AlgOptRule.convertList( minus.getInputs(), out ), false );
        }

    }


    /**
     * Minus operator implemented in JDBC convention.
     */
    public static class JdbcMinus extends Minus implements JdbcAlg {

        public JdbcMinus( AlgCluster cluster, AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            super( cluster, traitSet, inputs, all );
            assert !all;
        }


        public static JdbcMinus create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            return new JdbcMinus( cluster, children.get( 0 ).getTraitSet(), children, args.getArg( "all", BooleanArg.class ).toBool() );
        }


        @Override
        public JdbcMinus copy( AlgTraitSet traitSet, List<AlgNode> inputs, boolean all ) {
            return new JdbcMinus( getCluster(), traitSet, inputs, all );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule that converts a table-modification to JDBC.
     */
    public static class JdbcTableModificationRule extends JdbcConverterRule {

        /**
         * Creates a JdbcTableModificationRule.
         */
        private JdbcTableModificationRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( RelModify.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcTableModificationRule." + out );
        }


        @Override
        public boolean matches( AlgOptRuleCall call ) {
            final RelModify<?> modify = call.alg( 0 );
            if ( modify.getEntity().unwrap( JdbcTable.class ).isPresent() ) {
                JdbcTable table = modify.getEntity().unwrap( JdbcTable.class ).get();
                return out.getJdbcSchema() == table.getSchema();
            }
            return false;
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            final RelModify<?> modify = (RelModify<?>) alg;
            Optional<ModifiableTable> oModifiableTable = modify.getEntity().unwrap( ModifiableTable.class );
            if ( oModifiableTable.isEmpty() ) {
                return null;
            }
            final AlgTraitSet traitSet = modify.getTraitSet().replace( out );
            return new JdbcTableModify(
                    modify.getCluster(),
                    traitSet,
                    modify.getEntity().unwrapOrThrow( JdbcTable.class ),
                    AlgOptRule.convert( modify.getInput(), traitSet ),
                    modify.getOperation(),
                    modify.getUpdateColumns(),
                    modify.getSourceExpressions(),
                    modify.isFlattened() );
        }

    }


    /**
     * Table-modification operator implemented in JDBC convention.
     */
    public static class JdbcTableModify extends RelModify<JdbcTable> implements JdbcAlg {


        public JdbcTableModify(
                AlgCluster cluster,
                AlgTraitSet traitSet,
                JdbcTable table,
                AlgNode input,
                Operation operation,
                List<String> updateColumnList,
                List<? extends RexNode> sourceExpressionList,
                boolean flattened ) {
            super( cluster, traitSet, table, input, operation, updateColumnList, sourceExpressionList, flattened );
            assert input.getConvention() instanceof JdbcConvention;
            assert getConvention() instanceof JdbcConvention;
            Optional<ModifiableTable> oModifiableTable = table.unwrap( ModifiableTable.class );
            if ( oModifiableTable.isEmpty() ) {
                throw new AssertionError(); // TODO: user error in validator
            }
            Expression expression = table.asExpression( Queryable.class );
            if ( expression == null ) {
                throw new AssertionError(); // TODO: user error in validator
            }
        }


        public static JdbcTableModify create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            EntityArg entity = args.getArg( "table", EntityArg.class );
            Quadruple<Operation, List<String>, List<? extends RexNode>, Boolean> extracted = extractArgs( args );
            return new JdbcTableModify( cluster, children.get( 0 ).getTraitSet(), (JdbcTable) entity.getEntity(), children.get( 0 ),
                    extracted.a, extracted.b, extracted.c, extracted.d );
        }


        @Override
        public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
            double cost = super.computeSelfCost( planner, mq ).getCosts();
            return super.computeSelfCost( planner, mq ).multiplyBy( .1 );
        }


        @Override
        public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
            return new JdbcTableModify(
                    getCluster(),
                    traitSet,
                    entity,
                    AbstractAlgNode.sole( inputs ),
                    getOperation(),
                    getUpdateColumns(),
                    getSourceExpressions(),
                    isFlattened() );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Rule that converts a values operator to JDBC.
     */
    public static class JdbcValuesRule extends JdbcConverterRule {

        /**
         * Creates a JdbcValuesRule.
         */
        private JdbcValuesRule( JdbcConvention out, AlgBuilderFactory algBuilderFactory ) {
            super( LogicalRelValues.class, (Predicate<AlgNode>) r -> true, Convention.NONE, out, algBuilderFactory, "JdbcValuesRule." + out );
        }


        @Override
        public AlgNode convert( AlgNode alg ) {
            Values values = (Values) alg;
            return new JdbcValues( values.getCluster(), values.getTupleType(), values.getTuples(), values.getTraitSet().replace( out ) );
        }

    }


    /**
     * Values operator implemented in JDBC convention.
     */
    public static class JdbcValues extends Values implements JdbcAlg {

        JdbcValues( AlgCluster cluster, AlgDataType rowType, ImmutableList<ImmutableList<RexLiteral>> tuples, AlgTraitSet traitSet ) {
            super( cluster, rowType, tuples, traitSet );
        }


        public static JdbcValues create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
            Pair<AlgDataType, ImmutableList<ImmutableList<RexLiteral>>> extracted = extractArgs( args, cluster );
            return new JdbcValues( cluster, extracted.left, extracted.right, cluster.traitSetOf( ModelTrait.RELATIONAL ) );
        }


        @Override
        public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
            assert inputs.isEmpty();
            return new JdbcValues( getCluster(), rowType, tuples, traitSet );
        }


        @Override
        public Result implement( JdbcImplementor implementor ) {
            return implementor.implement( this );
        }

    }


    /**
     * Visitor for checking whether part of projection is a user defined function or not
     */
    private static class CheckingUserDefinedFunctionVisitor extends RexVisitorImpl<Void> {

        private boolean containsUsedDefinedFunction = false;


        CheckingUserDefinedFunctionVisitor() {
            super( true );
        }


        public boolean containsUserDefinedFunction() {
            return containsUsedDefinedFunction;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof Function && ((SqlFunction) operator).getFunctionCategory().isUserDefined() ) {
                containsUsedDefinedFunction = true;
            }
            return super.visitCall( call );
        }

    }


    private static class CheckingKnnFunctionVisitor extends RexVisitorImpl<Void> {

        private boolean containsKnnFunction = false;


        CheckingKnnFunctionVisitor() {
            super( true );
        }


        public boolean containsKnnFunction() {
            return containsKnnFunction;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof Function && ((SqlFunction) operator).getFunctionCategory().isKnn() ) {
                containsKnnFunction = true;
            }
            return super.visitCall( call );
        }

    }


    private static class CheckingMultimediaFunctionVisitor extends RexVisitorImpl<Void> {

        private boolean containsMultimediaFunction = false;


        CheckingMultimediaFunctionVisitor() {
            super( true );
        }


        public boolean containsMultimediaFunction() {
            return containsMultimediaFunction;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof Function && ((SqlFunction) operator).getFunctionCategory().isMultimedia() ) {
                containsMultimediaFunction = true;
            }
            return super.visitCall( call );
        }

    }


    private static class CheckingGeoFunctionVisitor extends RexVisitorImpl<Void> {

        private boolean containsGeoFunction = false;


        CheckingGeoFunctionVisitor() {
            super( true );
        }


        public boolean containsGeoFunction() {
            return containsGeoFunction;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof Function && ((SqlFunction) operator).getFunctionCategory().isGeo() ) {
                containsGeoFunction = true;
            }
            return super.visitCall( call );
        }

    }


    private static class CheckingGeoFunctionSupportVisitor extends RexVisitorImpl<Void> {

        private boolean supportsGeoFunction = false;
        private SqlDialect dialect;


        CheckingGeoFunctionSupportVisitor( SqlDialect dialect ) {
            super( true );
            this.dialect = dialect;
        }


        public boolean supportsGeoFunction() {
            return supportsGeoFunction;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof Function && ((SqlFunction) operator).getFunctionCategory().isGeo() && dialect.supportedGeoFunctions().contains( operator.getOperatorName() ) ) {
                supportsGeoFunction = true;
            }
            return super.visitCall( call );
        }

    }


    private static class CheckingItemOperatorVisitor extends RexVisitorImpl<Void> {

        private boolean containsItemOperator = false;


        CheckingItemOperatorVisitor() {
            super( true );
        }


        public boolean containsItemOperator() {
            return containsItemOperator;
        }


        @Override
        public Void visitCall( RexCall call ) {
            Operator operator = call.getOperator();
            if ( operator instanceof SqlItemOperator ) {
                containsItemOperator = true;
            }
            return super.visitCall( call );
        }

    }

}
