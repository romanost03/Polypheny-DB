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

package org.polypheny.db.transaction;


import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.adapter.Adapter;
import org.polypheny.db.adapter.index.IndexManager;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.algebra.AlgRoot;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.logical.common.LogicalConstraintEnforcer.EnforcementInformation;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.catalog.entity.LogicalConstraint;
import org.polypheny.db.catalog.entity.LogicalUser;
import org.polypheny.db.catalog.entity.logical.LogicalKey.EnforcementTime;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.snapshot.Snapshot;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.monitoring.core.MonitoringServiceProvider;
import org.polypheny.db.monitoring.events.StatementEvent;
import org.polypheny.db.prepare.JavaTypeFactoryImpl;
import org.polypheny.db.processing.ConstraintEnforceAttacher;
import org.polypheny.db.processing.DataMigrator;
import org.polypheny.db.processing.DataMigratorImpl;
import org.polypheny.db.processing.Processor;
import org.polypheny.db.processing.QueryProcessor;
import org.polypheny.db.transaction.locking.Lockable;
import org.polypheny.db.transaction.locking.MonotonicNumberSource;
import org.polypheny.db.type.entity.category.PolyNumber;
import org.polypheny.db.util.DeadlockException;
import org.polypheny.db.util.Pair;
import org.polypheny.db.view.MaterializedViewManager;


@Slf4j
public class TransactionImpl implements Transaction, Comparable<Object> {

    private static final AtomicLong TRANSACTION_COUNTER = new AtomicLong();

    @Getter
    private final long id;

    @Getter
    private final long sequenceNumber;

    @Getter
    private final PolyXid xid;

    @Getter
    private final AtomicBoolean cancelFlag = new AtomicBoolean();

    @Getter
    private final LogicalUser user;
    @Getter
    private final LogicalNamespace defaultNamespace;
    @Getter
    private final TransactionManagerImpl transactionManager;

    @Getter
    private final String origin;

    @Getter
    private final MultimediaFlavor flavor;

    @Getter
    @Setter
    private boolean analyze;

    private final List<Statement> statements = new ArrayList<>();


    @Getter
    private final Set<LogicalTable> usedTables = new TreeSet<>();

    @Getter
    private final Map<Long, List<LogicalConstraint>> entityConstraints = new HashMap<>();

    @Getter
    private final Set<Adapter<?>> involvedAdapters = new ConcurrentSkipListSet<>( ( a, b ) -> Math.toIntExact( a.adapterId - b.adapterId ) );

    private boolean useCache = true;

    private boolean acceptsOutdated = false;

    private AccessMode accessMode = AccessMode.NO_ACCESS;

    @Getter
    private final JavaTypeFactory typeFactory = new JavaTypeFactoryImpl();

    private final Catalog catalog = Catalog.getInstance();

    @Getter
    private Set<Lockable> lockedEntities = new HashSet<>();

    private Set<Entity> writtenEntities = new HashSet<>();


    TransactionImpl(
            PolyXid xid,
            TransactionManagerImpl transactionManager,
            LogicalUser user,
            LogicalNamespace defaultNamespace,
            boolean analyze,
            String origin,
            MultimediaFlavor flavor ) {
        this.id = TRANSACTION_COUNTER.getAndIncrement();
        this.xid = xid;
        this.transactionManager = transactionManager;
        this.user = user;
        this.defaultNamespace = defaultNamespace;
        this.analyze = analyze;
        this.origin = origin;
        this.flavor = flavor;
        this.sequenceNumber = MonotonicNumberSource.getInstance().getNextNumber();
    }


    @Override
    public Snapshot getSnapshot() {
        return catalog.getSnapshot();
    }


    @Override
    public InformationManager getQueryAnalyzer() {
        return InformationManager.getInstance( xid.toString() );
    }


    @Override
    public void registerInvolvedAdapter( Adapter<?> adapter ) {
        involvedAdapters.add( adapter );
    }


    @Override
    public void commit() throws TransactionException {
        if ( !isActive() ) {
            log.trace( "This transaction has already been finished!" );
            return;
        }
        boolean okToCommit = true;

        if ( !writtenEntities.isEmpty() ) {
            okToCommit &= validateWriteSet();
        }

        Pair<Boolean, String> isValid = catalog.checkIntegrity();
        if ( !isValid.left ) {
            throw new TransactionException( isValid.right + "\nThere are violated constraints, the transaction was rolled back!" );
        }

        // physical changes
        catalog.executeCommitActions();

        // Prepare to commit changes on all involved adapters and the catalog
        if ( RuntimeConfig.TWO_PC_MODE.getBoolean() ) {
            for ( Adapter<?> adapter : involvedAdapters ) {
                okToCommit &= adapter.prepare( xid );
            }
        }

        if ( !usedTables.isEmpty() ) {
            Statement statement = createStatement();
            QueryProcessor processor = statement.getQueryProcessor();
            List<EnforcementInformation> infos = ConstraintEnforceAttacher
                    .getConstraintAlg( usedTables, statement, EnforcementTime.ON_COMMIT );
            List<PolyImplementation> results = infos
                    .stream()
                    .map( s -> processor.prepareQuery( AlgRoot.of( s.control(), Kind.SELECT ), s.control().getCluster().getTypeFactory().builder().build(), false, true, false ) ).toList();
            List<List<?>> rows = results.stream().map( r -> r.execute( statement, -1 ).getAllRowsAndClose() ).filter( r -> !r.isEmpty() ).collect( Collectors.toList() );
            if ( !rows.isEmpty() ) {
                int index = ((List<PolyNumber>) rows.get( 0 ).get( 0 )).get( 1 ).intValue();
                String error = infos.get( 0 ).errorMessages().get( index ) + "\nThere are violated constraints, the transaction was rolled back!";
                rollback( error );
                throw new TransactionException( error );
            }
        }

        if ( okToCommit ) {
            // Commit changes
            for ( Adapter<?> adapter : involvedAdapters ) {
                adapter.commit( xid );
            }
            if ( involvedAdapters.isEmpty() ) {
                log.debug( "No adapter used." );
            }

            this.statements.forEach( statement -> {
                if ( statement.getMonitoringEvent() != null ) {
                    StatementEvent eventData = statement.getMonitoringEvent();
                    eventData.setCommitted( true );
                    MonitoringServiceProvider.getInstance().monitorEvent( eventData );
                }
            } );

            IndexManager.getInstance().commit( this.xid );
        } else {
            rollback( "Unable to prepare all involved entities for commit. Rollback changes!" );
            throw new TransactionException( "Unable to prepare all involved entities for commit. Changes have been rolled back." );
        }

        catalog.commit();

        // Free resources hold by statements
        statements.forEach( Statement::close );

        updateCommitInstantLog();

        // Release locks
        releaseAllLocks();
        // Remove transaction
        transactionManager.removeTransaction( xid );

        // Handover information about commit to Materialized Manager
        MaterializedViewManager.getInstance().updateCommittedXid( xid );

    }

    private boolean validateWriteSet() {
        /*
        ToDo TH: get the write set based on the transaction id and the written entities and compare to other comitted entities

        1) get read set

        pseudocode:
        writtenIds = []
        for each entity in writtenEntities:
             writtenIds.add (SELECT _eid, _vid FROM entity WHERE _vid = TxID;

        2) get latest committed version
            SELECT MAX(_vid) AS max_vid
            FROM main_table
            WHERE _eid IN (?, ?, ?, ...);

        pseudocode:
        maxVersion = 0
        for each entity in writtenEntities:
            maxVersion.updateIfGreater(
                SELECT MAX(_vid) AS max_vid
                FROM entity
                WHERE _eid IN (?, ?, ?, ...); <-- those are the writtenIds set
            )

         return maxVid <= TxID
         */

        return true;
    }

    private void updateCommitInstantLog() {
        /*
        ToDo TH: update the vids of each written entity

        1) get read set as parameter for efficiency
        2) flip the sign of each of the -vid entries to vid

        pseudocode:
        for each entity in writeSet:
            UPDATE entity
            SET _vid = TxCommitTimestamp
            WHERE _vid = -TxID;
         */
    }


    @Override
    public void rollback( @Nullable String reason ) throws TransactionException {
        if ( !isActive() ) {
            log.trace( "This transaction has already been finished!" );
            return;
        }
        try {
            if ( reason != null ) {
                log.warn( "Rolling back because: \"{}\" transaction {} ", reason, xid );
            }

            //  Rollback changes to the adapters
            for ( Adapter<?> adapter : involvedAdapters ) {
                adapter.rollback( xid );
            }
            IndexManager.getInstance().rollback( this.xid );
            catalog.rollback();
            // Free resources hold by statements
            statements.forEach( statement -> {
                if ( statement.getMonitoringEvent() != null ) {
                    StatementEvent eventData = statement.getMonitoringEvent();
                    eventData.setCommitted( false );
                    MonitoringServiceProvider.getInstance().monitorEvent( eventData );
                }
                statement.close();
            } );
        } finally {
            // Release locks
            releaseAllLocks();
            // Remove transaction
            transactionManager.removeTransaction( xid );
        }
    }


    @Override
    public boolean isActive() {
        return transactionManager.isActive( xid );
    }


    @Override
    public Processor getProcessor( QueryLanguage language ) {
        // note dl, while caching the processors works in most cases,
        // it can lead to validator bleed when using multiple simultaneous insert for example
        // caching therefore is not possible atm
        return language.processorSupplier().get();
    }


    @Override
    public StatementImpl createStatement() {
        if ( !isActive() ) {
            throw new IllegalStateException( "Transaction is not active!" );
        }
        StatementImpl statement = new StatementImpl( this );
        statements.add( statement );
        return statement;
    }


    @Override
    public int compareTo( @NonNull Object o ) {
        Transaction that = (Transaction) o;
        return Integer.compare( this.xid.hashCode(), that.getXid().hashCode() );
    }


    @Override
    public int hashCode() {
        return Objects.hash( xid );
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }
        Transaction that = (Transaction) o;
        return xid.equals( that.getXid() );
    }


    @Override
    public void setUseCache( boolean useCache ) {
        this.useCache = useCache;
    }


    @Override
    public boolean getUseCache() {
        return this.useCache;
    }


    @Override
    public void addUsedTable( LogicalTable table ) {
        this.usedTables.add( table );
    }


    @Override
    public void removeUsedTable( LogicalTable table ) {
        this.usedTables.remove( table );
    }


    @Override
    public void getNewEntityConstraints( long entity ) {
        this.entityConstraints.getOrDefault( entity, List.of() );
    }


    @Override
    public void addNewConstraint( long entityId, LogicalConstraint constraint ) {
        this.entityConstraints.putIfAbsent( entityId, new ArrayList<>() );
        this.entityConstraints.get( entityId ).add( constraint );
    }


    @Override
    public List<LogicalConstraint> getUsedConstraints( long id ) {
        return this.entityConstraints.getOrDefault( id, List.of() );
    }


    @Override
    public void releaseAllLocks() {
        lockedEntities.forEach( lockedEntity -> {
            try {
                lockedEntity.release( this );

            } catch ( IllegalMonitorStateException e ) {
                throw new DeadlockException( MessageFormat.format( "Failed to release lock for transaction {0}", this ) );
            }
        } );
    }


    @Override
    public void acquireLockable( Lockable lockable, Lockable.LockType lockType ) {
        lockable.acquire( this, lockType );
        lockedEntities.add( lockable );
    }



    


    @Override
    public void wakeup() {
        Thread.currentThread().interrupt();
    }


    @Override
    public long getNumberOfStatements() {
        return statements.size();
    }


    @Override
    public DataMigrator getDataMigrator() {
        return new DataMigratorImpl();
    }

}
