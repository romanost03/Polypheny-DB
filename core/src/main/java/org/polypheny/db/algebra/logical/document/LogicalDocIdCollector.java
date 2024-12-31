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

package org.polypheny.db.algebra.logical.document;

import java.util.List;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.common.IdentifierCollector;
import org.polypheny.db.algebra.core.document.DocumentAlg;
import org.polypheny.db.algebra.metadata.AlgMetadataQuery;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.plan.AlgOptCost;
import org.polypheny.db.plan.AlgPlanner;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.transaction.Transaction;

public class LogicalDocIdCollector extends IdentifierCollector implements DocumentAlg {

    protected LogicalDocIdCollector( AlgCluster cluster, AlgTraitSet traits, Transaction transaction, Entity entity, AlgNode input ) {
        super( cluster, traits, transaction, entity, input );
    }


    public static LogicalDocIdCollector create( final AlgNode input, Transaction transaction, Entity entity ) {
        final AlgCluster cluster = input.getCluster();
        final AlgTraitSet traits = input.getTraitSet();
        return new LogicalDocIdCollector( cluster, traits, transaction, entity, input );
    }


    @Override
    public DocType getDocType() {
        // ToDo TH: is this correct?
        return DocType.VALUES;
    }


    @Override
    public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
        double dRows = mq.getTupleCount( getInput() );
        return planner.getCostFactory().makeCost( dRows, 0, 0 );
    }


    @Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        return new LogicalDocIdCollector( getCluster(), traitSet, transaction, entity, sole( inputs ) );
    }

}
