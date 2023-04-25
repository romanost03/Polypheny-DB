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

package org.polypheny.db.algebra.core.document;

import java.util.List;
import lombok.Getter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.core.common.Modify;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.entity.CatalogEntity;
import org.polypheny.db.plan.AlgOptUtil;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.rex.RexNode;


public abstract class DocumentModify<E extends CatalogEntity> extends Modify<E> implements DocumentAlg {

    @Getter
    public final Operation operation;
    @Getter
    private final List<String> keys;
    @Getter
    private final List<RexNode> updates;


    /**
     * Creates a {@link DocumentModify}.
     * {@link org.polypheny.db.schema.ModelTrait#DOCUMENT} node, which modifies a collection.
     */
    protected DocumentModify( AlgTraitSet traits, E collection, AlgNode input, Operation operation, List<String> keys, List<RexNode> updates ) {
        super( input.getCluster(), input.getTraitSet(), collection, input );
        this.operation = operation;
        this.keys = keys;
        this.updates = updates;
        this.traitSet = traits;
    }


    @Override
    public AlgDataType deriveRowType() {
        return AlgOptUtil.createDmlRowType( Kind.INSERT, getCluster().getTypeFactory() );
    }


    @Override
    public String algCompareString() {
        String compare = "$" + getClass().getSimpleName() + "$" + operation + "$" + input.algCompareString();
        if ( keys != null ) {
            compare += "$" + keys.hashCode() + "$" + updates.hashCode();
        }
        return compare + "$" + input.algCompareString();
    }


    @Override
    public DocType getDocType() {
        return DocType.MODIFY;
    }


    public boolean isInsert() {
        return operation == Modify.Operation.INSERT;
    }


    public boolean isDelete() {
        return operation == Modify.Operation.DELETE;
    }

}
