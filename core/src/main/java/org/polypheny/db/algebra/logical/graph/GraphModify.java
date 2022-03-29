/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.algebra.logical.graph;

import java.util.List;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.GraphAlg;
import org.polypheny.db.algebra.SingleAlg;
import org.polypheny.db.algebra.core.Modify.Operation;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.graph.Graph;

public abstract class GraphModify extends SingleAlg implements GraphAlg {

    public final Operation operation;
    public final List<String> ids;
    public final List<? extends RexNode> operations;
    private final Graph graph;


    /**
     * Creates a <code>SingleRel</code>.
     *
     * @param cluster Cluster this relational expression belongs to
     * @param traits
     * @param input Input relational expression
     */
    protected GraphModify( AlgOptCluster cluster, AlgTraitSet traits, Graph graph, AlgNode input, Operation operation, List<String> ids, List<? extends RexNode> operations ) {
        super( cluster, traits, input );
        this.operation = operation;
        this.ids = ids;
        this.operations = operations;
        this.graph = graph;
    }


    @Override
    public String algCompareString() {
        return "$" + getClass().getSimpleName() +
                "$" + (ids != null ? ids.hashCode() : "[]") +
                "$" + (operations != null ? operations.hashCode() : "[]") +
                "{" + input.algCompareString() + "}";
    }


    @Override
    public NodeType getNodeType() {
        return NodeType.MODIFY;
    }


}
