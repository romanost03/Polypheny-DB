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

import org.polypheny.db.algebra.AbstractAlgNode;
import org.polypheny.db.algebra.GraphAlg;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgTraitSet;

public abstract class GraphValues extends AbstractAlgNode implements GraphAlg {

    /**
     * Creates an <code>AbstractRelNode</code>.
     *
     * @param cluster
     * @param traitSet
     */
    public GraphValues( AlgOptCluster cluster, AlgTraitSet traitSet ) {
        super( cluster, traitSet );
    }


    @Override
    public NodeType getNodeType() {
        return NodeType.VALUES;
    }

}
