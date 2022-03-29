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

import java.math.BigDecimal;
import lombok.Getter;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.Sort;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;

@Getter
public class LogicalGraphSort extends GraphSort {


    public LogicalGraphSort( AlgOptCluster cluster, AlgTraitSet traitSet, AlgCollation collation, AlgNode input, Integer skip, Integer limit ) {
        super( cluster, traitSet, input, collation,
                skip != null ? cluster.getRexBuilder().makeExactLiteral( new BigDecimal( skip ) ) : null,
                limit != null ? cluster.getRexBuilder().makeExactLiteral( new BigDecimal( limit ) ) : null );
    }


    @Override
    public String algCompareString() {
        return "$" + getClass().getSimpleName() + "$" + collation.hashCode() + "$" + input.algCompareString();
    }


    /*@Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        return new LogicalGraphSort( inputs.get( 0 ).getCluster(), traitSet, collation, inputs.get( 0 ), skip, limit );
    }*/


    @Override
    public Sort copy( AlgTraitSet traitSet, AlgNode newInput, AlgCollation newCollation, RexNode offset, RexNode fetch ) {
        return new LogicalGraphSort( newInput.getCluster(), traitSet, collation, newInput,
                offset == null ? null : ((RexLiteral) offset).getValueAs( Integer.class ),
                fetch == null ? null : ((RexLiteral) fetch).getValueAs( Integer.class ) );
    }


    public RexNode getRexLimit() {
        return this.fetch;

    }


    public RexNode getRexSkip() {
        return this.offset;
    }

}
