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

package org.polypheny.db.transaction.locking;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.AlgShuttleImpl;
import org.polypheny.db.algebra.core.JoinAlgType;
import org.polypheny.db.algebra.core.common.Modify.Operation;
import org.polypheny.db.algebra.logical.common.LogicalConditionalExecute;
import org.polypheny.db.algebra.logical.common.LogicalConstraintEnforcer;
import org.polypheny.db.algebra.logical.document.LogicalDocIdentifier;
import org.polypheny.db.algebra.logical.document.LogicalDocumentAggregate;
import org.polypheny.db.algebra.logical.document.LogicalDocumentFilter;
import org.polypheny.db.algebra.logical.document.LogicalDocumentModify;
import org.polypheny.db.algebra.logical.document.LogicalDocumentProject;
import org.polypheny.db.algebra.logical.document.LogicalDocumentScan;
import org.polypheny.db.algebra.logical.document.LogicalDocumentSort;
import org.polypheny.db.algebra.logical.document.LogicalDocumentTransformer;
import org.polypheny.db.algebra.logical.document.LogicalDocumentValues;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgAggregate;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgFilter;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgIdentifier;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgMatch;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgModify;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgProject;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgScan;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgSort;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgTransformer;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgUnwind;
import org.polypheny.db.algebra.logical.lpg.LogicalLpgValues;
import org.polypheny.db.algebra.logical.relational.LogicalRelAggregate;
import org.polypheny.db.algebra.logical.relational.LogicalRelCorrelate;
import org.polypheny.db.algebra.logical.relational.LogicalRelExchange;
import org.polypheny.db.algebra.logical.relational.LogicalRelFilter;
import org.polypheny.db.algebra.logical.relational.LogicalRelIdentifier;
import org.polypheny.db.algebra.logical.relational.LogicalRelIntersect;
import org.polypheny.db.algebra.logical.relational.LogicalRelJoin;
import org.polypheny.db.algebra.logical.relational.LogicalRelMatch;
import org.polypheny.db.algebra.logical.relational.LogicalRelMinus;
import org.polypheny.db.algebra.logical.relational.LogicalRelModify;
import org.polypheny.db.algebra.logical.relational.LogicalRelProject;
import org.polypheny.db.algebra.logical.relational.LogicalRelScan;
import org.polypheny.db.algebra.logical.relational.LogicalRelSort;
import org.polypheny.db.algebra.logical.relational.LogicalRelTableFunctionScan;
import org.polypheny.db.algebra.logical.relational.LogicalRelUnion;
import org.polypheny.db.algebra.logical.relational.LogicalRelValues;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactoryImpl;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.locking.DeferredAlgTreeModification.Modification;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeFactoryImpl;
import org.polypheny.db.type.entity.PolyBoolean;
import org.polypheny.db.type.entity.PolyString;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.graph.PolyEdge;
import org.polypheny.db.type.entity.graph.PolyNode;

public class AlgTreeRewriter extends AlgShuttleImpl {

    public static final AlgDataType BOOLEAN_TRUE_ALG_TYPE = ((PolyTypeFactoryImpl) AlgDataTypeFactoryImpl.DEFAULT).createBasicPolyType( PolyType.BOOLEAN, true );
    public static final AlgDataType SINGLE_VERSION_ROW_ALG_TYPE = AlgDataTypeFactoryImpl.DEFAULT.createStructType( List.of(0L), List.of(IdentifierUtils.VERSION_ALG_TYPE), List.of("_vid"));

    private final Statement statement;
    private final Set<DeferredAlgTreeModification> pendingModifications;
    private boolean containsIdentifierKey;


    public AlgTreeRewriter( Statement statement ) {
        this.statement = statement;
        this.pendingModifications = new HashSet<>();
        this.containsIdentifierKey = false;
    }


    public AlgRoot process( AlgRoot root ) {
        AlgNode rootAlg = root.alg.accept( this );
        if ( pendingModifications.isEmpty() ) {
            return root.withAlg( rootAlg );
        }
        throw new IllegalStateException( "No pending tree modifications must be left on root level." );
    }


    @Override
    protected <T extends AlgNode> T visitChild( T parent, int i, AlgNode child ) {
        T visited = super.visitChild( parent, i, child );
        return applyModificationsOrSkip( visited );
    }


    private <T extends AlgNode> T applyModificationsOrSkip( T node ) {
        if ( pendingModifications.isEmpty() ) {
            return node;
        }

        Iterator<DeferredAlgTreeModification> iterator = pendingModifications.iterator();
        while ( iterator.hasNext() ) {
            DeferredAlgTreeModification modification = iterator.next();
            if ( modification.notTargets( node ) ) {
                continue;
            }
            node = modification.applyOrSkip( node );
            iterator.remove();
        }
        return node;
    }


    private Transaction getTransaction() {
        return statement.getTransaction();
    }


    private List<AlgNode> getLastNNodes( int count ) {
        List<AlgNode> result = new ArrayList<>();
        for ( Iterator<AlgNode> it = stack.descendingIterator(); it.hasNext(); ) {
            AlgNode node = it.next();
            if ( count-- <= 0 ) {
                break;
            }
            result.add( 0, node );
        }
        return result;
    }

    /*
    private void updateCollectorInsertPosition( AlgNode current ) {
        List<AlgNode> trace = getLastNNodes( 2 );

        if ( trace.isEmpty() ) {
            collectorInsertPosition = current;
            return;
        }

        AlgNode first = trace.get( 0 );
        if ( first instanceof Filter ) {
            collectorInsertPosition = first;
            return;
        }
        if ( trace.size() == 1 ) {
            collectorInsertPosition = (first instanceof LpgMatch) ? first : current;
            return;
        }

        AlgNode second = trace.get( 1 );
        collectorInsertPosition = (second instanceof Filter) ? second : current;
    }
    */


    private Entity findEntity( AlgNode node ) {
        Entity entity = null;
        while ( entity == null && node != null ) {
            entity = node.getEntity();
            if ( node.getInputs().isEmpty() ) {
                continue;
            }
            node = node.getInput( 0 );
        }
        return entity;
    }


    @Override
    public AlgNode visit( LogicalRelAggregate aggregate ) {
        LogicalRelAggregate aggregate1 = visitChild( aggregate, 0, aggregate.getInput() );
        return aggregate1;
    }


    @Override
    public AlgNode visit( LogicalRelMatch match ) {
        LogicalRelMatch match1 = visitChild( match, 0, match.getInput() );
        return match1;
    }


    @Override
    public AlgNode visit( LogicalRelScan scan ) {
        if ( MvccUtils.isInNamespaceUsingMvcc( scan.getEntity() ) ) {
            pendingModifications.add( new DeferredAlgTreeModification( scan, Modification.LIMIT_REL_SCAN_TO_SNAPSHOT, statement ) );
        }
        return scan;
    }


    @Override
    public AlgNode visit( LogicalRelTableFunctionScan scan ) {
        LogicalRelTableFunctionScan tableFunctionScan1 = visitChildren( scan );
        return tableFunctionScan1;
    }


    @Override
    public AlgNode visit( LogicalRelValues values ) {
        // check for identifier not needed as this is done during sql validation
        return values;
    }


    @Override
    public AlgNode visit( LogicalRelFilter filter ) {
        LogicalRelFilter filter1 = visitChild( filter, 0, filter.getInput() );
        return filter1;
    }


    @Override
    public AlgNode visit( LogicalRelProject project ) {
        LogicalRelProject project1 = visitChildren( project );
        return project1;
    }


    @Override
    public AlgNode visit( LogicalRelJoin join ) {
        LogicalRelJoin join1 = visitChildren( join );
        return join1;
    }


    @Override
    public AlgNode visit( LogicalRelCorrelate correlate ) {
        LogicalRelCorrelate correlate1 = visitChildren( correlate );
        return correlate1;
    }


    @Override
    public AlgNode visit( LogicalRelUnion union ) {
        LogicalRelUnion union1 = visitChildren( union );
        return union1;
    }


    @Override
    public AlgNode visit( LogicalRelIntersect intersect ) {
        LogicalRelIntersect intersect1 = visitChildren( intersect );
        return intersect1;
    }


    @Override
    public AlgNode visit( LogicalRelMinus minus ) {
        LogicalRelMinus minus1 = visitChildren( minus );
        return minus1;
    }


    @Override
    public AlgNode visit( LogicalRelSort sort ) {
        LogicalRelSort sort1 = visitChildren( sort );
        return sort1;
    }


    @Override
    public AlgNode visit( LogicalRelExchange exchange ) {
        LogicalRelExchange exchange1 = visitChildren( exchange );
        return exchange1;
    }


    @Override
    public AlgNode visit( LogicalConditionalExecute lce ) {
        LogicalConditionalExecute lce1 = visitChildren( lce );
        return lce1;
    }


    @Override
    public AlgNode visit( LogicalRelModify modify ) {

        LogicalRelModify modify1 = visitChildren( modify );

        if ( !MvccUtils.isInNamespaceUsingMvcc( modify1.getEntity() ) ) {
            return modify1;
        }

        if ( containsIdentifierKey ) {
            IdentifierUtils.throwIllegalFieldName();
        }

        getTransaction().addWrittenEntitiy( modify1.getEntity() );

        switch ( modify1.getOperation() ) {
            case INSERT:
                AlgNode input = modify1.getInput();
                LogicalRelIdentifier identifier = LogicalRelIdentifier.create(
                        modify1.getEntity(),
                        input,
                        input.getTupleType()
                );
                return modify1.copy( modify1.getTraitSet(), List.of( identifier ) );

            case UPDATE:
                /* Rewrite:
                   Update <- Filter <- Input

                   to

                                 v Project <- RelValues (for new _vid)
                   Insert <- Join
                                 ^ Project (to remove old _vid) <- Filter <- Input
                 */

                PolyValue newVersion = IdentifierUtils.getVersionAsPolyLong( getTransaction().getSequenceNumber(), false );
                LogicalRelValues newVersionValues = LogicalRelValues.create(
                        modify1.getCluster(),
                        SINGLE_VERSION_ROW_ALG_TYPE,
                        ImmutableList.of( ImmutableList.of(
                                new RexLiteral(
                                        newVersion,
                                        IdentifierUtils.VERSION_ALG_TYPE,
                                        PolyType.BIGINT )
                        ) ) );

                LogicalRelProject versionProject = LogicalRelProject.create(
                        newVersionValues,
                        List.of(new RexIndexRef(0, IdentifierUtils.VERSION_ALG_TYPE) ),
                        List.of(IdentifierUtils.VERSION_KEY));

                List<AlgDataTypeField> inputFields = modify1.getInput().getTupleType().getFields().stream()
                        .filter( f -> !f.getName().equals( IdentifierUtils.VERSION_KEY ) ).toList();
                List<RexIndexRef> inputProjects = inputFields.stream()
                        .map( f -> new RexIndexRef( f.getIndex(), f.getType()) )
                        .toList();
                LogicalRelProject inputProject = LogicalRelProject.create(
                        modify1.getInput(),
                        inputProjects,
                        inputFields.stream().map( AlgDataTypeField::getName ).toList()
                );

                LogicalRelJoin versionToInputJoin = LogicalRelJoin.create(
                        inputProject,
                        versionProject,
                        new RexLiteral( PolyBoolean.TRUE, BOOLEAN_TRUE_ALG_TYPE, PolyType.BOOLEAN ),
                        Set.of(),
                        JoinAlgType.INNER );

                return LogicalRelModify.create(
                        modify1.getEntity(),
                        versionToInputJoin,
                        Operation.INSERT,
                        null,
                        null,
                        false
                        );

            default:
                return modify1;
        }
    }


    @Override
    public AlgNode visit( LogicalConstraintEnforcer enforcer ) {
        LogicalConstraintEnforcer enforcer1 = visitChildren( enforcer );
        return enforcer1;
    }


    @Override
    public AlgNode visit( LogicalLpgModify modify ) {

        LogicalLpgModify modify1 = visitChildren( modify );

        if ( !MvccUtils.isInNamespaceUsingMvcc( modify.getEntity() ) ) {
            return modify1;
        }

        if ( containsIdentifierKey ) {
            IdentifierUtils.throwIllegalFieldName();
        }

        statement.getTransaction().addWrittenEntitiy( modify.getEntity() );

        switch ( modify1.getOperation() ) {
            case INSERT:
                AlgNode input = modify1.getInput();
                LogicalLpgIdentifier identifier = LogicalLpgIdentifier.create(
                        modify1.getEntity(),
                        input
                );
                return modify1.copy( modify1.getTraitSet(), List.of( identifier ) );
            case UPDATE:
                IdentifierUtils.throwIfContainsDisallowedKey( modify1 );
            default:
                return modify1;
        }
    }


    @Override
    public AlgNode visit( LogicalLpgScan scan ) {
        if ( MvccUtils.isInNamespaceUsingMvcc( scan.getEntity() ) ) {
            pendingModifications.add( new DeferredAlgTreeModification( scan, Modification.LIMIT_LPG_SCAN_TO_SNAPSHOT, statement ) );
        }
        return scan;
    }


    @Override
    public AlgNode visit( LogicalLpgValues values ) {
        for ( PolyNode node : values.getNodes() ) {
            containsIdentifierKey |= node.properties.containsKey( new PolyString( IdentifierUtils.IDENTIFIER_KEY ) );
            containsIdentifierKey |= node.properties.containsKey( new PolyString( IdentifierUtils.VERSION_KEY ) );
        }
        for ( PolyEdge edge : values.getEdges() ) {
            containsIdentifierKey |= edge.properties.containsKey( new PolyString( IdentifierUtils.IDENTIFIER_KEY ) );
            containsIdentifierKey |= edge.properties.containsKey( new PolyString( IdentifierUtils.VERSION_KEY ) );
        }
        return values;
    }


    @Override
    public AlgNode visit( LogicalLpgFilter filter ) {
        LogicalLpgFilter filter1 = visitChildren( filter );
        return filter1;
    }


    @Override
    public AlgNode visit( LogicalLpgMatch match ) {
        LogicalLpgMatch match1 = visitChildren( match );
        return match1;
    }


    @Override
    public AlgNode visit( LogicalLpgProject project ) {
        LogicalLpgProject project1 = visitChildren( project );
        return project1;
    }


    @Override
    public AlgNode visit( LogicalLpgAggregate aggregate ) {
        LogicalLpgAggregate aggregate1 = visitChildren( aggregate );
        return aggregate1;
    }


    @Override
    public AlgNode visit( LogicalLpgSort sort ) {
        LogicalLpgSort sort1 = visitChildren( sort );
        return sort1;
    }


    @Override
    public AlgNode visit( LogicalLpgUnwind unwind ) {
        LogicalLpgUnwind unwind1 = visitChildren( unwind );
        return unwind1;
    }


    @Override
    public AlgNode visit( LogicalLpgTransformer transformer ) {
        LogicalLpgTransformer unwind1 = visitChildren( transformer );
        return unwind1;
    }


    @Override
    public AlgNode visit( LogicalDocumentModify modify ) {

        LogicalDocumentModify modify1 = visitChildren( modify );

        if ( !MvccUtils.isInNamespaceUsingMvcc( modify.getEntity() ) ) {
            return modify1;
        }

        if ( containsIdentifierKey ) {
            IdentifierUtils.throwIllegalFieldName();
        }

        statement.getTransaction().addWrittenEntitiy( modify.getEntity() );

        switch ( modify1.getOperation() ) {
            case INSERT:
                AlgNode input = modify1.getInput();
                LogicalDocIdentifier identifier = LogicalDocIdentifier.create(
                        modify1.getEntity(),
                        input
                );
                return modify1.copy( modify1.getTraitSet(), List.of( identifier ) );
            case UPDATE:
                IdentifierUtils.throwIfContainsDisallowedKey( modify1.getUpdates().keySet() );
                return modify1;

        }
        return modify1;
    }


    @Override
    public AlgNode visit( LogicalDocumentAggregate aggregate ) {
        LogicalDocumentAggregate aggregate1 = visitChildren( aggregate );
        return aggregate1;
    }


    @Override
    public AlgNode visit( LogicalDocumentFilter filter ) {
        LogicalDocumentFilter filter1 = visitChildren( filter );
        return filter1;
    }


    @Override
    public AlgNode visit( LogicalDocumentProject project ) {
        LogicalDocumentProject project1 = visitChildren( project );
        return project1;
    }


    @Override
    public AlgNode visit( LogicalDocumentScan scan ) {
        if ( MvccUtils.isInNamespaceUsingMvcc( scan.getEntity() ) ) {
            pendingModifications.add( new DeferredAlgTreeModification( scan, Modification.LIMIT_DOC_SCAN_TO_SNAPSHOT, statement ) );
        }
        return visitChildren( scan );
    }


    @Override
    public AlgNode visit( LogicalDocumentSort sort ) {
        LogicalDocumentSort sort1 = visitChildren( sort );
        return sort1;
    }


    @Override
    public AlgNode visit( LogicalDocumentTransformer transformer ) {
        LogicalDocumentTransformer sort1 = visitChildren( transformer );
        return sort1;
    }


    @Override
    public AlgNode visit( LogicalDocumentValues values ) {
        containsIdentifierKey |= IdentifierUtils.containsDisallowedKeys( values.getDocuments() );
        return values;
    }


    @Override
    public AlgNode visit( AlgNode other ) {
        return visitChildren( other );
    }

}
